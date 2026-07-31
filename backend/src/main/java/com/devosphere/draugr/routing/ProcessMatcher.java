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

    public ProcessMatcher(JdbcTemplate jdbc, ActivityClassifier classifier) {
        this.jdbc = jdbc; this.classifier = classifier;
    }

    /**
     * Apply the rule. Pure — no database, no clock, no randomness.
     *
     * <p>The longest matching keyword wins, so "fire the pot" beats a bare "pot"
     * elsewhere; ties fall to the lexically first process key so that the same text
     * always resolves to the same process no matter what order the rows arrive in.
     *
     * @param category the classified category, or null to drop the category condition
     * @return the winning process key, or null when nothing agrees on all three counts
     */
    public static String match(String text, String category, List<Candidate> candidates) {
        String v = ActivityClassifier.normalise(text);
        String best = null; int bestLen = -1;
        for (Candidate c : candidates) {
            if (category != null && !category.equals(c.categoryKey())) continue;
            int len = -1;
            for (String kw : c.keywords())
                if (ActivityClassifier.containsTerm(v, kw) && kw.length() > len) len = kw.length();
            if (len < 0) continue;
            boolean subject = false;
            for (String s : c.subjects()) if (ActivityClassifier.containsTerm(v, s)) { subject = true; break; }
            if (!subject) continue;
            if (len > bestLen || (len == bestLen && c.processKey().compareTo(best) < 0)) {
                best = c.processKey(); bestLen = len;
            }
        }
        return best;
    }

    /** Apply the rule against the world's own processes. Null when none matches. */
    @Transactional(readOnly = true)
    public String match(String actionText) {
        return match(actionText, classifier.classify(actionText), candidates());
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
    public List<Candidate> candidates() {
        return jdbc.query(
            "SELECT mp.process_key, mp.category_key, mp.keywords, " +
            "       (SELECT string_agg(s.subject_term, ',') FROM process_subject s " +
            "        WHERE s.process_key = mp.process_key) AS subjects " +
            "FROM material_process mp WHERE mp.review_state = 'VERIFIED'",
            (rs, row) -> Candidate.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }
}
