package com.devosphere.draugr.quality;

import java.util.Locale;

/**
 * An ordered quality grade (M3b). Worse grades sort first, so {@link #ordinal()}
 * gives a natural ranking and {@link #worst} is a simple minimum.
 *
 * <p>Grade is set when a thing is made and flows forward: an output is never better
 * than the worst of its inputs and the care of the attempt. That "care of the
 * attempt" is Layer 2 of the success model — how the action was described — read
 * here by {@link #attempt}.
 */
public enum QualityGrade {
    DEFECTIVE, POOR, SOUND, FINE;

    /** The lower (worse) of two grades — the rule by which quality flows to an output. */
    public static QualityGrade worst(QualityGrade a, QualityGrade b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /** One step better, capped at FINE — the minor, bounded quality assist a reachable workstation gives an
     *  attempt. It only nudges; {@code worst(materials, attempt)} still caps the result against the stock, so
     *  a bench never rescues poor work or lifts quality past what the hands and materials already allow. */
    public QualityGrade up() { QualityGrade[] v = values(); return v[Math.min(v.length - 1, ordinal() + 1)]; }

    /** Parse a stored grade, defaulting to SOUND for anything unrecognised or null. */
    public static QualityGrade of(String s) {
        if (s == null) return SOUND;
        try { return valueOf(s); } catch (IllegalArgumentException e) { return SOUND; }
    }

    /** Human phrase for narration. */
    public String label() {
        return switch (this) {
            case DEFECTIVE -> "defective";
            case POOR -> "poor";
            case SOUND -> "sound";
            case FINE -> "fine";
        };
    }

    /**
     * The grade an attempt earns from how it was described. A careless attempt spoils
     * the work outright; a careful, detailed one earns fine work; anything ordinary is
     * sound. This never lifts an output above its inputs — it is combined with the
     * input grade by {@link #worst}.
     *
     * <p>Whole words only (#77). This used to be a raw {@code contains}, and "rush" is inside b-RUSH, c-RUSH,
     * bul-RUSH and RUSH-es: the catalogue's own words spoiled the work they named. {@code build a brush dome}
     * finished defective, every {@code crush <herb>} poultice was defective, and {@code build a brush path} could
     * never be finished at all — its first stage came out flawed, the next refused flawed work, and reworking it
     * in the same words flawed it again. The same defect lifted "un-EVENLY" into fine work. And "rush" alone is
     * not even a careless word: it is also the plant (a rush light, a rush mat), so only the senses that mean
     * hurrying count.
     */
    public static QualityGrade attempt(String text) {
        String v = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (CARELESS.matcher(v).find()) return DEFECTIVE;
        int words = v.isBlank() ? 0 : v.trim().split("\\s+").length;
        if (CAREFUL.matcher(v).find()) return words >= 6 ? FINE : SOUND;
        return SOUND;
    }

    private static final java.util.regex.Pattern CARELESS = java.util.regex.Pattern.compile(
        "\\b(rushed|rushing|in a rush|rush it|rush the|rush this|rush through|hasty|hastily|quickly|careless|carelessly|sloppy|sloppily|botch|botched|botching"
      + "|slap together|slap it together|slapped together|any old|just get it done|half-hearted|half-heartedly)\\b");
    private static final java.util.regex.Pattern CAREFUL = java.util.regex.Pattern.compile(
        "\\b(careful|carefully|precise|precisely|evenly|slowly|patient|patiently|deliberate|deliberately"
      + "|meticulous|meticulously|take my time|take care)\\b");
}
