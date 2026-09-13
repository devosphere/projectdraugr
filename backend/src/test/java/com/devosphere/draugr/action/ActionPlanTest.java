package com.devosphere.draugr.action;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser behind #38: a written procedure must mean what it says, and a wrong split must never happen.
 *
 * <p>The composer takes 2,500 characters and the resolver matched exactly one process to the whole of it, so a
 * Chronicle who wrote out four steps got one and lost three without being told. But a wrong split is worse than
 * no split — it would route half a phrase and act on it — so these tests are as much about what must NOT be cut
 * as about what must. Pure function; runs everywhere.
 */
class ActionPlanTest {

    @Test
    void anOrdinaryActionIsOneStepAndUntouched() {
        assertEquals(List.of("gather mushrooms from the forest floor"),
            ActionPlan.steps("gather mushrooms from the forest floor"));
        assertEquals(List.of("strike flint against pyrite"),
            ActionPlan.steps("  strike flint against pyrite  "), "surrounding space is trimmed, nothing else");
    }

    /** The heart of it: a bare "and" joins the parts of one thing far more often than it sequences two. */
    @Test
    void aBareAndIsNeverASeparator() {
        for (String oneThing : new String[]{
                "build a wattle and daub hut",
                "strike flint and pyrite together",
                "twist grass and willow fibre into cordage",
                "gather bow and arrow stock",
                "butcher the carcass for bone and hide"}) {
            assertEquals(List.of(oneThing), ActionPlan.steps(oneThing),
                "\"" + oneThing + "\" is one thing, and cutting it in half would route a fragment");
        }
    }

    @Test
    void explicitSequencingWordsDeclareSteps() {
        assertEquals(List.of("carve a spindle", "form a tinder nest", "spin the bow drill until it catches"),
            ActionPlan.steps("carve a spindle, then form a tinder nest, and then spin the bow drill until it catches"));
        assertEquals(List.of("split the log", "shave the arrow shafts"),
            ActionPlan.steps("split the log. shave the arrow shafts."));
        assertEquals(List.of("gut the fish", "salt the fish for winter"),
            ActionPlan.steps("gut the fish; salt the fish for winter"));
        assertEquals(List.of("till the ground", "sow the wild grain"),
            ActionPlan.steps("till the ground\nsow the wild grain"));
    }

    /** A sequencing word at the head of a sentence is scaffolding for the reader, not part of the instruction. */
    @Test
    void aLeadingSequencingWordIsNotPartOfTheStep() {
        assertEquals(List.of("look around at the ground here", "listen for a while", "take a short rest"),
            ActionPlan.steps("look around at the ground here. Then listen for a while. Finally take a short rest."));
        assertEquals(List.of("cut the withies", "weave the panel"),
            ActionPlan.steps("cut the withies. After that, weave the panel."));
    }

    @Test
    void numberedAndBulletedProceduresAreRead() {
        assertEquals(List.of("till the ground", "sow the wild grain", "weed the stand"),
            ActionPlan.steps("1. till the ground\n2. sow the wild grain\n3. weed the stand"));
        assertEquals(List.of("cut the withies", "weave the panel"),
            ActionPlan.steps("- cut the withies\n- weave the panel"));
    }

    /** A sentence end only splits when it really ends a sentence. */
    @Test
    void punctuationNoiseIsNotAStep() {
        assertEquals(List.of("gather berries"), ActionPlan.steps("gather berries."));
        assertEquals(List.of("gather berries"), ActionPlan.steps("gather berries. ."));
        assertEquals(List.of(), ActionPlan.steps("   "));
        assertEquals(List.of(), ActionPlan.steps(null));
    }

    @Test
    void aVeryLongProcedureIsRecognisedAsLongerThanOneStretchOfEffort() {
        StringBuilder plan = new StringBuilder("gather fibre");
        for (int i = 0; i < ActionPlan.MAX_STEPS + 2; i++) plan.append(". twist more cordage");
        List<String> steps = ActionPlan.steps(plan.toString());
        assertTrue(steps.size() > ActionPlan.MAX_STEPS, "the parser reads every step the player wrote");
        assertTrue(ActionPlan.exceedsLimit(plan.toString()),
            "and says plainly that it is more than one stretch of effort, rather than dropping the tail in silence");
    }

    /**
     * #38 resume: only a bare request to go on counts, matched against the whole submission. Each negative here is a
     * phrase that already means something else in the world, and a substring match would take it.
     */
    @Test
    void onlyABareRequestToGoOnTakesUpWhatWasSetAside() {
        for (String yes : List.of("carry on", "Carry on.", "I carry on", "ok, carry on then", "continue", "resume the plan",
                                  "pick up where I left off", "now go on with the rest", "finish what I started"))
            assertTrue(ActionPlan.isResumeRequest(yes), "should take up what was set aside: " + yes);
        for (String no : List.of("carry on my back the bundle of wood", "continue building the wall", "resume the lean-to",
                                 "continue working on the lean-to", "go on north", "carry on to the river", "finish the basket",
                                 "look around", ""))
            assertFalse(ActionPlan.isResumeRequest(no), "means something else and must not be taken: " + no);
        assertFalse(ActionPlan.isResumeRequest(null));
    }
}
