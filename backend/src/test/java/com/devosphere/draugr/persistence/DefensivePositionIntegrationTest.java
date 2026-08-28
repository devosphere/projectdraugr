package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defensive positions (EPIC #123 / #132). A completed perimeter fence or a raised lookout gives the Chronicle the
 * advantage of prepared ground in a fight — the same effort counts for more. Proven self-calibrating: for one fixed
 * confrontation, binary-search the tactic level to the exact point where fighting on open ground is a loss, then show
 * that the same fight from behind a fence is no longer lost. Robust to whatever the underlying combat numbers are.
 * Skips without Docker.
 */
@SpringBootTest
class DefensivePositionIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle, chunk, pop;
    private final UUID action = UUID.nameUUIDFromBytes("draugr-defensive-position".getBytes());

    /** Restore a fixed footing before each confront so capability depends only on the tactic level being probed. */
    private void resetState() {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=50, injury_severity=0, pain_level=0 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE wildlife_population SET population_count=50, behavior_state='FORAGING' WHERE id=?", pop);
    }

    private boolean lost(int tactic, Instant now) {
        resetState();
        return "FAILED".equals(wildlife.confront(chronicle, chunk, action, now, tactic).outcome());
    }

    @Test
    void aDefensivePositionTurnsAConfrontThatWouldOtherwiseBeLost() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        chronicle = summary.id();
        chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = ticks.current().simulatedAt();

        // One animal to face; clear any other populations so the confront is deterministic.
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',60)", site, world, chunk);
        pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'lone_predator','CARNIVORE','DIURNAL',50,80,'FORAGING',?)", pop, site, Timestamp.from(now));

        // The confront is monotonic in tactic: a very low tactic is a certain loss, a very high one a certain win.
        assertTrue(lost(-300, now), "at a hopeless tactic level, an open-ground confront is lost");
        assertTrue(!lost(300, now), "at an overwhelming tactic level, the confront is won");

        // Binary-search the exact boundary: the highest tactic level at which open ground still loses (deficit ~1).
        int lo = -300, hi = 300;
        while (lo < hi) {
            int mid = Math.floorDiv(lo + hi + 1, 2);
            if (lost(mid, now)) lo = mid; else hi = mid - 1;
        }
        int boundary = lo;
        assertTrue(lost(boundary, now), "on open ground, this confront at the boundary tactic is lost");

        // The same confront, at the same tactic, from behind a completed fence: the position edge turns the loss.
        UUID fence = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Wattle fence',?)", fence, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'WATTLE_FENCE','COMPLETED',100,?,100)", fence, Timestamp.from(now));
        resetState();
        String fenced = wildlife.confront(chronicle, chunk, action, now, boundary).outcome();
        assertTrue(!"FAILED".equals(fenced),
            () -> "a defensive position must turn a would-be loss into at least a stand, got " + fenced + " at boundary tactic " + boundary);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
