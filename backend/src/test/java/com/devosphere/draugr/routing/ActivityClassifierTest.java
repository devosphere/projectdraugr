package com.devosphere.draugr.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The classification rule itself, on a vocabulary small enough to reason about. */
class ActivityClassifierTest {

    private static final Map<String, Integer> PREC = Map.of("HUNT", 1, "PROCESS", 3, "CRAFT", 4);

    private static final List<ActivityClassifier.Term> VOCAB = List.of(
        new ActivityClassifier.Term("HUNT", "fish", 3),
        new ActivityClassifier.Term("HUNT", "trap", 2),
        new ActivityClassifier.Term("PROCESS", "split", 2),
        new ActivityClassifier.Term("PROCESS", "ore", 2),
        new ActivityClassifier.Term("PROCESS", "shape into", 2),
        new ActivityClassifier.Term("CRAFT", "carve", 2),
        new ActivityClassifier.Term("CRAFT", "make", 1));

    private static String classify(String text) {
        return ActivityClassifier.classify(text, VOCAB, PREC);
    }

    @Test
    @DisplayName("normalisation lowercases, strips punctuation, and pads")
    void normalises() {
        assertEquals(" split the log ", ActivityClassifier.normalise("Split the log."));
        assertEquals(" a b ", ActivityClassifier.normalise("  a---b  "));
        assertEquals(" ", ActivityClassifier.normalise(null));
    }

    @Test
    @DisplayName("terms match whole words, never fragments")
    void wholeWordOnly() {
        // The collision that started this: 'ore' must not be found inside "forest".
        assertNull(classify("walk through the forest"));
        assertEquals("PROCESS", classify("carry the ore uphill"));
        assertFalse(ActivityClassifier.containsTerm(" flashing ", "ash"));
        assertTrue(ActivityClassifier.containsTerm(" gather the ash ", "ash"));
    }

    @Test
    @DisplayName("multi-word terms match only when the words are adjacent")
    void phrasesNeedAdjacency() {
        assertEquals("PROCESS", classify("shape into a blank"));
        assertNull(classify("shape it into a blank"));
    }

    @Test
    @DisplayName("weights accumulate, so the category a sentence leans on hardest wins")
    void weightsSum() {
        // CRAFT 'carve' 2 + 'make' 1 = 3 against PROCESS 'split' 2.
        assertEquals("CRAFT", classify("carve and make a split handle"));
    }

    @Test
    @DisplayName("precedence breaks a tie, lower first")
    void precedenceBreaksTies() {
        // HUNT 'fish' 3 against CRAFT 'carve' 2 + 'make' 1; HUNT has the lower precedence.
        assertEquals("HUNT", classify("carve and make a fish"));
    }

    @Test
    @DisplayName("no recognised term classifies to null, not to a default category")
    void unrecognisedIsNull() {
        assertNull(classify("baulk the timber"));
        assertNull(classify(""));
        assertNull(classify(null));
    }
}
