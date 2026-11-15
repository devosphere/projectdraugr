package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.LocalWeatherService;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #912 — A STORM HAS EDGES, AND EVERY READER OF THE SKY AGREES WHERE THEY ARE.
 *
 * <p>A Chronicle on seed 681013497 spawned in a storm, crossed 560 tiles, and never left it: {@code
 * world_weather} holds one row per world, so every chunk was inside the only front there was. The front now
 * carries a centre and a radius, and {@code local_sky(chunk)} (V469) is the one place that is honoured.
 *
 * <p>That last part is the whole risk of this change. {@code LocalWeatherService} claimed to be the only
 * place the environment query lived; it was not — twenty-two statements across eight files read {@code
 * world_weather} directly. Had the footprint been honoured in one and not the others, the player would get a
 * HUD saying clear while their fire's ember hazard, their wet work and the wildlife around them all still
 * thought it was storming: this project's recurring defect, introduced on purpose. So this test does not only
 * ask whether the boundary exists — it asks SEVERAL DIFFERENT READERS, on both sides of it, whether they say
 * the same thing.
 *
 * <p>What each kind reads as away from the front is written in SQL as well as in Java;
 * {@code TheSkyIsWrittenInTwoPlacesIntegrationTest} holds those two against each other.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AStormHasEdgesIntegrationTest {

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
    @Autowired LocalWeatherService localWeather;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        return jdbc.queryForObject("SELECT world_id FROM world_genesis", UUID.class);
    }

    /** The chunk at these coordinates, whatever its biome — the sky does not care what is underneath it. */
    private UUID chunkAt(UUID world, int x, int y) {
        return jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, x, y);
    }

    private String skyAt(UUID chunk) {
        return jdbc.queryForObject("SELECT local_weather_kind(?)", String.class, chunk);
    }

    /**
     * Put a front of a known kind over a known place. The world is shared by some 925 tests, so the row is
     * restored by {@link #putTheSkyBack} before this test leaves — a storm left standing over the map would
     * reach every suite that runs afterwards.
     */
    private Map<String, Object> theSkyAsItWas(UUID world) {
        return jdbc.queryForMap("SELECT weather_kind, intensity, ambient_temperature_c, wind_speed_kph, " +
            "front_centre_x, front_centre_y, front_radius_chunks FROM world_weather WHERE world_id=?", world);
    }

    private void putTheSkyBack(UUID world, Map<String, Object> was) {
        jdbc.update("UPDATE world_weather SET weather_kind=?, intensity=?, ambient_temperature_c=?, wind_speed_kph=?, " +
            "front_centre_x=?, front_centre_y=?, front_radius_chunks=? WHERE world_id=?",
            was.get("weather_kind"), was.get("intensity"), was.get("ambient_temperature_c"), was.get("wind_speed_kph"),
            was.get("front_centre_x"), was.get("front_centre_y"), was.get("front_radius_chunks"), world);
    }

    private void aStormOver(UUID world, int centreX, int centreY, int radius) {
        jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at," +
            "front_centre_x,front_centre_y,front_radius_chunks) VALUES (?,'STORM',88,9.0,60,now(),?,?,?) " +
            "ON CONFLICT (world_id) DO UPDATE SET weather_kind='STORM',intensity=88,ambient_temperature_c=9.0,wind_speed_kph=60," +
            "front_centre_x=EXCLUDED.front_centre_x,front_centre_y=EXCLUDED.front_centre_y,front_radius_chunks=EXCLUDED.front_radius_chunks",
            world, centreX, centreY, radius);
    }

    /**
     * THE DEFECT ITSELF: at one simulated minute, two grounds in one world report different skies, and the
     * ground between them is neither — the edge of a weather system is not a wall.
     */
    @Test
    void atOneMinuteTwoGroundsReportDifferentSkies() {
        UUID world = world();
        Map<String, Object> was = theSkyAsItWas(world);
        try {
            aStormOver(world, 4, 4, 3);

            assertEquals("STORM", skyAt(chunkAt(world, 4, 4)), "the middle of the storm is the storm");
            assertEquals("STORM", skyAt(chunkAt(world, 6, 4)), "and so is ground three chunks from its centre");
            assertEquals("OVERCAST", skyAt(chunkAt(world, 8, 4)), "just past the edge the sky is heavy, not sudden blue");
            assertEquals("CLEAR", skyAt(chunkAt(world, 14, 4)), "and well outside it the storm is somewhere else entirely");

            // Which is the sentence #912 was filed about: there is ground to walk to.
            Integer outside = jdbc.queryForObject(
                "SELECT COUNT(*) FROM world_chunk wc WHERE wc.world_id=? AND local_weather_kind(wc.id)='CLEAR'", Integer.class, world);
            assertNotNull(outside);
            assertTrue(outside > 100, () -> "a storm a player can walk out of must leave real ground outside it: " + outside + " chunks");
        } finally {
            putTheSkyBack(world, was);
        }
    }

    /**
     * AND EVERY READER MOVES TOGETHER. Each of these went to {@code world_weather} directly before this
     * change, and each one is a different file: the HUD's environment, the guttering-flame rule, the fire's
     * ember hazard, and the physiology query's own idea of the sky. One of them left behind is the whole bug.
     */
    @Test
    void everyReaderOfTheSkyAgreesOnWhichSideOfTheFrontYouAreOn() {
        UUID world = world();
        Map<String, Object> was = theSkyAsItWas(world);
        try {
            aStormOver(world, 4, 4, 3);
            UUID under = chunkAt(world, 4, 4);
            UUID beyond = chunkAt(world, 20, 14);
            assertEquals("STORM", skyAt(under));
            assertEquals("CLEAR", skyAt(beyond), "this test needs a chunk that is genuinely outside");

            ChronicleService.ChronicleSummary summary = chronicles.awaken();
            assertNotNull(summary);
            UUID chronicle = summary.id();

            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", under, chronicle);
            assertEquals("STORM", localWeather.at(under).kind(), "LocalWeatherService, standing under it");
            assertTrue(localWeather.wetHere(under), "work in progress under a storm is getting wet");
            assertTrue(localWeather.galeHere(under), "and the wind is a storm's wind");
            assertTrue(items.guttering(chronicle), "an open flame gutters in a storm");
            assertEquals("STORM", chronicles.activeEnvironment().weatherKind(), "and the HUD says so too");

            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", beyond, chronicle);
            assertEquals("CLEAR", localWeather.at(beyond).kind(), "LocalWeatherService, having walked out of it");
            assertTrue(!localWeather.wetHere(beyond), "nothing is falling on the work here");
            assertTrue(!localWeather.galeHere(beyond), "and there is no gale to keep the timbers down");
            assertTrue(!items.guttering(chronicle), "the flame stands up out from under the front");
            assertEquals("CLEAR", chronicles.activeEnvironment().weatherKind(), "and the HUD agrees with all of it");

            assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
        } finally {
            putTheSkyBack(world, was);
        }
    }

    /**
     * A SAVE THAT HAS NOT TICKED SINCE THIS CHANGE READS EXACTLY AS IT DID. The footprint columns are
     * nullable on purpose and a null footprint means the whole world — otherwise the first thing this change
     * would do to a live playthrough is silently clear the sky over it.
     */
    @Test
    void aFrontWithNoFootprintStillCoversTheWorld() {
        UUID world = world();
        Map<String, Object> was = theSkyAsItWas(world);
        try {
            aStormOver(world, 4, 4, 3);
            jdbc.update("UPDATE world_weather SET front_centre_x=NULL, front_centre_y=NULL, front_radius_chunks=NULL WHERE world_id=?", world);
            Integer notStorming = jdbc.queryForObject(
                "SELECT COUNT(*) FROM world_chunk wc WHERE wc.world_id=? AND local_weather_kind(wc.id) <> 'STORM'", Integer.class, world);
            assertEquals(0, notStorming, "with no footprint written, the front is over everything as it always was");
        } finally {
            putTheSkyBack(world, was);
        }
    }

    /**
     * And the temperature outside the front is the sky that is actually there, not the front's own. The
     * stored reading carries the front's adjustment; a chunk beyond its edge has to have that backed out and
     * its own applied, or a sunlit tile three chunks past the storm reads three degrees cold for weather it
     * is nowhere near.
     */
    @Test
    void groundOutsideTheFrontGetsItsOwnTemperatureAndWind() {
        UUID world = world();
        Map<String, Object> was = theSkyAsItWas(world);
        try {
            aStormOver(world, 4, 4, 3);   // stored: STORM, 9.0 C, 60 kph — and a storm is -3 C of its own
            Map<String, Object> clear = jdbc.queryForMap(
                "SELECT (local_sky(?)).ambient_temperature_c AS t, (local_sky(?)).wind_speed_kph AS w",
                chunkAt(world, 20, 14), chunkAt(world, 20, 14));
            assertEquals(14.0, ((Number) clear.get("t")).doubleValue(), 0.05,
                "9.0 with the storm's -3 backed out and clear's +2 applied is 14.0");
            // 8 kph is a clear sky's wind; WeatherConstantsAgree holds that figure against its SQL twin.
            assertEquals(8, ((Number) clear.get("w")).intValue(), "and a clear sky's wind, not the storm's sixty");
        } finally {
            putTheSkyBack(world, was);
        }
    }
}
