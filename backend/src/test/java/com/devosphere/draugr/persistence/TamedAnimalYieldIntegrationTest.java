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
 * Keeping animals must give something back. fowl_egg, goat_milk and wool_tuft each declared an item_source of
 * TAMED_YIELD, but nothing produced them and TAMED_YIELD was handled nowhere — so a tamed goat gave no milk and
 * tamed fowl laid nothing a Chronicle could gather. Proves milk and eggs can now be taken from tamed stock, that
 * the produce is perishable, and that an animal must rest before it gives again. Skips without Docker.
 */
@SpringBootTest
class TamedAnimalYieldIntegrationTest {

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

    private void tame(UUID chronicle, UUID chunk, UUID worldId, String species, Timestamp ts) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',2,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,12,?)", UUID.randomUUID(), chronicle, pop, ts);
    }

    @Test
    void tamedStockGiveMilkAndEggsAndMustRestBetween() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());

        // With nothing tamed, there is nothing to milk — and it says so rather than inventing a pail.
        var nothing = actions.resolve("milk the goat");
        assertEquals("FAILED", nothing.outcome(), () -> "with no tamed milk animal, milking must fail: " + nothing.perception());

        tame(chronicle, chunk, worldId, "mountain_goat", ts);
        var milked = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", milked.outcome(), () -> "a tamed goat must give milk: " + milked.perception());
        assertTrue(items.hasAtLeast(chronicle, "goat_milk", 1), "the milk must be in hand");
        assertEquals("FRESH", jdbc.queryForObject(
            "SELECT f.preparation_kind FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id WHERE i.item_key='goat_milk' LIMIT 1", String.class),
            "milk is perishable from the moment it is drawn");

        // It has given what it has; it cannot be milked again on the spot.
        var again = actions.resolve("milk the goat");
        assertEquals("FAILED", again.outcome(), () -> "a milked-out animal must be allowed to rest: " + again.perception());

        // Tamed fowl lay eggs a Chronicle can gather.
        tame(chronicle, chunk, worldId, "marsh_fowl", ts);
        var eggs = actions.resolve("collect the eggs");
        assertEquals("SUCCEEDED", eggs.outcome(), () -> "tamed fowl must give eggs: " + eggs.perception());
        assertTrue(items.hasAtLeast(chronicle, "fowl_egg", 1), "the eggs must be in hand");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
