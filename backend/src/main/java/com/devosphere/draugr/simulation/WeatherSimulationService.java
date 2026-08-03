package com.devosphere.draugr.simulation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * The world's weather, advanced each tick.
 *
 * <p>Weather is a <b>stable daily draw</b>: the same world seed and calendar day always yield the same
 * kind, so it does not flicker tick-to-tick, and it is reproducible for a given world. The draw is
 * <b>seasonal and weighted realistically</b> (GitHub #28) — storms are rare, clear and overcast common,
 * rain moderate, and snow belongs to winter and never falls in summer. This replaces the old
 * {@code (seed+day) mod 4} cycle, which made a storm strike every fourth day and never produced snow.
 *
 * <p><b>Scope note:</b> {@code world_weather} holds one row per world, so this is a single global sky.
 * Per-region climate (weather rational to a specific biome / ecology site) needs a schema change to a
 * per-region weather model and is tracked as a follow-up on #28. The temperature already varies by
 * season and hour, and narration grounds it against the local biome, so the sky reads differently in
 * different places even while the kind is shared.
 */
@Service
public class WeatherSimulationService {

    private final JdbcTemplate jdbc;
    public WeatherSimulationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Declaration order matches the per-season weight arrays below. */
    enum Kind { CLEAR, OVERCAST, RAIN, STORM, SNOW }

    // Per-season likelihood of each kind, {CLEAR, OVERCAST, RAIN, STORM, SNOW}, summing to 1.0.
    // Storms sit at 7–8% (was an implicit 25%); snow is winter's, tapering through the shoulder seasons.
    private static final Map<String, double[]> SEASON_WEIGHTS = Map.of(
        "WINTER", new double[]{0.25, 0.35, 0.12, 0.08, 0.20},
        "SPRING", new double[]{0.30, 0.30, 0.28, 0.07, 0.05},
        "SUMMER", new double[]{0.50, 0.22, 0.20, 0.08, 0.00},
        "AUTUMN", new double[]{0.28, 0.32, 0.28, 0.08, 0.04});

    /** Mid-latitude seasonal baseline air temperature (°C), before the diurnal swing and weather adjustment. */
    private static final Map<String, Double> SEASON_BASE_C = Map.of(
        "WINTER", 0.0, "SPRING", 11.0, "SUMMER", 22.0, "AUTUMN", 9.0);

    @Transactional
    public void advanceTo(Instant now) {
        Timestamp observedAt = Timestamp.from(now);
        LocalDate date = now.atZone(ZoneOffset.UTC).toLocalDate();
        String season = seasonOf(date.getMonthValue());
        long day = date.toEpochDay();
        int hour = now.atZone(ZoneOffset.UTC).getHour();
        double diurnal = 6 * Math.sin((hour - 9) * Math.PI / 12); // warmest through mid-afternoon, coldest before dawn

        jdbc.query("SELECT world_id, seed FROM world_genesis", rs -> {
            while (rs.next()) {
                UUID world = rs.getObject(1, UUID.class);
                long seed = rs.getLong(2);
                Kind kind = pickWeather(season, seed, day);
                int intensity = switch (kind) { case CLEAR -> 8; case OVERCAST -> 30; case RAIN -> 55; case SNOW -> 45; case STORM -> 88; };
                int wind = switch (kind) { case CLEAR -> 8; case OVERCAST -> 12; case SNOW -> 18; case RAIN -> 24; case STORM -> 60; };
                double weatherAdj = switch (kind) { case CLEAR -> 2; case RAIN, STORM -> -3; case SNOW -> -5; default -> 0; };
                double temp = clampTemp(SEASON_BASE_C.get(season) + diurnal + weatherAdj);
                jdbc.update(
                    "INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                    "VALUES (?,?,?,?,?,?) ON CONFLICT (world_id) DO UPDATE SET weather_kind=EXCLUDED.weather_kind,intensity=EXCLUDED.intensity," +
                    "ambient_temperature_c=EXCLUDED.ambient_temperature_c,wind_speed_kph=EXCLUDED.wind_speed_kph,observed_at=EXCLUDED.observed_at",
                    world, kind.name(), intensity, BigDecimal.valueOf(temp), wind, observedAt);
            }
            return null;
        });
    }

    /**
     * Stable daily draw over the season's weighted distribution — same seed+day always yields the same
     * kind. Uses a SplitMix64 finalizer to turn (seed, day) into a well-distributed value: a plain
     * {@code new Random(seed+day)} correlates consecutive days (its first output barely moves as the
     * seed increments by one), which skewed the distribution badly.
     */
    Kind pickWeather(String season, long seed, long day) {
        double[] weights = SEASON_WEIGHTS.get(season);
        long z = seed * 0x9E3779B97F4A7C15L + day * 0xD1B54A32D192ED03L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        double r = (z >>> 11) * 0x1.0p-53; // top 53 bits → [0, 1)
        double cumulative = 0;
        Kind[] kinds = Kind.values();
        for (int i = 0; i < weights.length; i++) { cumulative += weights[i]; if (r < cumulative) return kinds[i]; }
        return Kind.CLEAR;
    }

    private static String seasonOf(int month) {
        return switch (month) { case 12, 1, 2 -> "WINTER"; case 3, 4, 5 -> "SPRING"; case 6, 7, 8 -> "SUMMER"; default -> "AUTUMN"; };
    }

    private static double clampTemp(double c) { return Math.max(-40, Math.min(55, Math.round(c * 10) / 10.0)); }
}
