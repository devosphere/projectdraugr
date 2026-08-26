package com.devosphere.draugr.ai;

import com.devosphere.draugr.action.ChronicleActionService.PerceptionFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Simulation Narrator seam — role 3 of the generative pipeline (Interpreter→Architect→Narrator, #244).
 * It appends exactly one AI sentence of atmosphere to the deterministic prose when the feature is on, and
 * returns the deterministic prose unchanged on anything less than a clean success. Proven here as a fast
 * unit test (no Spring, no DB) against a stub {@link LanguageModel}, so the narration half of #244 is a
 * CI-guarded contract without a real key. Together with the compose/author integration tests, all three
 * AI roles are now proven to fire.
 */
class SimulationNarratorTest {

    private static AiProperties props(boolean enabled, String key) {
        AiProperties p = new AiProperties();
        p.setEnabled(enabled);
        p.setApiKey(key);
        return p; // per-agent flags (incl. narration) default on, so master + key ⇒ narration active
    }

    private static PerceptionFrame frame(String backend) {
        return new PerceptionFrame("OBSERVE", "SUCCEEDED", null, "morning", null, "HIGH", null, null, List.of(), backend);
    }

    @Test
    void appendsExactlyOneAiSentenceToTheDeterministicProseWhenActive() {
        LanguageModel stub = (model, system, user) -> Optional.of("A cold hush settles over the wet ground.");
        SimulationNarrator narrator = new SimulationNarrator(stub, props(true, "test-key"));

        String out = narrator.refine(frame("You look around."), "You look around.");

        assertEquals("You look around. A cold hush settles over the wet ground.", out,
            "with the feature on, the narrator appends one AI sentence to the deterministic prose");
    }

    @Test
    void returnsTheDeterministicProseUnchangedWhenTheMasterIsOff() {
        LanguageModel stub = (model, system, user) -> Optional.of("this sentence must never appear");
        SimulationNarrator narrator = new SimulationNarrator(stub, props(false, "test-key")); // master OFF

        assertEquals("You look around.", narrator.refine(frame("You look around."), "You look around."),
            "with the master off the AI layer is inert — deterministic prose only");
    }

    @Test
    void returnsTheDeterministicProseWhenTheModelYieldsNothing() {
        LanguageModel stub = (model, system, user) -> Optional.empty(); // outage / refusal / blank
        SimulationNarrator narrator = new SimulationNarrator(stub, props(true, "test-key"));

        assertEquals("You look around.", narrator.refine(frame("You look around."), "You look around."),
            "a model that produces nothing degrades voice, never correctness");
    }

    @Test
    void sendsTheWitnessConstraintsAndTheExistingNarrationToTheModel() {
        AtomicReference<String> sysSeen = new AtomicReference<>();
        AtomicReference<String> userSeen = new AtomicReference<>();
        LanguageModel capturing = (model, system, user) -> { sysSeen.set(system); userSeen.set(user); return Optional.of("."); };
        SimulationNarrator narrator = new SimulationNarrator(capturing, props(true, "test-key"));

        narrator.refine(frame("You crouch by the stream."), "You crouch by the stream.");

        assertNotNull(sysSeen.get(), "the narrator must call the model");
        assertTrue(sysSeen.get().contains("witness narrator"), "the system prompt must carry the witness-stance role");
        assertTrue(sysSeen.get().contains("EXACTLY ONE sentence"), "the system prompt must hold the one-sentence constraint");
        assertTrue(userSeen.get().contains("You crouch by the stream."), "the user turn must include the deterministic narration as context");
    }
}
