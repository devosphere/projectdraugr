package com.devosphere.draugr.ai;

import com.devosphere.draugr.ai.PersistentStateArchitect.ArchitectProposal;
import com.devosphere.draugr.ai.PersistentStateArchitect.BacklogEntry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Architect's proposal path is pure over a {@link LanguageModel} — the backlog read (jdbc) is
 * separate, so these run with no DB. The boundary under test: it drafts a proposal on the configured
 * architect model, and it stays silent (proposes nothing) when the feature is off.
 */
class PersistentStateArchitectTest {

    private AiProperties props(boolean enabled) {
        AiProperties p = new AiProperties();
        p.setEnabled(enabled);
        p.setApiKey("test-key");
        p.setArchitectModel("claude-opus-4-8");
        return p;
    }

    private BacklogEntry mechanicGap() {
        return new BacklogEntry("ferment the berries into wine", "ferment the berries into wine",
                "PROCESS", null, 7, "MECHANIC");
    }

    @Test
    void draftsAProposalOnTheArchitectModelWhenEnabled() {
        String[] usedModel = new String[1];
        LanguageModel model = (m, system, user) -> {
            usedModel[0] = m;
            return Optional.of("INSERT INTO material_process (...) VALUES (...);\nRationale: fermentation is a real primitive process.");
        };

        Optional<ArchitectProposal> proposal = new PersistentStateArchitect(null, model, props(true)).propose(mechanicGap());

        assertTrue(proposal.isPresent(), "an enabled Architect drafts a proposal for a gap");
        assertEquals("claude-opus-4-8", usedModel[0], "the Architect runs on the configured architect model");
        assertEquals("MECHANIC", proposal.get().gapKind());
        assertTrue(proposal.get().draftMigration().contains("Rationale:"), "the draft carries a rationale");
    }

    @Test
    void proposesNothingWhenDisabled() {
        LanguageModel model = (m, system, user) -> Optional.of("SHOULD NOT BE CALLED");
        assertTrue(new PersistentStateArchitect(null, model, props(false)).propose(mechanicGap()).isEmpty(),
                "a disabled Architect never proposes — the backlog just waits, as it does with no AI at all");
    }

    @Test
    void proposesNothingWhenModelDeclines() {
        LanguageModel model = (m, system, user) -> Optional.empty();
        assertTrue(new PersistentStateArchitect(null, model, props(true)).propose(mechanicGap()).isEmpty(),
                "a model failure/refusal leaves the gap unproposed");
    }
}
