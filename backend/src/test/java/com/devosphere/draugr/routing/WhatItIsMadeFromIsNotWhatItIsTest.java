package com.devosphere.draugr.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A material named only as what the work is made FROM is not a request to work that material (#889).
 *
 * <p>"Whittle and lash a freestanding triangular coaster from a dry twig and cord" classified PROCESS and resolved to
 * twist_cordage: its keyword "cord" is also its subject, and the subject gate found it in the sentence. The Chronicle
 * asked for a coaster and was handed new cord twisted out of their fibre. With the category relaxed, the same words
 * would have reached assemble_lamellar_armor ("lash" with "cord") — leather armour, for a cup stand.
 *
 * <p>The candidates below are copied from the live catalogue (keywords trimmed to the ones that bear on these
 * sentences), so the test measures the real rule against the real data shape without a database.
 */
class WhatItIsMadeFromIsNotWhatItIsTest {

    private static final ProcessMatcher.Candidate CORDAGE = new ProcessMatcher.Candidate(
        "twist_cordage", "PROCESS", List.of("twist cordage", "make cord", "cordage", "cord"),
        List.of("bundle", "cordage", "fiber", "plant", "processed", "rope", "string", "twine", "braid", "cord"), "fiber_cordage");
    private static final ProcessMatcher.Candidate LAMELLAR = new ProcessMatcher.Candidate(
        "assemble_lamellar_armor", "CRAFT", List.of("assemble lamellar armor", "lamellar armour", "lash"),
        List.of("armor", "armour", "cord", "lamella", "leather", "panel", "lamellae"), "leather_armor");
    private static final ProcessMatcher.Candidate CHERT = new ProcessMatcher.Candidate(
        "knap_chert_flake", "PROCESS", List.of("chert flake", "knap chert", "strike a flake from the chert", "chert"),
        List.of("chert"), "chert_flake");
    private static final ProcessMatcher.Candidate CLAY = new ProcessMatcher.Candidate(
        "clean_surface_clay", "ACQUIRE", List.of("clean the surface clay", "pick grit from the clay"),
        List.of("clay", "surface clay"), "clean_clay");

    private static final List<ProcessMatcher.Candidate> ALL = List.of(CORDAGE, LAMELLAR, CHERT, CLAY);

    private static final String COASTER =
        "Whittle and lash a freestanding triangular coaster from a dry twig and cord, sized to steady a small drinking cup on uneven ground";

    @Test
    @DisplayName("the coaster sentence makes neither cord nor armour")
    void theCoasterIsNotCordage() {
        assertNull(ProcessMatcher.resolve(COASTER, "PROCESS", ALL).processKey(),
            "cord named as an input to a coaster is not a request to twist cord");
        assertNull(ProcessMatcher.resolveForPlay(COASTER, "PROCESS", ALL).processKey(),
            "nor may the all-category fallback answer it with lamellar armour for the 'lash' and the 'cord'");
    }

    @Test
    @DisplayName("the same material outside an input clause is still the work")
    void namedAsTheWorkItStillRoutes() {
        assertEquals("twist_cordage", ProcessMatcher.resolve("twist cordage from plant fiber", "PROCESS", ALL).processKey());
        assertEquals("twist_cordage", ProcessMatcher.resolve("make cord", "PROCESS", ALL).processKey());
        assertEquals("twist_cordage", ProcessMatcher.resolve("twist the fiber into cord", "PROCESS", ALL).processKey(),
            "'into' closes nothing that was opened; the cord is the result here");
        assertEquals("assemble_lamellar_armor",
            ProcessMatcher.resolve("assemble lamellar armor from leather panels", "CRAFT", ALL).processKey(),
            "the armour is named outside the clause, so the inputs after 'from' are its own");
    }

    @Test
    @DisplayName("subtractive work keeps its source: the catalogue's own phrase, or the product named")
    void theSourceOfSubtractiveWorkIsItsSubject() {
        assertEquals("knap_chert_flake", ProcessMatcher.resolve("strike a flake from the chert", "PROCESS", ALL).processKey(),
            "a declared keyword that spells out its own 'from' must reach its process");
        assertEquals("clean_surface_clay", ProcessMatcher.resolve("pick grit from the clay", "ACQUIRE", ALL).processKey());
        assertEquals("knap_chert_flake", ProcessMatcher.resolve("knap a flake from the chert", "PROCESS", ALL).processKey(),
            "the flake is what knapping chert makes, named outside the clause");
    }

    @Test
    @DisplayName("a sentence with no input clause is compared exactly as before")
    void noInputClauseIsUnchanged() {
        for (String s : List.of("split the log, then plank it", "I weave a basket.", "twist a bast bowstring",
                                "fire the pot (carefully)", "case-harden the breastplate"))
            assertEquals(ActivityClassifier.normalise(s), ProcessMatcher.outsideTheInputs(s), s);
    }

    @Test
    @DisplayName("the cut cannot weld a phrase neither half said")
    void theCutIsABoundary() {
        String kept = ProcessMatcher.outsideTheInputs("make a coaster from twig and cord sized to fit");
        assertEquals(" make a coaster | to fit ", kept);
        assertEquals(" make a coaster | ", ProcessMatcher.outsideTheInputs("make a coaster out of the antler"));
        assertEquals(" carve | the bone ", ProcessMatcher.outsideTheInputs("carve using a flint, the bone"),
            "punctuation ends the clause");
    }
}
