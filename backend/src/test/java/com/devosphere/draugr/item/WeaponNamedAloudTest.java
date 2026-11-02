package com.devosphere.draugr.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * USING A WEAPON IS NOT MAKING ONE (#37/#99).
 *
 * <p>The hunt gate wants an attack verb AND a named animal. #827 taught the animal side the whole 215-species
 * catalogue; nothing ever read the weapon side, so a Chronicle carrying a bow, arrows, a javelin, a sling and a
 * spear, standing in a chunk with an ox herd in it, was answered like this:
 *
 * <pre>
 * throw the spear at the ox  -&gt; the fight, and a kill
 * throw the spear            -&gt; UNKNOWN
 * shoot the bow              -&gt; an offer to ASSEMBLE a bow
 * loose an arrow             -&gt; "You have not got enough fletched shaft"
 * sling a stone              -&gt; EQUIP, SUCCEEDED, "You settle the Sling stone into place."
 * </pre>
 *
 * <p>These hold the shipped reader itself to account — the carried list is the only thing supplied here, and
 * every display name and item key below was read off the live catalogue.
 */
class WeaponNamedAloudTest {

    /** A hunter's kit. No axe: the catalogue marks every axe a working tool too, so the real query omits it. */
    private static final List<String[]> KIT = List.of(
        new String[]{"Hunting bow", "hunting_bow"}, new String[]{"Hunting arrow", "hunting_arrow"},
        new String[]{"Primitive spear", "primitive_spear"}, new String[]{"Javelin", "javelin"},
        new String[]{"Stone club", "stone_club"}, new String[]{"Sling", "sling"},
        new String[]{"Sling stone", "sling_stone"});

    @Test
    @DisplayName("the weapon named, by the word a person would use for it")
    void theWeaponNamed() {
        assertTrue(PhysicalItemService.anAttackNaming("throw the spear", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("shoot the bow", KIT), "the head noun is what gets said");
        assertTrue(PhysicalItemService.anAttackNaming("loose an arrow", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("hurl the javelin", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("swing the club", KIT));
    }

    @Test
    @DisplayName("the verb is stripped before the thing is read, because several of these words are both")
    void theVerbIsNotTheThing() {
        // "sling" is the verb here and the stone is the thing — the sling stone in the pack answers to it.
        assertTrue(PhysicalItemService.anAttackNaming("sling a stone", KIT));
        // And here "sling" is the only weapon word in the sentence, which means the sentence named no weapon.
        assertFalse(PhysicalItemService.anAttackNaming("sling the pack over my shoulder", KIT));
        // Strip the verb from "club it" and nothing at all is named. A miss is the honest reading.
        assertFalse(PhysicalItemService.anAttackNaming("club it", KIT));
    }

    @Test
    @DisplayName("an empty-handed Chronicle is not attacking with anything")
    void nothingCarried() {
        assertFalse(PhysicalItemService.anAttackNaming("throw the spear", List.of()));
        assertFalse(PhysicalItemService.anAttackNaming("shoot the bow", List.of()));
    }

    @Test
    @DisplayName("no attack verb, no attack — however much of the kit the sentence names")
    void noVerb() {
        assertFalse(PhysicalItemService.anAttackNaming("my spear and my bow and my javelin", KIT));
        assertFalse(PhysicalItemService.anAttackNaming("how heavy is the javelin", KIT));
        assertFalse(PhysicalItemService.anAttackNaming("the bow is in the pack", KIT));
        assertFalse(PhysicalItemService.anAttackNaming(null, KIT));
    }

    /**
     * A WORD AFTER "MY" IS A THING, NOT A DOING.
     *
     * <p>The first cut of this reader answered {@code my spear and my bow and my sling} with a dead animal —
     * "sling" is the only word in that sentence it knows as a verb, and striking it out leaves a spear and a
     * bow behind. I first wrote this case down as a harmless limit, on the grounds that the camp stocktake
     * would claim such a sentence first. MEASURED LIVE, IT DOES NOT: {@code what am i carrying} is claimed and
     * a bare list is not, so the sentence reached the fight and killed something. The reassurance was wrong
     * and the limit was real, which is the whole argument for measuring a claim instead of reasoning it.
     */
    @Test
    @DisplayName("a kit list is not an attack, however many of these words it contains")
    void aDeterminerInFrontMakesItAThing() {
        assertFalse(PhysicalItemService.anAttackNaming("my spear and my bow and my sling", KIT));
        assertFalse(PhysicalItemService.anAttackNaming("put the sling on", KIT));
        assertFalse(PhysicalItemService.anAttackNaming("where is my sling", KIT));
        // ...and the verb in verb position is untouched by it.
        assertTrue(PhysicalItemService.anAttackNaming("sling a stone", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("take up the sling and cast a stone", KIT),
            "a noun-position hit must not stop the scan finding the real verb");
    }

    @Test
    @DisplayName("a qualifier that belongs to something else still refuses the match")
    void theQualifierGuardStillHolds() {
        // howWellNamed's guard, which this reader inherits: a bone spear is not the primitive one in the pack.
        assertFalse(PhysicalItemService.anAttackNaming("throw the bone spear", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("throw the primitive spear", KIT), "its own name, though");
    }

    @Test
    @DisplayName("said in the plural, which is how a person talks about arrows and stones")
    void inThePlural() {
        assertTrue(PhysicalItemService.anAttackNaming("loose the arrows", KIT));
        assertTrue(PhysicalItemService.anAttackNaming("pelt it with stones", KIT));
    }

    /**
     * A SENTENCE THAT IS A RECIPE'S OWN NAME IS A REQUEST FOR THAT RECIPE.
     *
     * <p>Held against the whole catalogue before pushing rather than after a 50-minute CI round: sixty
     * catalogue phrases contain one of these verbs, and six of them the rule took. Measured live, before the
     * guard, asking the catalogue for a thing answered with a dead animal:
     *
     * <pre>
     * sling stone  /  sling stone pouch  /  shot sling  /  stone club  /  stone sling  /  cast spear
     *      -&gt; CONFRONT_WILDLIFE, SUCCEEDED, "The struggle ends in the wet earth."
     * </pre>
     *
     * <p>In each the verb stands in NOUN position inside the recipe's own name, so no making verb precedes it
     * to be caught. The names below are the catalogue's own, read off the live world.
     */
    @Test
    @DisplayName("asking the catalogue for a thing is not attacking something with it")
    void aRecipesOwnNameIsARequestForTheRecipe() {
        PhysicalItemService catalogue = new PhysicalItemService(null, null, null) {
            @Override protected java.util.List<String> everyRecipeName() {
                return List.of("sling stone", "sling stone pouch", "shot sling", "stone club", "stone sling",
                               "cast spear", "cast a bronze spear", "shape a sling stone", "make a sling");
            }
        };
        for (String askedFor : new String[]{"sling stone", "sling stone pouch", "shot sling", "stone club",
                                            "stone sling", "cast spear", "Cast a Bronze Spear"})
            assertTrue(catalogue.isARecipesOwnName(askedFor), askedFor + " is a recipe's own name");
        // Whole sentence, not substring: a throw is not a founding just because a recipe shares a word.
        assertFalse(catalogue.isARecipesOwnName("cast the spear"));
        assertFalse(catalogue.isARecipesOwnName("sling a stone"));
        assertFalse(catalogue.isARecipesOwnName("throw the spear at the ox"));
    }
}
