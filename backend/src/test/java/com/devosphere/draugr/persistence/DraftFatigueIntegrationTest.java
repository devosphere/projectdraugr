package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.action.ChronicleActionService;
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
 * Draft welfare (EPIC #100 / #101). A working beast tires: its haul falls as it is worked, and comes back only with
 * rest. Proven on the haul-capacity: a fresh aurochs adds its full haul; working it drops the haul in step with its
 * fatigue, to nothing when spent; a spell of rest restores it. Skips without Docker.
 */
@SpringBootTest
class DraftFatigueIntegrationTest {

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
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
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

    @Test
    void aWorkedBeastHaulsLessUntilItIsRested() {
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
        assertEquals(base + 250000, items.sustainedMassCapacity(chronicle), "a fresh aurochs adds its full haul");

        // Work it twice (fatigue 40): the haul falls in step — 60% of 250 kg. (Working also builds conditioning, which
        // eases fatigue's bite — a separate lever tested in DraftConditioningIntegrationTest — so zero it here to read
        // the fatigue scaling alone.)
        items.workDraftBeasts(chronicle);
        items.workDraftBeasts(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        assertEquals(base + 150000, items.sustainedMassCapacity(chronicle), "a part-worked beast hauls less");

        // Work it to spent (fatigue 100): it adds nothing.
        items.workDraftBeasts(chronicle);
        items.workDraftBeasts(chronicle);
        items.workDraftBeasts(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        assertEquals(base, items.sustainedMassCapacity(chronicle), "a spent beast hauls nothing until it is rested");

        // A spell of rest through the public action pipeline recovers it (fatigue 100 -> 60): 40% of 250 kg back.
        assertEquals("SUCCEEDED", actions.resolve("rest a while").outcome(), "resting succeeds");
        assertEquals(base + 100000, items.sustainedMassCapacity(chronicle), "rest brings the beast's haul back");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
