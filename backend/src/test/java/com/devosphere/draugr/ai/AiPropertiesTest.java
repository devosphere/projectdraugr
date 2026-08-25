package com.devosphere.draugr.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI enablement contract and model-id currency (#244). Two things must hold at all times: the generative pipeline
 * is OFF by default and inert without a human-supplied key (the credential boundary), and the per-agent model ids are
 * current valid identifiers — a stale id does not fail the build or CI (AI is off), but the moment the pipeline is
 * enabled it makes every call fail silently, looking identical to "nothing happened". A fast, Docker-free guard.
 */
class AiPropertiesTest {

    @Test
    void theGenerativePipelineIsOffByDefaultAndNeedsBothMasterAndKey() {
        AiProperties p = new AiProperties();
        assertFalse(p.isEnabled(), "AI must be OFF by default — the master switch is never committed on");
        assertFalse(p.isUsable(), "without a key the pipeline must be inert, whatever the flags say");
        assertFalse(p.isAuthoringActive(), "the Architect must not create mechanics until master AND key are set");

        p.setEnabled(true);
        assertFalse(p.isUsable(), "the master alone is not enough — a human-supplied key is required");

        p.setApiKey("sk-test-not-a-real-key");
        assertTrue(p.isUsable(), "master plus a key makes the pipeline usable");
        assertTrue(p.isAuthoringActive(), "and the per-agent switches default on, so the one master lights the whole pipeline");
    }

    @Test
    void modelIdsAreCurrentNotTheStaleIdsThatFailSilently() {
        AiProperties p = new AiProperties();
        // The specific stale id the diagnosis (#244) found — there is no Sonnet 4.6.
        assertNotEquals("claude-sonnet-4-6", p.getInterpreterModel(), "interpreter model must not be the stale sonnet id (#244)");
        assertNotEquals("claude-sonnet-4-6", p.getAuditorModel(), "auditor model must not be the stale sonnet id (#244)");
        // Each agent is served by the intended model family.
        assertTrue(p.getInterpreterModel().contains("sonnet"), "the interpreter is a Sonnet model: " + p.getInterpreterModel());
        assertTrue(p.getAuditorModel().contains("sonnet"), "the auditor is a Sonnet model: " + p.getAuditorModel());
        assertTrue(p.getNarrationModel().contains("haiku"), "the narrator is a Haiku model: " + p.getNarrationModel());
        assertTrue(p.getArchitectModel().contains("opus"), "the architect is an Opus model: " + p.getArchitectModel());
        assertTrue(p.getQaModel().contains("opus"), "the QA critic is an Opus model: " + p.getQaModel());
    }
}
