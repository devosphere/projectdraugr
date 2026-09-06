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
 * A tallow-fuelled lamp (#182 fuel uses; #75 working by light). An oil lamp burned only fish oil, so working after
 * dark hung on having fished. The same lamp burns rendered tallow just as readily — a fat lamp, the commonest light
 * there ever was — which gives rendered tallow a use beyond candles and frees light from fishing.
 *
 * <p>Proven: with an oil lamp and rendered tallow (and no other light), the Chronicle can light work by the lamp and
 * a measure of tallow is spent; the lamp itself keeps; and a lamp with no fuel at all cannot light. Skips gracefully
 * without Docker.
 */
@SpringBootTest
class TallowLampFuelIntegrationTest {

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
    void anOilLampBurnsRenderedTallowNotOnlyFishOil() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Clear any higher-priority light the awakening kit may carry, so the only light in play is the tallow lamp.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
                "SELECT object_id FROM item_instance WHERE item_key IN ('firebrand','resin_torch','rush_light','tallow_candle','fish_oil'))", chronicle);
        assertFalse(items.consumePortableLight(chronicle, now), "with nothing to burn, there is no light to work by");

        // An oil lamp but no fuel: it still cannot light.
        items.createCarriedItem(chronicle, "oil_lamp", "Oil lamp", now, "TEST_SEED");
        assertFalse(items.consumePortableLight(chronicle, now), "an empty lamp gives no light");

        // Rendered tallow to fill it: now the lamp lights, and a measure of tallow is spent.
        items.createCarriedItem(chronicle, "rendered_tallow", "Rendered tallow", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "rendered_tallow", "Rendered tallow", now, "TEST_SEED");
        assertEquals(2, count(chronicle, "rendered_tallow"), "start with two measures of tallow");
        assertTrue(items.consumePortableLight(chronicle, now), "a lamp with rendered tallow must light work by");
        assertEquals(1, count(chronicle, "rendered_tallow"), "lighting the lamp must burn one measure of tallow");
        assertEquals(1, count(chronicle, "oil_lamp"), "the lamp itself keeps — only the fuel is spent");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * A bare flame will not stand in weather, and the stone lantern cover is what the catalogue made for exactly
     * that — "a stone cover to hood a flame and control its light". Nothing read it, so a Chronicle could shape
     * one, wear it, and be no better off in a gale than with a naked candle.
     */
    @Test
    void aHoodedFlameHoldsInWeatherAndABareOneDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject(
            "SELECT c.world_id FROM world_object b JOIN world_chunk c ON c.id=b.current_location_id WHERE b.id=?", UUID.class, chronicle);

        // Strip the awakening kit's lights so only what this test grants is in play.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
                "SELECT object_id FROM item_instance WHERE item_key IN ('firebrand','resin_torch','rush_light','tallow_candle','oil_lamp','fish_oil','rendered_tallow','stone_lantern_cover'))", chronicle);

        // Fair weather first: a bare candle is light enough. world_weather has no row until the weather sim runs,
        // so it must be UPSERTED here rather than updated, or the seeding silently matches nothing.
        jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                "VALUES (?,'CLEAR',1,12.0,5,?) ON CONFLICT (world_id) DO UPDATE SET weather_kind='CLEAR', wind_speed_kph=5", worldId, Timestamp.from(now));
        assertEquals("CLEAR", jdbc.queryForObject("SELECT weather_kind FROM world_weather WHERE world_id=?", String.class, worldId),
            "the fair-weather precondition must actually have taken");

        items.createCarriedItem(chronicle, "tallow_candle", "Tallow candle", now, "TEST_SEED");
        assertTrue(items.consumePortableLight(chronicle, now), "a candle lights the work on a still, clear night");

        // Now a storm. The same candle cannot be kept alight.
        jdbc.update("UPDATE world_weather SET weather_kind='STORM', wind_speed_kph=55 WHERE world_id=?", worldId);
        assertEquals("STORM", jdbc.queryForObject("SELECT weather_kind FROM world_weather WHERE world_id=?", String.class, worldId),
            "the storm precondition must actually have taken");

        items.createCarriedItem(chronicle, "tallow_candle", "Tallow candle", now, "TEST_SEED");
        assertTrue(items.weatherWouldGutterALight(chronicle), "a storm must be weather that takes a bare flame");
        assertFalse(items.consumePortableLight(chronicle, now), "a bare candle cannot be struck in a storm");
        assertEquals(1, count(chronicle, "tallow_candle"), "and it must not be wasted in the attempt either");

        // Hood it, and the same candle holds.
        items.createCarriedItem(chronicle, "stone_lantern_cover", "Stone lantern cover", now, "TEST_SEED");
        assertTrue(items.consumePortableLight(chronicle, now), "a hooded flame holds in the weather it was shaped for");
        assertEquals(0, count(chronicle, "tallow_candle"), "the candle is spent by the work, cover or no cover");
        assertEquals(1, count(chronicle, "stone_lantern_cover"), "the cover is stone and keeps");

        // A firebrand is a burning brand — "a wrapped brand to carry fire and ward off beasts" — and it lit
        // nothing, because consumePortableLight had never heard of it. It is a light before it is anything else.
        jdbc.update("UPDATE world_weather SET weather_kind='CLEAR', wind_speed_kph=5 WHERE world_id=?", worldId);
        items.createCarriedItem(chronicle, "firebrand", "Firebrand", now, "TEST_SEED");
        assertTrue(items.consumePortableLight(chronicle, now), "a burning brand must light work by");
        assertEquals(0, count(chronicle, "firebrand"), "and it burns down in doing it");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
