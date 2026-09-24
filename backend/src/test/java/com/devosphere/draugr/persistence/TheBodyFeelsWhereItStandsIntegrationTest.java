package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body endures the weather where it STANDS (#37).
 *
 * <p>Every other consumer of the world's single sky already ran it through
 * {@link com.devosphere.draugr.simulation.BiomeClimate} first: the Body HUD, the action prose, the backdrops and
 * the four senses all report a peak as colder and windier than the valley under one front. The physiology read
 * did not — it went straight to {@code world_weather} and never touched {@code world_chunk} — so a Chronicle on a
 * mountain was chilled as though they stood in the lowlands, and a Chronicle sheltering inside a cave took the
 * open mountain's wind. Altitude was decoration: climbing cost the body nothing.
 *
 * <p>Proven below by standing the SAME Chronicle, in the SAME hour, under the SAME weather row, first on the
 * highest ground in the world and then on the lowest, and comparing what the cold took from their core. Under the
 * old read the two were identical to the digit, so this test fails with a difference of exactly zero.
 */
@SpringBootTest
class TheBodyFeelsWhereItStandsIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /**
     * Stand the body on this ground with a fresh, warm, well-fed core, let the given span pass, and report how
     * far the core fell. Food and energy are set high so {@code metabolicVigour} is not the variable under test.
     */
    private double coreLostAt(UUID chronicle, UUID chunk, Instant base, int hours) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=37.0, wetness_level=0, energy_level=90, " +
                    "hours_without_food=2, hours_without_water=1, last_metabolic_update=? WHERE chronicle_id=?",
                    Timestamp.from(base), chronicle);
        physiology.advanceTo(base.plus(Duration.ofHours(hours)));
        BigDecimal after = jdbc.queryForObject(
            "SELECT core_temperature_c FROM chronicle_physiology WHERE chronicle_id=?", BigDecimal.class, chronicle);
        return 37.0 - after.doubleValue();
    }

    @Test
    void thePeakTakesMoreFromABodyThanTheValleyUnderOneSky() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID world = jdbc.queryForObject("SELECT world_id FROM chronicle WHERE id=?", UUID.class, chronicle);

        UUID wasAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        List<Map<String, Object>> wasWeather = jdbc.queryForList(
            "SELECT weather_kind, intensity, ambient_temperature_c, wind_speed_kph FROM world_weather WHERE world_id=?", world);
        try {
            // One sky over the whole world, and a mild one: 10 degrees is not weather that kills anybody at sea
            // level. Whatever difference the two grounds show is the ground's doing and nothing else's.
            Instant base = Instant.parse("2031-06-15T12:00:00Z");
            jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                        "VALUES (?,'CLEAR',0,10.0,20,?) ON CONFLICT (world_id) DO UPDATE SET weather_kind='CLEAR', " +
                        "intensity=0, ambient_temperature_c=10.0, wind_speed_kph=20, observed_at=EXCLUDED.observed_at",
                        world, Timestamp.from(base));

            UUID high = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? ORDER BY elevation DESC, grid_y, grid_x LIMIT 1", UUID.class, world);
            UUID low = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? AND biome NOT IN ('OCEAN') ORDER BY elevation ASC, grid_y, grid_x LIMIT 1", UUID.class, world);
            assertNotNull(high); assertNotNull(low);

            double lostHigh = coreLostAt(chronicle, high, base, 6);
            double lostLow  = coreLostAt(chronicle, low,  base, 6);

            // The whole point. Under the old read both numbers came from the same global row and this difference
            // was exactly 0.0 — which is why asserting "the peak is cold" alone would not have caught it.
            assertTrue(lostHigh > lostLow + 0.05,
                "high ground must cost the body more than low ground under one sky: high=" + lostHigh + " low=" + lostLow);

            // And the sky itself never moved, so the difference cannot have come from the weather changing
            // underneath the two measurements.
            Map<String, Object> still = jdbc.queryForMap(
                "SELECT ambient_temperature_c, wind_speed_kph FROM world_weather WHERE world_id=?", world);
            assertEquals(0, new BigDecimal("10.0").compareTo((BigDecimal) still.get("ambient_temperature_c")),
                "the global temperature must be unchanged between the two readings");
            assertEquals(20, ((Number) still.get("wind_speed_kph")).intValue());

            assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
        } finally {
            // This class shares one database with the rest of the suite. Leave the sky and the body where they
            // were found, or the next test wakes up in weather it never asked for.
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wasAt, chronicle);
            if (!wasWeather.isEmpty()) {
                Map<String, Object> w = wasWeather.get(0);
                jdbc.update("UPDATE world_weather SET weather_kind=?, intensity=?, ambient_temperature_c=?, wind_speed_kph=? WHERE world_id=?",
                    w.get("weather_kind"), w.get("intensity"), w.get("ambient_temperature_c"), w.get("wind_speed_kph"), world);
            }
            jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=37.0, wetness_level=0 WHERE chronicle_id=?", chronicle);
        }
    }
}
