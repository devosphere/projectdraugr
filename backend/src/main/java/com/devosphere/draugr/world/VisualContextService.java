package com.devosphere.draugr.world;

import com.devosphere.draugr.simulation.BiomeClimate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * What the living Chronicle can actually SEE where they stand (#224/#232).
 *
 * <p>This is the authoritative answer to "what does this place look like right now", and it exists so that a
 * presentation layer never has to guess — or, worse, reach into the Overseer's map to find out. That distinction
 * is the whole point of the service:
 *
 * <ul>
 *   <li><b>The Overseer's marker plan is not perception.</b> It knows where every lair, seam and roost in the
 *       world is. A payload built from it would let a player learn, by watching their own screen, that a monster
 *       lair sits two chunks east — knowledge the Chronicle has no way to have. So nothing here reads
 *       {@code markerPlan}, and the only sites reported are the ones on the ground underfoot.</li>
 *   <li><b>Nor is an encounter.</b> What is hunting you is not scenery, and it is reported by the encounter
 *       systems when they resolve, not by a description of the place.</li>
 * </ul>
 *
 * <p>The response carries a <b>fingerprint</b>: a stable hash of everything in it. Unchanged world state yields
 * the same fingerprint across a reload, so a caller can tell "the same place, unchanged" from "the same place,
 * different now" without diffing prose — and travel, a build finished, or a structure destroyed all change it
 * because they change the facts it is made of.
 *
 * <p>Versioned deliberately ({@link #VERSION}). The shape of what a place looks like will grow, and a caller
 * that has not been updated needs to be able to tell.
 */
@Service
public class VisualContextService {

    /** The response contract version. Additive changes keep it; a removal or rename must raise it. */
    public static final int VERSION = 1;

    private final JdbcTemplate jdbc;

    public VisualContextService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One thing standing on this ground: a natural site, or something a Chronicle has built. */
    public record Feature(String kind, String name) { }

    /**
     * Everything visible from where the Chronicle stands.
     *
     * @param version     the contract version this payload was built to
     * @param biome       the ground underfoot
     * @param features    sites and completed structures ON this chunk — never anywhere else
     * @param timeOfDay   DAWN / DAY / DUSK / NIGHT
     * @param season      SPRING / SUMMER / AUTUMN / WINTER
     * @param weather     the weather as FELT here, not the global sky
     * @param temperatureC the temperature as felt here
     * @param lit         whether there is light enough to see by — daylight, or a fire burning here
     * @param fingerprint stable hash of all of the above
     */
    public record VisualContext(int version, String biome, List<Feature> features, String timeOfDay,
                                String season, String weather, double temperatureC, boolean lit,
                                String fingerprint) { }

    /** The ground the Chronicle stands on, and the sky over it — a record because Map.of stops at ten pairs. */
    private record Ground(java.util.UUID chunk, java.util.UUID worldId, String biome, int elevation, int moisture,
                          int gridX, int gridY, int worldHeight, String weatherKind, double tempC, int windKph) { }

    @Transactional(readOnly = true)
    public VisualContext active(Instant at) {
        Ground here = jdbc.query(
            "SELECT wc.id, wc.biome, wc.elevation, wc.moisture, wc.grid_y, wc.grid_x, wc.world_id, " +
            "  COALESCE(wg.height_chunks,1) AS height, ww.weather_kind, " +
            "  COALESCE(ww.ambient_temperature_c,18.0) AS t, COALESCE(ww.wind_speed_kph,6) AS w " +
            "FROM chronicle c JOIN world_object o ON o.id=c.id " +
            "JOIN world_chunk wc ON wc.id=o.current_location_id " +
            "LEFT JOIN world_genesis wg ON wg.world_id=wc.world_id " +
            "LEFT JOIN world_weather ww ON ww.world_id=wc.world_id " +
            "WHERE c.life_state='LIVING'",
            rs -> rs.next() ? new Ground(
                rs.getObject("id", java.util.UUID.class), rs.getObject("world_id", java.util.UUID.class),
                rs.getString("biome"), rs.getInt("elevation"), rs.getInt("moisture"),
                rs.getInt("grid_x"), rs.getInt("grid_y"), rs.getInt("height"),
                rs.getString("weather_kind") == null ? "CLEAR" : rs.getString("weather_kind"),
                rs.getBigDecimal("t").doubleValue(), rs.getInt("w")) : null);
        if (here == null) return null;

        java.util.UUID chunk = here.chunk();
        String biome = here.biome();

        // Aspect, exactly as the weather callers derive it (#159) — a south-facing slope is warmer, and what a
        // place looks like should agree with what it feels like.
        boolean sunWarmed = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk n WHERE n.world_id=? AND n.grid_x=? AND n.grid_y=? " +
            "AND n.elevation > ?)", Boolean.class,
            here.worldId(), here.gridX(), here.gridY() - 1, here.elevation() + 40));

        BiomeClimate.Local local = BiomeClimate.at(biome, here.elevation(), here.moisture(),
            here.gridY(), here.worldHeight(), here.weatherKind(), here.tempC(), here.windKph(), sunWarmed);

        // Only what stands on THIS ground. No neighbour, no plan, no lair two chunks away.
        List<Feature> features = new java.util.ArrayList<>();
        jdbc.query("SELECT site_category, site_kind FROM ecology_site WHERE chunk_id=? ORDER BY site_kind", rs -> {
            features.add(new Feature("SITE:" + rs.getString(1), rs.getString(2)));
        }, chunk);
        jdbc.query("SELECT cp.project_kind, w.display_name FROM construction_project cp " +
                   "JOIN world_object w ON w.id=cp.object_id " +
                   "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' " +
                   "ORDER BY cp.project_kind", rs -> {
            features.add(new Feature("BUILT:" + rs.getString(1), rs.getString(2)));
        }, chunk);

        String timeOfDay = timeOfDay(at);
        String season = season(at);
        boolean lit = !"NIGHT".equals(timeOfDay) || fireBurningAt(chunk);
        // Inside the rock the sky is irrelevant: a cave is dark at noon unless something is burning (#158).
        if ("CAVE_INTERIOR".equals(biome) || "CAVE_MOUTH".equals(biome)) lit = fireBurningAt(chunk);

        return new VisualContext(VERSION, biome, List.copyOf(features), timeOfDay, season,
            local.kind(), local.temperatureC(), lit,
            fingerprint(biome, features, timeOfDay, season, local, lit));
    }

    private boolean fireBurningAt(java.util.UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id " +
            "WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, chunk));
    }

    static String timeOfDay(Instant at) {
        int hour = at.atZone(ZoneOffset.UTC).getHour();
        if (hour < 6) return "NIGHT";
        if (hour < 9) return "DAWN";
        if (hour < 18) return "DAY";
        if (hour < 21) return "DUSK";
        return "NIGHT";
    }

    /** The same four seasons the foraging gate reads, so a picture of the place cannot disagree with its food. */
    static String season(Instant at) {
        return switch (at.atZone(ZoneOffset.UTC).getMonthValue()) {
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "AUTUMN";
            default -> "WINTER";
        };
    }

    /**
     * A stable hash of everything the payload reports. Same place, same state, same string — across a reload, a
     * restart, or a different machine, because it is built from the facts and never from an object identity or a
     * clock reading finer than the fields above.
     */
    private static String fingerprint(String biome, List<Feature> features, String timeOfDay, String season,
                                      BiomeClimate.Local local, boolean lit) {
        StringBuilder material = new StringBuilder()
            .append(VERSION).append('|').append(biome).append('|').append(timeOfDay).append('|').append(season)
            .append('|').append(local.kind()).append('|').append(Math.round(local.temperatureC()))
            .append('|').append(lit);
        features.stream()
            .map(f -> f.kind() + ':' + f.name())
            .sorted()                       // order of arrival must not change the fingerprint
            .forEach(f -> material.append('|').append(f));
        return Integer.toHexString(material.toString().hashCode());
    }
}
