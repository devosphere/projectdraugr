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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reforging worn iron (EPIC #180 heavy industry / #186 iron). Bronze objects melt back to ingots, but iron had no
 * such path — a worn iron axe or a battered cuirass was heavy good iron with nowhere to go. Iron does not pour, so
 * scrap is reforged, not melted: worn pieces are heated and hammered back into a fresh bloom, lossily, so it takes
 * two worn axe-heads or one whole cuirass to make one. This closes the iron wear-out end.
 *
 * <p>Proven: both feed paths reforge through the real router — a single worn cuirass yields a bloom, and two worn
 * axe-heads yield a bloom — and the world stays Auditor-consistent. Skips gracefully without Docker.
 */
@SpringBootTest
class ReforgeIronScrapIntegrationTest {

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

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void wornIronReforgesBackIntoAFreshBloom() {
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
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // A hot fire at hand — reforging gates on it (requires_fire), no striking tool needed (tool_class NULL).
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, ts);

        // Path one: a single worn cuirass reforges into one bloom.
        items.createCarriedItem(chronicle, "iron_cuirass", "Iron cuirass", now, "TEST_SEED");
        int bloomsBefore = count(chronicle, "iron_bloom");
        String[] fromCuirass = items.runProcess(chronicle, chunk, "reforge the iron scrap", now);
        assertEquals("SUCCEEDED", fromCuirass[0], () -> "reforging a worn cuirass must succeed through the router: " + fromCuirass[1]);
        assertEquals(bloomsBefore + 1, count(chronicle, "iron_bloom"), "reforging a cuirass must yield one iron bloom");
        assertEquals(0, count(chronicle, "iron_cuirass"), "the reforged cuirass must be consumed");

        // Path two: two worn axe-heads reforge into one bloom (one alone is too little iron — it must not fire).
        items.createCarriedItem(chronicle, "iron_axe", "Iron axe", now, "TEST_SEED");
        String[] oneAxe = items.runProcess(chronicle, chunk, "work the old iron down", now);
        assertFalse("SUCCEEDED".equals(oneAxe[0]), "one worn axe is too little iron to reforge a whole bloom — it must not fire");
        assertEquals(1, count(chronicle, "iron_axe"), "a failed reforge must not consume the lone axe");

        items.createCarriedItem(chronicle, "iron_axe", "Iron axe", now, "TEST_SEED");
        int bloomsBeforeAxes = count(chronicle, "iron_bloom");
        String[] twoAxes = items.runProcess(chronicle, chunk, "work the old iron down", now);
        assertEquals("SUCCEEDED", twoAxes[0], () -> "two worn axe-heads must reforge through the router: " + twoAxes[1]);
        assertEquals(bloomsBeforeAxes + 1, count(chronicle, "iron_bloom"), "reforging two axes must yield one iron bloom");
        assertEquals(0, count(chronicle, "iron_axe"), "both reforged axes must be consumed");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
