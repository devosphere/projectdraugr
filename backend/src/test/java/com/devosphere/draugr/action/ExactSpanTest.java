package com.devosphere.draugr.action;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #880 — a rest or a sleep reads back the span the clock actually advanced, not a rounded one.
 * Pure: no database, so it runs wherever the suite runs.
 */
class ExactSpanTest {

    @Test
    void theTwoReportedCasesReadBackExactly() {
        // "sleep for 90 minutes" was narrated as "2 hours"; 45 minutes as "an hour".
        assertThat(ChronicleActionService.exactSpan(90)).isEqualTo("an hour and 30 minutes");
        assertThat(ChronicleActionService.exactSpan(45)).isEqualTo("45 minutes");
    }

    @Test
    void wholeHoursStillReadAsHours() {
        assertThat(ChronicleActionService.exactSpan(60)).isEqualTo("an hour");
        assertThat(ChronicleActionService.exactSpan(120)).isEqualTo("2 hours");
        assertThat(ChronicleActionService.exactSpan(480)).isEqualTo("8 hours");
    }

    @Test
    void singularsAreSpokenAsSingulars() {
        assertThat(ChronicleActionService.exactSpan(1)).isEqualTo("a minute");
        assertThat(ChronicleActionService.exactSpan(61)).isEqualTo("an hour and a minute");
        assertThat(ChronicleActionService.exactSpan(135)).isEqualTo("2 hours and 15 minutes");
    }

    /** Every span up to a day, read back as a number, is the number it was said for. */
    @Test
    void noSpanUpToADayDisagreesWithItsMinutes() {
        Pattern shape = Pattern.compile("^(?:(an hour|\\d+ hours))?(?: and )?(?:(a minute|\\d+ minutes))?$");
        for (int m = 1; m <= 24 * 60; m++) {
            String said = ChronicleActionService.exactSpan(m);
            Matcher x = shape.matcher(said);
            assertThat(x.matches()).as("'%s' has the shape of a span", said).isTrue();
            int back = part(x.group(1), 60) + part(x.group(2), 1);
            assertThat(back).as("%d minutes said as '%s'", m, said).isEqualTo(m);
        }
    }

    private static int part(String words, int unit) {
        if (words == null) return 0;
        if (words.startsWith("a")) return unit;
        return Integer.parseInt(words.split(" ")[0]) * unit;
    }
}
