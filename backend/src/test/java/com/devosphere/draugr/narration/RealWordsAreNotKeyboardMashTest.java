package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Real words are not keyboard mash (#37, #99).
 *
 * <p>The pre-classifier reads an input as gibberish when no token looks like a plausible word, and one of its
 * tests was a run of <b>four</b> consecutive consonants — the structural giveaway of {@code asdfghjkl}. English
 * has plenty of real words with four. Measured on a booted backend, every one of these was answered <em>"The
 * body has no shape for that intent. Nothing moves."</em>:
 *
 * <pre>
 *   withdraw   wi(thdr)aw   — and WITHDRAW is a declared contact act
 *   windbreak  wi(ndbr)eak  — and PLACE_WINDBREAK is a declared intent
 *   sandstone  sa(ndst)one
 *   birchbark  bi(rchb)ark
 *   strength   stre(ngth)
 *   handcraft  ha(ndcr)aft
 * </pre>
 *
 * <p>Telling a player their own language is nonsense, in a voice that sounds certain, is the worst answer this
 * composer can give — and it was reached before any routing could happen, so nothing downstream could recover.
 *
 * <p><b>Six still catches what the rule is for</b>, because the vowel test does most of the work: mash with no
 * vowel at all never reaches the consonant test, and mash that has one runs far longer than real words.
 * This test holds both halves, since loosening the rule is only safe while the mash is still caught.
 *
 * <p>Pure over text — no world, no clock, no Chronicle — so it runs everywhere, including on a machine where
 * the integration suite skips for want of Docker. That is deliberate: this defect was found by playing, and a
 * test that cannot run locally would not have kept it fixed.
 */
class RealWordsAreNotKeyboardMashTest {

    private final ActionInputClassifier classifier = new ActionInputClassifier();

    @Test
    void realEnglishWordsWithFourConsonantsInARowAreNotGibberish() {
        for (String word : List.of("withdraw", "windbreak", "sandstone", "birchbark", "strength", "handcraft",
                                   "handprint", "woodcraft", "lengths", "strengths")) {
            assertNotEquals(ActionInputClassifier.InputClass.NONSENSICAL, classifier.classify(word),
                () -> "'" + word + "' is a word, not keyboard mash");
        }
    }

    @Test
    void theWordsInASentenceAreNotGibberishEither() {
        // The rule looks at every token and clears the input as soon as ONE is plausible, so a sentence was
        // safer than a bare noun. These are the forms a player actually types, and they must hold too.
        for (String said : List.of("withdraw from the isle", "put up a windbreak", "gather sandstone",
                                   "strip the birchbark", "test my strength")) {
            assertNotEquals(ActionInputClassifier.InputClass.NONSENSICAL, classifier.classify(said),
                () -> "'" + said + "' is a sentence, not keyboard mash");
        }
    }

    @Test
    void keyboardMashIsStillKeyboardMash() {
        // THE HALF THAT MAKES LOOSENING SAFE. If the rule stopped catching these, the threshold moved too far.
        for (String mash : List.of("asdfghjkl", "zxcvbnm", "qwertyuiopasdfghjkl", "!!!", "12345", "@#$%^&", "x")) {
            assertEquals(ActionInputClassifier.InputClass.NONSENSICAL, classifier.classify(mash),
                () -> "'" + mash + "' is not an action");
        }
    }

    @Test
    void aWordWithNoVowelAtAllIsStillCaughtWithoutTheConsonantRule() {
        // Why six is safe: the vowel test catches the shorter mash on its own, so the consonant run only ever
        // needed to catch the long vowel-bearing kind.
        assertEquals(ActionInputClassifier.InputClass.NONSENSICAL, classifier.classify("zxcvbnm"),
            "no vowel, so the consonant threshold never decides it");
        assertEquals(ActionInputClassifier.InputClass.NONSENSICAL, classifier.classify("bcdfg"),
            "and the same for a shorter one");
    }
}
