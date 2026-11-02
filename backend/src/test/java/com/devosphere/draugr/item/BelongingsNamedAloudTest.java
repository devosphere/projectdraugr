package com.devosphere.draugr.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NAME YOUR OWN BELONGINGS AND BE UNDERSTOOD (#37).
 *
 * <p>Equip, unequip, drop and refine each carried their own copy of {@code lower.contains(displayName)}, which
 * is true only when the player utters the catalogue's full spelling. Measured live, with the thing in question
 * in the pack or on the body the whole time:
 *
 * <pre>
 *   drop the hide         -> "You are not carrying anything by that name."   (an Animal hide was carried)
 *   drop the knife        -> "You are not carrying anything by that name."   (three knives were carried)
 *   improve my knife      -> "You have nothing by that name to improve."
 *   take off my trousers  -> "You are not wearing anything by that name."    (Everyday trousers, worn)
 *   put on my shirt       -> SUCCEEDED, and settled a stone knife into the hand
 * </pre>
 *
 * <p>Every display name and key below was read off a live world's own inventory. These are assertions about
 * English, so they need no database and run everywhere — which is the point, because the integration tests
 * that would cover this skip locally.
 */
class BelongingsNamedAloudTest {

    /** The family word, which is what a person says for their own gear. */
    @Test
    void theHeadNounIsEnough() {
        assertEquals(1, PhysicalItemService.howWellNamed("drop the hide", "Animal hide", "animal_hide"));
        assertEquals(1, PhysicalItemService.howWellNamed("put on my shirt", "Everyday shirt", "arrival_shirt"));
        assertEquals(1, PhysicalItemService.howWellNamed("take off my trousers", "Everyday trousers", "arrival_trousers"));
        assertEquals(1, PhysicalItemService.howWellNamed("drop the basket", "Woven basket", "woven_basket"));
        assertEquals(1, PhysicalItemService.howWellNamed("improve my knife", "Bone knife", "bone_knife"));
        // A display_name spelt as its own key - three of the Chronicle's belongings are - still has a head noun.
        assertEquals(1, PhysicalItemService.howWellNamed("wield the knife", "stone_knife", "stone_knife"));
    }

    /** Said in full, which must outrank the family word so the player gets the one they named. */
    @Test
    void theWholeNameOutranksTheFamilyWord() {
        assertEquals(3, PhysicalItemService.howWellNamed("drop the animal hide", "Animal hide", "animal_hide"));
        assertEquals(3, PhysicalItemService.howWellNamed("drop the bone knife", "Bone knife", "bone_knife"));
        assertTrue(PhysicalItemService.howWellNamed("drop the bone knife", "Bone knife", "bone_knife")
                 > PhysicalItemService.howWellNamed("drop the bone knife", "stone_knife", "stone_knife"));
    }

    /**
     * The key said as words, for the thing whose key and shown name are not the same word. A raw-key display
     * name needs no second reading any more — "stone_knife" flattens to "stone knife" and is simply its name.
     */
    @Test
    void theKeySaidAsWords() {
        assertEquals(3, PhysicalItemService.howWellNamed("equip the stone knife", "stone_knife", "stone_knife"));
        assertEquals(2, PhysicalItemService.howWellNamed("take off the arrival shirt", "Everyday shirt", "arrival_shirt"));
    }

    /**
     * A QUALIFIER THE THING CANNOT ANSWER TO REFUSES THE MATCH. {@code equip the bone knife} matched a stone
     * knife on the bare word <i>knife</i> and answered SUCCEEDED — the family-word defect looking the other
     * way, reaching the wrong member of the family.
     */
    @Test
    void aQualifierThatBelongsToSomethingElse() {
        assertEquals(0, PhysicalItemService.howWellNamed("equip the bone knife", "stone_knife", "stone_knife"));
        assertEquals(0, PhysicalItemService.howWellNamed("wear the hide boots", "Left everyday shoe", "arrival_left_shoe"));
        assertEquals(0, PhysicalItemService.howWellNamed("wear the fur hat", "Bark headband", "bark_headband"));
        // ...and the function words a request is built from are not qualifiers.
        assertEquals(1, PhysicalItemService.howWellNamed("take off my shoes", "Left everyday shoe", "arrival_left_shoe"));
        assertEquals(1, PhysicalItemService.howWellNamed("wield knife", "stone_knife", "stone_knife"));
        assertEquals(1, PhysicalItemService.howWellNamed("i would like to drop the mat", "Reed mat", "reed_mat"));
    }

    /** Said in the plural, by the same reader the hunt and the stockyard use. */
    @Test
    void saidInThePlural() {
        assertEquals(1, PhysicalItemService.howWellNamed("take off my shoes", "Right everyday shoe", "arrival_right_shoe"));
        assertEquals(1, PhysicalItemService.howWellNamed("drop the knives", "Bone knife", "bone_knife"));
        assertEquals(3, PhysicalItemService.howWellNamed("drop the bone knives", "Bone knife", "bone_knife"));
        assertEquals(1, PhysicalItemService.howWellNamed("drop the baskets", "Woven basket", "woven_basket"));
        // The f that turns to v: eight of the catalogue's knives and fourteen of its leaves end this way.
        assertEquals("knives", PhysicalItemService.pluralOf("knife"));
        assertEquals("bone knives", PhysicalItemService.pluralOf("bone knife"));
        assertEquals("nettle leaves", PhysicalItemService.pluralOf("nettle leaf"));
        assertEquals("tied reed sheaves", PhysicalItemService.pluralOf("tied reed sheaf"));
        assertEquals(3, PhysicalItemService.howWellNamed("eat the nettle leaves", "Nettle Leaf", "nettle_leaf"));
        assertEquals(1, PhysicalItemService.howWellNamed("eat the leaves", "Nettle Leaf", "nettle_leaf"));
    }

    /** A thing improved in place carries its revision; nobody says that part aloud. */
    @Test
    void aRevisionIsNotPartOfWhatYouSay() {
        assertEquals(3, PhysicalItemService.howWellNamed("improve the stone knife", "Stone knife Revision II", "stone_knife"));
        assertEquals(1, PhysicalItemService.howWellNamed("improve my knife", "Stone knife Revision IV", "stone_knife"));
    }

    /** Whole words on both sides, so letters inside a longer word are not a name. */
    @Test
    void whatIsNotNamedAtAll() {
        assertEquals(0, PhysicalItemService.howWellNamed("look for hidden tracks", "Animal hide", "animal_hide"));
        assertEquals(0, PhysicalItemService.howWellNamed("sharpen the matting", "Reed mat", "reed_mat"));
        assertEquals(0, PhysicalItemService.howWellNamed("get dressed", "stone_knife", "stone_knife"));
        assertEquals(0, PhysicalItemService.howWellNamed("cover my head", "Woven basket", "woven_basket"));
        assertEquals(0, PhysicalItemService.howWellNamed(null, "Woven basket", "woven_basket"));
    }
}
