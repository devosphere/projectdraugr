package com.devosphere.draugr.persistence;

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
 * Draft training (EPIC #100 / #101 working-animal progression). A seasoned beast hardens to the draught: it feels less
 * of the fatigue a green beast does, so it keeps pulling where a green one would flag. Proven on the haul: at the same
 * high fatigue, a fully-conditioned beast still hauls where an unconditioned one hauls nothing; and working a beast
 * builds its conditioning. Skips without Docker.
 */
@SpringBootTest
class DraftConditioningIntegrationTest {

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

    private void setDraft(UUID chronicle, int fatigue, int conditioning) {
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=?, draft_conditioning=? WHERE chronicle_id=?", fatigue, conditioning, chronicle);
    }

    @Test
    void aSeasonedBeastEnduresFatigueAndWorkingBuildsConditioning() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, maximum_single_lift_grams=500000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        int base = 500000;

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);

        // A green beast worked to spent (fatigue 100, conditioning 0) hauls nothing.
        setDraft(chronicle, 100, 0);
        assertEquals(base, items.sustainedMassCapacity(chronicle), "a green, spent beast hauls nothing");

        // A fully-seasoned beast at the same fatigue feels only half of it, and still hauls half its worth.
        setDraft(chronicle, 100, 100);
        assertEquals(base + 125000, items.sustainedMassCapacity(chronicle), "a seasoned beast endures fatigue and still hauls");

        // Conditioning never lifts a fresh beast above its base haul — it is endurance, not strength.
        setDraft(chronicle, 0, 100);
        assertEquals(base + 250000, items.sustainedMassCapacity(chronicle), "a fresh beast hauls its base whatever its conditioning");

        // Working the beast builds its conditioning (the earned progression) as it accrues fatigue.
        setDraft(chronicle, 0, 0);
        items.workDraftBeasts(chronicle);
        Integer conditioning = jdbc.queryForObject("SELECT draft_conditioning FROM wildlife_bond WHERE chronicle_id=?", Integer.class, chronicle);
        assertTrue(conditioning != null && conditioning > 0, () -> "working a beast builds its draft conditioning, got " + conditioning);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
