package com.devosphere.draugr.ecology;

/**
 * What counts as fresh water on a piece of ground, written once (#156).
 *
 * <p>Eight places asked this question and every one of them spelled the answer out again: a site whose kind reads
 * as a spring, a stream, a river or freshwater. Seven in the main code — collecting water, safe drinking water,
 * the process water gate, draft stock watering, and the perception of water in two places — and an eighth in a
 * test fixture that deletes water sites so it can assert dry ground.
 *
 * <p>That is the shape that has bitten this codebase repeatedly: a list duplicated until adding to the catalogue
 * updates some copies and not others. A pond added to the world would have been water to whichever call sites had
 * been remembered and dry to the rest, and the test that clears water would have failed to clear it.
 *
 * <p>The vocabulary is deliberately wider than the world currently uses. {@code pond} and {@code lake} match no
 * site the generator places today, so adding them changes nothing now — they are here so that the standing water
 * #156 still owes (pond, lake margin, floodplain) is water everywhere the moment it exists, rather than water in
 * whichever places somebody remembered to update.
 *
 * <p>Deliberately NOT included: {@code pool}. "Fen siren pool" is a monster lair, and a word that broad would
 * quietly turn one into a drinking source. {@code beaver pool} is named in full for exactly that reason — a
 * dammed pool is water to drink, and the siren's is not, and the difference between them is the whole phrase.
 */
public final class FreshWater {

    private FreshWater() { }

    private static final String[] KINDS = { "spring", "stream", "river", "freshwater", "pond", "lake", "beaver pool" };

    /**
     * A parenthesised SQL predicate over {@code ecology_site} rows.
     *
     * @param alias the table alias in scope, or "" when the columns are unqualified
     */
    public static String sites(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        StringBuilder sql = new StringBuilder("(");
        for (int i = 0; i < KINDS.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append(prefix).append("site_kind ILIKE '%").append(KINDS[i]).append("%'");
        }
        return sql.append(")").toString();
    }

    /** The unqualified form, which is what most callers want. */
    public static String sites() { return sites(""); }
}
