package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Travelling together (#113, epic #109): companionship with one of a people.
 *
 * <p>A companion is a person who has chosen to walk with the Chronicle for a while, never a follower summoned by a
 * menu. The ticket's rules, and how each is kept:
 * <ul>
 *   <li><b>Consent.</b> Only someone who knows the Chronicle, trusts them, and can be understood by them will go; only
 *       an adult whose work can be spared (never the one who speaks for the isle, an elder, or a child); and only
 *       from an isle that is fed, open and settled. They can refuse, and the isle can refuse for them.</li>
 *   <li><b>Needs.</b> They carry their own food, taken from the isle's store when they set out, and eat it day by
 *       day. When it runs out they go hungry, and a second hungry day sends them home.</li>
 *   <li><b>Location.</b> They are where they are: they walk where the Chronicle walks, and their body is a world
 *       object on the same ground. Going home puts them back on the isle, and what they carried back in the store.</li>
 *   <li><b>History and harm.</b> The companionship is a row with a start and an end and why it ended. They grow
 *       homesick after ten days away. If they die, it ends with them.</li>
 *   <li><b>Not an extension of the player.</b> They cannot be ordered, loaded or spent. What they give is company and
 *       their people's speech: each day together, the Chronicle understands their people a little better.</li>
 * </ul>
 */
@Service
public class CompanionService {

    public enum Act { ASK_TO_TRAVEL_TOGETHER, END_COMPANIONSHIP }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("ask to travel together", Act.ASK_TO_TRAVEL_TOGETHER), Map.entry("travel with me", Act.ASK_TO_TRAVEL_TOGETHER),
        Map.entry("come with me", Act.ASK_TO_TRAVEL_TOGETHER), Map.entry("walk with me", Act.ASK_TO_TRAVEL_TOGETHER),
        Map.entry("offer companionship", Act.ASK_TO_TRAVEL_TOGETHER), Map.entry("ask for a companion", Act.ASK_TO_TRAVEL_TOGETHER),
        Map.entry("journey with me", Act.ASK_TO_TRAVEL_TOGETHER), Map.entry("ask them to travel with me", Act.ASK_TO_TRAVEL_TOGETHER),
        Map.entry("end companionship", Act.END_COMPANIONSHIP), Map.entry("end our companionship", Act.END_COMPANIONSHIP),
        Map.entry("part ways", Act.END_COMPANIONSHIP), Map.entry("send them home", Act.END_COMPANIONSHIP),
        Map.entry("say goodbye", Act.END_COMPANIONSHIP), Map.entry("part company", Act.END_COMPANIONSHIP));

    /** What a companion takes from the store to eat on the way: three days' food. */
    static final int PROVISIONS = 3;
    /** Days away from home before a companion longs to go back. */
    static final int HOMESICK_AFTER_DAYS = 10;
    static final int TRUST_NEEDED = 30, UNDERSTANDING_NEEDED = 50;

    private final JdbcTemplate jdbc;

    public CompanionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** The act a text names, or null. Longest phrase wins. */
    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** The companion walking with this Chronicle now, or null. */
    public Map<String, Object> companionOf(UUID chronicle) {
        return jdbc.queryForList(
            "SELECT c.id, c.individual_id, c.community_id, c.started_at, n.given_name FROM native_companionship c " +
            "JOIN native_individual n ON n.object_id=c.individual_id WHERE c.chronicle_id=? AND c.ended_at IS NULL", chronicle)
            .stream().findFirst().orElse(null);
    }

    @Transactional
    public String[] act(UUID chronicle, UUID chunk, UUID community, Act act, String text, Instant at) {
        Map<String, Object> walking = companionOf(chronicle);
        if (act == Act.END_COMPANIONSHIP) {
            if (walking == null) return new String[]{"FAILED", "No one is walking with you."};
            goHome(walking, at, "PARTED");
            return new String[]{"SUCCEEDED", walking.get("given_name") + " touches your arm, turns, and sets off back toward the isle without looking round."};
        }
        if (walking != null)
            return new String[]{"PARTIAL", walking.get("given_name") + " is already walking with you."};
        if (community == null) return new String[]{"FAILED", "There is no one here to ask."};

        Map<String, Object> c = jdbc.queryForMap("SELECT home_chunk_id, lifecycle, trade_policy, shortage_days, staple_item_key FROM native_community WHERE id=?", community);
        Map<String, Object> r = jdbc.queryForList(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle).stream().findFirst().orElse(Map.of("standing", 0, "understanding", 0));
        int standing = ((Number) r.get("standing")).intValue(), understanding = ((Number) r.get("understanding")).intValue();
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
        String staple = (String) c.get("staple_item_key");
        String refusal = r.get("first_contact_at") == null ? "No one here knows you well enough to go anywhere with you."
            : !chunk.equals(c.get("home_chunk_id")) ? "You would have to ask on the isle itself, face to face."
            : understanding < UNDERSTANDING_NEEDED ? "They do not follow what you are asking. Pointing at the road and at them draws only puzzled looks."
            : standing < TRUST_NEEDED ? "They understand you well enough. No one moves. They do not trust you with one of their own."
            : !"SETTLED".equals(c.get("lifecycle")) || "CLOSED".equals(c.get("trade_policy")) || ((Number) c.get("shortage_days")).intValue() > 0
                ? "The isle cannot spare anyone now; every pair of hands is at the nets."
            : store == null || held(store, staple).size() < PROVISIONS ? "No one can go: there is not food enough in the store for anyone to take on the way."
            : null;
        if (refusal != null) { event(community, chronicle, at, "COMPANIONSHIP_REFUSED", null); return new String[]{"PARTIAL", refusal}; }

        Map<String, Object> who = willing(community, text);
        if (who == null) {
            event(community, chronicle, at, "COMPANIONSHIP_REFUSED", null);
            return new String[]{"PARTIAL", "Those you might ask are needed here: no one who can be spared steps forward."};
        }
        UUID person = (UUID) who.get("object_id");
        for (UUID fish : held(store, staple).subList(0, PROVISIONS)) {
            jdbc.update("UPDATE world_object SET current_owner_id=?, updated_at=now() WHERE id=?", person, fish);
            transition(fish, at, "PROVISIONS_TAKEN", community);
        }
        jdbc.update("INSERT INTO native_companionship (individual_id, community_id, chronicle_id, started_at) VALUES (?,?,?,?)",
            person, community, chronicle, Timestamp.from(at));
        jdbc.update("UPDATE native_individual SET available=FALSE WHERE object_id=?", person);
        event(community, chronicle, at, "WENT_WITH_CHRONICLE", person);
        return new String[]{"SUCCEEDED", who.get("given_name") + " looks to the elders, and something passes between them. Then " + who.get("given_name")
            + " goes up to the store, comes back with a bundle of dried fish on a cord, and stands beside you, ready to go."};
    }

    /** Where the Chronicle goes, their companion goes: returns a line for the narration, or null. */
    public String follow(UUID chronicle, UUID arrived, Instant at) {
        Map<String, Object> walking = companionOf(chronicle);
        if (walking == null || arrived == null) return null;
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=now() WHERE id=? AND lifecycle_state='ACTIVE'", arrived, walking.get("individual_id"));
        return walking.get("given_name") + " keeps pace beside you.";
    }

    /**
     * A companion's day away from home, run by the community's daily step: they eat what they carry, learn and teach a
     * little, and go home when hungry twice over, homesick, or left alone.
     */
    void liveADay(UUID community, Instant day) {
        String staple = jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, community);
        for (Map<String, Object> away : jdbc.queryForList(
                "SELECT c.id, c.individual_id, c.chronicle_id, c.started_at, c.hungry_days, n.given_name, n.condition, w.lifecycle_state AS chronicle_state " +
                "FROM native_companionship c JOIN native_individual n ON n.object_id=c.individual_id JOIN world_object w ON w.id=c.chronicle_id " +
                "WHERE c.community_id=? AND c.ended_at IS NULL", community)) {
            if ("DEAD".equals(away.get("condition"))) { end(away, day, "DIED"); continue; }
            if (!"ACTIVE".equals(away.get("chronicle_state"))) { goHome(away, day, "LEFT_ALONE"); continue; }
            UUID person = (UUID) away.get("individual_id");
            UUID meal = jdbc.query("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "LEFT JOIN food_preservation_state f ON f.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "AND i.item_key=? AND f.spoiled_at IS NULL ORDER BY w.created_at LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, person, staple);
            int hungry = ((Number) away.get("hungry_days")).intValue();
            if (meal != null) {
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=(SELECT current_location_id FROM world_object WHERE id=?), " +
                    "destroyed_cause='EATEN_BY_COMPANION', current_owner_id=NULL, current_location_id=NULL WHERE id=?", Timestamp.from(day), person, meal);
                transition(meal, day, "EATEN_BY_COMPANION", community);
                hungry = 0;
                jdbc.update("UPDATE native_individual SET condition='WELL' WHERE object_id=? AND condition='HUNGRY'", person);
            } else {
                hungry++;
                jdbc.update("UPDATE native_individual SET condition='HUNGRY' WHERE object_id=? AND condition='WELL'", person);
            }
            jdbc.update("UPDATE native_companionship SET hungry_days=? WHERE id=?", hungry, away.get("id"));
            // A day together is a day of hearing their speech: understanding grows by one.
            jdbc.update("UPDATE community_relation SET understanding=LEAST(100, understanding + 1) WHERE community_id=? AND chronicle_id=?", community, away.get("chronicle_id"));
            if (hungry >= 2) goHome(away, day, "WENT_HOME_HUNGRY");
            else if (Duration.between(((Timestamp) away.get("started_at")).toInstant(), day).toDays() >= HOMESICK_AFTER_DAYS) goHome(away, day, "HOMESICK");
        }
    }

    /** Back to the isle: the body on home ground, anything carried back in the store, the row closed. */
    private void goHome(Map<String, Object> walking, Instant at, String reason) {
        UUID person = (UUID) walking.get("individual_id");
        UUID community = jdbc.queryForObject("SELECT community_id FROM native_individual WHERE object_id=?", UUID.class, person);
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=now() WHERE id=? AND lifecycle_state='ACTIVE'", home, person);
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
        if (store != null)
            for (UUID carried : jdbc.queryForList("SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", UUID.class, person)) {
                jdbc.update("UPDATE world_object SET current_owner_id=?, updated_at=now() WHERE id=?", store, carried);
                transition(carried, at, "PUT_BACK_IN_STORE", community);
            }
        end(walking, at, reason);
    }

    private void end(Map<String, Object> walking, Instant at, String reason) {
        UUID person = (UUID) walking.get("individual_id");
        jdbc.update("UPDATE native_companionship SET ended_at=?, end_reason=? WHERE id=?", Timestamp.from(at), reason, walking.get("id"));
        jdbc.update("UPDATE native_individual SET available=TRUE WHERE object_id=? AND condition <> 'DEAD'", person);
        UUID community = jdbc.queryForObject("SELECT community_id FROM native_individual WHERE object_id=?", UUID.class, person);
        UUID chronicle = jdbc.queryForObject("SELECT chronicle_id FROM native_companionship WHERE id=?", UUID.class, walking.get("id"));
        event(community, chronicle, at, "COMPANIONSHIP_ENDED_" + reason, person);
    }

    /** Someone who will go: the one named, if they can be spared; otherwise the first adult whose work can wait. */
    private Map<String, Object> willing(UUID community, String text) {
        List<Map<String, Object>> spare = jdbc.queryForList(
            "SELECT n.object_id, n.given_name FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition='WELL' AND n.available AND n.life_stage='ADULT' AND n.role IN ('FISHER','FORAGER','MAKER','HUNTER') " +
            "AND w.lifecycle_state='ACTIVE' ORDER BY CASE n.role WHEN 'FORAGER' THEN 0 WHEN 'MAKER' THEN 1 ELSE 2 END, n.given_name", community);
        String v = text.toLowerCase(Locale.ROOT);
        for (Map<String, Object> s : spare)
            if (v.contains(((String) s.get("given_name")).toLowerCase(Locale.ROOT))) return s;
        boolean namedSomeone = jdbc.queryForList("SELECT given_name FROM native_individual WHERE community_id=?", String.class, community)
            .stream().anyMatch(n -> v.contains(n.toLowerCase(Locale.ROOT)));
        if (namedSomeone) return null;   // the one asked for cannot be spared, and no one else is volunteered in their place
        // With fewer than two food-workers left behind, no one can be spared.
        return spare.size() >= 3 ? spare.get(0) : null;
    }

    private List<UUID> held(UUID store, String key) {
        return jdbc.queryForList("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "LEFT JOIN food_preservation_state f ON f.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "AND i.item_key=? AND f.spoiled_at IS NULL ORDER BY w.created_at", UUID.class, store, key);
    }

    private void transition(UUID item, Instant at, String kind, UUID community) {
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('community',?::text))",
            item, Timestamp.from(at), kind, community.toString());
    }

    private void event(UUID community, UUID chronicle, Instant at, String kind, UUID person) {
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,jsonb_build_object('individual', ?::text))",
            community, Timestamp.from(at), kind, chronicle, person == null ? null : person.toString());
    }
}
