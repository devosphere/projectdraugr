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
 * <p><b>And the front has edges (#912).</b> {@code world_weather} still holds one row per world — WHEN it
 * rains is world-wide, and the draw above is unchanged — but the front now carries a CENTRE and a RADIUS, so
 * there is ground outside it. A Chronicle on seed 681013497 spawned in a storm, crossed 560 tiles, and never
 * left it, because every chunk was inside the only front there was.
 *
 * <p>The footprint comes from the same {@code (seed, day)} SplitMix64 stream that picks the kind, so it stays
 * reproducible per seed, and the centre DRIFTS WITH THE HOUR — a front crosses the map over a day, and a
 * Chronicle can walk out from under it or be caught by it. CLEAR and OVERCAST get no footprint at all: they
 * are not fronts, they are what the sky does when none is overhead, and a null footprint means the whole
 * world (which is also what a save written before #912 says, so it reads as it always did until it ticks).
 *
 * <p>How that front is FELT is still {@link BiomeClimate}'s: altitude cools it, latitude shifts it, humidity
 * biases rain↔snow. <b>WHERE it is falling is {@code local_sky(chunk)}'s</b> — a SQL function (V469), so the
 * twenty-two statements that read {@code world_weather} directly and the Java readers that go through
 * {@link LocalWeatherService} all ask one rule rather than keeping two in step.
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

        jdbc.query("SELECT world_id, seed, width_chunks, height_chunks FROM world_genesis", rs -> {
            while (rs.next()) {
                UUID world = rs.getObject(1, UUID.class);
                long seed = rs.getLong(2);
                int width = rs.getInt(3), height = rs.getInt(4);
                Kind kind = pickWeather(season, seed, day);
                int intensity = intensityOf(kind);
                int wind = windOf(kind);
                double temp = clampTemp(SEASON_BASE_C.get(season) + diurnal + temperatureAdjustmentOf(kind));
                Footprint front = footprint(kind, seed, day, hour, width, height);
                jdbc.update(
                    "INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at," +
                    "front_centre_x,front_centre_y,front_radius_chunks) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (world_id) DO UPDATE SET weather_kind=EXCLUDED.weather_kind,intensity=EXCLUDED.intensity," +
                    "ambient_temperature_c=EXCLUDED.ambient_temperature_c,wind_speed_kph=EXCLUDED.wind_speed_kph,observed_at=EXCLUDED.observed_at," +
                    "front_centre_x=EXCLUDED.front_centre_x,front_centre_y=EXCLUDED.front_centre_y,front_radius_chunks=EXCLUDED.front_radius_chunks",
                    world, kind.name(), intensity, BigDecimal.valueOf(temp), wind, observedAt,
                    front == null ? null : front.centreX(), front == null ? null : front.centreY(),
                    front == null ? null : front.radiusChunks());
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

    /** Where a front sits and how far it reaches; null for a sky that is not a front at all. */
    record Footprint(int centreX, int centreY, int radiusChunks) { }

    /**
     * How much of the map a front of this kind covers, as a fraction of the map's longer side. A storm is the
     * tightest thing the sky does and rain the broadest; on a 28x20 world that is a storm about nine chunks
     * across and rain about fourteen, which leaves real ground outside either.
     */
    private static double spanOf(Kind kind) {
        return switch (kind) { case STORM -> 0.33; case RAIN -> 0.50; case SNOW -> 0.50; default -> 0; };
    }

    /**
     * The front's footprint for this world, day and hour — or null for CLEAR and OVERCAST, which are not
     * fronts and so cover everything by having no edge to be outside of.
     *
     * <p>Drawn from the same {@code (seed, day)} stream as the kind, with its own salt, so a seed's weather
     * is still reproducible in full. THE CENTRE DRIFTS WITH THE HOUR, far enough that a front crosses the
     * whole map in a day: that is what makes walking out from under it, or being caught by it, a thing that
     * can happen rather than a fact about where you spawned.
     *
     * <p><b>It crosses, it does not blow away.</b> The first cut drew a start position and drifted a chunk an
     * hour from it, and on the first live run it put a rain front at centre (-28, 14) of a 28-wide world — an
     * entire rainy day during which no rain fell on any ground in the world. The draw above says it rains on
     * 28% of spring days and it has to still mean that. So the centre is a POSITION ALONG A CROSSING: it
     * enters just off one edge, is over the middle at midday, leaves by the other, and wraps, with a
     * seed-drawn phase so two worlds are not rained on at the same hour.
     */
    static Footprint footprint(Kind kind, long seed, long day, int hour, int width, int height) {
        double span = spanOf(kind);
        if (span == 0 || width <= 0 || height <= 0) return null;
        long z = mix(seed * 0x9E3779B97F4A7C15L + day * 0xD1B54A32D192ED03L + 0x2545F4914F6CDD1DL);
        int radius = Math.max(1, (int) Math.round(Math.max(width, height) * span / 2));
        int crossing = width + 2 * radius;              // just off one edge to just off the other
        int phase = (int) Math.floorMod(z >>> 5, (long) crossing);
        int along = (int) ((Math.round(crossing * (hour / 24.0)) + phase) % crossing);
        boolean eastward = ((z >>> 41) & 1L) == 0;      // mid-latitude fronts mostly run with the westerlies
        int centreX = eastward ? -radius + along : width + radius - along;
        int startY = (int) Math.floorMod(z >>> 21, (long) height);
        int driftY = ((z >>> 42) & 3L) == 0 ? 1 : ((z >>> 44) & 3L) == 0 ? -1 : 0;
        return new Footprint(centreX, startY + driftY * (hour / 6), radius);
    }

    /** SplitMix64's finalizer — the same avalanche {@link #pickWeather} relies on, named once. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * What each kind reads as, away from any front.
     *
     * <p>These three live in SQL as well (V469's {@code sky_intensity}, {@code sky_wind_kph} and
     * {@code sky_temperature_adjustment}), because a chunk outside the front has to be given its own figures
     * and the rule that does that is SQL. A table of numbers written twice is the shape this project keeps
     * getting bitten by, so {@code WeatherConstantsAgreeIntegrationTest} holds the two against each other.
     */
    static int intensityOf(Kind kind) {
        return switch (kind) { case CLEAR -> 8; case OVERCAST -> 30; case RAIN -> 55; case SNOW -> 45; case STORM -> 88; };
    }

    static int windOf(Kind kind) {
        return switch (kind) { case CLEAR -> 8; case OVERCAST -> 12; case SNOW -> 18; case RAIN -> 24; case STORM -> 60; };
    }

    static double temperatureAdjustmentOf(Kind kind) {
        return switch (kind) { case CLEAR -> 2; case RAIN, STORM -> -3; case SNOW -> -5; default -> 0; };
    }

    private static String seasonOf(int month) {
        return switch (month) { case 12, 1, 2 -> "WINTER"; case 3, 4, 5 -> "SPRING"; case 6, 7, 8 -> "SUMMER"; default -> "AUTUMN"; };
    }

    private static double clampTemp(double c) { return Math.max(-40, Math.min(55, Math.round(c * 10) / 10.0)); }
}
