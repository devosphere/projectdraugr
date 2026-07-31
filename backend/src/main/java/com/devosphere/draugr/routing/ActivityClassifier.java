package com.devosphere.draugr.routing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decides what kind of activity an action text describes, so a process can only be
 * matched by something that is actually talking about the same kind of work.
 *
 * <p>Matching used to be lexical and global: any process whose keyword appeared
 * anywhere in the text was a candidate. That produced eight recorded collisions —
 * "weatherproof the shell" reaching the weather domain, "split the fish" reaching
 * {@code split_planks}. A wrong match does not throw. It silently suppresses the
 * Architect call the moment needed and hands the chronicle a confidently wrong
 * result, which is worse than an honest gap.
 *
 * <p>Category is one of the two axes V54 introduced; the other is subject material,
 * enforced where processes are resolved. Category alone separates only five of the
 * eight collisions, because "split the fish" and "split the log" are both PROCESS.
 *
 * <p>The vocabulary lives in {@code category_term} and changes only by migration, so
 * it is loaded once and cached. Classification itself is a pure function of text and
 * vocabulary — {@link #classify(String, List, Map)} — which keeps the rule testable
 * without a database and keeps it honest: the same text always yields the same
 * category.
 */
@Component
public class ActivityClassifier {

    /** One vocabulary entry: a term that signals a category, and how strongly. */
    public record Term(String categoryKey, String term, int weight) { }

    private final JdbcTemplate jdbc;
    private volatile List<Term> vocabulary;
    private volatile Map<String, Integer> precedence;

    public ActivityClassifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Normalise for whole-word comparison: lowercase, every non-alphanumeric run
     * collapsed to a single space, and the whole thing space-padded.
     *
     * <p>The padding is what makes " ore " fail against "forest" and " ash " fail
     * against "flashing". Punctuation becomes space so that "log," does not weld into
     * a token nothing can match.
     */
    public static String normalise(String text) {
        if (text == null) return " ";
        return " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    /** Whether a term appears as a whole word (or whole phrase) in already-normalised text. */
    public static boolean containsTerm(String normalised, String term) {
        return normalised.contains(" " + term + " ");
    }

    /**
     * The rule, in full and without a database.
     *
     * <p>Sum the weight of every vocabulary term present in the text, per category.
     * Highest total wins; {@code precedence} breaks ties, lower first.
     *
     * @return the winning category key, or {@code null} when no term matches at all.
     *     Null means "the vocabulary does not recognise this", not "this belongs to no
     *     category" — callers must drop the category condition rather than matching
     *     nothing, or an unknown verb would spend an Architect call on work the
     *     foundation already covers.
     */
    public static String classify(String text, List<Term> vocabulary, Map<String, Integer> precedence) {
        String v = normalise(text);
        if (v.isBlank()) return null;
        Map<String, Integer> score = new HashMap<>();
        for (Term t : vocabulary)
            if (containsTerm(v, t.term()))
                score.merge(t.categoryKey(), t.weight(), Integer::sum);
        if (score.isEmpty()) return null;
        String best = null; int bestScore = Integer.MIN_VALUE; int bestPrec = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : score.entrySet()) {
            int prec = precedence.getOrDefault(e.getKey(), Integer.MAX_VALUE);
            if (e.getValue() > bestScore || (e.getValue() == bestScore && prec < bestPrec)) {
                best = e.getKey(); bestScore = e.getValue(); bestPrec = prec;
            }
        }
        return best;
    }

    /** Classify against the world's own vocabulary. Null when nothing is recognised. */
    @Transactional(readOnly = true)
    public String classify(String text) {
        return classify(text, vocabulary(), precedence());
    }

    private List<Term> vocabulary() {
        List<Term> v = vocabulary;
        if (v == null) synchronized (this) {
            v = vocabulary;
            if (v == null) {
                v = List.copyOf(jdbc.query(
                    "SELECT category_key, term, weight FROM category_term",
                    (rs, row) -> new Term(rs.getString(1), rs.getString(2), rs.getInt(3))));
                vocabulary = v;
            }
        }
        return v;
    }

    private Map<String, Integer> precedence() {
        Map<String, Integer> p = precedence;
        if (p == null) synchronized (this) {
            p = precedence;
            if (p == null) {
                Map<String, Integer> loaded = new HashMap<>();
                jdbc.query("SELECT category_key, precedence FROM activity_category",
                    rs -> { loaded.put(rs.getString(1), rs.getInt(2)); });
                p = Map.copyOf(loaded);
                precedence = p;
            }
        }
        return p;
    }

    /** Terms carried by more than one category, which is legitimate but worth watching. */
    @Transactional(readOnly = true)
    public List<String> overloadedTerms(int threshold) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Term t : vocabulary()) counts.merge(t.term(), 1, Integer::sum);
        counts.forEach((term, n) -> { if (n > threshold) out.add(term + " (" + n + " categories)"); });
        return List.copyOf(out);
    }
}
