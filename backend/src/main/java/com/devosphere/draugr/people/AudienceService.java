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
 * Who speaks for a people, and who does what (#121, epic #109).
 *
 * <p>A role is a person holding it, from a day to a day. The one who speaks for the isle authorises its trade and
 * its agreements; when they die the office is empty, and while it is empty and the isle mourns, nothing is decided
 * for outsiders. After the mourning the elders choose the eldest grown adult, and the office has a new holder with
 * its own tenure. Nothing is created to fill the gap — the speaker is someone who was already there.
 *
 * <p>The Chronicle learns roles the way a stranger does: by asking and being told, once they can be understood. A
 * title is never visible through omniscience; until someone tells them, they know faces, not offices.
 */
@Service
public class AudienceService {

    public enum Act { ASK_FOR_SPEAKER, OFFER_CONDOLENCE, ASK_ABOUT_ROLES }

    private static final Map<String, Act> PHRASES = Map.ofEntries(
        Map.entry("ask who speaks for", Act.ASK_FOR_SPEAKER), Map.entry("ask for their speaker", Act.ASK_FOR_SPEAKER),
        Map.entry("ask for the speaker", Act.ASK_FOR_SPEAKER), Map.entry("ask for their headsperson", Act.ASK_FOR_SPEAKER),
        Map.entry("ask for their leader", Act.ASK_FOR_SPEAKER), Map.entry("request an audience", Act.ASK_FOR_SPEAKER),
        Map.entry("ask to speak with their leader", Act.ASK_FOR_SPEAKER), Map.entry("who speaks for them", Act.ASK_FOR_SPEAKER),
        Map.entry("request an audience with their chief", Act.ASK_FOR_SPEAKER), Map.entry("ask who leads them", Act.ASK_FOR_SPEAKER),
        Map.entry("offer condolence", Act.OFFER_CONDOLENCE), Map.entry("offer condolences", Act.OFFER_CONDOLENCE),
        Map.entry("offer my condolences", Act.OFFER_CONDOLENCE), Map.entry("mourn with them", Act.OFFER_CONDOLENCE),
        Map.entry("grieve with them", Act.OFFER_CONDOLENCE), Map.entry("pay my respects", Act.OFFER_CONDOLENCE),
        Map.entry("ask about their roles", Act.ASK_ABOUT_ROLES), Map.entry("ask who does what", Act.ASK_ABOUT_ROLES),
        Map.entry("ask about their work", Act.ASK_ABOUT_ROLES), Map.entry("ask what each of them does", Act.ASK_ABOUT_ROLES),
        Map.entry("observe their roles", Act.ASK_ABOUT_ROLES));

    /** Days an isle mourns the one who spoke for it before the elders choose again. */
    static final int MOURNS_FOR_DAYS = 3;
    /** How long after a death condolence is still a kindness rather than an intrusion. */
    static final Duration CONDOLENCE_WINDOW = Duration.ofDays(14);

    private final JdbcTemplate jdbc;

    public AudienceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static Act recognise(String text) {
        if (text == null) return null;
        String v = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String best = null;
        for (String phrase : PHRASES.keySet())
            if (v.contains(" " + phrase + " ") && (best == null || phrase.length() > best.length())) best = phrase;
        return best == null ? null : PHRASES.get(best);
    }

    /** The living person who speaks for this community now, or null while the office is empty. */
    public static UUID speaker(JdbcTemplate jdbc, UUID community) {
        return jdbc.query("SELECT n.object_id FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.role='HEADSPERSON' AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);
    }

    /** The refusal every decision for outsiders meets while no one speaks for the isle, or null. */
    public static String withoutASpeaker(JdbcTemplate jdbc, UUID community) {
        return speaker(jdbc, community) != null ? null
            : "No one comes down to the landing to decide anything. The isle is mourning the one who spoke for it, and nothing is settled with strangers until another does.";
    }

    @Transactional
    public String[] act(UUID chronicle, UUID community, Act act, Instant at) {
        Map<String, Object> r = jdbc.queryForList(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?",
            community, chronicle).stream().findFirst().orElse(Map.of("standing", 0, "understanding", 0));
        int understanding = ((Number) r.get("understanding")).intValue();
        if (r.get("first_contact_at") == null)
            return new String[]{"PARTIAL", "No one answers. They do not yet know you well enough to be asked anything."};
        return switch (act) {
            case ASK_FOR_SPEAKER -> {
                if (understanding < 20) yield new String[]{"PARTIAL", "You try to ask, but the question does not reach them. They wait politely for you to go on."};
                UUID speaker = speaker(jdbc, community);
                if (speaker == null) {
                    event(community, chronicle, at, "ASKED_FOR_SPEAKER", "VACANT");
                    yield new String[]{"SUCCEEDED", "They shake their heads and look toward the fresh grave at the isle's edge. No one speaks for them now."};
                }
                String name = name(speaker);
                event(community, chronicle, at, "ASKED_FOR_SPEAKER", name);
                yield new String[]{"SUCCEEDED", "After a word passed between the houses, " + name + " comes down to the landing. It is " + name
                    + " who speaks for the isle, and who will hear what you have to ask of it."};
            }
            case OFFER_CONDOLENCE -> {
                Map<String, Object> death = jdbc.queryForList(
                    "SELECT id, occurred_at, payload->>'name' AS name FROM native_event WHERE community_id=? " +
                    "AND event_kind IN ('DIED_OF_AGE','DIED_OF_HUNGER','MURDER') AND occurred_at > ? ORDER BY occurred_at DESC LIMIT 1",
                    community, Timestamp.from(at.minus(CONDOLENCE_WINDOW))).stream().findFirst().orElse(null);
                if (death == null) yield new String[]{"PARTIAL", "They look at you, puzzled. No one here is mourning."};
                boolean killer = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind='MURDER')", Boolean.class, community, chronicle));
                if (killer) {
                    event(community, chronicle, at, "CONDOLENCE_REFUSED", "KILLER");
                    yield new String[]{"FAILED", "They turn their backs on you, every one of them. From you, of all people, they will not hear it."};
                }
                boolean already = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND subject_id=? AND event_kind='CONDOLENCE_RECEIVED' AND occurred_at >= ?)",
                    Boolean.class, community, chronicle, death.get("occurred_at")));
                if (already) yield new String[]{"PARTIAL", "They nod. You have said it already, and it was heard."};
                jdbc.update("UPDATE community_relation SET standing=LEAST(100, standing + 3), last_event_kind='CONDOLENCE_RECEIVED', last_event_at=? " +
                    "WHERE community_id=? AND chronicle_id=?", Timestamp.from(at), community, chronicle);
                event(community, chronicle, at, "CONDOLENCE_RECEIVED", (String) death.get("name"));
                yield new String[]{"SUCCEEDED", "You stand with them a while by the new grave" + (death.get("name") == null ? "" : " of " + death.get("name"))
                    + ", saying little. Someone touches your shoulder as you leave."};
            }
            case ASK_ABOUT_ROLES -> {
                if (understanding < 50)
                    yield new String[]{"PARTIAL", "You point from one face to another and mime the work you have seen. They smile, and say words you cannot yet follow."};
                List<String> told = new ArrayList<>();
                for (Map<String, Object> p : jdbc.queryForList(
                        "SELECT n.given_name, n.role, n.life_stage FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                        "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' " +
                        "ORDER BY CASE n.role WHEN 'HEADSPERSON' THEN 0 WHEN 'ELDER' THEN 1 ELSE 2 END, n.given_name", community))
                    told.add(p.get("given_name") + " " + work((String) p.get("role"), (String) p.get("life_stage")));
                event(community, chronicle, at, "ROLES_EXPLAINED", String.valueOf(told.size()));
                yield new String[]{"SUCCEEDED", "They tell you, patiently, pointing to each in turn: " + String.join("; ", told) + "."};
            }
        };
    }

    private static String work(String role, String stage) {
        String w = switch (role) {
            case "HEADSPERSON" -> "speaks for the isle";
            case "ELDER" -> "keeps its memory and is heard before anything is decided";
            case "FISHER" -> "fishes the channels and the weirs";
            case "FORAGER" -> "gathers from the marsh";
            case "MAKER" -> "makes mats, cordage, baskets and traps";
            case "HUNTER" -> "hunts";
            case "GUARD" -> "keeps watch";
            case "CHILD" -> "is a child of the isle";
            default -> "works";
        };
        return "ELDER".equals(stage) && !"ELDER".equals(role) && !"HEADSPERSON".equals(role) ? w + ", now old" : w;
    }

    /**
     * The office, day by day (called by the community's daily step): a speaker who has died leaves it empty, and after
     * the mourning the elders give it to the eldest grown adult among those at home. Each holder has a tenure with a
     * start and an end; the history records the loss and the choice.
     */
    void keepTheOffice(UUID community, Instant day) {
        // Open a tenure for a living speaker who has none (seeded isles, and anyone chosen before tenures existed).
        jdbc.update("INSERT INTO native_role_tenure (individual_id, community_id, role, began_at) " +
            "SELECT n.object_id, n.community_id, 'HEADSPERSON', ? FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.role='HEADSPERSON' AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM native_role_tenure t WHERE t.individual_id=n.object_id AND t.role='HEADSPERSON' AND t.ended_at IS NULL)",
            Timestamp.from(day), community);
        // A speaker who has died: the tenure ends, and the office is empty.
        for (Map<String, Object> ended : jdbc.queryForList(
                "SELECT t.id, n.given_name FROM native_role_tenure t JOIN native_individual n ON n.object_id=t.individual_id " +
                "WHERE t.community_id=? AND t.role='HEADSPERSON' AND t.ended_at IS NULL AND n.condition='DEAD'", community)) {
            jdbc.update("UPDATE native_role_tenure SET ended_at=?, end_reason='DIED' WHERE id=?", Timestamp.from(day), ended.get("id"));
            jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,'SPEAKER_LOST',jsonb_build_object('name',?::text))",
                community, Timestamp.from(day), ended.get("given_name"));
        }
        if (speaker(jdbc, community) != null) return;
        Timestamp lost = jdbc.queryForObject("SELECT MAX(occurred_at) FROM native_event WHERE community_id=? AND event_kind='SPEAKER_LOST'", Timestamp.class, community);
        if (lost == null || lost.toInstant().isAfter(day.minus(Duration.ofDays(MOURNS_FOR_DAYS)))) return;
        // The eldest grown adult at home, well enough to stand; never a child, never someone away with a Chronicle.
        Map<String, Object> chosen = jdbc.queryForList(
            "SELECT n.object_id, n.given_name, n.role FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.life_stage='ADULT' AND n.condition IN ('WELL','HUNGRY') AND w.lifecycle_state='ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM native_companionship c WHERE c.individual_id=n.object_id AND c.ended_at IS NULL) " +
            "ORDER BY n.born_on NULLS LAST, n.given_name LIMIT 1", community).stream().findFirst().orElse(null);
        if (chosen == null) return;   // no one to choose: the office stays empty, and nothing is invented to fill it
        jdbc.update("UPDATE native_individual SET role='HEADSPERSON' WHERE object_id=?", chosen.get("object_id"));
        jdbc.update("INSERT INTO native_role_tenure (individual_id, community_id, role, began_at) VALUES (?,?,'HEADSPERSON',?)",
            chosen.get("object_id"), community, Timestamp.from(day));
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,'SUCCESSION',jsonb_build_object('name',?::text,'was',?::text))",
            community, Timestamp.from(day), chosen.get("given_name"), chosen.get("role"));
    }

    private String name(UUID person) {
        return jdbc.queryForObject("SELECT given_name FROM native_individual WHERE object_id=?", String.class, person);
    }

    private void event(UUID community, UUID chronicle, Instant at, String kind, String detail) {
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,?,?,jsonb_build_object('detail',?::text))",
            community, Timestamp.from(at), kind, chronicle, detail);
    }
}
