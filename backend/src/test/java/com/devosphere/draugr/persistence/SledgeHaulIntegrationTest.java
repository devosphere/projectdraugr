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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sledge — the travois' heavier kin (EPIC #100, third draft-logistics slice). Proves the draft-haul wiring is
 * general: a sledge is a bigger load-bed than the travois, and — being a registered draft_vehicle — it too lets a
 * tamed draft beast add its haul to the handler's capacity. Skips without Docker.
 */
@SpringBootTest
class SledgeHaulIntegrationTest {

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
    void aMadeSledgeIsABiggerBedAndHitchesToADraftBeastLikeTheTravois() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=1000000, direct_bulk_ml=1000000, maximum_single_lift_grams=1000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Make a sledge through the public action pipeline.
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        ChronicleActionService.ActionResult make = actions.resolve("make a sledge");
        assertEquals("SUCCEEDED", make.outcome(), () -> "making a sledge must succeed: " + make.perception());

        UUID sledge = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='sledge' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' LIMIT 1", UUID.class, chronicle);
        assertNotNull(sledge, "the made sledge is owned by the Chronicle");

        // A bigger bed than the travois (400 kg vs 250 kg).
        Integer maxMass = jdbc.queryForObject("SELECT max_mass_grams FROM container_properties WHERE object_id=?", Integer.class, sledge);
        assertEquals(400000, maxMass, "the sledge bed holds more than a travois");

        // Being a registered draft_vehicle, a sledge + a tamed beast adds the beast's haul, exactly as the travois does.
        int before = items.sustainedMassCapacity(chronicle);
        tameAnAurochs(chronicle, now);
        int after = items.sustainedMassCapacity(chronicle);
        assertEquals(before + 250000, after, "a sledge hitched to a tamed aurochs adds the aurochs' haul");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
