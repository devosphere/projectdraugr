package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animal pen (EPIC #100 / #108). A beast kept in a pen recovers even while its keeper works. Proven end to end:
 * a spent aurochs adds no haul; a pen is built through the public pipeline; and after a turn of the world the penned
 * beast has recovered some fatigue and hauls again — without the keeper having to stop and rest. Skips without Docker.
 */
@SpringBootTest
class DraftPenIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void tameAnAurochs(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',20)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'aurochs','HERBIVORE','DIURNAL',2,4,'FORAGING',?)", pop, site, Timestamp.from(now));
        jdbc.update("INSERT INTO wildlife_bond (chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,'TAMED',100,10,?)", chronicle, pop, Timestamp.from(now));
    }

    private boolean penStands(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN world_object cw ON cw.id=? WHERE cp.project_kind='ANIMAL_PEN' AND cp.state='COMPLETED' " +
            "AND w.current_location_id=cw.current_location_id AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n != null && n > 0;
    }

    @Test
    void aPennedBeastRecoversFatigueWhileTheKeeperWorks() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, maximum_single_lift_grams=500000 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle); // easy draft ground: isolate fatigue from terrain (#103)
        Instant now = ticks.current().simulatedAt();
        int base = 500000;

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);
        // Work the beast to spent — it hauls nothing. (Working also builds conditioning, which eases fatigue — a
        // separate lever tested in DraftConditioningIntegrationTest — so zero it here to read the fatigue alone.)
        for (int i = 0; i < 5; i++) items.workDraftBeasts(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        assertEquals(base, items.sustainedMassCapacity(chronicle), "a spent beast hauls nothing");

        // Build a pen through the public pipeline.
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        ChronicleActionService.ActionResult pen = actions.resolve("build a pen");
        assertEquals("SUCCEEDED", pen.outcome(), () -> "raising a pen must succeed: " + pen.perception());
        assertTrue(penStands(chronicle), "a completed pen stands where the keeper is");

        // A turn of the world: the penned beast recovers, even as the keeper is about other work.
        int spent = items.sustainedMassCapacity(chronicle);
        ticks.advanceBy(Duration.ofHours(1));
        int rested = items.sustainedMassCapacity(chronicle);
        assertTrue(rested > spent, () -> "a penned beast must recover haul over a turn (spent=" + spent + ", rested=" + rested + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
