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

    /**
     * A sequencing word needs a step in front of it (#37).
     *
     * <p>Found live while measuring V442, and the routing unit test for the very same sentence had already
     * passed — because the unit test calls the classifier and this splitter runs before it. {@code what next for
     * the hide} was cut into "what" and "for the hide", two fragments that name nothing, so the question came
     * back "you spend the effort and come back with only the effort" while a reading that answers it exactly sat
     * one call further on.
     *
     * <p>"next" means "and after that" when an instruction stands in front of it and nothing of the kind when a
     * question word does. Three words is the shortest thing that can be an instruction with an object.
     */
    @Test
    void aSequencingWordNeedsAStepInFrontOfIt() {
        for (String oneQuestion : new String[]{
                "what next for the hide",
                "whats next for the fence",
                "what next",
                "how is the work going, then"}) {
            assertEquals(List.of(oneQuestion), ActionPlan.steps(oneQuestion),
                "\"" + oneQuestion + "\" is one question, and cutting it leaves fragments that name nothing");
        }

        // AND THE SPLITTING THAT MATTERS IS UNTOUCHED: a real instruction before the word still ends a step.
        assertEquals(List.of("cut the withies", "weave the panel"),
            ActionPlan.steps("cut the withies, next weave the panel"));
        assertEquals(List.of("gather dry grass", "strike the flint"),
            ActionPlan.steps("gather dry grass then strike the flint"));
        // ...and punctuation is never second-guessed, whatever words surround it: a person who writes a full
        // stop or a line break has ended a step.
        assertEquals(List.of("what next", "weave the panel"),
            ActionPlan.steps("what next. weave the panel"));
        assertEquals(List.of("what next", "weave the panel"),
            ActionPlan.steps("what next\nweave the panel"));
    }

    /**
     * A stopped plan never counts steps that are not there (#872).
     *
     * <p>When the step that failed was the LAST one, the report counted the remainder — none — and said so in
     * the plainest possible self-contradiction: <i>"the 0 steps after it went undone, and until this part
     * comes right there is no going on to them."</i> It named a quantity it had just counted as none, then
     * promised to withhold something that does not exist.
     *
     * <p>Reported twice from live play: on {@code check the fire; bank the fire for the night} over a cold
     * pit, and on a bow-drill ignition at the end of a longer procedure.
     */
    @Test
    void aStoppedPlanNeverCountsStepsThatAreNotThere() {
        // The failed step was the last: nothing follows it, so nothing may be claimed to follow it.
        String lastFailed = ChronicleActionService.stoppedReport(1, 0);
        assertFalse(lastFailed.contains("0 step"), () -> "a count of none must never be spoken: " + lastFailed);
        assertFalse(lastFailed.contains("went undone"), () -> "nothing went undone after the last step: " + lastFailed);
        assertTrue(lastFailed.contains("take it up again"),
            () -> "the failed step is set aside, so say it can be retried: " + lastFailed);

        // Steps really did go undone: the count is spoken, and it agrees in number.
        assertTrue(ChronicleActionService.stoppedReport(1, 1).contains("1 step after it went undone"));
        assertTrue(ChronicleActionService.stoppedReport(2, 3).contains("3 steps after it went undone"));

        // Nothing ran at all: the whole procedure is still ahead, and no count is invented for it.
        String nothingRan = ChronicleActionService.stoppedReport(0, 2);
        assertTrue(nothingRan.contains("still ahead of you"), () -> nothingRan);
        assertFalse(nothingRan.contains("stands"), () -> "nothing was done, so nothing stands: " + nothingRan);

        // Whatever the shape, the report never contradicts itself by counting none.
        for (int done = 0; done <= 3; done++)
            for (int remaining = 0; remaining <= 3; remaining++) {
                String said = ChronicleActionService.stoppedReport(done, remaining);
                final int d = done, r = remaining;
                assertFalse(said.contains("0 step"),
                    () -> "done=" + d + " remaining=" + r + " spoke a count of none: " + said);
            }
    }

    /**
     * A SHORT instruction before a comma-and-sequencing-word is still a step (#869).
     *
     * <p>The three-word rule exists to keep "what next for the hide" whole, and it also caught real
     * instructions that happen to be short. Worse, it did not merely decline to split them: the short piece was
     * skipped WITHOUT advancing past the separator, so it was absorbed into the next step and the resolver
     * matched that step alone.
     *
     * <p>Reported from a live playthrough: a Chronicle with an urgent bladder wrote
     * {@code urinate, then dry off, then sleep for 2 hours}, was told about the drying and the sleeping, and
     * went from Urgent to Critical. The relief did not fail — it ceased to exist. A step that is silently
     * dropped is worse than one that is refused, because nothing in the report says it is gone.
     */
    @Test
    void aShortInstructionBeforeACommaIsStillItsOwnStep() {
        assertEquals(List.of("urinate", "dry off", "sleep for 2 hours"),
            ActionPlan.steps("urinate, then dry off, then sleep for 2 hours"),
            "the relief is the step that disappeared");
        assertEquals(List.of("dry off", "rest 30 minutes"),
            ActionPlan.steps("dry off, then rest 30 minutes"));

        // One word is enough when the comma is there — the comma is the player saying "and after that".
        assertEquals(List.of("urinate", "sleep for 2 hours"),
            ActionPlan.steps("urinate, then sleep for 2 hours"));
        assertEquals(List.of("eat", "drink", "rest a while"),
            ActionPlan.steps("eat, then drink, then rest a while"));

        // AND THE QUESTION THE RULE WAS WRITTEN FOR IS UNTOUCHED, because it carries no comma. This is the
        // assertion that keeps the fix from becoming the defect it replaced.
        assertEquals(List.of("what next for the hide"),
            ActionPlan.steps("what next for the hide"));
        assertEquals(List.of("what next for the fibre"),
            ActionPlan.steps("what next for the fibre"));

        // A bare short piece with no comma still joins what follows, exactly as before.
        assertEquals(List.of("what next for the hide"),
            ActionPlan.steps("what next for the hide"));
    }
}
