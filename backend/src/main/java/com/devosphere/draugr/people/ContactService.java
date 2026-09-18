package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Meeting a people (#112, epic #109).
 *
 * <p>A Chronicle attempts contact through what they can perceive, express and physically do. Understanding is
 * gradual and uncertain: the same words land or miss according to how much of this community's speech and custom
 * the Chronicle has come to follow, which only watching, listening and time build. The answer depends on distance,
 * a weapon in the hand, the hour, the community's own state (a hungry isle closes up), and everything that has
 * passed between them before.
 *
 * <p>Three rules hold throughout, and are the ticket's:
 * <ul>
 *   <li>Narration reports what the Chronicle sees and hears — a figure at the landing, a hand lifted, a sharp call —
 *       and never a number, a menu of choices, or the community's hidden state.</li>
 *   <li>Every act of contact is written to the community's append-only history with the Chronicle as its subject,
 *       so what happened cannot be rewritten and trade (#113) and memory (#114) read the same evidence.</li>
 *   <li>Only a people answers. An act is recognised here only when an isle of a PEOPLE is within sight; "greet the
 *       wolves" beside no isle is not a social act, and prose cannot make it one.</li>
 * </ul>
 *
 * <p>Twenty-six acts from the ticket's catalogue, grouped by what they do to the relation rather than handled one
 * by one: watching builds understanding; making yourself known opens contact or is refused; showing peace eases
 * wariness; gifts move real things; speech lands or misfires by understanding; leaving well is remembered too.
 */
@Service
public class ContactService {

    /** The ticket's action catalogue. */
    public enum Act {
        OBSERVE_SETTLEMENT, OBSERVE_BOUNDARY_MARKER, LISTEN_FOR_LANGUAGE, WATCH_CUSTOM, RECORD_CUSTOM,
        ANNOUNCE_PRESENCE, APPROACH_BOUNDARY, WAIT_FOR_RESPONSE, REQUEST_PARLEY, ASK_PERMISSION, REVISIT,
        DISPLAY_EMPTY_HANDS, LOWER_WEAPON,
        OFFER_GIFT, LEAVE_GIFT,
        ATTEMPT_GESTURE, ATTEMPT_SHARED_WORDS, ASK_QUESTION, STATE_INTENTION, TELL_TRUTH, SHOW_ITEM, SHOW_MAP, MAKE_PROMISE,
        ASK_TO_LEAVE, RESPECT_BOUNDARY, WITHDRAW
    }

    /** Phrases that name each act. The longest phrase found in the text decides, so "show them my map" is a map. */
    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("observe the settlement", Act.OBSERVE_SETTLEMENT), Map.entry("watch the settlement", Act.OBSERVE_SETTLEMENT),
        Map.entry("observe the isle", Act.OBSERVE_SETTLEMENT), Map.entry("watch the isle", Act.OBSERVE_SETTLEMENT),
        Map.entry("observe the village", Act.OBSERVE_SETTLEMENT), Map.entry("watch the village", Act.OBSERVE_SETTLEMENT),
        Map.entry("study the village", Act.OBSERVE_SETTLEMENT), Map.entry("study the isle", Act.OBSERVE_SETTLEMENT),
        Map.entry("boundary marker", Act.OBSERVE_BOUNDARY_MARKER), Map.entry("look at the marker", Act.OBSERVE_BOUNDARY_MARKER),
        Map.entry("examine the marker", Act.OBSERVE_BOUNDARY_MARKER), Map.entry("look at their markers", Act.OBSERVE_BOUNDARY_MARKER),
        Map.entry("listen to them", Act.LISTEN_FOR_LANGUAGE), Map.entry("listen to their speech", Act.LISTEN_FOR_LANGUAGE),
        Map.entry("listen for their language", Act.LISTEN_FOR_LANGUAGE), Map.entry("listen to their language", Act.LISTEN_FOR_LANGUAGE),
        Map.entry("listen to the reedkin", Act.LISTEN_FOR_LANGUAGE), Map.entry("listen to their words", Act.LISTEN_FOR_LANGUAGE),
        Map.entry("watch their custom", Act.WATCH_CUSTOM), Map.entry("watch what they do", Act.WATCH_CUSTOM),
        Map.entry("watch how they", Act.WATCH_CUSTOM), Map.entry("observe their custom", Act.WATCH_CUSTOM),
        Map.entry("watch them work", Act.WATCH_CUSTOM), Map.entry("watch the reedkin", Act.WATCH_CUSTOM),
        Map.entry("record their custom", Act.RECORD_CUSTOM), Map.entry("note their custom", Act.RECORD_CUSTOM),
        Map.entry("write down their custom", Act.RECORD_CUSTOM), Map.entry("record what they do", Act.RECORD_CUSTOM),
        Map.entry("announce myself", Act.ANNOUNCE_PRESENCE), Map.entry("announce my presence", Act.ANNOUNCE_PRESENCE),
        Map.entry("call out to them", Act.ANNOUNCE_PRESENCE), Map.entry("call out to the", Act.ANNOUNCE_PRESENCE),
        Map.entry("call across the water", Act.ANNOUNCE_PRESENCE), Map.entry("hail them", Act.ANNOUNCE_PRESENCE),
        Map.entry("hail the", Act.ANNOUNCE_PRESENCE), Map.entry("greet them", Act.ANNOUNCE_PRESENCE), Map.entry("greet the", Act.ANNOUNCE_PRESENCE),
        Map.entry("approach the edge", Act.APPROACH_BOUNDARY), Map.entry("approach the boundary", Act.APPROACH_BOUNDARY),
        Map.entry("approach the isle", Act.APPROACH_BOUNDARY), Map.entry("approach the village", Act.APPROACH_BOUNDARY),
        Map.entry("approach them", Act.APPROACH_BOUNDARY), Map.entry("walk to the edge", Act.APPROACH_BOUNDARY),
        Map.entry("wait for a response", Act.WAIT_FOR_RESPONSE), Map.entry("wait for them", Act.WAIT_FOR_RESPONSE),
        Map.entry("wait for an answer", Act.WAIT_FOR_RESPONSE), Map.entry("wait to be answered", Act.WAIT_FOR_RESPONSE),
        Map.entry("request a parley", Act.REQUEST_PARLEY), Map.entry("ask to parley", Act.REQUEST_PARLEY),
        Map.entry("ask for a parley", Act.REQUEST_PARLEY), Map.entry("ask to speak", Act.REQUEST_PARLEY), Map.entry("ask to talk", Act.REQUEST_PARLEY),
        Map.entry("ask permission", Act.ASK_PERMISSION), Map.entry("ask if i may", Act.ASK_PERMISSION),
        Map.entry("ask whether i may", Act.ASK_PERMISSION), Map.entry("ask to enter", Act.ASK_PERMISSION), Map.entry("ask to come", Act.ASK_PERMISSION),
        Map.entry("revisit the", Act.REVISIT), Map.entry("visit the reedkin", Act.REVISIT), Map.entry("visit the isle", Act.REVISIT),
        Map.entry("visit the village", Act.REVISIT), Map.entry("return to the isle", Act.REVISIT),
        Map.entry("show my empty hands", Act.DISPLAY_EMPTY_HANDS), Map.entry("show empty hands", Act.DISPLAY_EMPTY_HANDS),
        Map.entry("open my hands", Act.DISPLAY_EMPTY_HANDS), Map.entry("raise my empty hands", Act.DISPLAY_EMPTY_HANDS),
        Map.entry("show my palms", Act.DISPLAY_EMPTY_HANDS), Map.entry("show them my empty hands", Act.DISPLAY_EMPTY_HANDS),
        Map.entry("lower my weapon", Act.LOWER_WEAPON), Map.entry("lower my spear", Act.LOWER_WEAPON),
        Map.entry("put down my weapon", Act.LOWER_WEAPON), Map.entry("set down my weapon", Act.LOWER_WEAPON), Map.entry("lower my bow", Act.LOWER_WEAPON),
        Map.entry("offer them", Act.OFFER_GIFT), Map.entry("offer a gift", Act.OFFER_GIFT), Map.entry("give them", Act.OFFER_GIFT),
        Map.entry("leave a gift", Act.LEAVE_GIFT), Map.entry("leave an offering", Act.LEAVE_GIFT), Map.entry("leave it for them", Act.LEAVE_GIFT),
        Map.entry("leave them a gift", Act.LEAVE_GIFT),
        Map.entry("make a gesture", Act.ATTEMPT_GESTURE), Map.entry("gesture to them", Act.ATTEMPT_GESTURE),
        Map.entry("sign to them", Act.ATTEMPT_GESTURE), Map.entry("make a sign", Act.ATTEMPT_GESTURE),
        Map.entry("try their words", Act.ATTEMPT_SHARED_WORDS), Map.entry("repeat their words", Act.ATTEMPT_SHARED_WORDS),
        Map.entry("say their word", Act.ATTEMPT_SHARED_WORDS), Map.entry("speak their words", Act.ATTEMPT_SHARED_WORDS),
        Map.entry("try to speak their", Act.ATTEMPT_SHARED_WORDS), Map.entry("speak to them", Act.ATTEMPT_SHARED_WORDS),
        Map.entry("ask them", Act.ASK_QUESTION), Map.entry("ask the reedkin", Act.ASK_QUESTION), Map.entry("ask the headsperson", Act.ASK_QUESTION),
        Map.entry("state my intention", Act.STATE_INTENTION), Map.entry("tell them why", Act.STATE_INTENTION),
        Map.entry("tell them i come", Act.STATE_INTENTION), Map.entry("tell them i mean", Act.STATE_INTENTION),
        Map.entry("show them", Act.SHOW_ITEM), Map.entry("show them my map", Act.SHOW_MAP), Map.entry("show them the map", Act.SHOW_MAP),
        Map.entry("tell them the truth", Act.TELL_TRUTH), Map.entry("be honest with them", Act.TELL_TRUTH),
        Map.entry("promise them", Act.MAKE_PROMISE), Map.entry("make a promise", Act.MAKE_PROMISE),
        Map.entry("give them my word", Act.MAKE_PROMISE), Map.entry("swear to them", Act.MAKE_PROMISE),
        Map.entry("ask to leave", Act.ASK_TO_LEAVE), Map.entry("ask leave to go", Act.ASK_TO_LEAVE),
        Map.entry("respect the boundary", Act.RESPECT_BOUNDARY), Map.entry("stay outside the", Act.RESPECT_BOUNDARY),
        Map.entry("keep to the edge", Act.RESPECT_BOUNDARY), Map.entry("stay at the edge", Act.RESPECT_BOUNDARY),
        Map.entry("withdraw from the", Act.WITHDRAW), Map.entry("withdraw", Act.WITHDRAW), Map.entry("take my leave", Act.WITHDRAW),
        Map.entry("leave them in peace", Act.WITHDRAW), Map.entry("back away from the isle", Act.WITHDRAW));

    /** How much understanding speech needs before it lands rather than misfires. Things shown are understood by sight. */
    static int understandingNeeded(Act act) {
        return switch (act) {
            case SHOW_ITEM -> 0;
            case ATTEMPT_GESTURE -> 10;
            case SHOW_MAP -> 20;
            case STATE_INTENTION -> 25;
            case ATTEMPT_SHARED_WORDS -> 30;
            case TELL_TRUTH -> 35;
            case ASK_QUESTION -> 40;
            case MAKE_PROMISE -> 45;
            default -> 0;
        };
    }

    private final JdbcTemplate jdbc;

    public ContactService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** The contact act a text names, or null. Pure: no world is consulted, so it can be tested on its own. */
    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /**
     * A people's community within sight of this ground: its isle here, or on the next chunk over. Only a PEOPLE or
     * SOVEREIGN community answers — the V344 class decides it, not the text — and a dispersed one is gone.
     */
    @Transactional(readOnly = true)
    public UUID communityInReach(UUID chunk) {
        List<UUID> found = jdbc.queryForList(
            "SELECT n.id FROM native_community n JOIN world_chunk home ON home.id=n.home_chunk_id " +
            "JOIN world_chunk here ON here.id=? AND here.world_id=home.world_id " +
            "JOIN cognition_profile cp ON cp.species_key=n.species_key AND cp.cognition_class IN ('PEOPLE','SOVEREIGN') " +
            "WHERE n.lifecycle <> 'DISPERSED' AND abs(home.grid_x-here.grid_x)<=1 AND abs(home.grid_y-here.grid_y)<=1 " +
            "ORDER BY (home.id=here.id) DESC, n.founded_at LIMIT 1", UUID.class, chunk);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * A line for a survey of this ground: what a Chronicle standing here can see of a people's isle, here or across
     * the water, and nothing they could not. Null when no isle is in sight.
     */
    @Transactional(readOnly = true)
    public String glimpse(UUID chunk, Instant at) {
        UUID community = communityInReach(chunk);
        if (community == null) return null;
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT n.home_chunk_id, n.trade_policy, n.security_posture, n.lifecycle, " +
            "(SELECT COUNT(*) FROM native_individual i JOIN world_object w ON w.id=i.object_id WHERE i.community_id=n.id AND i.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE') AS people " +
            "FROM native_community n WHERE n.id=?", community);
        boolean onIsle = chunk.equals(c.get("home_chunk_id"));
        if ("MOVING".equals(c.get("lifecycle")))
            return onIsle ? "The reed houses on the raised ground stand empty; the store is shut and the landing bare. Whoever lives here has gone to find food elsewhere."
                          : "Across the marsh a cluster of reed roofs stands on raised ground, and no smoke rises from any of them.";
        boolean dark = isDark(at);
        String who = ((Number) c.get("people")).intValue() > 5 ? "several figures" : "a few figures";
        String wary = "CLOSED".equals(c.get("trade_policy"))
            ? " The landing has been drawn up and a watcher stands at the edge of it, turned toward you."
            : "GUARDED".equals(c.get("security_posture")) || "HOSTILE".equals(c.get("security_posture"))
            ? " A watcher stands at the edge of the reeds, not working, only watching."
            : "";
        if (onIsle)
            return (dark ? "Firelight moves between reed houses on the raised ground around you, and low voices carry over the water."
                         : "Reed houses stand on the raised ground around you, their roofs thick with thatch, and " + who + " work at the water's edge — web-footed, fur-coated, quick with their hands.") + wary;
        return (dark ? "Across the marsh a few fires burn on raised ground, and voices carry over the water."
                     : "Across the marsh, reed roofs rise on raised ground, and " + who + " move along its edge.") + wary;
    }

    /**
     * Carry out one act of contact and answer with what the Chronicle observes.
     *
     * @return {outcome, narration}; outcome is SUCCEEDED when the act landed as meant, PARTIAL when it was answered
     *         otherwise (ignored, warned away, misunderstood), FAILED when it could not be done at all
     */
    @Transactional
    public String[] act(UUID chronicle, UUID chunk, UUID community, Act act, String text, Instant at) {
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT home_chunk_id, trade_policy, security_posture, lifecycle, shortage_days FROM native_community WHERE id=?", community);
        Map<String, Object> r = relation(community, chronicle);
        int standing = ((Number) r.get("standing")).intValue();
        int understanding = ((Number) r.get("understanding")).intValue();
        boolean met = r.get("first_contact_at") != null;
        boolean onIsle = chunk.equals(c.get("home_chunk_id"));
        boolean closed = "CLOSED".equals(c.get("trade_policy"));
        boolean gone = "MOVING".equals(c.get("lifecycle"));
        boolean armed = armed(chronicle);
        boolean dark = isDark(at);

        if (gone) return record(community, chronicle, act, at, "ABSENT", 0, 0,
            "PARTIAL", "There is no one here to answer. The isle stands empty; they have gone to find food elsewhere.");

        switch (act) {
            // ── Watching: understanding grows; nothing is asked of them, and nothing is changed. ───────────────────
            case OBSERVE_SETTLEMENT, OBSERVE_BOUNDARY_MARKER, WATCH_CUSTOM, RECORD_CUSTOM, LISTEN_FOR_LANGUAGE -> {
                if (dark && act != Act.LISTEN_FOR_LANGUAGE)
                    return record(community, chronicle, act, at, "TOO_DARK", 0, 0, "PARTIAL",
                        "In this light there is little to see but firelight moving between the houses.");
                int learned = act == Act.LISTEN_FOR_LANGUAGE ? 8 : act == Act.WATCH_CUSTOM || act == Act.RECORD_CUSTOM ? 6 : 4;
                if (recentlyDid(community, chronicle, act, at)) learned = 1;
                String seen = switch (act) {
                    case LISTEN_FOR_LANGUAGE -> understanding + learned < 20
                        ? "Their speech runs in clicks and low calls, and you cannot yet part it into words."
                        : understanding + learned < 50
                        ? "You begin to hear where one word ends and the next begins, and one sound comes back often when a boat is pushed off."
                        : "You catch words you have heard before: the one they call across the water to each other, the one they use for a stranger.";
                    case OBSERVE_BOUNDARY_MARKER -> "Bundles of reed tied to stakes mark the channel where the open water ends and theirs begins. Past them, nobody fishes but they do.";
                    case WATCH_CUSTOM, RECORD_CUSTOM -> "No one steps onto the raised ground from the water without first calling out and waiting to be answered; even a child coming back with a basket does it."
                        + (act == Act.RECORD_CUSTOM ? " You set it down in your own words to remember it." : "");
                    default -> "You watch the isle a while: who fishes and who mends, where the store stands, how they turn to look when anything moves at the reed edge.";
                };
                return record(community, chronicle, act, at, "WATCHED", 0, learned, "SUCCEEDED", seen);
            }
            // ── Making yourself known. ──────────────────────────────────────────────────────────────────────────
            case ANNOUNCE_PRESENCE, APPROACH_BOUNDARY, WAIT_FOR_RESPONSE, REQUEST_PARLEY, ASK_PERMISSION, REVISIT -> {
                if (dark)
                    return record(community, chronicle, act, at, "UNANSWERED_IN_DARK", -1, 0, "PARTIAL",
                        "Your voice goes out over the dark water and the talk on the isle stops. No one answers, and after a while the fires are banked low.");
                if (armed)
                    return record(community, chronicle, act, at, "SAW_A_WEAPON", -5, 0, "PARTIAL",
                        "Someone on the isle calls out sharply; the ones at the water's edge are gone into the reeds before you can see where. The watcher stays, looking at what is in your hand.");
                if (closed)
                    return record(community, chronicle, act, at, "WARNED_AWAY", 0, 0, "PARTIAL",
                        "A watcher comes to the edge of the reeds and waves you back, hard, twice. Behind them the store house is shut.");
                if (standing <= -30)
                    return record(community, chronicle, act, at, "WARNED_AWAY", 0, 0, "PARTIAL",
                        "They know you. A stone splashes into the water well short of you, and then another closer.");
                if (!met) {
                    // First contact is made from the edge, as their custom asks. Walking up to the raised ground is
                    // not how it is done, and they say so.
                    if (act == Act.APPROACH_BOUNDARY && onIsle)
                        return firstContact(community, chronicle, act, at, -2, "PARTIAL",
                            "A figure steps out in front of you before you reach the raised ground, open-handed but not moving aside. It looks at you a long while, then points back the way you came, and waits until you step back to lift a hand.");
                    return firstContact(community, chronicle, act, at, 5, "SUCCEEDED",
                        "After a long wait a figure comes to the edge of the reeds: web-footed, fur-coated, a head shorter than you. It looks at you, at your hands, at the way you came. Then it lifts one hand, palm out, and does not come closer.");
                }
                if (act == Act.ASK_PERMISSION && standing >= 20 && understanding >= 30)
                    return record(community, chronicle, act, at, "PERMITTED", 3, 1, "SUCCEEDED",
                        "The one who speaks for the isle hears you out, looks to an elder, and steps aside from the landing. You may come up onto the raised ground.");
                if (standing >= 10)
                    return record(community, chronicle, act, at, "WELCOMED_CAUTIOUSLY", 2, 1, "SUCCEEDED",
                        "They know your call now. Someone answers it from the isle, and a figure comes down to the landing to meet you, not hurrying.");
                return record(community, chronicle, act, at, "ACKNOWLEDGED", 1, 1, "PARTIAL",
                    "A watcher answers from the reeds with a single call, and the work on the isle goes on. They have seen you; no one comes.");
            }
            // ── Showing peace. ──────────────────────────────────────────────────────────────────────────────────
            case DISPLAY_EMPTY_HANDS, LOWER_WEAPON -> {
                if (act == Act.LOWER_WEAPON && armed) {
                    // Physically so: the weapon leaves the hand and is carried, not brandished.
                    jdbc.update("DELETE FROM equipment_attachment e USING item_instance i, weapon_profile w " +
                        "WHERE e.item_id=i.object_id AND w.item_key=i.item_key AND e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT')", chronicle);
                    return record(community, chronicle, act, at, "SAW_WEAPON_LOWERED", recentlyDid(community, chronicle, act, at) ? 0 : 3, 0, "SUCCEEDED",
                        "You lower what you were holding and let it hang from your belt. On the isle the watcher's shoulders come down a little.");
                }
                if (armed)
                    return record(community, chronicle, act, at, "SAW_A_WEAPON", -1, 0, "PARTIAL",
                        "You spread one hand, but the other still holds a weapon, and that is the one they watch.");
                return record(community, chronicle, act, at, "SAW_EMPTY_HANDS", recentlyDid(community, chronicle, act, at) ? 0 : 3, 0, "SUCCEEDED",
                    "You hold up your hands, open and empty. On the isle someone does the same.");
            }
            // ── Gifts: a real thing leaves your hands and is theirs. ────────────────────────────────────────────
            case OFFER_GIFT, LEAVE_GIFT -> {
                if (act == Act.OFFER_GIFT && (closed || !met))
                    return record(community, chronicle, act, at, "NO_ONE_CAME", 0, 0, "PARTIAL",
                        "You hold it out toward the isle, but no one comes down to the water to take it.");
                Map<String, Object> gift = giftFrom(chronicle, text);
                if (gift == null)
                    return record(community, chronicle, act, at, "NOTHING_GIVEN", 0, 0, "FAILED",
                        "You have nothing in hand to give.");
                UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
                    "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
                UUID item = (UUID) gift.get("id");
                String key = (String) gift.get("item_key");
                if (store != null) {
                    jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
                    jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
                    jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", store, item);
                    jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GIVEN_TO_COMMUNITY',jsonb_build_object('itemKey',?,'community',?::text))",
                        item, Timestamp.from(at), key, community.toString());
                }
                boolean food = "FOOD".equals(gift.get("category"));
                boolean hungry = ((Number) c.get("shortage_days")).intValue() > 0;
                int worth = food && hungry ? 10 : food ? 4 : "TOOL".equals(gift.get("category")) || "WEAPON".equals(gift.get("category")) ? 6 : 2;
                String name = ((String) gift.get("display_name")).toLowerCase(Locale.ROOT);
                String seen = act == Act.LEAVE_GIFT
                    ? "You set the " + name + " down at the edge of the reeds and step back. When you look again it is gone."
                    : food && hungry
                    ? "A figure wades out and takes the " + name + " from your hands quickly, and carries it straight up to the store."
                    : "A figure wades out, takes the " + name + ", turns it over, and carries it back up to the isle without a word.";
                return record(community, chronicle, act, at, "GIFT_" + key, worth, 1, "SUCCEEDED", seen);
            }
            // ── Speech: lands or misfires according to what the Chronicle has come to understand. ──────────────────
            case ATTEMPT_GESTURE, ATTEMPT_SHARED_WORDS, ASK_QUESTION, STATE_INTENTION, TELL_TRUTH, SHOW_ITEM, SHOW_MAP, MAKE_PROMISE -> {
                if (!met)
                    return record(community, chronicle, act, at, "UNSEEN", 0, 0, "PARTIAL",
                        "No one on the isle is looking your way. Whatever you meant by it goes unanswered.");
                if (closed)
                    return record(community, chronicle, act, at, "WARNED_AWAY", 0, 0, "PARTIAL",
                        "The watcher at the landing does not come closer to hear it, and waves you back.");
                if (understanding < understandingNeeded(act))
                    return record(community, chronicle, act, at, "MISUNDERSTOOD", -1, 2, "PARTIAL",
                        "They answer with a sharp call and a gesture of their own you do not follow. Whatever you meant, it was taken as something else, and the one nearest steps back.");
                if (act == Act.MAKE_PROMISE)
                    jdbc.update("UPDATE community_relation SET obligation=? WHERE community_id=? AND chronicle_id=?",
                        text.length() > 400 ? text.substring(0, 400) : text, community, chronicle);
                String seen = switch (act) {
                    case SHOW_ITEM -> "They lean in to look at what you hold, and one reaches out to touch it before thinking better of it.";
                    case SHOW_MAP -> "They crowd around the marks you have made, and one traces the line of the channel with a webbed finger, then taps a place you have not drawn.";
                    case MAKE_PROMISE -> "The one who speaks for them repeats something back to you slowly, twice, as if fixing it in place. They will remember it.";
                    case TELL_TRUTH, STATE_INTENTION -> "They hear you out. The elder answers with a few words and a slow nod, and the watcher sits down on the landing.";
                    case ASK_QUESTION -> "They answer, pointing: upstream, then at the sky, then at the store. You follow more of it than you would have a season ago.";
                    default -> "It lands. They answer in kind, and for a moment you are simply talking.";
                };
                return record(community, chronicle, act, at, "UNDERSTOOD", 3, 3, "SUCCEEDED", seen);
            }
            // ── Leaving well. ───────────────────────────────────────────────────────────────────────────────────
            default -> {
                return record(community, chronicle, act, at, "LEFT_RESPECTFULLY", recentlyDid(community, chronicle, act, at) ? 0 : 2, 0, "SUCCEEDED",
                    act == Act.RESPECT_BOUNDARY
                        ? "You stay at the edge of the reeds where their markers are. On the isle the work goes on around you, and after a while no one looks up when you move."
                        : "You lift a hand and back away from the isle. Someone on the landing lifts a hand in return.");
            }
        }
    }

    private String[] firstContact(UUID community, UUID chronicle, Act act, Instant at, int standing, String outcome, String seen) {
        jdbc.update("UPDATE community_relation SET first_contact_at=COALESCE(first_contact_at, ?) WHERE community_id=? AND chronicle_id=?",
            Timestamp.from(at), community, chronicle);
        return record(community, chronicle, act, at, "FIRST_CONTACT", standing, 2, outcome, seen);
    }

    /** The relation with this Chronicle, made on first acquaintance. */
    private Map<String, Object> relation(UUID community, UUID chronicle) {
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id) VALUES (?,?) " +
            "ON CONFLICT (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL DO NOTHING", community, chronicle);
        return jdbc.queryForMap("SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle);
    }

    /** The same gesture made again within a day is not a new gesture: no second helping of goodwill from repetition. */
    private boolean recentlyDid(UUID community, UUID chronicle, Act act, Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind=? AND occurred_at > ?)",
            Boolean.class, community, chronicle, "CONTACT_" + act.name(), Timestamp.from(at.minus(Duration.ofDays(1)))));
    }

    private boolean armed(UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id " +
            "JOIN weapon_profile w ON w.item_key=i.item_key " +
            "WHERE e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT') " +
            "AND w.combat_role IN ('HAND','BLUNT','JAVELIN','BOW','SLING'))", Boolean.class, chronicle));
    }

    /** What the Chronicle gives: the carried item the text names, or the first food they carry if it names none. */
    private Map<String, Object> giftFrom(UUID chronicle, String text) {
        List<Map<String, Object>> carried = jdbc.queryForList(
            "SELECT w.id, i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY CASE WHEN d.category='FOOD' THEN 0 ELSE 1 END, w.created_at", chronicle);
        String v = text.toLowerCase(Locale.ROOT);
        for (Map<String, Object> m : carried) {
            String key = ((String) m.get("item_key")).replace('_', ' ');
            String name = ((String) m.get("display_name")).toLowerCase(Locale.ROOT);
            if (v.contains(key) || v.contains(name)) return m;
        }
        return carried.stream().filter(m -> "FOOD".equals(m.get("category"))).findFirst().orElse(null);
    }

    /** Write the act to the community's history and move the relation; answer with what the Chronicle observed. */
    private String[] record(UUID community, UUID chronicle, Act act, Instant at, String response, int standingDelta,
                            int understandingDelta, String outcome, String narration) {
        jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), " +
            "understanding=GREATEST(0, LEAST(100, understanding + ?)), last_event_kind=?, last_event_at=? " +
            "WHERE community_id=? AND chronicle_id=?",
            standingDelta, understandingDelta, "CONTACT_" + act.name(), Timestamp.from(at), community, chronicle);
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) " +
            "VALUES (?,?,?,?,jsonb_build_object('response', ?::text))",
            community, Timestamp.from(at), "CONTACT_" + act.name(), chronicle, response);
        return new String[]{outcome, narration};
    }

    static boolean isDark(Instant at) {
        int h = at.atZone(ZoneOffset.UTC).getHour();
        return h < 6 || h >= 20;
    }
}
