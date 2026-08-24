package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading standing timber before you fell it (EPIC #200 forestry / #201 persistent stands). A survey now reports how
 * wooded the ground is — a full natural stand where none has been cut, and a thinned or cut-out stand where one has —
 * so a wood can be read before an axe is set to it, like a seam before a pick.
 *
 * <p>Proven through the real OBSERVE pipeline: untouched forest reads as a thick stand of oak; a thinned stand reads
 * as thinned; a cut-out stand reads as cut out. Skips gracefully without Docker.
 */
@SpringBootTest
class WoodlandSurveyIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private String surveyText() {
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    @Test
    void aSurveyReadsHowWoodedTheGroundIs() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have forest ground (oak) to read");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        // Make sure no stand is recorded yet for this chunk (a clean natural wood).
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", chunk);

        // Untouched forest reads as a thick, full stand.
        String thick = surveyText();
        assertTrue(thick.contains("oak grows thick"),
                () -> "untouched forest must read as a full stand of oak: " + thick);

        // A thinned stand reads as thinned.
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, last_harvested_at) VALUES (?, 'oak', 3, 16, NULL)", chunk);
        String thin = surveyText();
        assertTrue(thin.contains("thinned"),
                () -> "a thinned stand must read as thinned: " + thin);

        // A cut-out stand reads as cut out.
        jdbc.update("UPDATE chunk_flora SET quantity=0 WHERE chunk_id=? AND flora_key='oak'", chunk);
        String bare = surveyText();
        assertTrue(bare.contains("cut out"),
                () -> "a cut-out stand must read as cut out: " + bare);
    }
}
