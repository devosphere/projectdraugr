package com.devosphere.draugr.action;

import com.devosphere.draugr.narration.NarrationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #884 — a fully kitted bow-drill retry was answered "your hands raise no heat from bare wood alone" and then the
 * Chronicle died of cold: the clock ran the span first, the death laid their kit on the ground, and LIGHT_FIRE then
 * looked for it. An act the Chronicle died in now says only that it was begun and not finished. Pure: no database.
 */
class AnActDiedInIsNotDoneTest {

    @Test
    void itSaysTheWorkWasLeftUnfinished() {
        String told = ChronicleActionService.diedBeforeItWasDone("40 minutes");
        assertThat(told).contains("40 minutes").contains("unfinished");
    }

    @Test
    void itReadsNothingOffTheDeadBodysKitAndNamesNoCause() {
        String told = ChronicleActionService.diedBeforeItWasDone("2 hours").toLowerCase();
        assertThat(told).doesNotContain("bare wood").doesNotContain("you have no").doesNotContain("without a")
            .doesNotContain("cold").doesNotContain("thirst").doesNotContain("hunger");
    }

    @Test
    void itPassesTheNarrationPolicy() {
        new NarrationPolicy().validate(ChronicleActionService.diedBeforeItWasDone("an hour and 30 minutes"));
    }
}
