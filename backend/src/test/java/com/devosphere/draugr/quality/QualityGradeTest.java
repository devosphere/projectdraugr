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

    @Test void ofDefaultsToSound() {
        assertEquals(QualityGrade.SOUND, QualityGrade.of(null));
        assertEquals(QualityGrade.SOUND, QualityGrade.of("nonsense"));
        assertEquals(QualityGrade.POOR, QualityGrade.of("POOR"));
    }
}
