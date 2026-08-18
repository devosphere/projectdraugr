package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Habitat-restoration regression (EPIC #207, story #213). The Chronicle's counter-play to the disturbance they
 * cause: clearing the churned earth and replanting lowers a chunk's disturbance now, so the land grows quiet — and
 * the wildlife return to it — sooner than time and decay alone would bring. Honest work with a real, logged
 * effect; it fails grounded on ground that is already quiet. Proven end to end: restoring disturbed ground drops
 * its measured disturbance and logs the work, and the BUILD/RESTORE intent routes; quiet ground refuses.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class RestoreHabitatIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Integer level(UUID chunk) {
        return jdbc.query("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
    }

    @Test
    void restoringDisturbedGroundLowersItsDisturbanceAndFailsWhenQuiet() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // Ground worked hard by a fight.
        wildlife.recordDisturbance(chunk, "PREDATION", 60, now);
        assertEquals(60, (int) (Integer) level(chunk), "the disturbance must be recorded");

        // Tending the ground lowers the disturbance now and logs the work.
        String[] restored = wildlife.restoreHabitat(chunk, 30, now);
        assertEquals("SUCCEEDED", restored[0], () -> "restoring disturbed ground must succeed: " + restored[1]);
        assertEquals(30, (int) (Integer) level(chunk), "restoration must lower the measured disturbance (#213)");
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunk_disturbance_event WHERE chunk_id=? AND source_kind='RESTORATION'", Integer.class, chunk);
        assertTrue(events != null && events >= 1, "restoration must be kept in history (#208/#213)");

        // The RESTORE_HABITAT intent routes and lowers it further.
        ChronicleActionService.ActionResult r = actions.resolve("I restore the disturbed land here.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "the restore-habitat intent must route and succeed: " + r.perception());
        assertTrue(level(chunk) < 30, () -> "restoring again must lower the disturbance further: " + level(chunk));

        // Ground that is already quiet has nothing to mend.
        UUID quiet = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' AND id<>? " +
                "AND id NOT IN (SELECT chunk_id FROM chunk_disturbance) ORDER BY grid_y, grid_x LIMIT 1", UUID.class, chunk);
        String[] onQuiet = wildlife.restoreHabitat(quiet, 30, now);
        assertEquals("FAILED", onQuiet[0], "restoring already-quiet ground must fail grounded, not invent work");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
