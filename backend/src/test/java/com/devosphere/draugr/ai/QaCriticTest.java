package com.devosphere.draugr.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The QA critic against a stub model — PASS passes, FAIL fails, and it is fail-CLOSED on off/no-answer. */
class QaCriticTest {

    private AiProperties enabled() { AiProperties p = new AiProperties(); p.setEnabled(true); p.setApiKey("test-key-not-a-real-secret"); return p; }

    private ProcessDraft draft() {
        return new ProcessDraft("p", "PROCESS", "k", "s", null,
            List.of(new ProcessDraft.Ingredient("plant_fiber", 1)), "cord", 1, "You work it.", List.of());
    }

    @Test void passReplyPasses() {
        assertTrue(new QaCritic((m, s, u) -> Optional.of("PASS"), enabled()).review(draft()).passed());
    }

    @Test void failReplyFailsWithReason() {
        var v = new QaCritic((m, s, u) -> Optional.of("FAIL: skips the retting step"), enabled()).review(draft());
        assertFalse(v.passed());
        assertTrue(v.reasons().toLowerCase().contains("retting"));
    }

    @Test void failClosedWhenDisabled() {
        assertFalse(new QaCritic((m, s, u) -> Optional.of("PASS"), new AiProperties()).review(draft()).passed());
    }

    @Test void failClosedOnNoResponse() {
        assertFalse(new QaCritic((m, s, u) -> Optional.empty(), enabled()).review(draft()).passed());
    }
}
