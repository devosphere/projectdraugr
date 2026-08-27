package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A survey warns before the crop is lost (EPIC #162). A stand left ripe past its clean window begins to go over — the
 * heads shatter — and the survey now reads that warning, the way a fire's danger is read before it takes hold, so a
 * Chronicle can bring the harvest in before the birds and weather take it. A stand still freshly ripe reads only as
 * ready to reap. Skips gracefully without Docker.
 */
@SpringBootTest
class CropGoingOverSurveyIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String survey(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    private void sow(UUID chunk, Instant sownAt) {
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,30,false)",
            UUID.randomUUID(), chunk, "wild_grain", Timestamp.from(sownAt));
    }

    @Test
    void aSurveyWarnsWhenARipeCropIsGoingOver() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        java.util.List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        UUID goingOver = chunks.get(0), freshlyRipe = chunks.get(1);
        Instant now = ticks.current().simulatedAt();

        // One stand ripe eighteen days past its season (into the shattering window, not yet lost); one ripe five days.
        sow(goingOver, now.minus(Duration.ofDays(48)));   // grown 48, maturity 30 → 18 days past ripe
        sow(freshlyRipe, now.minus(Duration.ofDays(35)));  // grown 35 → 5 days past ripe

        String over = survey(chronicle, goingOver);
        assertTrue(over.contains("going over"), () -> "a crop past its clean window must read as going over, a warning to reap: " + over);

        String ripe = survey(chronicle, freshlyRipe);
        assertTrue(ripe.contains("ready to reap"), () -> "a freshly ripe crop must read as ready to reap: " + ripe);
        assertFalse(ripe.contains("going over"), () -> "a freshly ripe crop must not read as going over: " + ripe);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
