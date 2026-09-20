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
 * A place among them (#113, epic #109): joining a people, and what it costs both sides.
 *
 * <p>Membership is not a title. Asked for on the isle, granted only by a people who know the Chronicle well, trust
 * them deeply, and have food enough to feed another mouth, it changes real things:
 * <ul>
 *   <li><b>They feed you.</b> A member on the isle is counted among the isle's eaters, so the store must carry them
 *       — the same store that can run out. Joining a hungry people is refused; a people that goes hungry with a
 *       member in the house feels it a day sooner.</li>
 *   <li><b>You may take your share.</b> One day's food a day from the store, openly, and it is not theft.</li>
 *   <li><b>You are not a stranger.</b> Coming onto the isle is no longer trespass.</li>
 *   <li><b>It can end.</b> You can give up your place, and they can ask you to go: a member whose standing falls
 *       below what it took to join is asked to leave, on the isle's own daily step.</li>
 * </ul>
 */
@Service
public class MembershipService {

    public enum Act { ASK_TO_JOIN, TAKE_SHARE, LEAVE }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("ask to join them", Act.ASK_TO_JOIN), Map.entry("ask to join the isle", Act.ASK_TO_JOIN),
        Map.entry("ask to join their settlement", Act.ASK_TO_JOIN), Map.entry("ask to join the settlement", Act.ASK_TO_JOIN),
        Map.entry("ask to live with them", Act.ASK_TO_JOIN), Map.entry("ask to stay with them", Act.ASK_TO_JOIN),
        Map.entry("ask for a place among them", Act.ASK_TO_JOIN), Map.entry("ask to join their people", Act.ASK_TO_JOIN),
        Map.entry("take my share", Act.TAKE_SHARE), Map.entry("take my share of the food", Act.TAKE_SHARE),
        Map.entry("draw my share", Act.TAKE_SHARE), Map.entry("eat with them", Act.TAKE_SHARE),
        Map.entry("ask to leave the settlement", Act.LEAVE), Map.entry("leave their settlement", Act.LEAVE),
        Map.entry("give up my place", Act.LEAVE), Map.entry("leave their people", Act.LEAVE));

    /** What it takes to be given a place: they must know you, trust you, and have known you a while. */
    static final int TRUST_TO_JOIN = 60, UNDERSTANDING_TO_JOIN = 70, KNOWN_FOR_DAYS = 20;
    /** Standing below which a member is asked to leave: what it took to join, halved. */
    static final int ASKED_TO_LEAVE_BELOW = 30;

    private final JdbcTemplate jdbc;

    public MembershipService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** Whether this Chronicle holds a place with this community now. */
    public boolean belongs(UUID community, UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_membership WHERE community_id=? AND chronicle_id=? AND left_at IS NULL)",
            Boolean.class, community, chronicle));
    }

    @Transactional
    public String[] act(UUID chronicle, UUID chunk, UUID community, Act act, Instant at) {
        boolean member = belongs(community, chronicle);
        return switch (act) {
            case ASK_TO_JOIN -> member
                ? new String[]{"PARTIAL", "You have a place here already."}
                : join(chronicle, chunk, community, at);
            case TAKE_SHARE -> share(chronicle, chunk, community, member, at);
            case LEAVE -> {
                if (!member) yield new String[]{"FAILED", "You have no place here to give up."};
                end(community, chronicle, at, "LEFT");
                yield new String[]{"SUCCEEDED", "You tell them you are going. No one argues. The elder says something short that the others "
                    + "repeat, and then the isle goes back to its work, and you are a visitor again."};
            }
        };
    }

    private String[] join(UUID chronicle, UUID chunk, UUID community, Instant at) {
        Map<String, Object> c = jdbc.queryForMap("SELECT home_chunk_id, lifecycle, shortage_days, name FROM native_community WHERE id=?", community);
        Map<String, Object> r = jdbc.queryForList(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle).stream().findFirst().orElse(Map.of("standing", 0, "understanding", 0));
        int standing = ((Number) r.get("standing")).intValue(), understanding = ((Number) r.get("understanding")).intValue();
        Timestamp met = (Timestamp) r.get("first_contact_at");
        UUID store = store(community);
        int kept = store == null ? 0 : jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id LEFT JOIN food_preservation_state f ON f.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND f.spoiled_at IS NULL AND i.item_key=" +
            "(SELECT staple_item_key FROM native_community WHERE id=?)", Integer.class, store, community);
        String refusal = !chunk.equals(c.get("home_chunk_id")) ? "This is a thing to ask on the isle, standing among them."
            : met == null ? "They have never spoken with you. There is nothing to ask yet."
            : AudienceService.withoutASpeaker(jdbc, community);
        if (refusal == null)
            refusal = understanding < UNDERSTANDING_TO_JOIN ? "You ask as well as you can. They understand that you want something, and not what."
                : Duration.between(met.toInstant(), at).toDays() < KNOWN_FOR_DAYS ? "They hear you out, and the elder shakes their head slowly. They have not known you long enough for this."
                : standing < TRUST_TO_JOIN ? "The elder listens, looks at the others, and says no. You are welcome on the isle. That is not the same thing."
                : !"SETTLED".equals(c.get("lifecycle")) || ((Number) c.get("shortage_days")).intValue() > 0 || kept < 8
                    ? "They look at the store house, and then at you, and the answer is in their faces: there is not food enough here for another mouth."
                : null;
        if (refusal != null) { event(community, chronicle, at, "PLACE_REFUSED"); return new String[]{"PARTIAL", refusal}; }

        jdbc.update("INSERT INTO native_membership (community_id, chronicle_id, joined_at) VALUES (?,?,?)", community, chronicle, Timestamp.from(at));
        event(community, chronicle, at, "JOINED");
        return new String[]{"SUCCEEDED", "The elders talk it over while you wait at the landing, and then the one who speaks for the isle comes down, "
            + "takes your hands and says the same word to you three times until you say it back. You have a place among the " + c.get("name")
            + " kin: their food is yours while there is food, and their work is yours while there is work."};
    }

    private String[] share(UUID chronicle, UUID chunk, UUID community, boolean member, Instant at) {
        if (!member) return new String[]{"FAILED", "You have no share here. What is in their store is theirs."};
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        if (!chunk.equals(home)) return new String[]{"FAILED", "Your share is on the isle, in the store house."};
        boolean already = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind='TOOK_SHARE' AND occurred_at > ?)",
            Boolean.class, community, chronicle, Timestamp.from(at.minus(Duration.ofHours(20)))));
        if (already) return new String[]{"PARTIAL", "You have had your share today. The store keeper closes the lid, not unkindly."};
        UUID store = store(community);
        String staple = jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, community);
        List<UUID> share = store == null ? List.of() : jdbc.queryForList(
            "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id LEFT JOIN food_preservation_state f ON f.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND f.spoiled_at IS NULL ORDER BY f.safe_until NULLS LAST LIMIT 1",
            UUID.class, store, staple);
        if (share.isEmpty()) return new String[]{"PARTIAL", "The store house is bare. Whatever your share is worth today, it is nothing."};
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL, updated_at=now() WHERE id=?", chronicle, share.get(0));
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'SHARE_OF_THE_STORE',jsonb_build_object('community',?::text))",
            share.get(0), Timestamp.from(at), community.toString());
        event(community, chronicle, at, "TOOK_SHARE");
        return new String[]{"SUCCEEDED", "You take your day's share from the store, in front of whoever is there, and no one thinks anything of it."};
    }

    /**
     * The isle's daily reckoning of who belongs to it: a member whose standing has fallen below what it took to be
     * given a place is asked to go. Nothing is taken back — what they were given, they were given.
     */
    void keepOrAskToLeave(UUID community, Instant day) {
        for (Map<String, Object> m : jdbc.queryForList(
                "SELECT m.chronicle_id FROM native_membership m JOIN community_relation r ON r.community_id=m.community_id AND r.chronicle_id=m.chronicle_id " +
                "WHERE m.community_id=? AND m.left_at IS NULL AND r.standing < ?", community, ASKED_TO_LEAVE_BELOW))
            end(community, (UUID) m.get("chronicle_id"), day, "ASKED_TO_LEAVE");
    }

    /** How many members are on the isle today, and so eating from its store. */
    int mouthsAtHome(UUID community) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM native_membership m JOIN chronicle c ON c.id=m.chronicle_id JOIN world_object w ON w.id=c.id " +
            "WHERE m.community_id=? AND m.left_at IS NULL AND c.life_state='LIVING' " +
            "AND w.current_location_id = (SELECT home_chunk_id FROM native_community WHERE id=m.community_id)", Integer.class, community);
    }

    private void end(UUID community, UUID chronicle, Instant at, String reason) {
        jdbc.update("UPDATE native_membership SET left_at=?, end_reason=? WHERE community_id=? AND chronicle_id=? AND left_at IS NULL",
            Timestamp.from(at), reason, community, chronicle);
        event(community, chronicle, at, reason);
    }

    private UUID store(UUID community) {
        return jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
    }

    private void event(UUID community, UUID chronicle, Instant at, String kind) {
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id) VALUES (?,?,?,?)",
            community, Timestamp.from(at), kind, chronicle);
    }
}
