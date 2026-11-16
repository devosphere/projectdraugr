package com.devosphere.draugr.action;

import com.devosphere.draugr.narration.NarrationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #900 — a hypothermic Chronicle slept two hours in a debris hut, and one response told them they woke with the
 * tiredness lifted and then that the cold had killed them. The clock runs over the whole span before the sleep
 * is narrated, so the body is already dead when the sleep is told; waking is said only of a body that lived.
 * Pure: no database, so it runs wherever the suite runs.
 */
class AFatalSleepDoesNotWakeTest {

    @Test
    void aSleepDiedInSaysNothingOfWaking() {
        String told = ChronicleActionService.sleepNarration(false, true, false, "2 hours");
        assertThat(told.toLowerCase()).doesNotContain("you wake").doesNotContain("waking stiff")
            .doesNotContain("tiredness lifted").doesNotContain("half-rested");
        assertThat(told).contains("2 hours").contains("beyond waking");
    }

    @Test
    void aSleepDiedInNamesNoCauseOfItsOwn() {
        // The death coda names the cause after it; a sleep that blamed the cold would be wrong for thirst.
        String told = ChronicleActionService.sleepNarration(false, false, false, "8 hours").toLowerCase();
        assertThat(told).doesNotContain("cold").doesNotContain("thirst").doesNotContain("hunger");
    }

    @Test
    void aSleepLivedThroughStillWakes() {
        assertThat(ChronicleActionService.sleepNarration(true, true, false, "2 hours"))
            .contains("You wake to a changed sky, the deep tiredness lifted");
        assertThat(ChronicleActionService.sleepNarration(true, false, false, "2 hours"))
            .contains("waking stiff and only half-rested");
    }

    @Test
    void everySleepPassesTheNarrationPolicy() {
        NarrationPolicy policy = new NarrationPolicy();
        // Every night this game can report, including the two a bed makes (#893).
        for (boolean survived : new boolean[]{true, false})
            for (boolean sheltered : new boolean[]{true, false})
                for (boolean bedded : new boolean[]{true, false})
                    policy.validate(ChronicleActionService.sleepNarration(survived, sheltered, bedded, "an hour and 30 minutes"));
    }
    /**
     * FOUR KINDS OF NIGHT, NOT TWO (#893). A Chronicle who had gathered reeds, worked them into a pallet and
     * lain down on it at an open camp was told they "settled onto the bare ground" — the bedding was in the
     * recovery arithmetic and in none of the words.
     */
    @Test
    void aNightOnBeddingDoesNotReadAsTheBareGround() {
        String bare = ChronicleActionService.sleepNarration(true, false, false, "8 hours");
        String bedded = ChronicleActionService.sleepNarration(true, false, true, "8 hours");
        String roofed = ChronicleActionService.sleepNarration(true, true, false, "8 hours");
        String both = ChronicleActionService.sleepNarration(true, true, true, "8 hours");

        assertTrue(bare.contains("bare ground"), bare);
        assertTrue(bedded.contains("bedding"), () -> "a night on bedding must name it: " + bedded);
        assertTrue(!bedded.contains("bare ground"),
            () -> "and must not call a pallet the bare ground, which is the whole of #893: " + bedded);
        assertTrue(!bedded.contains("under cover"),
            () -> "nor claim a roof the camp has not got: " + bedded);
        assertTrue(roofed.contains("under cover"), roofed);
        assertTrue(both.contains("under cover") && both.contains("off the cold ground"),
            () -> "a bed under a roof is both, and reads as both: " + both);

        // All four are different nights, so a player can tell which one they had.
        assertEquals(4, new java.util.HashSet<>(java.util.List.of(bare, bedded, roofed, both)).size(),
            "the four nights must read differently");
    }
}
