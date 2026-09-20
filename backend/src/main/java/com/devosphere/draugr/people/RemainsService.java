package com.devosphere.draugr.people;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * What was theirs, and what is left of them (#122, epic #109).
 *
 * <p>A person of a people owns things — a hook, an awl, a knife, a basket — and owning them is what makes the
 * ticket's rules about possessions mean anything. When they die those things do not become loot and do not
 * vanish: they stay owned by the dead until their own people gather them up, which takes a few days, and until
 * then taking one is robbing the dead. A body left where it fell is buried by their own within two days, and the
 * grave stays on the isle as a place.
 *
 * <p>Both halves run on the community's daily step, so they happen whether or not a Chronicle is watching.
 */
@Service
public class RemainsService {

    /** Days before a people gather up what a dead kinsman owned, and days before they bury a body left lying. */
    static final int BELONGINGS_AFTER_DAYS = 3, BURIED_AFTER_DAYS = 2;

    /** What each kind of worker keeps of their own: the tool of the work they do. */
    private static final Map<String, String[]> THEIR_OWN = Map.of(
        "FISHER", new String[]{"bone_fish_hook", "Bone fish hook"},
        "MAKER", new String[]{"bone_awl", "Bone awl"},
        "FORAGER", new String[]{"woven_basket", "Woven basket"},
        "HUNTER", new String[]{"bone_knife", "Bone knife"},
        "HEADSPERSON", new String[]{"bone_knife", "Bone knife"},
        "GUARD", new String[]{"bone_knife", "Bone knife"},
        "ELDER", new String[]{"bone_needle", "Bone needle"});

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;

    public RemainsService(JdbcTemplate jdbc, PhysicalItemService items) {
        this.jdbc = jdbc;
        this.items = items;
    }

    /** Whether this object is a dead person's, and still theirs. */
    public boolean belongsToTheDead(UUID item) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object o JOIN native_individual n ON n.object_id=o.current_owner_id " +
            "WHERE o.id=? AND n.condition='DEAD')", Boolean.class, item));
    }

    /** The community a dead person whose things these are belonged to, or null. */
    public UUID theirPeople(UUID item) {
        return jdbc.query("SELECT n.community_id FROM world_object o JOIN native_individual n ON n.object_id=o.current_owner_id WHERE o.id=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, item);
    }

    /** One day of the community's care for its own: everyone grown has their own tool, the dead are buried, their things gathered. */
    void tendTheirOwn(UUID community, Instant day) {
        for (Map<String, Object> person : jdbc.queryForList(
                "SELECT n.object_id, n.role FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.condition <> 'DEAD' AND n.life_stage <> 'CHILD' AND w.lifecycle_state='ACTIVE' " +
                "AND NOT EXISTS (SELECT 1 FROM world_object o WHERE o.current_owner_id=n.object_id AND o.lifecycle_state='ACTIVE')", community)) {
            String[] own = THEIR_OWN.getOrDefault((String) person.get("role"), new String[]{"bone_needle", "Bone needle"});
            items.createHeldItem((UUID) person.get("object_id"), own[0], own[1], day, "MADE_BY_THEIR_OWN_HANDS");
        }

        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        // A body left lying is buried by their own, and the grave is a place on the isle.
        for (Map<String, Object> body : jdbc.queryForList(
                "SELECT n.object_id, n.given_name FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.condition='DEAD' AND w.lifecycle_state='ACTIVE' AND w.updated_at <= ?",
                community, Timestamp.from(day.minus(Duration.ofDays(BURIED_AFTER_DAYS))))) {
            UUID dead = (UUID) body.get("object_id");
            jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=?, destroyed_cause='BURIED', " +
                "current_location_id=NULL WHERE id=?", Timestamp.from(day), home, dead);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BURIED',jsonb_build_object('cause','found'))",
                dead, Timestamp.from(day));
            UUID grave = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'NATIVE_GRAVE',?,?)",
                grave, "The grave of " + body.get("given_name"), home);
            jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,'BURIED_THEIR_OWN',jsonb_build_object('name',?::text))",
                community, Timestamp.from(day), body.get("given_name"));
        }

        // What the dead owned is gathered by their kin, a few days on, and kept in the store.
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
        if (store == null) return;
        for (Map<String, Object> thing : jdbc.queryForList(
                "SELECT o.id, n.given_name FROM world_object o JOIN native_individual n ON n.object_id=o.current_owner_id " +
                "WHERE n.community_id=? AND n.condition='DEAD' AND o.lifecycle_state='ACTIVE' AND o.updated_at <= ?",
                community, Timestamp.from(day.minus(Duration.ofDays(BELONGINGS_AFTER_DAYS))))) {
            jdbc.update("UPDATE world_object SET current_owner_id=?, updated_at=now() WHERE id=?", store, thing.get("id"));
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BELONGINGS_RECOVERED',jsonb_build_object('community',?::text))",
                thing.get("id"), Timestamp.from(day), community.toString());
            jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,'BELONGINGS_RECOVERED',jsonb_build_object('name',?::text))",
                community, Timestamp.from(day), thing.get("given_name"));
        }
    }
}
