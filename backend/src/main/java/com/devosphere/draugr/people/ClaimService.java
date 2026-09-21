package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What they ask in return (#211, #114): compensation for a wrong, named in goods and settled or not.
 *
 * <p>A people who have been robbed, burnt out or bereaved do not only feel worse about the Chronicle: they say what
 * would put it right, and they hold it against them until it is paid. The claim is a row with a price in it, set by
 * what was done:
 *
 * <ul>
 *   <li><b>Named, not felt.</b> The Chronicle can ask what is demanded and be told, once they can be understood.</li>
 *   <li><b>Paid in real goods.</b> Anything carried, valued as trade values it (TradeService.worth), handed over at
 *       the landing until the price is met. A part payment is part paid, and they remember that too.</li>
 *   <li><b>Worth paying.</b> Settling a claim is the largest single step back toward being tolerated, and it lifts
 *       the isle from hostile to merely guarded — which is the difference between being driven off and being
 *       watched.</li>
 *   <li><b>Or not paid.</b> A claim left standing a month is not forgotten: it hardens into a grievance, and the
 *       history says it went unanswered.</li>
 * </ul>
 */
@Service
public class ClaimService {

    public enum Act { ASK_WHAT_IS_DEMANDED, PAY }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("ask what they demand", Act.ASK_WHAT_IS_DEMANDED), Map.entry("ask what they want", Act.ASK_WHAT_IS_DEMANDED),
        Map.entry("ask what is owed", Act.ASK_WHAT_IS_DEMANDED), Map.entry("ask what would put it right", Act.ASK_WHAT_IS_DEMANDED),
        Map.entry("ask about the claim", Act.ASK_WHAT_IS_DEMANDED),
        Map.entry("pay compensation", Act.PAY), Map.entry("pay what they ask", Act.PAY), Map.entry("pay the claim", Act.PAY),
        Map.entry("settle the claim", Act.PAY), Map.entry("pay their price", Act.PAY), Map.entry("pay what is owed", Act.PAY));

    /** What each wrong is reckoned to be worth, on the same scale trade values goods. */
    static final Map<String, Integer> PRICE = Map.ofEntries(
        Map.entry("MURDER", 40), Map.entry("HARM_TO_INDIVIDUAL", 20), Map.entry("RESTRAINT_ATTEMPT", 16),
        Map.entry("FIRE_DAMAGE", 24), Map.entry("DAMAGE_TO_SETTLEMENT", 12), Map.entry("GRAVE_ROBBING", 20),
        Map.entry("FOOD_THEFT", 8), Map.entry("PROPERTY_THEFT", 6), Map.entry("WATER_FOULED", 6));

    /** What settling one is worth in standing, and how long a claim waits before it hardens. */
    static final int SETTLED_IS_WORTH = 25;
    static final Duration WAITS_FOR = Duration.ofDays(30);

    private final JdbcTemplate jdbc;

    public ClaimService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /**
     * Raise a claim for a witnessed wrong, or add to the one already standing. Called by ConductService the moment
     * the wrong is seen, because what they ask for is part of how they answer it, not a later thought.
     */
    void demand(UUID community, UUID chronicle, String offence, Instant at) {
        Integer price = PRICE.get(offence);
        if (price == null) return;
        Map<String, Object> open = openClaim(community, chronicle);
        if (open == null) {
            jdbc.update("INSERT INTO native_claim (community_id, chronicle_id, for_offence, price, demanded_at) VALUES (?,?,?,?,?)",
                community, chronicle, offence, price, Timestamp.from(at));
        } else {
            jdbc.update("UPDATE native_claim SET price=price+?, for_offence=? WHERE id=?", price, offence, open.get("id"));
        }
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,'COMPENSATION_DEMANDED',?," +
            "jsonb_build_object('for', ?::text, 'price', ?::int))", community, Timestamp.from(at), chronicle, offence, price);
    }

    @Transactional
    public String[] act(UUID chronicle, UUID community, Act act, Instant at) {
        Map<String, Object> claim = openClaim(community, chronicle);
        int understanding = jdbc.query("SELECT understanding FROM community_relation WHERE community_id=? AND chronicle_id=?",
            rs -> rs.next() ? rs.getInt(1) : 0, community, chronicle);
        if (claim == null)
            return new String[]{act == Act.PAY ? "FAILED" : "SUCCEEDED", "They ask nothing of you. Whatever stands between you and them, it is not a debt."};
        int owed = ((Number) claim.get("price")).intValue() - ((Number) claim.get("paid")).intValue();
        if (act == Act.ASK_WHAT_IS_DEMANDED) {
            if (understanding < 20)
                return new String[]{"PARTIAL", "You ask what would put it right. They answer at length, and none of it reaches you yet."};
            return new String[]{"SUCCEEDED", "They make it plain, holding up what they mean: goods to the worth of about "
                + owed + (owed == 1 ? " mat" : " mats") + " — tools, food, anything of use — set down at the landing. "
                + "Until then, what you did stands between you."};
        }
        if (understanding < 20)
            return new String[]{"PARTIAL", "You hold out what you have brought. They will not take it from a stranger they cannot understand."};

        // Paid in whatever is carried, valued as they value it, oldest first, until the price is met.
        String staple = jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, community);
        boolean hungry = jdbc.queryForObject("SELECT shortage_days FROM native_community WHERE id=?", Integer.class, community) > 0;
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
        if (store == null) return new String[]{"FAILED", "There is no store house left standing to put anything in."};
        List<Map<String, Object>> carried = jdbc.queryForList(
            "SELECT w.id, i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM equipment_attachment e WHERE e.item_id=w.id) ORDER BY w.created_at", chronicle);
        int given = 0;
        List<String> handed = new ArrayList<>();
        for (Map<String, Object> thing : carried) {
            if (given >= owed) break;
            // Valued by what THIS people makes (#113, V366), the same as trade and a wage — a bark sheet handed
            // to the grovebound in settlement is a thing of theirs, and a reed mat handed to them is not.
            int worth = TradeService.worth((String) thing.get("item_key"), (String) thing.get("category"),
                makes(community, thing.get("item_key")), hungry, staple);
            jdbc.update("DELETE FROM item_containment WHERE item_id=?", thing.get("id"));
            jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", store, thing.get("id"));
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'PAID_IN_COMPENSATION',jsonb_build_object('community',?::text))",
                thing.get("id"), Timestamp.from(at), community.toString());
            given += worth;
            handed.add(((String) thing.get("display_name")).toLowerCase(Locale.ROOT));
        }
        if (given == 0)
            return new String[]{"FAILED", "You carry nothing they would take for it. An empty hand is not an answer to what they are asking."};

        int paid = ((Number) claim.get("paid")).intValue() + given;
        boolean settled = paid >= ((Number) claim.get("price")).intValue();
        jdbc.update("UPDATE native_claim SET paid=?, status=?, settled_at=? WHERE id=?",
            paid, settled ? "PAID" : "OPEN", settled ? Timestamp.from(at) : null, claim.get("id"));
        jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind='COMPENSATION_PAID', last_event_at=? " +
            "WHERE community_id=? AND chronicle_id=?", settled ? SETTLED_IS_WORTH : SETTLED_IS_WORTH / 3, Timestamp.from(at), community, chronicle);
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?," +
            "jsonb_build_object('paid', ?::int, 'price', ?::int))", community, Timestamp.from(at),
            settled ? "COMPENSATION_PAID" : "COMPENSATION_PART_PAID", chronicle, paid, claim.get("price"));
        if (settled)
            jdbc.update("UPDATE native_community SET security_posture='GUARDED' WHERE id=? AND security_posture='HOSTILE'", community);
        String what = String.join(", ", handed);
        return new String[]{settled ? "SUCCEEDED" : "PARTIAL", settled
            ? "You set down " + what + " at the landing and step back. The elder looks at it for a long moment, then has it carried up to the store. "
              + "It is not forgotten. It is answered, and they let you stand where you are."
            : "You set down " + what + ", which is not all of it. They take it up, and go on waiting for the rest."};
    }

    /** The daily reckoning: a claim nobody answers hardens, once, into a grievance the history keeps. */
    void reckon(UUID community, Instant day) {
        for (Map<String, Object> stale : jdbc.queryForList(
                "SELECT id, chronicle_id FROM native_claim WHERE community_id=? AND status='OPEN' AND demanded_at <= ?",
                community, Timestamp.from(day.minus(WAITS_FOR)))) {
            jdbc.update("UPDATE native_claim SET status='UNANSWERED', settled_at=? WHERE id=?", Timestamp.from(day), stale.get("id"));
            jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, standing - 5), last_event_kind='CLAIM_UNANSWERED', last_event_at=? " +
                "WHERE community_id=? AND chronicle_id=?", Timestamp.from(day), community, stale.get("chronicle_id"));
            jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id) VALUES (?,?,'CLAIM_UNANSWERED',?)",
                community, Timestamp.from(day), stale.get("chronicle_id"));
        }
    }

    /**
     * Does this people make this thing with its own hands (#113, V366)? Read from {@code made_goods} on the
     * community row. It was a Java constant naming reed goods, so a bark sheet handed to the grovebound in
     * settlement was priced as something brought in from elsewhere — while trade, taught when the second people
     * landed, priced the same object as theirs. One object, two worths, depending on the door.
     */
    private boolean makes(UUID community, Object itemKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT ?::varchar = ANY(made_goods) FROM native_community WHERE id=?", Boolean.class, itemKey, community));
    }

    private Map<String, Object> openClaim(UUID community, UUID chronicle) {
        return jdbc.queryForList("SELECT id, price, paid, for_offence FROM native_claim WHERE community_id=? AND chronicle_id=? AND status='OPEN'",
            community, chronicle).stream().findFirst().orElse(null);
    }
}
