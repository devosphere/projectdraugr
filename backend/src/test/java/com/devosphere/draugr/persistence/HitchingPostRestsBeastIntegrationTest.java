package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
 * Story #106 - handling and restraint infrastructure. A tamed draft beast could only be rested by building a full
 * animal pen; a hitching post is the simple way stock is actually held at a camp. Proves that building one lets a
 * tired beast stand and recover, exactly as a pen does. Skips without Docker.
 */
@SpringBootTest
class HitchingPostRestsBeastIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aHitchingPostLetsATiredBeastRest() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // A tamed ox, worked tired, bonded to this Chronicle.
        UUID site = UUID.randomUUID();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Pasture',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Pasture',50)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'ox','HERBIVORE','DIURNAL',1,3,'FORAGING',?)", pop, site, ts);
        UUID bond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at,draft_fatigue) " +
                "VALUES (?,?,?,'TAMED',90,10,?,80)", bond, chronicle, pop, ts);

        // No pen and no post yet: the beast stays tired.
        items.restPennedDraftBeasts(now);
        int stillTired = jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertEquals(80, stillTired, "with nowhere to be held, a tired beast does not recover");

        // Build a hitching post through the real action boundary.
        items.createCarriedItem(chronicle, "timber_log", "Timber log", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "fiber_cordage", "Fibre cordage", now, "TEST_FIXTURE");
        var built = actions.resolve("build a hitching post");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a hitching post must succeed: " + built.perception());
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE cp.project_kind='HITCHING_POST' AND cp.state='COMPLETED' AND w.current_location_id=?)", Boolean.class, chunk),
            "a completed hitching post must stand on this ground");

        // Now it can be tied and left standing, and it rests.
        items.restPennedDraftBeasts(now);
        int rested = jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertTrue(rested < 80, () -> "a beast held at a hitching post must recover (was 80, now " + rested + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
