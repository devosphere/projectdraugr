package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Trade with a people (#113, epic #109): the physical-exchange half of the ticket.
 *
 * <p>Every exchange is between real objects. The Chronicle offers things they carry for things the community's
 * store holds; a trade that completes moves both sets of objects at once, each with its own transition, and the
 * trade row records which went which way. There is no currency, and no stock that refills itself: what the isle
 * has is what its fishers and makers have put by, and what it gives away it no longer has.
 *
 * <p>A community values goods as its own life makes them valuable. The reedkin make mats, cordage, baskets and fish
 * traps, and cannot make an edge: a blade or a tool from outside is worth far more to them than another mat. Food is
 * worth what food is worth, and twice that to an isle that is going hungry. They will not trade away food when they
 * are short, nor trade at all with someone they do not know, cannot yet understand, find armed, or have come to
 * distrust; a hungry isle has closed its store to everyone.
 *
 * <p>An offer is answered in one of three ways: accepted, if what is offered is worth what is asked; met with a
 * counter-offer of fewer things, if it is close; or declined. A counter-offer waits for the Chronicle to accept or
 * decline it. Narration reports what is held up, set down, and carried away, and never a price.
 */
@Service
public class TradeService {

    public enum Act { OPEN_TRADE, INSPECT_OFFERED_GOODS, MAKE_OFFER, ACCEPT_TRADE, DECLINE_TRADE, RETURN_GOODS }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("trade with them", Act.OPEN_TRADE), Map.entry("open trade", Act.OPEN_TRADE), Map.entry("ask to trade", Act.OPEN_TRADE),
        Map.entry("barter with them", Act.OPEN_TRADE), Map.entry("trade with the reedkin", Act.OPEN_TRADE),
        Map.entry("see what they have", Act.INSPECT_OFFERED_GOODS), Map.entry("look at their goods", Act.INSPECT_OFFERED_GOODS),
        Map.entry("inspect their goods", Act.INSPECT_OFFERED_GOODS), Map.entry("what will they trade", Act.INSPECT_OFFERED_GOODS),
        Map.entry("see what they will trade", Act.INSPECT_OFFERED_GOODS),
        Map.entry("accept their offer", Act.ACCEPT_TRADE), Map.entry("accept the trade", Act.ACCEPT_TRADE),
        Map.entry("agree to the trade", Act.ACCEPT_TRADE), Map.entry("take their offer", Act.ACCEPT_TRADE), Map.entry("accept the offer", Act.ACCEPT_TRADE),
        Map.entry("decline their offer", Act.DECLINE_TRADE), Map.entry("decline the trade", Act.DECLINE_TRADE),
        Map.entry("refuse the trade", Act.DECLINE_TRADE), Map.entry("turn down their offer", Act.DECLINE_TRADE), Map.entry("decline the offer", Act.DECLINE_TRADE),
        Map.entry("return the", Act.RETURN_GOODS), Map.entry("give back the", Act.RETURN_GOODS));

    private static final Map<String, Integer> COUNTS = Map.ofEntries(
        Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
        Map.entry("five", 5), Map.entry("six", 6), Map.entry("a couple of", 2), Map.entry("a pair of", 2));
    private static final String[] WORDS = {"none", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"};

    private final JdbcTemplate jdbc;

    public TradeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * The trade act a text names, or null. An offer is anything shaped "offer / trade / swap / give ... my X for Y"
     * — the " for " is what makes it an exchange rather than a gift.
     */
    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        if (v.contains(" for ") && (v.contains(" offer ") || v.contains(" trade ") || v.contains(" swap ") || v.contains(" exchange ")
                                   || v.contains(" counter ") || v.contains(" give ")))
            return Act.MAKE_OFFER;
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** What something is worth to a community whose own hands make {@code made}. */
    static int worth(String itemKey, String category, boolean madeHere, boolean hungry, String staple) {
        if (itemKey.equals(staple)) return hungry ? 4 : 2;
        if (madeHere) return switch (itemKey) { case "fish_trap" -> 4; case "reed_mat", "woven_basket" -> 3; default -> 2; };
        return switch (category == null ? "" : category) {
            case "TOOL" -> 6;       // an edge they cannot make
            case "WEAPON" -> 4;
            case "CLOTHING" -> 3;
            case "CONTAINER" -> 2;
            case "FOOD" -> hungry ? 4 : 2;
            case "MATERIAL" -> 1;
            default -> 1;
        };
    }

    /** Carry out one act of trade. */
    @Transactional
    public String[] act(UUID chronicle, UUID community, Act act, String text, Instant at, boolean armed) {
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT trade_policy, base_trade_policy, shortage_days, staple_item_key, lifecycle FROM native_community WHERE id=?", community);
        Map<String, Object> r = jdbc.queryForList(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle).stream().findFirst().orElse(Map.of("standing", 0, "understanding", 0));
        int standing = ((Number) r.get("standing")).intValue();
        int understanding = ((Number) r.get("understanding")).intValue();
        boolean met = r.get("first_contact_at") != null;
        boolean hungry = ((Number) c.get("shortage_days")).intValue() > 0;
        String staple = (String) c.get("staple_item_key");
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);

        // Who they will trade with at all.
        String refusal = !met ? "No one comes down to the water. They do not trade with a stranger they have not yet seen."
            : "MOVING".equals(c.get("lifecycle")) || store == null ? "There is no one here to trade with; the store is empty and shut."
            : "CLOSED".equals(c.get("trade_policy")) ? "The watcher at the landing waves you back. The store house is shut, and nothing is coming out of it."
            : armed ? "They will not come near enough to trade while you hold a weapon."
            : standing < ("OPEN".equals(c.get("base_trade_policy")) ? 0 : 10) ? "They watch you from the landing and make no move to bring anything down. They do not trust you enough for that yet."
            : understanding < 20 ? "They bring nothing down. Whatever you are asking, it has not yet reached them as a wish to trade."
            // No one decides for the isle while its speaker's office is empty (#121).
            : Eligibility.firstOf(
                Eligibility.refusal(jdbc, community, Eligibility.TRADE,
                    "They will not barter. Goods pass among their own and not across to outsiders, and no gesture of yours changes that."),
                AudienceService.withoutASpeaker(jdbc, community));
        if (refusal != null && act != Act.RETURN_GOODS) return record(community, chronicle, act, at, "REFUSED", 0, "PARTIAL", refusal);

        switch (act) {
            case OPEN_TRADE, INSPECT_OFFERED_GOODS -> {
                String laid = offered(store, staple, hungry);
                return record(community, chronicle, act, at, "GOODS_SHOWN", 0, "SUCCEEDED", laid.isEmpty()
                    ? "They come down to the landing empty-handed and spread their hands: there is nothing they will part with."
                    : "They lay out on the landing what they will part with: " + laid + ". Then they wait to see what you have.");
            }
            case MAKE_OFFER -> { return offer(chronicle, community, store, staple, hungry, text, at); }
            case ACCEPT_TRADE -> {
                Map<String, Object> open = openOffer(community, chronicle);
                if (open == null) return record(community, chronicle, act, at, "NOTHING_TO_ACCEPT", 0, "FAILED", "There is no offer of theirs waiting on you.");
                UUID[] offered = uuids(open.get("offered_item_ids"));
                List<UUID> stillCarried = new ArrayList<>();
                for (UUID id : offered) if (carries(chronicle, id)) stillCarried.add(id);
                String wantedKey = (String) open.get("wanted_item_key");
                int count = ((Number) open.get("wanted_count")).intValue();
                List<UUID> theirs = held(store, wantedKey, count);
                if (stillCarried.size() < offered.length || theirs.size() < count) {
                    jdbc.update("UPDATE native_trade SET status='WITHDRAWN', settled_at=? WHERE id=?", Timestamp.from(at), open.get("id"));
                    return record(community, chronicle, act, at, "OFFER_LAPSED", 0, "PARTIAL", "What was on the landing is no longer all there, and the offer lapses.");
                }
                return complete((UUID) open.get("id"), chronicle, community, store, stillCarried, theirs, at, act);
            }
            case DECLINE_TRADE -> {
                Map<String, Object> open = openOffer(community, chronicle);
                if (open == null) return record(community, chronicle, act, at, "NOTHING_TO_DECLINE", 0, "FAILED", "There is no offer of theirs waiting on you.");
                jdbc.update("UPDATE native_trade SET status='DECLINED', settled_at=? WHERE id=?", Timestamp.from(at), open.get("id"));
                return record(community, chronicle, act, at, "DECLINED_BY_CHRONICLE", 0, "SUCCEEDED",
                    "You shake your head. They gather up what they had laid out and carry it back up to the store, without any show of offence.");
            }
            default -> {
                // Giving back what you had from them: the isle's goods go home, and it is noticed.
                Map<String, Object> got = receivedNamed(chronicle, community, text);
                if (got == null) return record(community, chronicle, act, at, "NOTHING_TO_RETURN", 0, "FAILED", "You carry nothing of theirs to give back.");
                // Something stolen and brought back is restitution (#114), and counts for more than a traded thing returned.
                boolean stolen = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM object_transition WHERE object_id=? AND transition_type='STOLEN_FROM_COMMUNITY')", Boolean.class, got.get("id")));
                transfer((UUID) got.get("id"), store, "RETURNED_TO_COMMUNITY", (String) got.get("item_key"), at, community);
                return record(community, chronicle, act, at, "RETURNED_PROPERTY", stolen ? 8 : 2, "SUCCEEDED",
                    "You carry the " + ((String) got.get("display_name")).toLowerCase(Locale.ROOT) + " back to the landing and set it down. Someone takes it up to the store, and looks back at you once.");
            }
        }
    }

    private String[] offer(UUID chronicle, UUID community, UUID store, String staple, boolean hungry, String text, Instant at) {
        String v = text.toLowerCase(Locale.ROOT);
        int split = v.indexOf(" for ");
        String give = v.substring(0, split), want = v.substring(split + 5);

        // What is wanted: one of the things in their store, by name.
        List<Map<String, Object>> theirKinds = jdbc.queryForList(
            "SELECT DISTINCT i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", store);
        Map<String, Object> wanted = named(want, theirKinds);
        if (wanted == null)
            return record(community, chronicle, Act.MAKE_OFFER, at, "NOT_THEIRS", 0, "PARTIAL",
                "They look where you point, and then at each other. Nothing like that is among what they keep.");
        String wantedKey = (String) wanted.get("item_key");
        if (wantedKey.equals(staple) && hungry)
            return record(community, chronicle, Act.MAKE_OFFER, at, "WILL_NOT_PART_WITH_FOOD", 0, "PARTIAL",
                "At the word for food their faces close. That, of all things, they will not part with now.");
        int wantCount = count(want);
        List<UUID> theirs = held(store, wantedKey, wantCount);
        if (theirs.isEmpty())
            return record(community, chronicle, Act.MAKE_OFFER, at, "NOT_THEIRS", 0, "PARTIAL", "They have none of that to spare.");
        wantCount = theirs.size();

        // What is offered: carried things named on the giving side.
        List<Map<String, Object>> carried = jdbc.queryForList(
            "SELECT w.id, i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM equipment_attachment e WHERE e.item_id=w.id) ORDER BY w.created_at", chronicle);
        Map<String, Object> kind = named(give, carried);
        if (kind == null)
            return record(community, chronicle, Act.MAKE_OFFER, at, "NOTHING_OFFERED", 0, "FAILED", "You are not carrying what you meant to offer.");
        int giveCount = count(give);
        List<UUID> mine = carried.stream().filter(k -> kind.get("item_key").equals(k.get("item_key")))
            .map(k -> (UUID) k.get("id")).limit(giveCount).toList();

        boolean madeHere = java.util.Arrays.asList(NativeCommunityService.MADE_GOODS).contains(wantedKey);
        int given = mine.size() * worth((String) kind.get("item_key"), (String) kind.get("category"),
            java.util.Arrays.asList(NativeCommunityService.MADE_GOODS).contains(kind.get("item_key")), hungry, staple);
        int each = worth(wantedKey, (String) wanted.get("category"), madeHere, hungry, staple);
        jdbc.update("UPDATE native_trade SET status='WITHDRAWN', settled_at=? WHERE community_id=? AND chronicle_id=? AND status='COUNTERED'",
            Timestamp.from(at), community, chronicle);

        UUID trade = UUID.randomUUID();
        if (given >= each * wantCount) {
            jdbc.update("INSERT INTO native_trade (id, community_id, chronicle_id, status, offered_item_ids, wanted_item_key, wanted_count, offered_at) " +
                "VALUES (?,?,?,'COUNTERED',?,?,?,?)", trade, community, chronicle, toArray(mine), wantedKey, wantCount, Timestamp.from(at));
            return complete(trade, chronicle, community, store, mine, theirs, at, Act.MAKE_OFFER);
        }
        int fewer = given / each;
        if (fewer >= 1 && given * 10 >= each * wantCount * 6) {
            jdbc.update("INSERT INTO native_trade (id, community_id, chronicle_id, status, offered_item_ids, wanted_item_key, wanted_count, offered_at) " +
                "VALUES (?,?,?,'COUNTERED',?,?,?,?)", trade, community, chronicle, toArray(mine), wantedKey, fewer, Timestamp.from(at));
            String name = ((String) wanted.get("display_name")).toLowerCase(Locale.ROOT);
            return record(community, chronicle, Act.MAKE_OFFER, at, "COUNTERED", 0, "PARTIAL",
                "They look at what you hold out, then put back some of what you asked for and hold up " + WORDS[Math.min(fewer, 12)] + " " + name
                + (fewer == 1 ? "" : "s") + ", not " + WORDS[Math.min(wantCount, 12)] + ". They wait for you to take it or leave it.");
        }
        return record(community, chronicle, Act.MAKE_OFFER, at, "DECLINED", 0, "PARTIAL",
            "They look at what you hold out and shake their heads, and the goods on the landing stay where they are.");
    }

    /** Both sides move at once, each object with its own history; the trade records which went which way. */
    private String[] complete(UUID trade, UUID chronicle, UUID community, UUID store, List<UUID> mine, List<UUID> theirs, Instant at, Act act) {
        for (UUID id : mine) transfer(id, store, "TRADED_TO_COMMUNITY", null, at, community);
        for (UUID id : theirs) {
            jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", chronicle, id);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TRADED_FROM_COMMUNITY',jsonb_build_object('community',?::text))",
                id, Timestamp.from(at), community.toString());
        }
        jdbc.update("UPDATE native_trade SET status='COMPLETED', received_item_ids=?, settled_at=? WHERE id=?", toArray(theirs), Timestamp.from(at), trade);
        String name = jdbc.queryForObject("SELECT d.display_name FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key WHERE i.object_id=?", String.class, theirs.get(0));
        return record(community, chronicle, act, at, "FAIR_TRADE", 1, "SUCCEEDED",
            "They take what you give and turn it over in their hands, and push " + WORDS[Math.min(theirs.size(), 12)] + " "
            + name.toLowerCase(Locale.ROOT) + (theirs.size() == 1 ? "" : "s") + " across the landing to you. The trade is made.");
    }

    private void transfer(UUID item, UUID store, String transition, String itemKey, Instant at, UUID community) {
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", store, item);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('community',?::text))",
            item, Timestamp.from(at), transition, community.toString());
    }

    private String offered(UUID store, String staple, boolean hungry) {
        List<Map<String, Object>> kinds = jdbc.queryForList(
            "SELECT d.display_name, i.item_key, COUNT(*) AS n FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "GROUP BY d.display_name, i.item_key ORDER BY d.display_name", store);
        List<String> shown = new ArrayList<>();
        for (Map<String, Object> k : kinds) {
            if (hungry && staple.equals(k.get("item_key"))) continue;
            shown.add(((String) k.get("display_name")).toLowerCase(Locale.ROOT));
        }
        return String.join(", ", shown);
    }

    private List<UUID> held(UUID store, String itemKey, int count) {
        return jdbc.queryForList("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "LEFT JOIN food_preservation_state f ON f.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND f.spoiled_at IS NULL " +
            "ORDER BY w.created_at LIMIT ?", UUID.class, store, itemKey, count);
    }

    private boolean carries(UUID chronicle, UUID item) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object WHERE id=? AND current_owner_id=? AND lifecycle_state='ACTIVE')", Boolean.class, item, chronicle));
    }

    private Map<String, Object> openOffer(UUID community, UUID chronicle) {
        return jdbc.queryForList("SELECT id, offered_item_ids, wanted_item_key, wanted_count FROM native_trade " +
            "WHERE community_id=? AND chronicle_id=? AND status='COUNTERED'", community, chronicle).stream().findFirst().orElse(null);
    }

    /** A carried item the Chronicle received from this community, named in the text. */
    private Map<String, Object> receivedNamed(UUID chronicle, UUID community, String text) {
        List<Map<String, Object>> theirs = jdbc.queryForList(
            "SELECT DISTINCT w.id, i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key JOIN object_transition t ON t.object_id=w.id " +
            "AND t.transition_type IN ('TRADED_FROM_COMMUNITY','STOLEN_FROM_COMMUNITY','ROBBED_FROM_THE_DEAD') " +
            "AND t.payload->>'community' = ?::text WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", community.toString(), chronicle);
        return named(text.toLowerCase(Locale.ROOT), theirs);
    }

    /**
     * The kind a phrase names: an exact name first ("fish trap" is the fish trap), and only failing that a thing
     * named by its last word ("the mat"), so "the fish trap" is never taken for dried fish because both say "fish".
     */
    private static Map<String, Object> named(String text, List<Map<String, Object>> kinds) {
        for (Map<String, Object> k : kinds) {
            String key = ((String) k.get("item_key")).replace('_', ' ');
            String name = ((String) k.get("display_name")).toLowerCase(Locale.ROOT);
            if (text.contains(key) || text.contains(name)) return k;
        }
        for (Map<String, Object> k : kinds) {
            String last = lastWord(((String) k.get("item_key")).replace('_', ' '));
            if (last != null && (" " + text + " ").contains(" " + last)) return k;
        }
        return null;
    }

    /** The last word of a thing's name, if it is long enough to mean that thing ("mat" is too short to be sure of). */
    private static String lastWord(String key) {
        String[] parts = key.split(" ");
        String last = parts[parts.length - 1];
        return last.length() >= 4 ? last : null;
    }

    /** How many a phrase asks for: a number word or digits before the thing, else one. */
    static int count(String phrase) {
        String p = " " + phrase.toLowerCase(Locale.ROOT).trim() + " ";
        for (String w : new String[]{"a couple of", "a pair of"}) if (p.contains(" " + w + " ")) return 2;
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b").matcher(p);
        if (d.find()) return Math.max(1, Math.min(12, Integer.parseInt(d.group(1))));
        for (Map.Entry<String, Integer> e : COUNTS.entrySet())
            if (e.getKey().length() > 2 && p.contains(" " + e.getKey() + " ")) return e.getValue();
        return 1;
    }

    private static UUID[] uuids(Object array) {
        try {
            Object[] raw = (Object[]) ((java.sql.Array) array).getArray();
            UUID[] out = new UUID[raw.length];
            for (int i = 0; i < raw.length; i++) out[i] = raw[i] instanceof UUID u ? u : UUID.fromString(raw[i].toString());
            return out;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not read the items of an open offer", e);
        }
    }

    private java.sql.Array toArray(List<UUID> ids) {
        return jdbc.execute((java.sql.Connection con) -> con.createArrayOf("uuid", ids.toArray()));
    }

    private String[] record(UUID community, UUID chronicle, Act act, Instant at, String response, int standingDelta, String outcome, String narration) {
        if (standingDelta != 0)
            jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind=?, last_event_at=? " +
                "WHERE community_id=? AND chronicle_id=?", standingDelta, "TRADE_" + act.name(), Timestamp.from(at), community, chronicle);
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,jsonb_build_object('response', ?::text))",
            community, Timestamp.from(at), "TRADE_" + act.name(), chronicle, response);
        return new String[]{outcome, narration};
    }
}
