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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading how well the water fishes before a line is cast (EPIC #181 / #36 fishing). With finite fish stocks (V166),
 * the OBSERVE survey now reports the state of the water — thick with fish, workable, fished thin, or fished out — the
 * parallel of the geological and woodland reads, so a stretch can be judged before it is worked.
 *
 * <p>Proven through the real OBSERVE pipeline: untouched wetland reads as thick with fish; a thinned stock reads as
 * fished thin; a spent stock reads as fished out. Skips gracefully without Docker.
 */
@SpringBootTest
class WaterSurveyIntegrationTest {

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
    void aSurveyReadsHowWellTheWaterFishes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to read");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", chunk); // a clean, unworked stretch

        // Untouched water reads as thick with fish.
        String full = surveyText();
        assertTrue(full.contains("thick with fish"),
                () -> "untouched wetland must read as thick with fish: " + full);

        // A thinned stock reads as fished thin. last_fished_at must be the simulated instant, or it would restock.
        jdbc.update("INSERT INTO fish_stock (chunk_id, remaining_units, last_fished_at) VALUES (?, 50, ?)", chunk, Timestamp.from(now));
        String thin = surveyText();
        assertTrue(thin.contains("fished thin"),
                () -> "a drawn-down stock must read as fished thin: " + thin);

        // A spent stock reads as fished out.
        jdbc.update("UPDATE fish_stock SET remaining_units=0, last_fished_at=? WHERE chunk_id=?", Timestamp.from(now), chunk);
        String bare = surveyText();
        assertTrue(bare.contains("fished out"),
                () -> "a spent stock must read as fished out: " + bare);
    }
}
