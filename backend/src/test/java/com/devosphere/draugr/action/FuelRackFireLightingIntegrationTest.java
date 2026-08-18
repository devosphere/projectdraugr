package com.devosphere.draugr.action;

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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fuel-rack fire-lighting regression (#127, EPIC #123). Fire-lighting takes a heavy rain/storm penalty (the
 * ignition odds are quartered when the method needs dry material and the sky is wet), but a Chronicle had no way
 * to answer it. A built covered fuel rack now keeps dry kindling: {@code wetFireOdds} reads a completed FUEL_RACK
 * at the chunk and eases the wet-weather penalty from a quartering (×0.25) to a light one (×0.7), while leaving
 * fair weather untouched (×1.0). Proven end to end: the multiplier is measured before and after the rack is
 * BUILT through the action layer.
 *
 * <p>In the {@code action} package so it can assert the exact package-private multiplier. Skips without Docker.
 */
@SpringBootTest
class FuelRackFireLightingIntegrationTest {

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

    @Test
    void aBuiltFuelRackEasesTheRainPenaltyOnFireLighting() {
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
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Before any rack: rain quarters a dry-method attempt, fair weather leaves it alone, and a method that
        // needs no dry material is never penalised.
        assertEquals(0.25, actions.wetFireOdds(chunk, true, "RAIN"), 1e-9, "rain must quarter the odds with no dry fuel");
        assertEquals(0.25, actions.wetFireOdds(chunk, true, "STORM"), 1e-9, "a storm must quarter the odds with no dry fuel");
        assertEquals(1.0, actions.wetFireOdds(chunk, true, "CLEAR"), 1e-9, "fair weather must leave the odds untouched");
        assertEquals(1.0, actions.wetFireOdds(chunk, false, "RAIN"), 1e-9, "a method that needs no dry material is never rain-penalised");

        // Build a covered fuel rack from carried poles, a roof, and a lashing — the full acquisition path.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        ChronicleActionService.ActionResult built = actions.resolve("I build a fuel rack.");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a fuel rack must succeed: " + built.perception());
        Integer racks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='FUEL_RACK' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk);
        assertEquals(1, racks, "the fuel rack must stand as a persistent construction at the chunk");

        // With the rack, dry kindling eases the rain penalty from a quartering to a light one — fair weather is
        // still untouched.
        assertEquals(0.7, actions.wetFireOdds(chunk, true, "RAIN"), 1e-9, "a fuel rack must ease the rain penalty (#127)");
        assertEquals(0.7, actions.wetFireOdds(chunk, true, "STORM"), 1e-9, "a fuel rack must ease the storm penalty (#127)");
        assertEquals(1.0, actions.wetFireOdds(chunk, true, "CLEAR"), 1e-9, "the rack changes nothing in fair weather");

        assertEquals(true, auditor.inspect().consistent(), "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
