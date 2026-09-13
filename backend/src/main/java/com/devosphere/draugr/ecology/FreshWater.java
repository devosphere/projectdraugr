package com.devosphere.draugr.ecology;

import java.util.List;
import java.util.Locale;

/**
 * What counts as fresh water on a piece of ground, written once (#156) — and what that water is called (#37).
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
 *
 * <p><b>Naming (#37).</b> The world places a fast stream, a slow river reach, a still pond, a lake margin, a beaver
 * pool and a headwater spring, and every line that spoke of water said "the standing water here" or "the water
 * here" over all of them alike — a Chronicle kneeling at a fast stream was told they drank standing water. What the
 * water is called is read from the same sites that make it water, so the name and the fact cannot disagree.
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

    /**
     * The fresh-water sites standing on one chunk ({@code ?} = chunk id), open water first: a stream, reach, pond or
     * lake is what a person drinks from or fishes, and a spring or a ford beside it is not the name they would use.
     * RESOURCE sites only — "River fishing run" is a wildlife site that happens to contain the word, not a water.
     */
    public static final String SITE_KINDS_ON_CHUNK =
        "SELECT site_kind FROM ecology_site WHERE chunk_id=? AND site_category='RESOURCE' AND " + sites() + " " +
        "GROUP BY site_kind ORDER BY MAX(CASE WHEN site_kind ILIKE '%spring%' OR site_kind ILIKE '%ford%' THEN 1 ELSE 0 END), site_kind";

    /**
     * What the water on this ground is called, with its article: "the still pond", "the river", "the water here".
     * Pure, so the naming can be tested without a world.
     *
     * @param siteKinds the rows of {@link #SITE_KINDS_ON_CHUNK}, in order
     * @param biome     the chunk's biome, for ground that is water with no named site on it
     */
    public static String name(List<String> siteKinds, String biome) {
        if (siteKinds != null && !siteKinds.isEmpty()) return "the " + siteKinds.get(0).toLowerCase(Locale.ROOT);
        if ("RIVER_BANK".equals(biome)) return "the river";
        if ("WETLAND".equals(biome)) return "the marsh water";
        return "the water here";
    }

    /** The same name opening a sentence: "The still pond". */
    public static String capitalised(String name) {
        return name == null || name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
