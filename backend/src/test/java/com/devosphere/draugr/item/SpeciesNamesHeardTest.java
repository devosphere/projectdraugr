package com.devosphere.draugr.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SAY THE PLURAL AND BE UNDERSTOOD (#37/#99).
 *
 * <p>Every matcher of species names in this project tried the key and the key with an {@code s} on the end,
 * which covers 200 of the 215 species in the registry and silently none of the other 15. Measured live on a
 * keeper with a herd of oxen: {@code cull the herd} worked and {@code cull one of the oxen} reached nothing at
 * all — and the keeper who says <i>oxen</i> is the keeper who has more than one, which is exactly who asks to
 * cull.
 *
 * <p>Every species key named below is a real row of {@code wildlife_species}, read off a live world. These are
 * assertions about English, so they need no database and they run everywhere, which is the point: the
 * integration tests that would cover this skip locally.
 */
class SpeciesNamesHeardTest {

    /** The fifteen the {@code +"s"} rule could never hear, by their own keys. */
    @Test
    void theIrregularPluralsTheRegistryActuallyHolds() {
        assertEquals("oxen", PhysicalItemService.pluralOf("ox"));
        assertEquals("musk oxen", PhysicalItemService.pluralOf("musk ox"));
        assertEquals("geese", PhysicalItemService.pluralOf("goose"));
        assertEquals("greylag geese", PhysicalItemService.pluralOf("greylag goose"));
        assertEquals("mice", PhysicalItemService.pluralOf("mouse"));
        assertEquals("field mice", PhysicalItemService.pluralOf("field mouse"));
        assertEquals("dormice", PhysicalItemService.pluralOf("dormouse"));
        assertEquals("wolves", PhysicalItemService.pluralOf("wolf"));
        assertEquals("gray wolves", PhysicalItemService.pluralOf("gray wolf"));
        // Unchanged in the plural, which is its own trap: "two sheeps" is how a matcher gives itself away.
        assertEquals("sheep", PhysicalItemService.pluralOf("sheep"));
        assertEquals("bighorn sheep", PhysicalItemService.pluralOf("bighorn sheep"));
        assertEquals("red deer", PhysicalItemService.pluralOf("red deer"));
        assertEquals("river trout", PhysicalItemService.pluralOf("river trout"));
    }

    /**
     * AND THE ORDINARY ONES STILL TAKE AN S, including the ones an {@code s} cannot simply follow.
     *
     * <p>{@code fox} is why the ox rule tests a word boundary instead of a suffix. A red fox is not a red
     * "foxen", and {@code red_fox} is a real species key sharing ground with the ox in the registry.
     */
    @Test
    void theOrdinaryPluralsAreUntouched() {
        assertEquals("goats", PhysicalItemService.pluralOf("goat"));
        assertEquals("mountain goats", PhysicalItemService.pluralOf("mountain goat"));
        assertEquals("foxes", PhysicalItemService.pluralOf("fox"));
        assertEquals("red foxes", PhysicalItemService.pluralOf("red fox"));
        assertEquals("thrushes", PhysicalItemService.pluralOf("thrush"));
        assertEquals("horses", PhysicalItemService.pluralOf("horse"));
    }

    /**
     * What the sentence has to carry for the name to count: the whole name, or its head noun, either number.
     *
     * <p>Whole words with the spaces kept, because the substring has cost this project four separate defects —
     * {@code ox} inside {@code oxbow}, {@code cabin}, {@code leggings}, {@code fox}.
     */
    @Test
    void aNameIsHeardInFullByItsHeadNounAndInEitherNumber() {
        assertTrue(PhysicalItemService.saidAsASpeciesName("kill the ox", "ox"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("cull one of the oxen", "ox"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("slaughter the musk ox", "musk_ox"));
        // By the head noun, which is how a keeper actually speaks of their own.
        assertTrue(PhysicalItemService.saidAsASpeciesName("kill the goat", "mountain_goat"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("feed the goats", "mountain_goat"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("milk the geese", "greylag_goose"));

        // AND NOT INSIDE ANOTHER WORD, which is the half that matters.
        assertFalse(PhysicalItemService.saidAsASpeciesName("cross at the oxbow", "ox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("check the leggings", "egg_layer"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("kill the ox", "red_fox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("kill the fox", "ox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("", "ox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName(null, "ox"));
    }
}
