package com.devosphere.draugr.action;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * The two soft layers of the action success model, distinct from the hard
 * physical gate (materials, tools, environment) which callers check first.
 *
 * <p>Layer 2 — text specificity: how precisely the player described a
 * procedurally complex act. Someone who names the materials, the technique,
 * and the sequence has described a competent attempt; "light the fire" has not.
 *
 * <p>Layer 3 — accumulated familiarity: supplied by the caller from
 * {@code chronicle_capability_adaptation}. A practiced chronicle succeeds even
 * on a terse command because the body knows the work.
 *
 * <p>The roll is seeded by the action id so a given resolved action always has
 * the same outcome — it never re-rolls differently on idempotent replay.
 */
final class SuccessModel {
    private SuccessModel() {}

    /**
     * Fraction (0..1) of the given signal groups the action text satisfies.
     * Each group is a set of synonyms; a group counts once if any synonym
     * appears. More groups matched = a more precisely described attempt.
     */
    static double specificity(String text, List<String[]> signalGroups) {
        if (signalGroups.isEmpty()) return 0.0;
        String v = text.toLowerCase(Locale.ROOT);
        int hit = 0;
        for (String[] group : signalGroups) {
            for (String s : group) { if (v.contains(s)) { hit++; break; } }
        }
        return (double) hit / signalGroups.size();
    }

    /** Deterministic Bernoulli trial: true with the given probability, seeded by the action id. */
    static boolean roll(double probability, UUID seed) {
        double p = Math.max(0.0, Math.min(1.0, probability));
        if (p >= 1.0) return true;
        if (p <= 0.0) return false;
        long s = seed.getMostSignificantBits() ^ seed.getLeastSignificantBits();
        return new Random(s).nextDouble() < p;
    }
}
