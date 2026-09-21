package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ground that is theirs (#211, epic #207): how a people perceives work done in its territory, and what it does about it.
 *
 * <p>A community's territory is the ground within its radius of the isle. What it perceives there is what the world
 * already records: every work act a Chronicle does leaves disturbance on the chunk (#215/#216), and refuse left near
 * water fouls it. In a world with one outsider in it, work in the territory is that outsider's.
 *
 * <p>The response is a ladder, climbed only by what actually happens and never by a number shown to anyone:
 * <ol>
 *   <li><b>Notice.</b> Work in their territory without leave is noticed: reed markers appear where it was done, and
 *       it counts a little against the Chronicle each day it goes on.</li>
 *   <li><b>Demand.</b> Three notices within ten days bring a demand for compensation, kept as the relation's
 *       obligation. Restitution (#114) answers it.</li>
 *   <li><b>Boycott.</b> Encroachment that goes on runs trust down until trade is refused, as #113 already does below
 *       its threshold; the boycott is recorded when it falls that far.</li>
 *   <li><b>Retaliation.</b> Past that, the isle turns hostile, with everything #114 makes of hostility.</li>
 * </ol>
 * Refuse fouling the water by the isle is its own grievance. And coexistence is possible: a Chronicle who has earned
 * enough trust and understanding may ask leave to work their ground, and work done under that leave is no offence
 * for as long as it lasts (#112, ASK_ACCESS).
 */
@Service
public class TerritoryService {

    /** Disturbance in their territory in a day that counts as encroachment rather than someone passing through. */
    static final int NOTICED = 4;
    /** How long leave to work their ground lasts once given. */
    static final Duration LEAVE_LASTS = Duration.ofDays(30);
    /** Refuse on the water by the isle at which they hold it against whoever left it. */
    static final int FOULED = 50;
    /** What taking the standing wood off a people's own ground costs, and how long they remember one. */
    static final int TREES_TAKEN_COSTS = -12;
    static final Duration REMEMBERS_A_FELLING = Duration.ofDays(30);
    /** Fellings on their ground within that span after which the isle stops treating it as a grievance and treats it as an enemy. */
    static final int FELLINGS_THAT_MAKE_AN_ENEMY = 3;

    private final JdbcTemplate jdbc;

    public TerritoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One day of watching their ground. Called from the community's daily step. */
    void watch(UUID community, Instant day) {
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING' LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return;
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT n.home_chunk_id, n.territory_radius, n.grave_encroachment_kind, h.world_id, h.grid_x, h.grid_y " +
            "FROM native_community n JOIN world_chunk h ON h.id=n.home_chunk_id WHERE n.id=?", community);
        int radius = ((Number) c.get("territory_radius")).intValue();
        Timestamp since = Timestamp.from(day.minus(Duration.ofDays(1))), until = Timestamp.from(day);

        // The isle itself is not "their ground" in this sense — standing on it is trespass, which #114 answers on
        // arrival. This is the fishing water and the reed beds around it.
        Integer worked = jdbc.queryForObject(
            "SELECT COALESCE(SUM(e.amount),0) FROM chunk_disturbance_event e JOIN world_chunk k ON k.id=e.chunk_id " +
            "WHERE k.world_id=? AND k.id <> ? AND greatest(abs(k.grid_x-?), abs(k.grid_y-?)) <= ? AND e.occurred_at > ? AND e.occurred_at <= ?",
            Integer.class, c.get("world_id"), c.get("home_chunk_id"), c.get("grid_x"), c.get("grid_y"), radius, since, until);
        boolean fouled = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chunk_refuse r JOIN world_chunk k ON k.id=r.chunk_id WHERE k.world_id=? " +
            "AND greatest(abs(k.grid_x-?), abs(k.grid_y-?)) <= 1 AND r.refuse_level >= ?)",
            Boolean.class, c.get("world_id"), c.get("grid_x"), c.get("grid_y"), FOULED));

        if (worked == null || worked < NOTICED) worked = 0;
        if (worked == 0 && !fouled) return;
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id) VALUES (?,?) " +
            "ON CONFLICT (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL DO NOTHING", community, chronicle);
        if (fouled && !happenedSince(community, chronicle, "WATER_FOULED", day.minus(Duration.ofDays(7))))
            grievance(community, chronicle, day, "WATER_FOULED", -5, Map.of());
        boolean leave = hasLeave(community, chronicle, day);

        // The one kind of work that is not a trespass but a loss (#211, #118). A people names the disturbance that
        // takes the thing its life is made of — for a reed-isle people, the standing wood of the carr: their withies,
        // their poles, their fuel and the cover their water sits in. Cutting it inside their ground is not "work
        // somebody did nearby"; it is the ground itself going. Counted apart from ordinary encroachment, weighed far
        // heavier, and not excused by leave to work the ground: leave to cut reeds is not leave to fell the alders.
        String grave = (String) c.get("grave_encroachment_kind");
        if (grave != null) {
            Integer felled = jdbc.queryForObject(
                "SELECT COALESCE(SUM(e.amount),0) FROM chunk_disturbance_event e JOIN world_chunk k ON k.id=e.chunk_id " +
                "WHERE k.world_id=? AND greatest(abs(k.grid_x-?), abs(k.grid_y-?)) <= ? AND e.source_kind=? " +
                "AND e.occurred_at > ? AND e.occurred_at <= ?",
                Integer.class, c.get("world_id"), c.get("grid_x"), c.get("grid_y"), radius, grave, since, until);
            if (felled != null && felled > 0) {
                grievance(community, chronicle, day, "TREES_TAKEN", TREES_TAKEN_COSTS, Map.of("taken", felled));
                int fellings = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM native_event WHERE community_id=? AND subject_id=? AND event_kind='TREES_TAKEN' AND occurred_at > ?",
                    Integer.class, community, chronicle, Timestamp.from(day.minus(REMEMBERS_A_FELLING)));
                if (fellings >= FELLINGS_THAT_MAKE_AN_ENEMY) {
                    jdbc.update("UPDATE native_community SET security_posture='HOSTILE' WHERE id=?", community);
                    jdbc.update("UPDATE native_settlement_site SET access_rule='CLOSED' WHERE community_id=?", community);
                }
            }
        }

        if (worked == 0 || leave) return;

        grievance(community, chronicle, day, "ENCROACHMENT_NOTICED", -2, Map.of("disturbance", worked));
        int notices = jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND subject_id=? AND event_kind='ENCROACHMENT_NOTICED' AND occurred_at > ?",
            Integer.class, community, chronicle, Timestamp.from(day.minus(Duration.ofDays(10))));
        if (notices >= 3 && !happenedSince(community, chronicle, "COMPENSATION_DEMANDED", day.minus(Duration.ofDays(10)))) {
            jdbc.update("UPDATE community_relation SET obligation='compensation for work done on their ground' WHERE community_id=? AND chronicle_id=?", community, chronicle);
            grievance(community, chronicle, day, "COMPENSATION_DEMANDED", -3, Map.of());
        }
        int standing = jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
        if (standing < 10 && !happenedSince(community, chronicle, "BOYCOTT", day.minus(Duration.ofDays(30))))
            grievance(community, chronicle, day, "BOYCOTT", 0, Map.of());
        if (standing <= -30) {
            jdbc.update("UPDATE native_community SET security_posture='HOSTILE' WHERE id=?", community);
            if (!happenedSince(community, chronicle, "RETALIATION_THRESHOLD", day.minus(Duration.ofDays(30))))
                grievance(community, chronicle, day, "RETALIATION_THRESHOLD", 0, Map.of());
        }
    }

    /** Leave to work their ground, given and not yet run out. */
    boolean hasLeave(UUID community, UUID chronicle, Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND payload->>'response'='ACCESS_GRANTED' " +
            "AND occurred_at > ? AND occurred_at <= ?)", Boolean.class, community, chronicle,
            Timestamp.from(at.minus(LEAVE_LASTS)), Timestamp.from(at)));
    }

    private boolean happenedSince(UUID community, UUID chronicle, String kind, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind=? AND occurred_at > ?)",
            Boolean.class, community, chronicle, kind, Timestamp.from(since)));
    }

    private void grievance(UUID community, UUID chronicle, Instant at, String kind, int standingDelta, Map<String, Object> payload) {
        if (standingDelta != 0)
            jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind=?, last_event_at=? " +
                "WHERE community_id=? AND chronicle_id=?", standingDelta, kind, Timestamp.from(at), community, chronicle);
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (json.length() > 1) json.append(',');
            json.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        json.append('}');
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,?::jsonb)",
            community, Timestamp.from(at), kind, chronicle, json.toString());
    }
}
