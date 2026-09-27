package com.devosphere.draugr.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A process must be reachable by the phrases it declares (#37).
 *
 * <p>The category is guessed from the verb, and the verb a process is NAMED for is not always the verb its own
 * category is keyed to. {@code weave} is a PROCESS term while {@code weave_quiver} is a CRAFT process, so
 * "weave a quiver" — a phrase the quiver itself lists as one of its keywords — classified PROCESS, never saw
 * the quiver, and reached nothing at all.
 *
 * <p>Measured across the live catalogue: 183 declared keywords lead with a verb belonging to a different
 * category than their own process. Of thirty such phrases driven through a running stack, <b>thirteen reached
 * nothing</b> — "build a bucket", "build a cart", "build a shield", "build a travois", "cut a walking staff",
 * "cut a whistle" among them. Every one is a phrase the catalogue itself promises an answer to.
 *
 * <p>{@code resolveAndRecord} therefore tries the guessed category first and, only when nothing there answers,
 * asks the rest of the catalogue. This test pins the two halves of that reasoning without Spring or a database:
 * the gate really does hide a matching process, and dropping it really does find it.
 */
class CategoryIsAHintNotAGateTest {

    /** A CRAFT process whose own keyword leads with a verb the classifier reads as PROCESS. */
    private static final ProcessMatcher.Candidate QUIVER_LIKE = new ProcessMatcher.Candidate(
        "weave_quiver", "CRAFT", List.of("weave a quiver", "quiver"), List.of("quiver"), "hide_quiver");

    @Test
    @DisplayName("the guessed category hides a process that answers to the words")
    void theGateHidesIt() {
        // Told the category is PROCESS — which is what "weave" classifies to — the CRAFT quiver is filtered out
        // before its keyword is ever compared, and the caller is told the words fit nothing.
        ProcessMatcher.Result gated = ProcessMatcher.resolve("weave a quiver", "PROCESS", List.of(QUIVER_LIKE));
        assertNull(gated.processKey(), "the gate must be what hides it, or this test is not about the gate");
    }

    @Test
    @DisplayName("dropping the guess finds it, and keyword and subject still both have to agree")
    void droppingTheGuessFindsIt() {
        ProcessMatcher.Result free = ProcessMatcher.resolve("weave a quiver", null, List.of(QUIVER_LIKE));
        assertEquals("weave_quiver", free.processKey(), "the words do fit it; only the category said otherwise");

        // The fallback is not a free-for-all. Without a keyword it still finds nothing...
        assertNull(ProcessMatcher.resolve("weave a basket", null, List.of(QUIVER_LIKE)).processKey(),
            "a phrase naming none of its keywords must still reach nothing");

        // ...and without a subject it still finds nothing, which is the bar that keeps the widening honest.
        ProcessMatcher.Candidate noSubjectHere = new ProcessMatcher.Candidate(
            "weave_quiver", "CRAFT", List.of("weave a quiver"), List.of("birch bark"), "hide_quiver");
        assertNull(ProcessMatcher.resolve("weave a quiver", null, List.of(noSubjectHere)).processKey(),
            "the subject gate must survive the category gate being relaxed");
    }

    @Test
    @DisplayName("a category that CAN answer is still answered by the category, unchanged")
    void theHintStillGoesFirst() {
        // Two processes answer to the same words in different categories. The guessed category must win, or
        // relaxing the gate would quietly re-route phrases that already resolved correctly.
        ProcessMatcher.Candidate inCategory = new ProcessMatcher.Candidate(
            "boil_quiver_glue", "PROCESS", List.of("weave a quiver"), List.of("quiver"), "hide_glue");
        ProcessMatcher.Result r = ProcessMatcher.resolve("weave a quiver", "PROCESS", List.of(QUIVER_LIKE, inCategory));
        assertEquals("boil_quiver_glue", r.processKey(),
            "when the guessed category has an answer it keeps it, and the fallback never runs");
    }
}
