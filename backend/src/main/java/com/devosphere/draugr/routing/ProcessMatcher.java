package com.devosphere.draugr.routing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which material process, if any, an action text is actually asking for.
 *
 * <p>This is the whole of V54's resolution rule and the only implementation of it.
 * Both the runtime ({@code PhysicalItemService.runProcess}) and the coverage check
 * ({@code ArchitectRouter}) call through here, because a process that would not run
 * for an action must not be reported as covering it either — otherwise the gap goes
 * both unreported and unresolved.
 *
 * <p>The rule, restated from the foot of V54:
 * <ol>
 *   <li>Classify the text into an activity category ({@link ActivityClassifier}).</li>
 *   <li>A process matches only when <em>all three</em> agree: its category equals the
 *       classified category; one of its keywords appears whole-word in the text; one
 *       of its subject terms appears whole-word in the text.</li>
 *   <li>A null classification drops the category condition rather than matching
 *       nothing — not recognising a verb is ignorance of the vocabulary, not evidence
 *       the action belongs to no category.</li>
 * </ol>
 *
 * <p>Two axes are needed, not one. Category alone separates five of the eight recorded
 * collisions; "split the fish" and "split the log" are both PROCESS, and only the
 * subject tells them apart. Subject terms are derived in the migration from each
 * process's own inputs and outputs, so a recipe cannot drift out of agreement with
 * itself.
 *
 * <p>Matching lives in a pure static function over supplied candidates rather than in
 * SQL so there is exactly one place the rule is written, and so it can be tested
 * against the full collision fixture without a database.
 */
@Component
public class ProcessMatcher {

    /** A process reduced to what matching needs. */
    public record Candidate(String processKey, String categoryKey, List<String> keywords, List<String> subjects) {

        /** Build from the comma-separated forms the tables store. */
        public static Candidate of(String processKey, String categoryKey, String keywordCsv, String subjectCsv) {
            return new Candidate(processKey, categoryKey, split(keywordCsv), split(subjectCsv));
        }

        private static List<String> split(String csv) {
            if (csv == null || csv.isBlank()) return List.of();
            List<String> out = new ArrayList<>();
            for (String s : csv.split(",")) { String t = s.trim(); if (!t.isEmpty()) out.add(t); }
            return List.copyOf(out);
        }
    }

    private final JdbcTemplate jdbc;
    private final ActivityClassifier classifier;
    private final RoutingMissRecorder misses;

    public ProcessMatcher(JdbcTemplate jdbc, ActivityClassifier classifier, RoutingMissRecorder misses) {
        this.jdbc = jdbc; this.classifier = classifier; this.misses = misses;
    }

    /**
     * The outcome of applying the rule: what matched, or how far the nearest candidate
     * got before a gate stopped it.
     *
     * <p>The near-miss is not decoration. When nothing resolves, the only question
     * worth asking is whether the WORDS are missing or the MECHANIC is missing, and
     * those have completely different fixes — a category term versus a whole process
     * definition. {@code furthestGate} answers it: NONE means no process in that
     * category exists at all, CATEGORY means one exists but does not know this
     * phrasing, KEYWORD means the right process was reached and rejected the material.
     *
     * @param processKey the winning process, or null when nothing matched
     * @param furthestGate NONE, CATEGORY or KEYWORD — the last gate a candidate passed
     * @param nearProcessKey the candidate that got furthest, or null when none did
     */
    public record Result(String processKey, String furthestGate, String nearProcessKey) {
        boolean matched() { return processKey != null; }
    }

    /**
     * Apply the rule. Pure — no database, no clock, no randomness.
     *
     * <p>The longest matching keyword wins, so "fire the pot" beats a bare "pot"
     * elsewhere; ties fall to the lexically first process key so that the same text
     * always resolves to the same process no matter what order the rows arrive in.
     *
     * @param category the classified category, or null to drop the category condition
     */
    public static Result resolve(String text, String category, List<Candidate> candidates) {
        String v = ActivityClassifier.normalise(text);
        String best = null; int bestLen = -1;
        String nearKey = null; String gate = "NONE";
        for (Candidate c : candidates) {
            if (category != null && !category.equals(c.categoryKey())) continue;
            int len = -1;
            for (String kw : c.keywords())
                if (ActivityClassifier.containsTerm(v, kw) && kw.length() > len) len = kw.length();
            if (len < 0) {
                // Shares the category but answers to none of these words.
                if ("NONE".equals(gate)) { gate = "CATEGORY"; nearKey = c.processKey(); }
                continue;
            }
            boolean subject = false;
            for (String s : c.subjects()) if (ActivityClassifier.containsSubject(v, s)) { subject = true; break; }
            if (!subject) {
                // Right work, right verb, wrong material — the closest kind of miss.
                if (!"KEYWORD".equals(gate)) { gate = "KEYWORD"; nearKey = c.processKey(); }
                continue;
            }
            if (len > bestLen || (len == bestLen && c.processKey().compareTo(best) < 0)) {
                best = c.processKey(); bestLen = len;
            }
        }
        return new Result(best, gate, nearKey);
    }

    /** The winning process key alone, for callers that do not care why it missed. */
    public static String match(String text, String category, List<Candidate> candidates) {
        return resolve(text, category, candidates).processKey();
    }

    /**
     * Apply the rule against the world's own processes, without side effects. Null when
     * none matches.
     *
     * <p>Used by {@code ArchitectRouter}, which is read-only by contract: assessing
     * whether a gap exists is not the same as a player running into one.
     */
    @Transactional(readOnly = true)
    public String match(String actionText) {
        return resolve(actionText, classifier.classify(actionText), candidates()).processKey();
    }

    /** The activity category this text classifies to (PROCESS/ACQUIRE/CRAFT/…), or null if the vocabulary
     *  recognises no verb in it. Lets a caller tell "material work the world can't yet do" from true gibberish. */
    @Transactional(readOnly = true)
    public String activityCategory(String actionText) {
        return classifier.classify(actionText);
    }

    /**
     * Apply the rule on the play path, recording a miss when nothing resolves.
     *
     * <p>This is the only place misses are counted, and it is the right one: it means
     * a player actually tried to do material work and the world could not. An action
     * merely being assessed for routing is not evidence of anything.
     */
    public String matchAndRecord(String actionText, java.util.UUID chronicle) {
        String category = classifier.classify(actionText);
        Result r = resolve(actionText, category, candidates(chronicle));
        if (!r.matched()) misses.record(actionText, category, r);
        return r.processKey();
    }


    /**
     * Only reviewed processes are candidates (V53). A definition the Auditor has
     * flagged is held out of play entirely rather than allowed to write a suspect
     * result into a chronicle's permanent record, where it would become history before
     * anyone noticed.
     *
     * <p>Read fresh rather than cached: review state is the one field here that can
     * change without a migration, and serving a stale one would defeat the gate.
     */
    @Transactional(readOnly = true)
    public List<Candidate> candidates() { return candidates(null); }

    /**
     * Verified processes visible to a chronicle: canonical (owner NULL — everyone's) PLUS that
     * chronicle's own runtime-discovered ones (DR-0021). A null chronicle yields canonical only, for
     * read-only callers with no chronicle in hand. Existing play is unchanged — every canonical row has
     * a NULL owner, so with no scoped rows the result is identical to before.
     */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(java.util.UUID chronicle) {
        return jdbc.query(
            "SELECT mp.process_key, mp.category_key, mp.keywords, " +
            "       (SELECT string_agg(s.subject_term, ',') FROM process_subject s " +
            "        WHERE s.process_key = mp.process_key) AS subjects " +
            "FROM material_process mp WHERE mp.review_state = 'VERIFIED' " +
            "  AND (mp.discovered_by_chronicle_id IS NULL OR mp.discovered_by_chronicle_id = ?)",
            (rs, row) -> Candidate.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), chronicle);
    }
}
