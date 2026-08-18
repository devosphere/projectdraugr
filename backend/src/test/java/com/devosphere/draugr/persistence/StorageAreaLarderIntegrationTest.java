package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
 * Resource-store regression (#207 heritage STORAGE_AREA, EPIC #123). The tool shed and storage area were both
 * carried in the world's heritage as constructions, but STORAGE_AREA was wholly dead — registered in
 * construction_kind with nothing to build it and nothing to read it. Now a Chronicle can raise a covered camp
 * store from reachable materials, and a completed store on the ground ends the fresh-kill predator draw: a home
 * camp with a proper larder is somewhere a raw carcass can be brought back to, instead of riding on the body as
 * bait through predator ground. The counter-play the encounter code already named ("store or cache the meat and
 * the draw is gone") now has a built structure behind it.
 *
 * <p>Proven end to end: the store is built from carried stock (and the intent routes and mends), and with a raw
 * kill carried, a predator lands strictly fewer ambushes when a store stands than when none does. Skips gracefully
 * without Docker.
 */
@SpringBootTest
class StorageAreaLarderIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired ConstructionService construction;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awakenAt(UUID chunk) {
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    @Test
    void aResourceStoreIsBuiltFromReachableStockAndMended() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID chronicle = awakenAt(chunk);
        Instant base = ticks.current().simulatedAt();

        // The full acquisition path: a pole frame, a roof cover, cordage to lash it, and a blade to fit the frame.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", base, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", base, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", base, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", base, "TEST_SEED");

        String[] built = construction.buildStorageArea(chronicle, chunk, base);
        assertEquals("SUCCEEDED", built[0], () -> "raising a resource store from carried stock must succeed: " + built[1]);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='STORAGE_AREA' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk),
                "the store must stand as a persistent construction at the chunk");

        // The BUILD_STORAGE_AREA intent routes and mends an existing store rather than raising a second — proving
        // the classifier and the mend path, and guarding against a collision (e.g. with the STORE intent).
        items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", base, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult reBuilt = actions.resolve("I build a storage area.");
        assertEquals("SUCCEEDED", reBuilt.outcome(), () -> "the BUILD_STORAGE_AREA intent must route and mend: " + reBuilt.perception());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='STORAGE_AREA' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk),
                "mending must not raise a second store");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Landed ambushes over a fixed id set, resetting the hunter to HUNTING and the body to unhurt before each roll
     *  so every roll faces the same threat and the count reflects the draw alone, not accumulated wounds. */
    private int ambushes(UUID chronicle, UUID chunk, UUID hunter, Instant now, java.util.List<UUID> ids) {
        int hits = 0;
        for (UUID a : ids) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", hunter);
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, blood_loss_ml=0, pain_level=0 WHERE chronicle_id=?", chronicle);
            if (wildlife.passiveEncounter(chronicle, chunk, a, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aCampStoreEndsTheFreshKillDraw() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // A chunk of its own, with a hunting predator on the ground and no other construction to confound the read.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID chronicle = awakenAt(chunk);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Wolf hunting ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Wolf hunting ground',300)", site, world, chunk);
        UUID hunter = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gray_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", hunter, site, ts);

        // A fresh kill on the body — blood and scent that draws the hunt in.
        items.createCarriedItem(chronicle, "raw_game_meat", "Raw game meat", now, "TEST_SEED");

        java.util.Random rnd = new java.util.Random(7);
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) ids.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // No store at camp: the carried kill draws the hunt in at full strength.
        int withoutStore = ambushes(chronicle, chunk, hunter, now, ids);
        assertTrue(withoutStore > 0, "the carried kill must actually draw the hunt (else the test proves nothing)");

        // Raise a completed resource store here, isolating the read from the build (which the other test covers).
        UUID store = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Resource store',?)", store, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'STORAGE_AREA','COMPLETED',100,?,100)",
                store, ts);

        // With the store standing, the same kill goes to the larder — the draw does not follow the Chronicle here.
        int withStore = ambushes(chronicle, chunk, hunter, now, ids);
        assertTrue(withStore < withoutStore,
                () -> "a built camp store must end the fresh-kill draw (withStore=" + withStore + ", withoutStore=" + withoutStore + ") (#207)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
