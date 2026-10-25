package com.devosphere.draugr.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pure quality-grade logic (M3b): ordering, the flow rule, and the attempt read. */
class QualityGradeTest {

    @Test void worstIsTheLowerGrade() {
        assertEquals(QualityGrade.DEFECTIVE, QualityGrade.worst(QualityGrade.DEFECTIVE, QualityGrade.FINE));
        assertEquals(QualityGrade.POOR, QualityGrade.worst(QualityGrade.SOUND, QualityGrade.POOR));
        assertEquals(QualityGrade.FINE, QualityGrade.worst(QualityGrade.FINE, QualityGrade.FINE));
    }

    @Test void attemptReadsTheCareOfTheText() {
        // Careless work spoils it outright.
        assertEquals(QualityGrade.DEFECTIVE, QualityGrade.attempt("rush the sinew backing on"));
        assertEquals(QualityGrade.DEFECTIVE, QualityGrade.attempt("botch the whole thing"));
        // Careful and detailed earns fine work.
        assertEquals(QualityGrade.FINE, QualityGrade.attempt("carefully and evenly lay the backing along the whole limb"));
        // Careful but terse, or ordinary, is sound.
        assertEquals(QualityGrade.SOUND, QualityGrade.attempt("carefully back it"));
        assertEquals(QualityGrade.SOUND, QualityGrade.attempt("back the bow with sinew"));
    }

    /**
     * Whole words only (#77). Every phrase here is one the catalogue declares as a keyword, and every one of them
     * was graded DEFECTIVE while {@link QualityGrade#attempt} matched raw substrings: "rush" is inside b-RUSH,
     * c-RUSH and bul-RUSH, and is the plant in a rush light.
     */
    @Test void theCataloguesOwnWordsDoNotSpoilTheWorkTheyName() {
        for (String phrase : new String[]{"build a brush dome", "lay a brush path", "make a brush screen",
                "crush yarrow", "crush the salt", "peel the rushes", "dip the rushes", "twist bulrush", "rushlight",
                "rush light", "weave a rush mat"})
            assertEquals(QualityGrade.SOUND, QualityGrade.attempt(phrase), phrase);
    }

    @Test void carelessWordsStillSpoilIt() {
        for (String phrase : new String[]{"rush the brush dome", "build a brush dome hastily", "rushed it",
                "botched the joint", "slap it together", "a half-hearted go at the hut", "just get it done",
                "quickly lash a frame"})
            assertEquals(QualityGrade.DEFECTIVE, QualityGrade.attempt(phrase), phrase);
    }

    @Test void unevenlyIsNotEvenly() {
        assertEquals(QualityGrade.SOUND, QualityGrade.attempt("carve the handle unevenly, in a few strokes"));
        assertEquals(QualityGrade.FINE, QualityGrade.attempt("build the brush dome slowly, one bough at a time"));
    }

    @Test void ofDefaultsToSound() {
        assertEquals(QualityGrade.SOUND, QualityGrade.of(null));
        assertEquals(QualityGrade.SOUND, QualityGrade.of("nonsense"));
        assertEquals(QualityGrade.POOR, QualityGrade.of("POOR"));
    }
}
