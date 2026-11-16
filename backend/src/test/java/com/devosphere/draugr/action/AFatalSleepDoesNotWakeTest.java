package com.devosphere.draugr.action;

import com.devosphere.draugr.narration.NarrationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #900 — a hypothermic Chronicle slept two hours in a debris hut, and one response told them they woke with the
 * tiredness lifted and then that the cold had killed them. The clock runs over the whole span before the sleep
 * is narrated, so the body is already dead when the sleep is told; waking is said only of a body that lived.
 * Pure: no database, so it runs wherever the suite runs.
 */
class AFatalSleepDoesNotWakeTest {

    @Test
    void aSleepDiedInSaysNothingOfWaking() {
        String told = ChronicleActionService.sleepNarration(false, true, "2 hours");
        assertThat(told.toLowerCase()).doesNotContain("you wake").doesNotContain("waking stiff")
            .doesNotContain("tiredness lifted").doesNotContain("half-rested");
        assertThat(told).contains("2 hours").contains("beyond waking");
    }

    @Test
    void aSleepDiedInNamesNoCauseOfItsOwn() {
        // The death coda names the cause after it; a sleep that blamed the cold would be wrong for thirst.
        String told = ChronicleActionService.sleepNarration(false, false, "8 hours").toLowerCase();
        assertThat(told).doesNotContain("cold").doesNotContain("thirst").doesNotContain("hunger");
    }

    @Test
    void aSleepLivedThroughStillWakes() {
        assertThat(ChronicleActionService.sleepNarration(true, true, "2 hours"))
            .contains("You wake to a changed sky, the deep tiredness lifted");
        assertThat(ChronicleActionService.sleepNarration(true, false, "2 hours"))
            .contains("waking stiff and only half-rested");
    }

    @Test
    void everySleepPassesTheNarrationPolicy() {
        NarrationPolicy policy = new NarrationPolicy();
        for (boolean survived : new boolean[]{true, false})
            for (boolean sheltered : new boolean[]{true, false})
                policy.validate(ChronicleActionService.sleepNarration(survived, sheltered, "an hour and 30 minutes"));
    }
}
