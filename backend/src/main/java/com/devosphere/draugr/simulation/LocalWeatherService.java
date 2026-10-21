package com.devosphere.draugr.simulation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * The sky over one chunk.
 *
 * <p>Weather is stored once per world, but what a Chronicle stands in is local: a sheltered forest floor, a
 * hollow, a high ridge and an open shore do not share a sky, and {@link BiomeClimate#at} is what turns the
 * world's weather into the weather HERE. That distinction was the whole point of #778 — before it, the reason
 * given for a refusal came from the global front rather than the ground underfoot.
 *
 * <p><b>Why this class exists.</b> The environment query that feeds {@code BiomeClimate.at} was written out
 * longhand at six separate call sites, each with its own COALESCE defaults. Six copies of one query is the
 * shape that produced the unwearable-garment defect (a catalogue and a CHECK constraint keeping two separate
 * lists of the same thing), and it will drift the moment one of them learns something the others do not. New
 * code asks here instead; {@code ChronicleActionService.localClimate} now delegates here too, so this change
 * removes a copy rather than adding a seventh. The other five (ExaminationService, ChroniclePhysiologyService,
 * ChronicleService, VisualContextService and ChronicleActionService's prose grounding) now ask here too, so this
 * is the only place the query exists. Keep it that way: a caller that needs the local sky calls {@link #at}.
 */
@Service
public class LocalWeatherService {

    /** The weather kinds that make work-in-progress wet. */
    private static final java.util.Set<String> WET = java.util.Set.of("RAIN", "STORM", "SNOW");

    private final JdbcTemplate jdbc;

    public LocalWeatherService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * The sky over this chunk. Null-safe throughout: a world with no weather row reads as CLEAR rather than
     * throwing, because a missing front is not a reason to refuse an action.
     */
    public BiomeClimate.Local at(UUID location) { return at(jdbc, location); }

    /**
     * The same reading, for a caller that holds a {@link JdbcTemplate} but not this service.
     *
     * <p>Static so {@code ChronicleActionService} can delegate here without a new constructor parameter: its
     * constructor takes twenty-six collaborators and {@code IntentClassificationRegressionTest} builds it with
     * nulls on purpose, so widening it to remove a duplicated query would be a poor trade.
     */
    public static BiomeClimate.Local at(JdbcTemplate jdbc, UUID location) {
        Map<String, Object> env = jdbc.queryForMap(
            "SELECT wc.biome, COALESCE(wc.elevation,0) AS elevation, COALESCE(wc.moisture,500) AS moisture, " +
            "COALESCE(wc.grid_y,0) AS grid_y, COALESCE(wg.height_chunks,1) AS height_chunks, " +
            "COALESCE(ww.weather_kind,'CLEAR') AS weather_kind, COALESCE(ww.ambient_temperature_c,18.0) AS t, " +
            "COALESCE(ww.wind_speed_kph,6) AS w, " +
            "COALESCE((SELECT TRUE FROM world_chunk n WHERE n.world_id=wc.world_id AND n.grid_x=wc.grid_x " +
            "          AND n.grid_y=wc.grid_y-1 AND n.elevation > wc.elevation + 40 LIMIT 1), FALSE) AS sun_warmed " +
            "FROM world_chunk wc LEFT JOIN world_genesis wg ON wg.world_id=wc.world_id " +
            "LEFT JOIN world_weather ww ON ww.world_id=wc.world_id WHERE wc.id=?", location);
        return BiomeClimate.at(
            (String) env.get("biome"), ((Number) env.get("elevation")).intValue(),
            ((Number) env.get("moisture")).intValue(), ((Number) env.get("grid_y")).intValue(),
            ((Number) env.get("height_chunks")).intValue(), (String) env.get("weather_kind"),
            ((Number) env.get("t")).doubleValue(), ((Number) env.get("w")).intValue(),
            Boolean.TRUE.equals(env.get("sun_warmed")));
    }

    /** Is work here getting rained, stormed or snowed on? */
    public boolean wetHere(UUID location) { return WET.contains(at(location).kind()); }

    /** Is the wind here strong enough that long timbers and high work are a bad idea? */
    public boolean galeHere(UUID location) { return "STORM".equals(at(location).kind()); }
}
