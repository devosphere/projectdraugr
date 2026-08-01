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
     */
    public static QualityGrade attempt(String text) {
        String v = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String careless : CARELESS) if (v.contains(careless)) return DEFECTIVE;
        int words = v.isBlank() ? 0 : v.trim().split("\\s+").length;
        for (String care : CAREFUL) if (v.contains(care)) return words >= 6 ? FINE : SOUND;
        return SOUND;
    }

    private static final String[] CARELESS = {
        "rush", "hasty", "hastily", "quickly", "careless", "sloppy", "sloppily",
        "botch", "slap together", "any old", "just get it done", "half-heart"
    };
    private static final String[] CAREFUL = {
        "careful", "precise", "precisely", "evenly", "slowly", "patient", "patiently",
        "deliberate", "meticulous", "take my time", "take care"
    };
}
