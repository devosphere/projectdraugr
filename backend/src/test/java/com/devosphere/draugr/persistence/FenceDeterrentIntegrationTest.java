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
 * Perimeter-fence deterrent regression (M1 #127, EPIC #123). The camp defence catalogue had a warning layer
 * (CAMP_ALARM) and a flight layer (DISENGAGE escape) but no BARRIER — nothing a Chronicle could put physically
 * between themselves and a predator's rush. A built perimeter fence now cuts the passive ambush chance (a woven
 * wattle wall stronger than a piled brush one): the predator must breach it, and many turn aside.
 *
 * <p>Proven end to end and deterministically: a wattle fence is BUILT from carried withies with a blade, then
 * over a fixed seeded set of encounters the fence yields strictly fewer ambushes from the same unchanged threat
 * (the fence only lowers the chance, so its ambushes are a strict subset). Skips gracefully without Docker.
 */
@SpringBootTest
class FenceDeterrentIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Count how many of a fixed set of encounters land, resetting the pack to HUNTING before each so every
     *  roll faces the same threat. The high bits of the id's hash drive the roll, so seeded random longs. */
    private int ambushes(UUID chronicle, UUID chunk, UUID pack, Instant now, java.util.List<UUID> actionIds) {
        int hits = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", pack);
            if (wildlife.passiveEncounter(chronicle, chunk, action, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aBuiltPerimeterFenceDetersMoreAmbushesThanNone() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler territory',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dusk prowler territory',400)", site, worldId, chunk);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pack, site, ts);

        java.util.Random rnd = new java.util.Random(42);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // No barrier: a baseline of how often the hunt closes across those encounters.
        int withoutFence = ambushes(chronicle, chunk, pack, now, actionIds);
        assertTrue(withoutFence > 0, "a HUNTING predator must actually reach an unfenced Chronicle sometimes (else the test proves nothing)");

        // Build a wattle fence from carried withies with a blade — the full acquisition path.
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        String[] built = construction.buildFence(chronicle, chunk, "I weave a wattle fence around the camp.", now);
        assertEquals("SUCCEEDED", built[0], () -> "building a wattle fence must succeed: " + built[1]);
        Integer fences = jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='WATTLE_FENCE' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk);
        assertEquals(1, fences, "the wattle fence must stand as a persistent construction at the chunk");

        // The same encounters, now with the fence between the Chronicle and the pack.
        int withFence = ambushes(chronicle, chunk, pack, now, actionIds);

        assertTrue(withFence < withoutFence,
                () -> "a built perimeter fence must deter ambushes the same threat would otherwise land (with=" + withFence + ", without=" + withoutFence + ") (#127)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
