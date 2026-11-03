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

    /**
     * THE KEEPER'S TWO REPORTS NOW ASK THIS, AND THEY DID NOT (#37/#99).
     *
     * <p>{@code stockWelfare} and {@code breedingProspects} each rolled their own name matching — a substring
     * for the full name, a bare {@code s} for the plural, and in the breeding report no head-noun arm at all.
     * These are the sentences those two reports actually receive, held against the one reader, over the 23
     * species a Chronicle can keep. What the old inline rules did with them:
     *
     * <pre>
     * look over the flock by the oxbow   narrowed to the OXEN         b-OX, found inside another word
     * check the stock near the fox run   narrowed to the OXEN         f-OX, the same
     * how are the geese                  matched NOTHING              "gooses" is not a word
     * how are the oxen                   found ox but not MUSK ox     the head arm tried "ox" and "oxs"
     * are the goats in kid               welfare narrowed, breeding answered about the whole flock
     * is the sheep in lamb               welfare narrowed, breeding answered about the whole flock
     * </pre>
     *
     * <p>So a keeper of several kinds, asking after the flock beside an oxbow, was answered about one of them —
     * and the two reports disagreed about what had just been said.
     */
    @Test
    void theSentencesTheKeepersReportsReceiveAreHeardTheSameWay() {
        // The irregular plural of a bird a Chronicle can actually keep.
        assertTrue(PhysicalItemService.saidAsASpeciesName("how are the geese", "greylag_goose"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the geese laying", "greylag_goose"));

        // Said in the plural, and heard for BOTH kinds of ox a keeper might have.
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the oxen in calf", "ox"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the oxen in calf", "musk_ox"));

        // By the head noun, which is how the breeding report is actually addressed.
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the goats in kid", "mountain_goat"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("is the sheep in lamb", "bighorn_sheep"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the deer in calf", "red_deer"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the ducks laying", "mallard_duck"));
        assertTrue(PhysicalItemService.saidAsASpeciesName("are the horses in foal", "horse"));

        // AND THE TWO THAT MATTERED MOST: a name inside another word is not that name. Both of these narrowed
        // a whole flock down to the oxen.
        assertFalse(PhysicalItemService.saidAsASpeciesName("look over the flock by the oxbow", "ox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("check the stock near the fox run", "ox"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("look over the flock by the oxbow", "musk_ox"));

        // A kind the keeper did not name is not answered about.
        assertFalse(PhysicalItemService.saidAsASpeciesName("are the goats in kid", "horse"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("how are the geese", "mallard_duck"));
    }
}
