package com.devosphere.draugr.ai;

import com.devosphere.draugr.audit.PersistentStateAuditor.AuditReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Auditor's voice is pure over a {@link LanguageModel} and an {@link AuditReport} — the audit
 * itself (which reads the DB) is a separate call, so these run with no DB. Boundary under test: it
 * summarizes on the configured auditor model when enabled, and stays silent when off.
 */
class AuditorSummarizerTest {

    private AiProperties props(boolean enabled) {
        AiProperties p = new AiProperties();
        p.setEnabled(enabled);
        p.setApiKey("test-key");
        p.setAuditorModel("claude-sonnet-4-6");
        return p;
    }

    @Test
    void summarizesOnTheAuditorModelWhenEnabled() {
        String[] captured = new String[2];
        LanguageModel model = (m, system, user) -> { captured[0] = m; captured[1] = user; return Optional.of("The world is consistent; no invariants are violated."); };
        AuditReport report = new AuditReport(true, List.of());

        Optional<String> summary = new AuditorSummarizer(null, model, props(true)).summarize(report);

        assertTrue(summary.isPresent(), "an enabled Auditor voice summarizes the report");
        assertEquals("claude-sonnet-4-6", captured[0], "the Auditor runs on the configured auditor model");
        assertTrue(captured[1].contains("World consistent: true"), "the prompt carries the consistency flag");
    }

    @Test
    void carriesEveryViolationIntoThePrompt() {
        String[] user = new String[1];
        LanguageModel model = (m, system, u) -> { user[0] = u; return Optional.of("summary"); };
        AuditReport report = new AuditReport(false, List.of("orphaned item_instance without world_object", "assembly holds a defective stage"));

        new AuditorSummarizer(null, model, props(true)).summarize(report);

        assertTrue(user[0].contains("Violations (2)"), "the prompt states the violation count");
        assertTrue(user[0].contains("orphaned item_instance"), "the prompt lists each violation");
        assertTrue(user[0].contains("defective stage"), "the prompt lists each violation");
    }

    @Test
    void staysSilentWhenDisabled() {
        LanguageModel model = (m, system, user) -> Optional.of("SHOULD NOT BE CALLED");
        assertTrue(new AuditorSummarizer(null, model, props(false)).summarize(new AuditReport(true, List.of())).isEmpty(),
                "a disabled Auditor voice produces no summary; the raw report still stands on its own");
    }
}
