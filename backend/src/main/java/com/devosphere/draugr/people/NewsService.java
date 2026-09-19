package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * What one isle hears of what a Chronicle did at another (#114, epic #109).
 *
 * <p>A people is not alone: kin isles trade, marry and visit, and what was seen at one is told at the other. News
 * travels at the pace people do — a few days for the distance between isles — and it arrives as hearsay: it moves
 * the hearers' standing by half what it moved the witnesses', never more, and it cannot make them hostile, only
 * wary, because they were not the ones wronged. Only what was witnessed is told; what no one saw, no one can pass
 * on. And it is told once: each piece of news is recorded where it was heard, against the event it came from.
 *
 * <p>Good report travels too, if more quietly: a promise kept is mentioned.
 */
@Service
public class NewsService {

    /** What a told thing does to the hearers' standing: about half of what it did where it happened. */
    static final Map<String, Integer> HEARSAY = Map.ofEntries(
        Map.entry("MURDER", -50), Map.entry("RESTRAINT_ATTEMPT", -30), Map.entry("FIRE_DAMAGE", -40),
        Map.entry("HARM_TO_INDIVIDUAL", -20), Map.entry("DAMAGE_TO_SETTLEMENT", -20), Map.entry("FOOD_THEFT", -15),
        Map.entry("PROPERTY_THEFT", -10), Map.entry("BROKEN_PROMISE", -7), Map.entry("OBLIGATION_COMPLETED", 2));

    /** The told kinds as an SQL list: constants of this class, never input. */
    private static final String KINDS = HEARSAY.keySet().stream().sorted().map(k -> "'" + k + "'").collect(java.util.stream.Collectors.joining(","));

    /** How far news carries between kin isles, in chunks, and how many chunks it covers in a day. */
    static final int CARRIES = 24, CHUNKS_A_DAY = 4, AT_LEAST_DAYS = 2;

    private final JdbcTemplate jdbc;

    public NewsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Called by the daily step: this community hears what has had time to reach it from its kin. */
    void hear(UUID community, Instant day) {
        for (Map<String, Object> told : jdbc.queryForList(
                "SELECT e.id, e.event_kind, e.subject_id, e.occurred_at, o.name AS from_isle, " +
                "       greatest(abs(oh.grid_x-h.grid_x), abs(oh.grid_y-h.grid_y)) AS distance " +
                "FROM native_community me JOIN world_chunk h ON h.id=me.home_chunk_id " +
                "JOIN native_community o ON o.species_key=me.species_key AND o.id <> me.id AND o.world_id=me.world_id " +
                "JOIN world_chunk oh ON oh.id=o.home_chunk_id " +
                "JOIN native_event e ON e.community_id=o.id " +
                "WHERE me.id=? AND e.subject_id IS NOT NULL AND e.event_kind IN (" + KINDS + ") " +
                "AND COALESCE(e.payload->>'witnessed','true') <> 'false' " +
                "AND greatest(abs(oh.grid_x-h.grid_x), abs(oh.grid_y-h.grid_y)) <= ? " +
                "AND e.occurred_at <= ? " +
                "AND EXISTS (SELECT 1 FROM chronicle c WHERE c.id=e.subject_id) " +
                "AND NOT EXISTS (SELECT 1 FROM native_event heard WHERE heard.community_id=me.id AND heard.event_kind='HEARD_OF' " +
                "                AND heard.payload->>'source' = e.id::text) " +
                "ORDER BY e.id",
                community, CARRIES, Timestamp.from(day.minus(Duration.ofDays(AT_LEAST_DAYS))))) {
            int days = Math.max(AT_LEAST_DAYS, ((Number) told.get("distance")).intValue() / CHUNKS_A_DAY);
            if (((Timestamp) told.get("occurred_at")).toInstant().plus(Duration.ofDays(days)).isAfter(day)) continue;
            UUID chronicle = (UUID) told.get("subject_id");
            int delta = HEARSAY.get((String) told.get("event_kind"));
            jdbc.update("INSERT INTO community_relation (community_id, chronicle_id) VALUES (?,?) " +
                "ON CONFLICT (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL DO NOTHING", community, chronicle);
            jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind='HEARD_OF', last_event_at=? " +
                "WHERE community_id=? AND chronicle_id=?", delta, Timestamp.from(day), community, chronicle);
            jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,'HEARD_OF',?," +
                "jsonb_build_object('source', ?::text, 'what', ?::text, 'from', ?::text))",
                community, Timestamp.from(day), chronicle, told.get("id").toString(), told.get("event_kind"), told.get("from_isle"));
            // Wary of someone with a bad name, but not hostile: they were not the ones wronged.
            Integer standing = jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?",
                Integer.class, community, chronicle);
            if (standing != null && standing <= -30)
                jdbc.update("UPDATE native_community SET security_posture='GUARDED' WHERE id=? AND security_posture IN ('AT_EASE','WARY')", community);
        }
    }

}
