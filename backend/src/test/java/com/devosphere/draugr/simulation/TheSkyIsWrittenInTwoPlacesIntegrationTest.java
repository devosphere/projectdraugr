package com.devosphere.draugr.simulation;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #912 — THE TWO COPIES OF THE SAME TABLE OF NUMBERS, HELD AGAINST EACH OTHER.
 *
 * <p>A chunk outside the front has to be given a sky of its own: the stored reading carries the FRONT'S
 * adjustment, so ground beyond its edge needs that backed out and its own applied, or a sunlit tile three
 * chunks past a storm reads three degrees cold for weather it is nowhere near. The rule that does that is
 * {@code local_sky} (V469), which is SQL — so what each kind reads as is now written in SQL as well as in
 * {@link WeatherSimulationService}.
 *
 * <p>Two places holding the same list is the shape that produced the unwearable-garment defect (a catalogue
 * and a CHECK constraint keeping separate lists of one thing) and several since. It is accepted here because
 * both sides genuinely need the numbers, and it is accepted ONLY with this test: a drift between them would
 * otherwise show up as a chunk outside a storm being quietly wrong about its own wind, which nobody would
 * ever file.
 *
 * <p>Lives beside the service rather than in {@code persistence} because the thing under test is this
 * package's constants. Skips without Docker.
 */
@SpringBootTest
class TheSkyIsWrittenInTwoPlacesIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;

    @Test
    void everyKindReadsTheSameInSqlAsInJava() {
        List<String> drifted = new ArrayList<>();
        for (WeatherSimulationService.Kind kind : WeatherSimulationService.Kind.values()) {
            Integer sqlIntensity = jdbc.queryForObject("SELECT sky_intensity(?)", Integer.class, kind.name());
            Integer sqlWind = jdbc.queryForObject("SELECT sky_wind_kph(?)", Integer.class, kind.name());
            Double sqlAdjustment = jdbc.queryForObject("SELECT sky_temperature_adjustment(?)", Double.class, kind.name());

            if (!Integer.valueOf(WeatherSimulationService.intensityOf(kind)).equals(sqlIntensity))
                drifted.add(kind + " intensity: java " + WeatherSimulationService.intensityOf(kind) + " vs sql " + sqlIntensity);
            if (!Integer.valueOf(WeatherSimulationService.windOf(kind)).equals(sqlWind))
                drifted.add(kind + " wind: java " + WeatherSimulationService.windOf(kind) + " vs sql " + sqlWind);
            if (sqlAdjustment == null || WeatherSimulationService.temperatureAdjustmentOf(kind) != sqlAdjustment)
                drifted.add(kind + " temperature adjustment: java "
                    + WeatherSimulationService.temperatureAdjustmentOf(kind) + " vs sql " + sqlAdjustment);
        }
        assertEquals(List.of(), drifted,
            () -> "the sky away from the front is written in two places and they no longer agree: " + drifted);
    }

    /** An unknown kind must not come back as nothing — a null here would travel a long way before it was noticed. */
    @Test
    void anUnknownSkyStillAnswersSomething() {
        assertEquals(8, jdbc.queryForObject("SELECT sky_intensity('NOT_A_KIND')", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT sky_wind_kph('NOT_A_KIND')", Integer.class));
        assertEquals(0.0, jdbc.queryForObject("SELECT sky_temperature_adjustment('NOT_A_KIND')", Double.class));
    }
}
