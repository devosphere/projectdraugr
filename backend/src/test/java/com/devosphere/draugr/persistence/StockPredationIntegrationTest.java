package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kept stock must be losable, and penning them must be why they are not. Once tamed, an animal was safe forever —
 * nothing reduced a bonded animal's population — so every secure structure in the catalogue had nothing to be secure
 * against. Proves a predator takes unprotected stock in the dark, that a pen prevents it, and that a raided herd is
 * left alone afterwards rather than stripped. Skips without Docker.
 */
@SpringBootTest
class StockPredationIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int flockSize(UUID pop) {
        return jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop);
    }

    @Test
    void aPredatorTakesUnpennedStockButNotPennedOnes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Clear this ground, then set a tamed flock and a hunting predator on it, with nothing built.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Fold',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Fold',30)", site, worldId, chunk);
        UUID flock = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'mountain_goat','HERBIVORE','DIURNAL',4,6,'FORAGING',?)", flock, site, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,10,?)", UUID.randomUUID(), chronicle, flock, ts);
        UUID wolves = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gray_wolf','CARNIVORE','NOCTURNAL',3,5,'HUNTING',?)", wolves, site, ts);

        // By day, nothing is taken — this is a night risk.
        assertNull(wildlife.raidUnprotectedStock(chronicle, now, false), "stock are not taken in daylight");
        assertEquals(4, flockSize(flock), "the flock is whole by day");

        // Left out in the dark beside hunting wolves, one is taken.
        String taken = wildlife.raidUnprotectedStock(chronicle, now, true);
        assertNotNull(taken, "unprotected stock beside hunting predators must be at risk in the dark");
        assertEquals(3, flockSize(flock), "exactly one animal is taken, never the herd");

        // The herd is left alone afterwards rather than stripped.
        assertNull(wildlife.raidUnprotectedStock(chronicle, now, true), "a raided herd is left alone for a while");
        assertEquals(3, flockSize(flock), "no second loss inside the rest window");

        // Penned, they are safe — which is the whole reason to pen stock at night.
        UUID pen = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Animal pen','ACTIVE',?)", pen, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'ANIMAL_PEN','COMPLETED',100,?,100)", pen, ts);
        jdbc.update("UPDATE wildlife_bond SET last_raid_at=NULL WHERE chronicle_id=?", chronicle);

        assertNull(wildlife.raidUnprotectedStock(chronicle, now, true), "a completed pen must keep predators off the stock");
        assertEquals(3, flockSize(flock), "penned stock are not taken");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
