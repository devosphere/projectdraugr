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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defensive positions (EPIC #123 / #132). A completed perimeter fence or a raised lookout gives the Chronicle the
 * advantage of prepared ground in a fight — the same effort counts for more. Proven deterministically: across the same
 * sweep of rolls, a Chronicle fighting from behind a fence wins more confrontations than the same Chronicle on open
 * ground. Skips without Docker.
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

    /** Count wins (a kill or a driven-off animal) over a fixed sweep of deterministic rolls, resetting between each so
     *  every roll starts from the same footing. */
    private int winsOverSweep(UUID chronicle, UUID chunk, UUID pop, Instant now) {
        int wins = 0;
        for (int i = 0; i < 30; i++) {
            jdbc.update("UPDATE chronicle_physiology SET energy_level=45, injury_severity=0, pain_level=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE wildlife_population SET population_count=50, behavior_state='FORAGING' WHERE id=?", pop);
            UUID action = UUID.nameUUIDFromBytes(("draugr-confront-" + i).getBytes());
            WildlifeEncounterService.EncounterResult r = wildlife.confront(chronicle, chunk, action, now, 0);
            if ("SUCCEEDED".equals(r.outcome()) || "PARTIAL".equals(r.outcome())) wins++;
        }
        return wins;
    }

    @Test
    void fightingFromBehindAFenceWinsMoreThanOnOpenGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = ticks.current().simulatedAt();

        // A plain herbivore to face (no registry entry -> role-based resistance), unarmed, no fire.
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',60)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'plains_grazer','HERBIVORE','DIURNAL',50,80,'FORAGING',?)", pop, site, Timestamp.from(now));

        // On open ground.
        int openWins = winsOverSweep(chronicle, chunk, pop, now);

        // Raise a completed fence at the fight, then run the same sweep from behind it.
        UUID fence = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Wattle fence',?)", fence, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'WATTLE_FENCE','COMPLETED',100,?,100)", fence, Timestamp.from(now));
        int fencedWins = winsOverSweep(chronicle, chunk, pop, now);

        assertTrue(fencedWins > openWins,
            () -> "a defensive position must win more confrontations than open ground (open=" + openWins + ", fenced=" + fencedWins + ")");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
