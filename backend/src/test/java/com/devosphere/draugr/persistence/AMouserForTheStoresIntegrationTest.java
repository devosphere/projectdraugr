package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.survival.FoodPreservationService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mouser for the stores (#79, V327): the site, guard and pest-control role.
 *
 * <p>A fouled camp draws vermin that gnaw at a keeper's food (#218). A tamed wildcat or polecat living on that ground
 * hunts them, so the stores keep their span. It must be ON the ground: a cat kept somewhere else guards nothing here.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class AMouserForTheStoresIntegrationTest {

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
    @Autowired FoodPreservationService food;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID storedFood(UUID chronicle, Instant base, int exposureHours, int lifeHours) {
        UUID meat = items.createCarriedItem(chronicle, "raw_game_meat", "Raw game meat", base, "TEST_SEED");
        food.registerRaw(meat, base);
        jdbc.update("UPDATE food_preservation_state SET pest_checked_at=?, safe_until=?, spoiled_at=NULL WHERE object_id=?",
                Timestamp.from(base.minus(Duration.ofHours(exposureHours))), Timestamp.from(base.plus(Duration.ofHours(lifeHours))), meat);
        return meat;
    }

    private boolean spoiled(UUID foodId) {
        return jdbc.queryForObject("SELECT spoiled_at IS NOT NULL FROM food_preservation_state WHERE object_id=?", Boolean.class, foodId);
    }

    /** A tamed animal of this species, living on this ground. Returns its bond id. */
    private UUID keep(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept animal',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept animal',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'CARNIVORE','NOCTURNAL',1,3,'RESTING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,12,?)", bond, chronicle, pop, ts);
        return bond;
    }

    @Test
    void aKeptCatOnTheGroundKeepsTheVerminOffTheStores() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID camp = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID elsewhere = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' AND id<>? ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class, camp);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", camp, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant base = ticks.current().simulatedAt();

        // A refuse-choked camp.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,90,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=90, last_updated_at=?",
                camp, Timestamp.from(base), Timestamp.from(base));

        // No animal: the vermin take the stores, as #218 made them.
        UUID unguarded = storedFood(chronicle, base, 10, 5);
        food.advanceTo(base);
        assertTrue(spoiled(unguarded), "with nothing hunting them, a fouled camp's vermin spoil the stores");

        // A cat kept on other ground guards nothing here.
        keep(chronicle, elsewhere, "european_wildcat");
        UUID stillUnguarded = storedFood(chronicle, base, 10, 5);
        food.advanceTo(base);
        assertTrue(spoiled(stillUnguarded), "a cat that lives somewhere else does not keep rats out of this larder");

        // A cat kept on this ground does.
        keep(chronicle, camp, "european_wildcat");
        UUID guarded = storedFood(chronicle, base, 10, 5);
        food.advanceTo(base);
        assertFalse(spoiled(guarded), "a tamed wildcat on the camp keeps the vermin off the stores");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Both candidates #79 names are covered by a wild line the world already keeps. */
    @Test
    void theCatAndTheFerretAreBothPestHunters() {
        assertEquals(2, (int) jdbc.queryForObject(
            "SELECT count(*) FROM pest_hunter WHERE species_key IN ('european_wildcat','polecat')", Integer.class),
            "the house cat's wild line and the ferret's must both hunt vermin");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM pest_hunter ph JOIN wildlife_species ws USING (species_key) WHERE ws.tamability <= 0", Integer.class),
            "a pest hunter must be something a keeper can keep");
    }
}
