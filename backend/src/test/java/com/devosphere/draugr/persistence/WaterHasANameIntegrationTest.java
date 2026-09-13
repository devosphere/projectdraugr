package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The water has a name (#37).
 *
 * <p>The Chronicle 9 review asked for the water to be named — a stream, a lake, a pool — rather than the biome. The
 * world already places a still pond, a lake margin, a fast stream; drinking from any of them said "the clean, moving
 * water" or "the standing water here" alike. Now the site that makes the ground water names it.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WaterHasANameIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void drinkingAtAStillPondNamesThePond() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();

        // Dry grassland with no site of any kind, so the pond placed here is the only water there is.
        UUID dry = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=c.id) ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, dry);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", dry, chronicle);

        var parched = actions.resolve("I drink the water.");
        assertEquals("FAILED", parched.outcome(), () -> "dry grassland with no site holds nothing to drink: " + parched.perception());

        UUID pond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Still pond',?)", pond, dry);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'RESOURCE','Still pond',600)", pond, world, dry);

        var drank = actions.resolve("I drink the water.");
        assertEquals("SUCCEEDED", drank.outcome(), drank::perception);
        assertTrue(drank.perception().contains("still pond"), () -> "the water drunk from must be named for what it is: " + drank.perception());
        assertTrue(!drank.perception().contains("moving water") && !drank.perception().contains("standing water here"),
            () -> "a pond is not described by the old generic words: " + drank.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
