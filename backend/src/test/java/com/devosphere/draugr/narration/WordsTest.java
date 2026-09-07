package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name hiding inside another name.
 *
 * <p>This is the CI failure that produced it, written down: ambient tracking narrated "A aurochs, by the look of
 * it", and the assertion that tracking never casually names a monster went red — because the catalogue holds a
 * monster keyed {@code roc}, and {@code "aurochs".contains("roc")} is true. It was intermittent, so it read as a
 * flake: it only fires when the ground happens to pick that animal.
 *
 * <p>The same matcher was live in gameplay, not only in the test. A Chronicle who wrote "hunt the aurochs" was run
 * against every species key by substring, so the roc could be selected as what they meant — a monster fetched by
 * typing the name of a cow. That is the reason this is a class with tests and not a fix in four places.
 */
class WordsTest {

    @Test
    void aNameInsideAnotherNameIsNotThatName() {
        assertFalse(Words.names("you find sign of an aurochs", "roc"),
            "a roc is not present because an aurochs is — this is the failure that prompted the class");
        assertFalse(Words.word("smoke the meat", "eat"), "'eat' does not occur in 'meat'");
        assertFalse(Words.names("hunt the thornhide boar", "boa"), "'boa' does not occur in 'boar'");
        assertFalse(Words.names("gather clay", "cave troll"), "unrelated names do not match at all");
    }

    @Test
    void aNameThatIsActuallySaidIsFound() {
        assertTrue(Words.names("hunt the wave roc", "wave_roc"), "the underscore form is how keys are spoken");
        assertTrue(Words.names("track a red deer", "red_deer"));
        assertTrue(Words.word("take a nap", "nap"));
        assertTrue(Words.names("HUNT THE AUROCHS", "aurochs"), "naming is not case-sensitive");
        assertTrue(Words.names("the roc circles overhead", "roc"), "and the roc itself is still findable");
    }

    /**
     * The reason for lookarounds rather than {@code \b}: a display name ending in punctuation has no word boundary
     * after it, and {@code \b} would refuse to match a name that is plainly there.
     */
    @Test
    void aNameEndingInPunctuationIsStillFound() {
        assertTrue(Words.word("repair the lean-to before dark", "lean-to"));
        assertTrue(Words.word("repair the lean-to", "lean-to"), "even with nothing following it");
        assertFalse(Words.word("repair the lean-tool", "lean-to"), "but it must still not run on into a longer word");
    }

    @Test
    void nothingMatchesNothing() {
        assertFalse(Words.word(null, "roc"));
        assertFalse(Words.word("a roc", null));
        assertFalse(Words.word("a roc", "  "), "a blank needle would otherwise match everywhere");
    }

    @Test
    void theArticleFollowsTheName() {
        assertEquals("an aurochs", Words.withArticle("aurochs"), "'A aurochs' is what the narration used to say");
        assertEquals("a red deer", Words.withArticle("red deer"));
        assertEquals("an elk", Words.withArticle("elk"));
        assertEquals("An aurochs", Words.startingSentence("aurochs"));
        assertEquals("A boar", Words.startingSentence("boar"));
        assertEquals("a", Words.article(""), "and an empty name does not throw");
    }
}
