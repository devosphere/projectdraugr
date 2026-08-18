package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
 * Tool-shed regression (#207 heritage TOOL_SHED, EPIC #123). A tool shed was carried in the world's heritage with
 * a stated effect — "organised tool storage reduces preparation time and loss" — but nothing built it and nothing
 * read it: a construction that read against nothing. Now a Chronicle can raise one from reachable materials, and a
 * completed shed at the settlement shortens the setting-up of bench work (fabrication and repair), because tools
 * and made stock are kept to hand instead of hunted out at the start of every job.
 *
 * <p>Proven end to end: the shed is built from carried stock (and the intent routes and mends), and the same
 * bench craft takes strictly less time with a shed standing than without. Skips gracefully without Docker.
 */
@SpringBootTest
class ToolShedPreparationIntegrationTest {

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

    /** Minutes of simulated time one action consumes — the clock advances by exactly the action's duration. */
    private long elapsedMinutes(String action) {
        Instant before = ticks.current().simulatedAt();
        actions.resolve(action);
        return Duration.between(before, ticks.current().simulatedAt()).toMinutes();
    }

    @Test
    void aToolShedIsBuiltFromReachableStockAndMended() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID chronicle = awakenAt(chunk);
        Instant base = ticks.current().simulatedAt();

        // The full acquisition path: a pole frame, wattle walls, a roof cover, cordage to lash it, and a blade.
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", base, "TEST_SEED"); // 4 frame + 4 wall
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", base, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", base, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", base, "TEST_SEED");

        String[] built = construction.buildToolShed(chronicle, chunk, base);
        assertEquals("SUCCEEDED", built[0], () -> "raising a tool shed from carried stock must succeed: " + built[1]);
        Integer sheds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='TOOL_SHED' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk);
        assertEquals(1, sheds, "the tool shed must stand as a persistent construction at the chunk");

        // The BUILD_TOOL_SHED intent routes and mends an existing shed rather than raising a second — proving the
        // classifier and the mend path, and guarding against a collision (e.g. with CRAFT_SHELF or STORE).
        items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", base, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult reBuilt = actions.resolve("I build a tool shed.");
        assertEquals("SUCCEEDED", reBuilt.outcome(), () -> "the BUILD_TOOL_SHED intent must route and mend: " + reBuilt.perception());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='TOOL_SHED' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk),
                "mending must not raise a second shed");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aStandingToolShedShortensBenchWork() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // A quiet chunk of its own so no other construction is present to confound the measurement.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        UUID chronicle = awakenAt(chunk);

        // No shed at camp: the baseline setting-up time for a bench craft (the craft may want for materials, but the
        // clock advances by the action's duration regardless — it is the duration we are measuring).
        long withoutShed = elapsedMinutes("I make a spear.");
        assertTrue(withoutShed > 0, "the bench craft must take measurable time (else the test proves nothing)");

        // Raise a completed tool shed here directly, isolating the read from the build (which the other test covers).
        UUID shed = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Tool shed',?)", shed, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'TOOL_SHED','COMPLETED',100,?,100)",
                shed, Timestamp.from(ticks.current().simulatedAt()));

        // The same bench craft, with the shed standing, takes strictly less setting-up.
        long withShed = elapsedMinutes("I make a spear.");
        assertTrue(withShed < withoutShed,
                () -> "a standing tool shed must shorten bench work (with=" + withShed + ", without=" + withoutShed + ") (#207)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
