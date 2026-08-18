package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lookout regression (#127/#128, EPIC #123). A boundary scout (SCOUT) reads a predator one chunk out, but a
 * Chronicle on the flat ground cannot see past the near treeline. A built LOOKOUT now lifts the eye: scoutBoundary
 * reads a completed lookout at the chunk and reports danger a second chunk out along each way. Proven before and
 * after: with a hunting carnivore placed two chunks to the east and the near ground east left clear, a scout from
 * flat ground does not see it, but after a lookout is BUILT the same scout names danger further to the east.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class LookoutScoutIntegrationTest {

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
    @Autowired com.devosphere.draugr.ecology.WildlifeEncounterService wildlife;
    @Autowired com.devosphere.draugr.construction.ConstructionService construction;
    @Autowired com.devosphere.draugr.item.PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aRaisedLookoutSeesDangerASecondChunkOut() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // Stand the Chronicle on a chunk with ground two tiles to its east, and fabricate a hunting carnivore
        // exactly two chunks east — a controlled scenario that never depends on the shared world's seeded
        // populations. The distance-2 danger is only ever reported from a lookout (the near ring is dist-1), so
        // the near ground east needs no clearing for the before/after distinction to hold.
        Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk e WHERE e.world_id=c.world_id AND e.grid_x=c.grid_x+2 AND e.grid_y=c.grid_y) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chronicleChunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID farEastChunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, gx + 2, gy);
        java.sql.Timestamp ts = java.sql.Timestamp.from(ticks.current().simulatedAt());
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Far prowler territory',?)", site, farEastChunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Far prowler territory',400)", site, world, farEastChunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", UUID.randomUUID(), site, ts);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chronicleChunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Read the boundary directly (no tick) throughout, and build the lookout directly too, so the fabricated
        // far predator is never touched by the wildlife simulation (a single tick displaces a lone fabricated
        // population). From flat ground the far danger is invisible — the near treeline hides it.
        String flat = wildlife.scoutBoundary(chronicle, chronicleChunk, "HIGH", 0.0).narration();
        assertFalse(flat.toLowerCase(Locale.ROOT).contains("further to the east"),
                () -> "without a lookout a scout must not see two chunks out: " + flat);

        // Raise a lookout from carried poles with a blade and a lashing.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST_SEED");
        String[] built = construction.buildLookout(chronicle, chronicleChunk, now);
        assertEquals("SUCCEEDED", built[0], () -> "building a lookout must succeed: " + built[1]);
        Integer lookouts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='LOOKOUT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chronicleChunk);
        assertEquals(1, lookouts, "the lookout must stand as a persistent construction at the chunk");

        // The fabricated far carnivore must still stand (no tick has run to disturb it) for the lookout to see it.
        Integer farPred = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
                "WHERE es.chunk_id=? AND wp.ecological_role='CARNIVORE' AND wp.population_count>0", Integer.class, farEastChunk);
        assertTrue(farPred >= 1, "the fabricated far carnivore must still stand two chunks east");

        // From the lookout the same far danger is now in view.
        String raised = wildlife.scoutBoundary(chronicle, chronicleChunk, "HIGH", 0.0).narration();
        assertTrue(raised.toLowerCase(Locale.ROOT).contains("further to the east"),
                () -> "from the lookout a scout must see danger two chunks to the east (#127/#128): " + raised);

        // The BUILD_LOOKOUT intent also routes and mends an existing lookout (a tick here is harmless — the reads
        // are done), proving the acquisition path end to end.
        items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        ChronicleActionService.ActionResult reBuilt = actions.resolve("I build a lookout.");
        assertEquals("SUCCEEDED", reBuilt.outcome(), () -> "the BUILD_LOOKOUT intent must route and mend: " + reBuilt.perception());

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
