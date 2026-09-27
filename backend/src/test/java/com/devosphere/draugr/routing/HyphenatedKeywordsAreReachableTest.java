package com.devosphere.draugr.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A keyword spelled with a hyphen must be findable in text (#37).
 *
 * <p>{@link ActivityClassifier#normalise} collapses every run of punctuation to a space, so by the time text
 * reaches {@code containsTerm} a hyphen has already become a space. The term was compared un-normalised, so a
 * hyphenated keyword was matched against text that could no longer contain it: <em>"fire-hardened spear"</em>
 * against <em>"fire hardened spear"</em>, missing every time, for every Chronicle who typed the phrase exactly
 * as the catalogue spells it.
 *
 * <p>Found by walking all 2,157 catalogue keywords that carry their own subject and asking which the matcher
 * could actually reach. Five could not, and every one was hyphenated: "fire-hardened spear",
 * "stone-headed club", "case-harden the breastplate", "venom-tipped spear", "make grass-lined sandals".
 */
class HyphenatedKeywordsAreReachableTest {

    @Test
    @DisplayName("a hyphenated keyword is found however the player punctuates it")
    void hyphenatedTermsMatch() {
        // The five real ones, each written the way a player would and the way the catalogue spells it.
        for (String written : new String[]{"fire-hardened spear", "fire hardened spear"})
            assertTrue(ActivityClassifier.containsTerm(ActivityClassifier.normalise(written), "fire-hardened spear"),
                "the catalogue's own spelling must be findable in: " + written);

        assertTrue(ActivityClassifier.containsTerm(ActivityClassifier.normalise("make a stone-headed club"), "stone-headed club"));
        assertTrue(ActivityClassifier.containsTerm(ActivityClassifier.normalise("case harden the breastplate"), "case-harden the breastplate"));
        assertTrue(ActivityClassifier.containsTerm(ActivityClassifier.normalise("make grass lined sandals"), "make grass-lined sandals"));
    }

    @Test
    @DisplayName("and an unhyphenated term behaves exactly as it always did")
    void plainTermsAreUnchanged() {
        String text = ActivityClassifier.normalise("gather mushrooms from the forest floor");
        assertTrue(ActivityClassifier.containsTerm(text, "mushrooms"));
        assertTrue(ActivityClassifier.containsTerm(text, "forest floor"));

        // The word-boundary rule is the whole reason this method exists, and relaxing punctuation must not
        // relax that: "ore" is inside "forest" and must still not be found there.
        assertFalse(ActivityClassifier.containsTerm(text, "ore"));
        assertFalse(ActivityClassifier.containsTerm(ActivityClassifier.normalise("install flashing"), "ash"));
        assertFalse(ActivityClassifier.containsTerm(text, "mushroom"), "a keyword is not pluralised — only subjects are");
    }
}
