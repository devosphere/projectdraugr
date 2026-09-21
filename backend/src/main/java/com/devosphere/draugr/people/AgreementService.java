package com.devosphere.draugr.people;

import com.devosphere.draugr.item.PhysicalItemService;
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
 * Paid work for a people (#113, epic #109): the agreements half of the ticket.
 *
 * <p>A Chronicle can offer days of work for goods from a community's store: "work for them for two dried fish". The
 * community weighs the wage against what a day's work is worth to them and either agrees on a number of days, or
 * refuses — a stranger, someone they distrust, a hungry isle asked for its food, or a wage out of all proportion.
 * The agreement is a real row with terms and a date by which the work is owed, and it is kept or broken:
 * <ul>
 *   <li><b>The work is real.</b> A day's work is a day's fishing for them: it takes the Chronicle most of the day,
 *       tires them, and puts the day's catch in the isle's store, where it can be eaten, traded or burned.</li>
 *   <li><b>The wage is real.</b> When the last day is worked, the wage is carried out of the store and put in the
 *       Chronicle's hands, each object with its own history. If the store no longer holds it all, they pay what they
 *       can now and the rest when they have it — a debt they carry, settled by the daily step.</li>
 *   <li><b>A promise is remembered.</b> Work left undone past its date, or walked away from, is a broken promise
 *       (#114): it costs standing, and it stays in their history. Asking to be released honestly costs a little.</li>
 * </ul>
 * One open agreement at a time between a Chronicle and a community: a person working for someone is working for
 * them, not collecting contracts.
 */
@Service
public class AgreementService {

    public enum Act { OFFER_WORK, DO_WORK, ASK_RELEASE, BREAK_AGREEMENT, ASK_TERMS }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("work for them", Act.DO_WORK), Map.entry("do the work", Act.DO_WORK), Map.entry("do my work for them", Act.DO_WORK),
        Map.entry("put in a day's work", Act.DO_WORK), Map.entry("do a day's work", Act.DO_WORK), Map.entry("work off my debt", Act.DO_WORK),
        Map.entry("keep my agreement", Act.DO_WORK), Map.entry("work for the reedkin", Act.DO_WORK),
        Map.entry("ask to be released", Act.ASK_RELEASE), Map.entry("release me from", Act.ASK_RELEASE), Map.entry("ask them to release me", Act.ASK_RELEASE),
        Map.entry("break the agreement", Act.BREAK_AGREEMENT), Map.entry("break my agreement", Act.BREAK_AGREEMENT), Map.entry("break my promise", Act.BREAK_AGREEMENT),
        Map.entry("abandon the work", Act.BREAK_AGREEMENT), Map.entry("walk away from the work", Act.BREAK_AGREEMENT), Map.entry("quit the work", Act.BREAK_AGREEMENT),
        Map.entry("what do i owe them", Act.ASK_TERMS), Map.entry("what do they owe me", Act.ASK_TERMS), Map.entry("recall our agreement", Act.ASK_TERMS),
        Map.entry("check the agreement", Act.ASK_TERMS), Map.entry("how much work is left", Act.ASK_TERMS));

    /** What a day's work is worth to a community, on the same scale trade values goods (TradeService.worth). */
    static final int DAY_IS_WORTH = 2;
    /** The longest a community will bind someone to: a week. */
    static final int LONGEST_TERM_DAYS = 7;
    /** Days allowed beyond the work itself before the work is owed: rest, weather, and a Chronicle's own needs. */
    static final int GRACE_DAYS = 3;
    /** What a broken promise costs in standing, and what an honest release costs. */
    static final int BROKEN_PROMISE = -15, RELEASED = -3, KEPT = 5;

    private static final String[] WORDS = {"no", "one", "two", "three", "four", "five", "six", "seven"};

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;

    public AgreementService(JdbcTemplate jdbc, PhysicalItemService items) {
        this.jdbc = jdbc;
        this.items = items;
    }

    /**
     * The act a text names, or null. An offer of work is "work ... for" with a wage after the "for" — "work for them"
     * alone is doing the work already agreed, so the offer needs something named after a second "for", or an explicit
     * "offer to work" / "offer my labour" with a wage.
     */
    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        boolean workWords = v.contains(" work ") || v.contains(" labour ") || v.contains(" labor ");
        boolean toThem = v.contains(" them ") || v.contains(" offer ") || v.contains(" reedkin ") || v.contains(" my labour ") || v.contains(" my labor ");
        if (workWords && toThem && wage(v) != null) return Act.OFFER_WORK;
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** The wage part of an offer — whatever follows the last " for " — when it names something other than a person. */
    static String wage(String normalised) {
        int split = normalised.lastIndexOf(" for ");
        if (split < 0) return null;
        String rest = normalised.substring(split + 5).trim();
        if (rest.isEmpty() || rest.matches("(them|the reedkin|the isle|you|him|her|us|a day|a while|some days|a few days|a week)")) return null;
        return rest;
    }

    @Transactional
    public String[] act(UUID chronicle, UUID chunk, UUID community, Act act, String text, Instant at) {
        Map<String, Object> open = openAgreement(community, chronicle);
        return switch (act) {
            case OFFER_WORK -> offer(chronicle, community, open, text, at);
            case DO_WORK -> work(chronicle, chunk, community, open, at);
            case ASK_RELEASE -> {
                if (open == null) yield new String[]{"FAILED", "You are bound to no work for them."};
                settle(open, "RELEASED", at);
                standing(community, chronicle, RELEASED, "RELEASED_FROM_AGREEMENT", at);
                event(community, chronicle, at, "RELEASED_FROM_AGREEMENT", ((Number) open.get("days_done")).intValue());
                yield new String[]{"SUCCEEDED", "You tell them you cannot finish the work. The one who speaks for the isle hears you out and "
                    + "lets you go with a small, tired gesture. Nothing is owed either way, and nothing is said about it again — but it is remembered."};
            }
            case BREAK_AGREEMENT -> {
                if (open == null) yield new String[]{"FAILED", "You are bound to no work for them."};
                breakPromise(open, at);
                yield new String[]{"SUCCEEDED", "You leave the work where it lies and do not go back to it. They see you go."};
            }
            case ASK_TERMS -> {
                if (open == null) yield new String[]{"SUCCEEDED", owedToYou(chronicle, community)};
                int owed = ((Number) open.get("days_owed")).intValue(), done = ((Number) open.get("days_done")).intValue();
                yield new String[]{"SUCCEEDED", "You have worked " + WORDS[Math.min(done, 7)] + " of the " + WORDS[Math.min(owed, 7)]
                    + " days you promised them, for " + wageWords(open) + ". "
                    + (Duration.between(at, ((Timestamp) open.get("due_at")).toInstant()).toHours() < 36
                        ? "The work is owed soon." : "There is time yet.")};
            }
        };
    }

    private String[] offer(UUID chronicle, UUID community, Map<String, Object> open, String text, Instant at) {
        if (open != null)
            return new String[]{"PARTIAL", "They remind you, with a gesture at the water, that you already owe them work."};
        Map<String, Object> c = jdbc.queryForMap("SELECT trade_policy, shortage_days, staple_item_key, lifecycle FROM native_community WHERE id=?", community);
        Map<String, Object> r = jdbc.queryForList(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle).stream().findFirst().orElse(Map.of("standing", 0, "understanding", 0));
        int standing = ((Number) r.get("standing")).intValue(), understanding = ((Number) r.get("understanding")).intValue();
        UUID store = store(community);
        String refusal = r.get("first_contact_at") == null ? "No one comes down to the water. They do not bargain with a stranger they have not seen."
            : "MOVING".equals(c.get("lifecycle")) || store == null ? "There is no store here to pay a wage from, and no one to take you on."
            : standing < 0 ? "They look at you, and at each other, and no one steps forward. They do not want your hands on their work."
            : understanding < 30 ? "You mime hauling nets and point to their store. They watch politely, but what you mean does not reach them yet."
            // A people arguing about whether to abandon its home does not take on a week's work (#121). Not a
            // refusal of you — a refusal of the question, while a larger one of their own is still open.
            : arguing(community) ? "They are in the middle of something among themselves, voices going back and forth across the fire, "
                + "and what they settle will decide more than your week. Nobody will put their name to anything today."
            : Eligibility.firstOf(
                Eligibility.refusal(jdbc, community, Eligibility.AGREEMENT,
                    "They do not bind themselves to outsiders, nor outsiders to them. Whatever you are offering, it is not a thing they do."),
                Eligibility.refusal(jdbc, community, Eligibility.WORKING, "They do not take anyone on to work beside them."),
                AudienceService.withoutASpeaker(jdbc, community));
        if (refusal != null) { event(community, chronicle, at, "WORK_REFUSED", 0); return new String[]{"PARTIAL", refusal}; }

        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String want = wage(v);
        List<Map<String, Object>> kinds = jdbc.queryForList(
            "SELECT DISTINCT i.item_key, d.display_name, d.category FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN item_definition d ON d.item_key=i.item_key WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", store);
        Map<String, Object> wanted = named(want, kinds);
        if (wanted == null) return new String[]{"PARTIAL", "They look at where you point, then spread their hands: nothing like that is theirs to give."};
        String key = (String) wanted.get("item_key"), staple = (String) c.get("staple_item_key");
        boolean hungry = ((Number) c.get("shortage_days")).intValue() > 0;
        if (key.equals(staple) && hungry)
            return new String[]{"PARTIAL", "At the word for food their faces close. They cannot promise food they do not have for themselves."};
        int count = Math.min(TradeService.count(want), countHeld(store, key));
        if (count == 0) return new String[]{"PARTIAL", "They have none of that to promise."};
        // What THIS people makes, from its own row (#113, V366) — not the reed list every people used to share.
        // Trade was taught this when the second people landed; a wage was not, so the grovebound would price a
        // bark sheet as a thing brought in from somewhere else, and the same object was worth two amounts
        // depending on which door a Chronicle came through.
        int worth = count * TradeService.worth(key, (String) wanted.get("category"),
            makes(community, key), hungry, staple);
        int days = Math.max(1, (worth + DAY_IS_WORTH - 1) / DAY_IS_WORTH);
        if (days > LONGEST_TERM_DAYS)
            return new String[]{"PARTIAL", "They count on their fingers, and then stop counting. That is more than they will bind anyone to."};

        UUID id = UUID.randomUUID();
        Instant due = at.plus(Duration.ofDays(days + GRACE_DAYS));
        jdbc.update("INSERT INTO native_agreement (id, community_id, chronicle_id, kind, days_owed, wage_item_key, wage_count, agreed_at, due_at) " +
            "VALUES (?,?,?,'PAID_WORK',?,?,?,?,?)", id, community, chronicle, days, key, count, Timestamp.from(at), Timestamp.from(due));
        event(community, chronicle, at, "AGREEMENT_MADE", days);
        String name = plural(((String) wanted.get("display_name")).toLowerCase(Locale.ROOT), count);
        return new String[]{"SUCCEEDED", "The one who speaks for the isle holds up " + WORDS[days] + (days == 1 ? " finger" : " fingers")
            + ", then points at the nets and the weirs, then at the store: " + WORDS[Math.min(count, 7)] + " " + name
            + " for " + WORDS[days] + (days == 1 ? " day's" : " days'") + " work. You nod, and it is agreed."};
    }

    private String[] work(UUID chronicle, UUID chunk, UUID community, Map<String, Object> open, Instant at) {
        if (open == null) return new String[]{"FAILED", "You have agreed no work with them. Offer your work for something first."};
        if (!chunk.equals(jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community)))
            return new String[]{"FAILED", "Their nets and weirs are on the isle. You cannot work them from here."};
        Timestamp last = (Timestamp) open.get("last_worked_at");
        if (last != null && Duration.between(last.toInstant(), at).toHours() < 18)
            return new String[]{"PARTIAL", "You have already done a day's work for them today. They wave you off to rest."};
        UUID store = store(community);
        String staple = jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, community);
        // A day at their nets and weirs: the catch goes into their store, as any fisher's does.
        int catchOfTheDay = NativeCommunityService.yieldPerWorker(at);
        if (store != null) {
            String stapleName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, staple);
            for (int i = 0; i < catchOfTheDay; i++) items.createHeldItem(store, staple, stapleName, at, "GATHERED_BY_COMMUNITY");
        }
        int done = ((Number) open.get("days_done")).intValue() + 1, owed = ((Number) open.get("days_owed")).intValue();
        jdbc.update("UPDATE native_agreement SET days_done=?, last_worked_at=? WHERE id=?", done, Timestamp.from(at), open.get("id"));
        event(community, chronicle, at, "WORK_DONE", done);
        String day = "You spend the day at their weirs and nets, hauling and gutting beside them until your back aches. "
            + (store != null ? "The day's catch goes up to their store. " : "");
        if (done < owed) return new String[]{"SUCCEEDED", day + (owed - done == 1 ? "One more day is owed." : WORDS[owed - done].substring(0, 1).toUpperCase(Locale.ROOT) + WORDS[owed - done].substring(1) + " more days are owed.")};

        // The last day: the wage is paid out of the store, what of it the store still holds.
        int paid = pay(open, chronicle, community, at);
        int wage = ((Number) open.get("wage_count")).intValue();
        standing(community, chronicle, KEPT, "OBLIGATION_COMPLETED", at);
        event(community, chronicle, at, "OBLIGATION_COMPLETED", paid);
        if (paid >= wage) {
            settle(open, "COMPLETED", at);
            return new String[]{"SUCCEEDED", day + "When it is done, they bring down " + wageWords(open) + " from the store and put them in your hands. The work is paid."};
        }
        jdbc.update("UPDATE native_agreement SET status='OWED', wage_paid=? WHERE id=?", paid, open.get("id"));
        return new String[]{"PARTIAL", day + "When it is done, they bring down " + (paid == 0 ? "nothing" : "what they have")
            + " and make you understand that the rest will come when the store holds it. They owe you, and they know it."};
    }

    /** Pay what the store holds of the wage still owed; returns the running total paid. */
    private int pay(Map<String, Object> agreement, UUID chronicle, UUID community, Instant at) {
        UUID store = store(community);
        int wage = ((Number) agreement.get("wage_count")).intValue();
        int already = ((Number) agreement.get("wage_paid")).intValue();
        if (store == null) return already;
        List<UUID> theirs = jdbc.queryForList("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "LEFT JOIN food_preservation_state f ON f.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "AND i.item_key=? AND f.spoiled_at IS NULL ORDER BY w.created_at LIMIT ?", UUID.class, store, agreement.get("wage_item_key"), wage - already);
        for (UUID id : theirs) {
            jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", chronicle, id);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'PAID_BY_COMMUNITY',jsonb_build_object('community',?::text))",
                id, Timestamp.from(at), community.toString());
        }
        jdbc.update("UPDATE native_agreement SET wage_paid=? WHERE id=?", already + theirs.size(), agreement.get("id"));
        return already + theirs.size();
    }

    /**
     * The daily reckoning (called by the community's daily step): work owed past its date is a broken promise, and a
     * wage still owed is paid out of the store as soon as the store holds it — but only while the one owed is on the
     * isle to take it, because a wage is put in someone's hands, not left on the landing.
     */
    void reckon(UUID community, Instant day) {
        for (Map<String, Object> late : jdbc.queryForList(
                "SELECT * FROM native_agreement WHERE community_id=? AND status='OPEN' AND due_at < ?", community, Timestamp.from(day)))
            breakPromise(late, day);
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        for (Map<String, Object> owed : jdbc.queryForList(
                "SELECT a.* FROM native_agreement a JOIN world_object w ON w.id=a.chronicle_id " +
                "WHERE a.community_id=? AND a.status='OWED' AND w.current_location_id=?", community, home)) {
            UUID chronicle = (UUID) owed.get("chronicle_id");
            int paid = pay(owed, chronicle, community, day);
            if (paid >= ((Number) owed.get("wage_count")).intValue()) {
                settle(owed, "COMPLETED", day);
                event(community, chronicle, day, "DEBT_SETTLED", paid);
            }
        }
    }

    private void breakPromise(Map<String, Object> agreement, Instant at) {
        UUID community = (UUID) agreement.get("community_id"), chronicle = (UUID) agreement.get("chronicle_id");
        settle(agreement, "BROKEN", at);
        standing(community, chronicle, BROKEN_PROMISE, "BROKEN_PROMISE", at);
        event(community, chronicle, at, "BROKEN_PROMISE", ((Number) agreement.get("days_done")).intValue());
    }

    private String owedToYou(UUID chronicle, UUID community) {
        Map<String, Object> owed = jdbc.queryForList("SELECT * FROM native_agreement WHERE community_id=? AND chronicle_id=? AND status='OWED'",
            community, chronicle).stream().findFirst().orElse(null);
        if (owed == null) return "There is no work agreed between you and them, and nothing owed either way.";
        int left = ((Number) owed.get("wage_count")).intValue() - ((Number) owed.get("wage_paid")).intValue();
        return "Your work for them is done. They still owe you " + WORDS[Math.min(left, 7)] + " of the " + wageWords(owed) + ".";
    }

    private Map<String, Object> openAgreement(UUID community, UUID chronicle) {
        return jdbc.queryForList("SELECT * FROM native_agreement WHERE community_id=? AND chronicle_id=? AND status='OPEN'",
            community, chronicle).stream().findFirst().orElse(null);
    }

    private void settle(Map<String, Object> agreement, String status, Instant at) {
        jdbc.update("UPDATE native_agreement SET status=?, settled_at=? WHERE id=?", status, Timestamp.from(at), agreement.get("id"));
    }

    private void standing(UUID community, UUID chronicle, int delta, String kind, Instant at) {
        jdbc.update("UPDATE community_relation SET standing=GREATEST(-100, LEAST(100, standing + ?)), last_event_kind=?, last_event_at=? " +
            "WHERE community_id=? AND chronicle_id=?", delta, kind, Timestamp.from(at), community, chronicle);
    }

    private void event(UUID community, UUID chronicle, Instant at, String kind, int days) {
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,jsonb_build_object('days', ?::int))",
            community, Timestamp.from(at), kind, chronicle, days);
    }

    /**
     * Does this people make this thing with its own hands (#113, V366)? Read from `made_goods` on the community
     * row rather than through NativeCommunityService, which already depends on this class — the same reason
     * {@code arguing} is a query here and not an injection.
     */
    private boolean makes(UUID community, Object itemKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT ?::varchar = ANY(made_goods) FROM native_community WHERE id=?", Boolean.class, itemKey, community));
    }

    /** Is this people in the middle of an argument of its own (#121)? Read here rather than injected, to keep
     *  AgreementService out of NativeCommunityService's constructor — the two already point at each other. */
    private boolean arguing(UUID community) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_disagreement WHERE community_id=? AND settled_at IS NULL)", Boolean.class, community));
    }

    private UUID store(UUID community) {
        return jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
    }

    private int countHeld(UUID store, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "LEFT JOIN food_preservation_state f ON f.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND f.spoiled_at IS NULL",
            Integer.class, store, key);
    }

    private String wageWords(Map<String, Object> agreement) {
        int n = ((Number) agreement.get("wage_count")).intValue();
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, agreement.get("wage_item_key"));
        return WORDS[Math.min(n, 7)] + " " + plural(name.toLowerCase(Locale.ROOT), n);
    }

    private static String plural(String name, int n) {
        if (n == 1 || name.endsWith("s") || name.endsWith("fish")) return name;
        return name + "s";
    }

    /** The kind a phrase names: an exact name first, then a thing named by its last word. As TradeService does. */
    private static Map<String, Object> named(String text, List<Map<String, Object>> kinds) {
        if (text == null) return null;
        for (Map<String, Object> k : kinds) {
            String key = ((String) k.get("item_key")).replace('_', ' ');
            String name = ((String) k.get("display_name")).toLowerCase(Locale.ROOT);
            if (text.contains(key) || text.contains(name)) return k;
        }
        for (Map<String, Object> k : kinds) {
            String[] parts = ((String) k.get("item_key")).split("_");
            String last = parts[parts.length - 1];
            if (last.length() >= 4 && (" " + text + " ").contains(" " + last)) return k;
        }
        return null;
    }
}
