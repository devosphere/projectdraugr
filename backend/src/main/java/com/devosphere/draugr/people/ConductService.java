package com.devosphere.draugr.people;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
 * What a people remembers (#114, epic #109): offence, amends, and the consequences a Chronicle lives with.
 *
 * <p>The ticket's rules, and how each is kept:
 * <ul>
 *   <li><b>Not every act is magically witnessed.</b> A theft from an unguarded store by night is unseen: nothing is
 *       held against the Chronicle, but the loss is discovered, the community grows guarded, and the history
 *       records both what happened and that no one saw it. In daylight, or with a watch posted, it is seen.</li>
 *   <li><b>Hostility changes what the world does, not a bar.</b> A community that has been wronged warns, closes its
 *       store and its gates, stops trading, and — past a point — drives the Chronicle off with stones. A killing
 *       makes it hostile outright. Contact (#112) and trade (#113) read the same standing and history, so the
 *       consequences are the same everywhere they could show.</li>
 *   <li><b>Persons are not livestock.</b> Seizing, binding or dragging one off is resisted, fails, and is among the
 *       gravest things a Chronicle can do; V344 already makes a person untameable and their body no harvest.</li>
 *   <li><b>Trust can be rebuilt, slowly.</b> Apology, restitution, returning what was taken and working alongside
 *       them all count — each a little, apology only once a week, and only while there is something to answer for.</li>
 *   <li><b>Identity and history survive everything.</b> The dead are marked dead and their bodies stay where they
 *       fell; stolen goods keep their identity and their history says whose they were.</li>
 * </ul>
 */
@Service
public class ConductService {

    public enum Act { THEFT, THREAT, HARM, RESTRAINT, APOLOGY, RESTITUTION, SHARED_LABOUR }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("steal from their store", Act.THEFT), Map.entry("steal from the store", Act.THEFT), Map.entry("take from their store", Act.THEFT),
        Map.entry("raid their store", Act.THEFT), Map.entry("raid the store", Act.THEFT), Map.entry("steal their", Act.THEFT),
        Map.entry("steal the", Act.THEFT), Map.entry("steal fish", Act.THEFT), Map.entry("steal food", Act.THEFT),
        Map.entry("threaten them", Act.THREAT), Map.entry("threaten the", Act.THREAT), Map.entry("insult them", Act.THREAT),
        Map.entry("shout at them", Act.THREAT), Map.entry("brandish my", Act.THREAT), Map.entry("curse them", Act.THREAT),
        Map.entry("attack the reedkin", Act.HARM), Map.entry("attack them", Act.HARM), Map.entry("strike the reedkin", Act.HARM),
        Map.entry("hit the reedkin", Act.HARM), Map.entry("kill the reedkin", Act.HARM), Map.entry("kill them", Act.HARM),
        Map.entry("stab the", Act.HARM), Map.entry("spear the reedkin", Act.HARM), Map.entry("attack the headsperson", Act.HARM),
        Map.entry("attack the elder", Act.HARM), Map.entry("attack the child", Act.HARM),
        Map.entry("tie up the", Act.RESTRAINT), Map.entry("capture the", Act.RESTRAINT), Map.entry("bind the reedkin", Act.RESTRAINT),
        Map.entry("seize the", Act.RESTRAINT), Map.entry("kidnap", Act.RESTRAINT), Map.entry("drag the", Act.RESTRAINT),
        Map.entry("take the child", Act.RESTRAINT), Map.entry("tame the reedkin", Act.RESTRAINT), Map.entry("leash the", Act.RESTRAINT),
        Map.entry("apologise", Act.APOLOGY), Map.entry("apologize", Act.APOLOGY), Map.entry("say i am sorry", Act.APOLOGY),
        Map.entry("ask their forgiveness", Act.APOLOGY), Map.entry("ask for forgiveness", Act.APOLOGY),
        Map.entry("make restitution", Act.RESTITUTION), Map.entry("pay them back", Act.RESTITUTION), Map.entry("make amends", Act.RESTITUTION),
        Map.entry("repay them", Act.RESTITUTION),
        Map.entry("help them fish", Act.SHARED_LABOUR), Map.entry("help them work", Act.SHARED_LABOUR), Map.entry("work alongside them", Act.SHARED_LABOUR),
        Map.entry("help them mend", Act.SHARED_LABOUR), Map.entry("help with their work", Act.SHARED_LABOUR), Map.entry("help them gather", Act.SHARED_LABOUR));

    /** Standing at or below which a community drives the Chronicle off by force rather than only warning them. */
    static final int DRIVEN_OFF = -60;

    private final JdbcTemplate jdbc;
    private final ChroniclePhysiologyService physiology;
    private final com.devosphere.draugr.item.PhysicalItemService items;

    public ConductService(JdbcTemplate jdbc, ChroniclePhysiologyService physiology, com.devosphere.draugr.item.PhysicalItemService items) {
        this.jdbc = jdbc;
        this.physiology = physiology;
        this.items = items;
    }

    /** The act of conduct a text names, or null. Longest phrase wins. */
    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** Carry out an act of conduct toward a community within reach. */
    @Transactional
    public String[] act(UUID chronicle, UUID chunk, UUID community, Act act, String text, Instant at, boolean armed, UUID actionId) {
        ensureRelation(community, chronicle);
        Map<String, Object> c = jdbc.queryForMap("SELECT home_chunk_id, security_posture, staple_item_key FROM native_community WHERE id=?", community);
        boolean onIsle = chunk.equals(c.get("home_chunk_id"));
        boolean dark = ContactService.isDark(at);
        boolean watched = !dark || "GUARDED".equals(c.get("security_posture")) || "HOSTILE".equals(c.get("security_posture"));

        switch (act) {
            case THEFT -> {
                if (!onIsle) return new String[]{"FAILED", "Their store is on the isle, and you are not."};
                UUID store = store(community);
                if (store == null) return new String[]{"FAILED", "There is no store here to take from."};
                String staple = (String) c.get("staple_item_key");
                List<UUID> taken = jdbc.queryForList("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                    "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' ORDER BY (i.item_key=?) DESC, w.created_at LIMIT 3", UUID.class, store, staple);
                if (taken.isEmpty()) return new String[]{"FAILED", "The store house is bare; there is nothing in it to take."};
                boolean food = false;
                for (UUID item : taken) {
                    String key = jdbc.queryForObject("SELECT item_key FROM item_instance WHERE object_id=?", String.class, item);
                    food |= key.equals(staple);
                    jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", chronicle, item);
                    jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'STOLEN_FROM_COMMUNITY',jsonb_build_object('community',?::text))",
                        item, Timestamp.from(at), community.toString());
                }
                String kind = food ? "FOOD_THEFT" : "PROPERTY_THEFT";
                if (!watched) {
                    // Unseen. Nothing is held against the Chronicle — they do not know who — but a store does not
                    // lose things without the keeper noticing, and a community that has been robbed keeps watch.
                    event(community, chronicle, at, kind, Map.of("witnessed", false, "count", taken.size()));
                    event(community, null, at, "GOODS_MISSING", Map.of("count", taken.size()));
                    jdbc.update("UPDATE native_community SET security_posture='GUARDED' WHERE id=? AND security_posture IN ('AT_EASE','WARY')", community);
                    return new String[]{"SUCCEEDED", "You take what your hands find in the dark of the store house and are away over the water before anyone stirs."};
                }
                offence(community, chronicle, at, kind, food ? -35 : -25, "HOSTILE_IF_LOW");
                return new String[]{"PARTIAL", "A shout goes up before you are out of the store house. By the time you reach the water the whole isle is on its feet, and every one of them has seen your face."};
            }
            case THREAT -> {
                offence(community, chronicle, at, "INSULT_OR_THREAT", -10, "GUARDED");
                return new String[]{"PARTIAL", "The work on the isle stops. The children are sent in, and those left at the water's edge stand very still and watch you until you have gone quiet."};
            }
            case HARM -> {
                Map<String, Object> target = target(community, text);
                if (target == null) return new String[]{"FAILED", "No one is within reach of you."};
                UUID body = (UUID) target.get("object_id");
                String name = (String) target.get("given_name");
                if (armed) {
                    jdbc.update("UPDATE native_individual SET condition='DEAD', available=FALSE WHERE object_id=?", body);
                    jdbc.update("UPDATE world_object SET display_name=?, updated_at=now() WHERE id=?", "The body of " + name, body);
                    offence(community, chronicle, at, "MURDER", -100, "HOSTILE");
                    event(community, body, at, "DIED", Map.of("cause", "killed by an outsider"));
                } else {
                    jdbc.update("UPDATE native_individual SET condition='INJURED', available=FALSE WHERE object_id=?", body);
                    offence(community, chronicle, at, "HARM_TO_INDIVIDUAL", -45, "HOSTILE");
                }
                // They answer in kind, at once: this is the proportionate response of a community defending one of its
                // own, and it is the Chronicle's body that pays.
                physiology.applyInjury(chronicle, armed ? 25 : 15, actionId, at, "NATIVE_DEFENCE");
                return new String[]{"PARTIAL", armed
                    ? name + " falls and does not get up. The isle erupts: stones and fish-spears come at you from every side, and you are hurt before you are clear of the reeds."
                    : name + " goes down under the blow and is dragged back by others. Stones follow you into the water, and one of them finds you."};
            }
            case RESTRAINT -> {
                Map<String, Object> target = target(community, text);
                if (target == null) return new String[]{"FAILED", "No one is within reach of you."};
                offence(community, chronicle, at, "RESTRAINT_ATTEMPT", -60, "HOSTILE");
                physiology.applyInjury(chronicle, 10, actionId, at, "NATIVE_DEFENCE");
                return new String[]{"FAILED", "You lay hands on " + target.get("given_name") + ", who twists free and shrieks. The rest are on you at once, and you are driven off the isle bruised and bleeding. A person is not a thing to be bound and led."};
            }
            case APOLOGY -> {
                if (!outstanding(community, chronicle, at))
                    return record(community, chronicle, at, "APOLOGY", Map.of("accepted", false), 0, "PARTIAL",
                        "They listen, and look at each other. Whatever you are sorry for, it is nothing they hold against you.");
                if (recently(community, chronicle, "APOLOGY", at, Duration.ofDays(7)))
                    return record(community, chronicle, at, "APOLOGY", Map.of("accepted", false), 0, "PARTIAL",
                        "They have heard your sorrow already. Words said again are not more sorrow.");
                return record(community, chronicle, at, "APOLOGY", Map.of("accepted", true), 5, "SUCCEEDED",
                    "The elder hears you out, and says something short to the others. Nothing is forgiven yet, but it has been heard.");
            }
            case RESTITUTION -> {
                UUID store = store(community);
                Map<String, Object> given = jdbc.queryForList(
                    "SELECT w.id, i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                    "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                    "AND NOT EXISTS (SELECT 1 FROM equipment_attachment e WHERE e.item_id=w.id) " +
                    "ORDER BY CASE WHEN d.category='FOOD' THEN 0 ELSE 1 END, w.created_at LIMIT 1", chronicle).stream().findFirst().orElse(null);
                if (given == null || store == null)
                    return new String[]{"FAILED", "You have nothing to hand to make good with."};
                jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", store, given.get("id"));
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'RESTITUTION_TO_COMMUNITY',jsonb_build_object('community',?::text))",
                    given.get("id"), Timestamp.from(at), community.toString());
                boolean owed = outstanding(community, chronicle, at);
                return record(community, chronicle, at, "RESTITUTION", Map.of("item", given.get("item_key")), owed ? 8 : 2, "SUCCEEDED",
                    "You set the " + ((String) given.get("display_name")).toLowerCase(Locale.ROOT) + " down at the landing as payment for what is owed. It is taken up, without thanks, but it is taken.");
            }
            default -> {
                // Working alongside them: real work, real fish in their store, and a little trust for the hours.
                Map<String, Object> r = jdbc.queryForMap("SELECT standing, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?", community, chronicle);
                if (r.get("first_contact_at") == null || ((Number) r.get("standing")).intValue() <= -30)
                    return new String[]{"PARTIAL", "They do not want your hands on their work."};
                UUID store = store(community);
                if (store != null && !recently(community, chronicle, "SHARED_LABOUR", at, Duration.ofDays(1))) {
                    String staple = (String) c.get("staple_item_key");
                    String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, staple);
                    for (int i = 0; i < 2; i++) items.createHeldItem(store, staple, name, at, "SHARED_LABOUR");
                    return record(community, chronicle, at, "SHARED_LABOUR", Map.of(), 3, "SUCCEEDED",
                        "You spend the hours beside them at the weirs, hauling where you are shown to haul. By the end the catch is in, and no one is watching you any more.");
                }
                return record(community, chronicle, at, "SHARED_LABOUR", Map.of(), 0, "SUCCEEDED",
                    "You work beside them a while longer. The work is done; they have what they need from today.");
            }
        }
    }

    /**
     * The Chronicle has just arrived on this ground (#114): walking onto a people's isle uninvited is trespass, and a
     * people who have come to hate them do not wait to be spoken to.
     *
     * @return a line to add to the move's narration, or null if nothing happened
     */
    @Transactional
    public String onArrival(UUID chronicle, UUID chunk, Instant at, UUID actionId) {
        List<Map<String, Object>> here = jdbc.queryForList(
            "SELECT n.id FROM native_community n JOIN cognition_profile cp ON cp.species_key=n.species_key " +
            "WHERE n.home_chunk_id=? AND n.lifecycle <> 'DISPERSED' AND cp.cognition_class IN ('PEOPLE','SOVEREIGN')", chunk);
        if (here.isEmpty()) return null;
        UUID community = (UUID) here.get(0).get("id");
        ensureRelation(community, chronicle);
        Map<String, Object> r = jdbc.queryForMap("SELECT standing, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?", community, chronicle);
        int standing = ((Number) r.get("standing")).intValue();
        boolean dark = ContactService.isDark(at);
        if (standing <= DRIVEN_OFF && !dark) {
            physiology.applyInjury(chronicle, 8, actionId, at, "NATIVE_DEFENCE");
            event(community, chronicle, at, "DROVE_OFF", Map.of());
            return "You are not a stride onto the raised ground before the stones start, hard and well aimed, and they do not stop until you are back in the water.";
        }
        boolean permitted = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND payload->>'response'='PERMITTED')",
            Boolean.class, community, chronicle));
        if (permitted || dark) return null;
        offence(community, chronicle, at, "BOUNDARY_TRESPASS", -10, "GUARDED");
        return r.get("first_contact_at") == null
            ? "Figures rise from the work at the water's edge as you come up onto the raised ground, and a shout goes from house to house. You were not asked here."
            : "Those at the landing look up as you come onto the raised ground unasked, and one steps across your path. They know you, and they did not invite you in.";
    }

    // ── What is kept. ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A witnessed offence: standing falls, the community's posture hardens, and a grave enough one closes the isle to
     * the Chronicle altogether (the store, the gates, and any trade they had).
     */
    private void offence(UUID community, UUID chronicle, Instant at, String kind, int standingDelta, String posture) {
        jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind=?, last_event_at=? " +
            "WHERE community_id=? AND chronicle_id=?", standingDelta, kind, Timestamp.from(at), community, chronicle);
        event(community, chronicle, at, kind, Map.of("witnessed", true));
        int standing = jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
        String to = "HOSTILE".equals(posture) || standing <= -30 ? "HOSTILE" : "GUARDED";
        if ("HOSTILE_IF_LOW".equals(posture) && standing > -30) to = "GUARDED";
        jdbc.update("UPDATE native_community SET security_posture = CASE WHEN security_posture='HOSTILE' THEN 'HOSTILE' ELSE ? END WHERE id=?", to, community);
        if ("HOSTILE".equals(to)) jdbc.update("UPDATE native_settlement_site SET access_rule='CLOSED' WHERE community_id=?", community);
    }

    /** Something the Chronicle still has to answer for: a witnessed offence in the last month. */
    private boolean outstanding(UUID community, UUID chronicle, Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND occurred_at > ? " +
            "AND event_kind IN ('BOUNDARY_TRESPASS','PROPERTY_THEFT','FOOD_THEFT','INSULT_OR_THREAT','HARM_TO_INDIVIDUAL','MURDER','RESTRAINT_ATTEMPT') " +
            "AND COALESCE((payload->>'witnessed')::boolean, TRUE))",
            Boolean.class, community, chronicle, Timestamp.from(at.minus(Duration.ofDays(30)))));
    }

    private boolean recently(UUID community, UUID chronicle, String kind, Instant at, Duration window) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind=? AND occurred_at > ? " +
            "AND COALESCE((payload->>'accepted')::boolean, TRUE))",
            Boolean.class, community, chronicle, kind, Timestamp.from(at.minus(window))));
    }

    private String[] record(UUID community, UUID chronicle, Instant at, String kind, Map<String, Object> payload, int standingDelta, String outcome, String narration) {
        if (standingDelta != 0)
            jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind=?, last_event_at=? " +
                "WHERE community_id=? AND chronicle_id=?", standingDelta, kind, Timestamp.from(at), community, chronicle);
        event(community, chronicle, at, kind, payload);
        return new String[]{outcome, narration};
    }

    private void event(UUID community, UUID subject, Instant at, String kind, Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (json.length() > 1) json.append(',');
            Object v = e.getValue();
            json.append('"').append(e.getKey()).append("\":").append(v instanceof Number || v instanceof Boolean ? v : "\"" + v + "\"");
        }
        json.append('}');
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,?::jsonb)",
            community, Timestamp.from(at), kind, subject, json.toString());
    }

    private void ensureRelation(UUID community, UUID chronicle) {
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id) VALUES (?,?) " +
            "ON CONFLICT (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL DO NOTHING", community, chronicle);
    }

    private UUID store(UUID community) {
        return jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
    }

    /** Who an act is aimed at: the one named, or else an adult who stands in the way. A child only if named. */
    private Map<String, Object> target(UUID community, String text) {
        List<Map<String, Object>> living = jdbc.queryForList(
            "SELECT n.object_id, n.given_name, n.role FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY CASE n.role WHEN 'GUARD' THEN 0 WHEN 'FISHER' THEN 1 WHEN 'FORAGER' THEN 2 WHEN 'MAKER' THEN 3 WHEN 'HEADSPERSON' THEN 4 WHEN 'ELDER' THEN 5 ELSE 6 END, n.given_name",
            community);
        String v = text.toLowerCase(Locale.ROOT);
        for (Map<String, Object> m : living)
            if (v.contains(((String) m.get("given_name")).toLowerCase(Locale.ROOT)) || v.contains(((String) m.get("role")).toLowerCase(Locale.ROOT))) return m;
        return living.stream().filter(m -> !"CHILD".equals(m.get("role"))).findFirst().orElse(null);
    }
}
