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

    /**
     * The contract version. Additive changes raise it; a removal or rename would too, and none has happened yet.
     *
     * <p><b>1</b> was biome, features and sky. <b>2</b> gave this ground its bands ({@link Land}) and the next
     * ground a bearing and a relief ({@link Nearby}). <b>3</b> lets a skyline carry what is built or broken on that
     * next ground ({@link Landmark}), which is the last line of #224's contract.
     *
     * <p>Every field each earlier version carried is still here and still means what it meant, so a client written
     * against any of them keeps working and may ignore whatever it does not know.
     */
    public static final int VERSION = 3;

    private final JdbcTemplate jdbc;

    public VisualContextService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One thing standing on this ground: a natural site, or something a Chronicle has built. */
    public record Feature(String kind, String name) { }

    /**
     * The lie of this ground in bands (#224).
     *
     * <p>{@code world_chunk.elevation} and {@code moisture} have been maintained by genesis since the table
     * existed, and both were read into this service's own query to hand to {@link BiomeClimate} — and then
     * thrown away. So the contract described a place by its biome alone: a saturated lowland fen and a dry
     * upland heath could be the same WETLAND and the same GRASSLAND, and no caller could tell a bog from a
     * moor. Bands rather than raw numbers, because a backdrop needs the kind of ground and not a survey, and
     * raw elevation is a step toward the world-seed coordinates this payload must never carry.
     *
     * @param elevation LOWLAND / RISING / UPLAND / HIGH
     * @param moisture  ARID / DRY / MOIST / WET / SATURATED
     * @param sunWarmed whether higher ground to the north leaves this slope facing the sun — the same aspect the
     *                  warmth model uses, so what a place looks like agrees with what it feels like
     */
    public record Land(String elevation, String moisture, boolean sunWarmed) { }

    /**
     * One piece of the next ground, and which way it lies (#224).
     *
     * <p>{@code surroundings} told a caller that moorland was visible from here and never which way to look, so
     * nothing could be composed from it: a sea to the west and a mountain wall to the east are the same payload.
     * The direction was always available — the neighbours are found by their grid offset — and was discarded on
     * the way out. {@code relief} is the ticket's "elevation transition": whether that ground stands above this,
     * level with it, or below.
     *
     * <p>Still only the LIE OF THE LAND. No neighbour's sites, structures, lairs or wildlife: a lair two chunks
     * east is exactly what this payload must never tell a player, and one chunk east is no better.
     *
     * @param direction north / south / east / west, by {@link Compass}
     * @param biome     the kind of country there
     * @param relief    ABOVE / LEVEL / BELOW, by the same 40-unit step the aspect model uses
     * @param distance  always ADJACENT — the one ring of ground a person can see the shape of from where they
     *                  stand. Carried as a band rather than left implicit so that a farther tier, if one is ever
     *                  earned, does not change the meaning of what is already here.
     */
    public record Nearby(String direction, String biome, String relief, String distance) { }

    /**
     * Something built or broken standing on the next ground, and which way it lies (#224).
     *
     * <p>The last line of this story's contract: "adjacent or visible waterbody, coast, elevation transition,
     * ecotone, <b>ruin, or structure</b> with direction/distance band". {@link Nearby} answered the first four and
     * stopped at the lie of the land, and the comment beside it said flatly that no neighbour's structures are
     * read — which was the right rule for the tier that existed and the wrong one for this ticket.
     *
     * <p><b>What makes it perception-safe is that a kind is not an identity.</b> A broken silhouette on the next
     * hill is one of the plainest things in a landscape — you cannot stand at (12,14) and fail to see the tower at
     * (12,15) — but which tower it is, what it was, and what is left inside it are not visible from here, and they
     * are what makes a ruin worth walking to. So this carries RUIN, BUILT or SETTLEMENT and a bearing, and never a
     * name: the watchtower is "something built, south" until the Chronicle goes and looks. The ticket's own words
     * for this are "discovery/perception eligibility—not secret marker identity", and the discovery is preserved
     * precisely because the shape is not.
     *
     * <p>What stays invisible is unchanged and deliberate: no ecology or resource site, no monster lair, no
     * wildlife. A lair two chunks east is what this payload must never tell a player, and one chunk east is no
     * better — a lair is not a silhouette, it is a thing you learn by tracking. Only works of hands and their
     * wrecks are reported, because only those change a skyline.
     *
     * @param kind      RUIN / BUILT / SETTLEMENT — the category only, never which one
     * @param direction north / south / east / west, by {@link Compass}
     * @param distance  always ADJACENT, the one ring whose shapes can be made out from here
     */
    public record Landmark(String kind, String direction, String distance) { }

    /**
     * Everything visible from where the Chronicle stands.
     *
     * @param version     the contract version this payload was built to
     * @param biome       the ground underfoot
     * @param features    sites, completed structures and standing villages ON this chunk — never anywhere else
     * @param timeOfDay   DAWN / DAY / DUSK / NIGHT
     * @param season      SPRING / SUMMER / AUTUMN / WINTER
     * @param weather     the weather as FELT here, not the global sky
     * @param temperatureC the temperature as felt here
     * @param lit         whether there is light enough to see by — daylight, or a fire burning here
     * @param surroundings the kinds of ground visible on the four neighbouring chunks (#232), distinct and sorted;
     *                    empty in the dark or inside a cave, where nothing beyond this ground can be seen. Only the
     *                    lie of the land — never what stands on it. <b>Kept</b> beside {@code nearby} rather than
     *                    replaced by it: the ticket asks for an ADDITIVE contract, and a client reading this field
     *                    at version 1 must keep working at version 2.
     * @param land        the lie of THIS ground in bands (#224)
     * @param nearby      the next ground with a direction and a relief on each piece of it (#224)
     * @param landmarks   works of hands and their wrecks visible on the next ground, by kind and bearing and never
     *                    by name (#224); empty in the dark, inside a cave, and for ground that is itself occluded
     * @param fingerprint stable hash of all of the above
     */
    public record VisualContext(int version, String biome, List<Feature> features, String timeOfDay,
                                String season, String weather, double temperatureC, boolean lit,
                                List<String> surroundings, Land land, List<Nearby> nearby,
                                List<Landmark> landmarks, String fingerprint) {
        /** The shape before #232's visible-nearby tier: nothing seen beyond this ground. */
        public VisualContext(int version, String biome, List<Feature> features, String timeOfDay, String season,
                             String weather, double temperatureC, boolean lit, String fingerprint) {
            this(version, biome, features, timeOfDay, season, weather, temperatureC, lit, List.of(), null, List.of(), List.of(), fingerprint);
        }
        /** The shape before #224 gave the ground bands and the next ground a bearing. */
        public VisualContext(int version, String biome, List<Feature> features, String timeOfDay, String season,
                             String weather, double temperatureC, boolean lit, List<String> surroundings, String fingerprint) {
            this(version, biome, features, timeOfDay, season, weather, temperatureC, lit, surroundings, null, List.of(), List.of(), fingerprint);
        }
        /** The shape before a skyline could carry a built thing: bands and bearings, but nothing standing on them. */
        public VisualContext(int version, String biome, List<Feature> features, String timeOfDay, String season,
                             String weather, double temperatureC, boolean lit, List<String> surroundings,
                             Land land, List<Nearby> nearby, String fingerprint) {
            this(version, biome, features, timeOfDay, season, weather, temperatureC, lit, surroundings, land, nearby, List.of(), fingerprint);
        }
    }

    /** Where one band ends and the next begins. Bands, not numbers: a backdrop wants the kind, not a survey. */
    static String elevationBand(int elevation) {
        return elevation < 120 ? "LOWLAND" : elevation < 400 ? "RISING" : elevation < 700 ? "UPLAND" : "HIGH";
    }

    /** The same, for how wet the ground is. SATURATED is standing water; ARID will not hold a crop. */
    static String moistureBand(int moisture) {
        return moisture < 20 ? "ARID" : moisture < 40 ? "DRY" : moisture < 60 ? "MOIST" : moisture < 80 ? "WET" : "SATURATED";
    }

    /** Whether that ground stands over this one, by the same 40-unit step the aspect model is built on. */
    static String relief(int there, int here) {
        return there > here + 40 ? "ABOVE" : there < here - 40 ? "BELOW" : "LEVEL";
    }

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
        // A people's village standing on this ground (#115, #224): houses, walkways and nets are as plain to see as a
        // lean-to, and only while they stand — a village burnt to nothing (#111) is no longer scenery. The people
        // themselves are not reported, any more than wildlife is: the payload says what the place looks like, not who
        // is in it.
        jdbc.query("SELECT DISTINCT n.species_key FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
                   "JOIN native_community n ON n.id=s.community_id " +
                   "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND s.site_kind='VILLAGE' ORDER BY 1", rs -> {
            features.add(new Feature("SETTLEMENT:" + rs.getString(1), "village"));
        }, chunk);

        String timeOfDay = timeOfDay(at);
        String season = season(at);
        boolean lit = !"NIGHT".equals(timeOfDay) || fireBurningAt(chunk);
        // Inside the rock the sky is irrelevant: a cave is dark at noon unless something is burning (#158).
        if ("CAVE_INTERIOR".equals(biome) || "CAVE_MOUTH".equals(biome)) lit = fireBurningAt(chunk);

        // What can be seen of the next ground (#232): the kind of country on the four neighbouring chunks, and only
        // that. The lie of the land is visible from where anyone stands — a mountain wall, the sea, a wood's edge —
        // but what stands on it is not: a lair two chunks east is exactly what this payload must never tell a player,
        // so no neighbour's sites or structures are read here. Nothing is seen from inside a cave, or in the dark.
        // ...and WHICH WAY it lies (#224). The neighbours were already found by their grid offset and the offset
        // was thrown away on the way out, so "moorland is visible" could not be composed into anything: a sea to
        // the west and a mountain wall to the east were the same payload. Ordered by bearing so the list is
        // stable, which the fingerprint depends on.
        boolean blind = !lit || "CAVE_INTERIOR".equals(biome);
        List<Nearby> nearby = new java.util.ArrayList<>();
        if (!blind) jdbc.query(
            "SELECT n.biome, n.elevation, n.grid_x - ? AS dx, n.grid_y - ? AS dy FROM world_chunk n " +
            "WHERE n.world_id=? AND abs(n.grid_x-?)+abs(n.grid_y-?)=1 ORDER BY dy, dx", rs -> {
                String bearing = Compass.of(rs.getInt("dx"), rs.getInt("dy"));
                if (bearing != null)
                    nearby.add(new Nearby(bearing, rs.getString("biome"),
                        relief(rs.getInt("elevation"), here.elevation()), "ADJACENT"));
            }, here.gridX(), here.gridY(), here.worldId(), here.gridX(), here.gridY());

        // AND WHAT STANDS ON IT (#224) — the last line of this story's contract, which asks for a "ruin, or
        // structure with direction/distance band". The tier above reads the lie of the land and the comment on it
        // said flatly that a neighbour's structures are never read; that was right for the tier that existed and
        // wrong for this ticket, so it is now narrower: no neighbour's SITES, LAIRS or WILDLIFE are read, and
        // works of hands are.
        //
        // The line between them is what a skyline shows. A ruin, a finished building and a standing village all
        // break a horizon and cannot be missed from the next chunk; an ore seam, a berry stand and a wolf's lair
        // do not and are learned by walking the ground. And only the KIND crosses — never the name — so the
        // Overgrown Watchtower at (12,15) is "something built, to the south" to anyone standing at (12,14). That
        // is the ticket's "perception eligibility, not secret marker identity", and it is also the only form in
        // which this is safe to send: the shape is the invitation, the identity is the discovery.
        //
        // Occlusion cuts both ways. Blind sees nothing, as above; and nothing is seen INSIDE rock either, so a
        // neighbour that is cave interior reports no shapes however bright the day is where the viewer stands.
        List<Landmark> landmarks = new java.util.ArrayList<>();
        if (!blind) jdbc.query(
            "SELECT DISTINCT k.kind, n.grid_x - ? AS dx, n.grid_y - ? AS dy " +
            "FROM world_chunk n " +
            "JOIN LATERAL (" +
            "      SELECT 'RUIN' AS kind FROM ecology_site es " +
            "       WHERE es.chunk_id = n.id AND es.site_category = 'RUIN' " +
            "  UNION ALL " +
            "      SELECT 'BUILT' FROM construction_project cp JOIN world_object w ON w.id = cp.object_id " +
            "       WHERE w.current_location_id = n.id AND cp.state = 'COMPLETED' AND w.lifecycle_state = 'ACTIVE' " +
            "  UNION ALL " +
            "      SELECT 'SETTLEMENT' FROM native_settlement_site s JOIN world_object w ON w.id = s.object_id " +
            "       WHERE w.current_location_id = n.id AND w.lifecycle_state = 'ACTIVE' AND s.site_kind = 'VILLAGE'" +
            ") k ON true " +
            "WHERE n.world_id = ? AND abs(n.grid_x - ?) + abs(n.grid_y - ?) = 1 " +
            "  AND n.biome <> 'CAVE_INTERIOR' " +
            "ORDER BY dy, dx, k.kind", rs -> {
                String bearing = Compass.of(rs.getInt("dx"), rs.getInt("dy"));
                if (bearing != null) landmarks.add(new Landmark(rs.getString("kind"), bearing, "ADJACENT"));
            }, here.gridX(), here.gridY(), here.worldId(), here.gridX(), here.gridY());

        // Kept exactly as it was, for a client written against version 1 (#232).
        List<String> surroundings = nearby.stream().map(Nearby::biome).distinct().sorted().toList();

        Land land = new Land(elevationBand(here.elevation()), moistureBand(here.moisture()), sunWarmed);

        String fingerprint = fingerprint(biome, features, timeOfDay, season, local, lit);
        // The ground's own bands always count: they are a property of the place and are known in the dark, so
        // folding them in unconditionally is right — and it means a bog and a moor that share a biome no longer
        // share a fingerprint, which is the bug the bands exist to fix.
        fingerprint = Integer.toHexString((fingerprint + "|land:" + land.elevation() + "/" + land.moisture()
            + (land.sunWarmed() ? "/sun" : "")).hashCode());
        // The view is folded in only when there IS one, so a place with nothing to see keeps the fingerprint it
        // always had — in a cave, or after dark.
        if (!nearby.isEmpty())
            fingerprint = Integer.toHexString((fingerprint + "|around:" + nearby.stream()
                .map(n -> n.direction() + ":" + n.biome() + ":" + n.relief()).reduce("", (a, b) -> a + "," + b)).hashCode());
        // Folded in only when a skyline actually carries something, for the same reason the view is: a place with
        // nothing built near it keeps the fingerprint it had before this tier existed, so "refresh returns the same
        // fingerprint for unchanged world state" still holds across the version bump for most of the map. When a
        // neighbour's building is finished or burns down, this is what makes the backdrop notice (#224).
        if (!landmarks.isEmpty())
            fingerprint = Integer.toHexString((fingerprint + "|skyline:" + landmarks.stream()
                .map(l -> l.direction() + ":" + l.kind()).reduce("", (a, b) -> a + "," + b)).hashCode());

        return new VisualContext(VERSION, biome, List.copyOf(features), timeOfDay, season,
            local.kind(), local.temperatureC(), lit, surroundings, land, List.copyOf(nearby),
            List.copyOf(landmarks), fingerprint);
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
