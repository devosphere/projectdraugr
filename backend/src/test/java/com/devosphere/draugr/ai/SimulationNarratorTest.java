package com.devosphere.draugr.ai;

import com.devosphere.draugr.action.ChronicleActionService.LocationView;
import com.devosphere.draugr.action.ChronicleActionService.PerceptionFrame;
import com.devosphere.draugr.action.ChronicleActionService.StateChange;
import com.devosphere.draugr.action.ChronicleActionService.WeatherView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narrator is pure over a {@link LanguageModel} and {@link AiProperties} — no network, no key,
 * no DB. A stub model stands in for the API so every branch of the fallback contract is asserted.
 */
class SimulationNarratorTest {

    private static final String DETERMINISTIC =
            "The oak comes down with a crack that carries across the ground.";

    private PerceptionFrame frame() {
        return new PerceptionFrame(
                "FELL_TREE", "SUCCEEDED",
                new LocationView(UUID.randomUUID(), "Uncharted forest", "TEMPERATE_FOREST", 3, 4),
                "MORNING", new WeatherView("RAIN", 2), "HIGH",
                List.of("a fallen cedar"), null,
                List.of(new StateChange("energy", "Rested", "Tired")),
                DETERMINISTIC);
    }

    private AiProperties props(boolean enabled) {
        AiProperties p = new AiProperties();
        p.setEnabled(enabled);
        p.setApiKey("test-key");
        return p;
    }

    @Test
    void appendsOneSentenceWhenEnabledAndModelResponds() {
        LanguageModel model = (system, user) -> Optional.of("Rain ticks against the fresh stump.");
        SimulationNarrator narrator = new SimulationNarrator(model, props(true));

        String out = narrator.refine(frame(), DETERMINISTIC);

        assertEquals(DETERMINISTIC + " Rain ticks against the fresh stump.", out,
                "an enabled narrator appends exactly the model's sentence to the deterministic prose");
    }

    @Test
    void returnsDeterministicProseWhenDisabled() {
        // Even a chatty model is never called when the feature is off.
        LanguageModel model = (system, user) -> Optional.of("SHOULD NOT APPEAR");
        SimulationNarrator narrator = new SimulationNarrator(model, props(false));

        assertEquals(DETERMINISTIC, narrator.refine(frame(), DETERMINISTIC),
                "a disabled narrator returns the deterministic prose unchanged");
    }

    @Test
    void fallsBackToDeterministicProseWhenModelReturnsEmpty() {
        // Timeout / transport error / refusal all surface as Optional.empty().
        LanguageModel model = (system, user) -> Optional.empty();
        SimulationNarrator narrator = new SimulationNarrator(model, props(true));

        assertEquals(DETERMINISTIC, narrator.refine(frame(), DETERMINISTIC),
                "an empty model result must fall back to the deterministic prose");
    }

    @Test
    void fallsBackWhenModelReturnsBlank() {
        LanguageModel model = (system, user) -> Optional.of("   ");
        SimulationNarrator narrator = new SimulationNarrator(model, props(true));

        assertEquals(DETERMINISTIC, narrator.refine(frame(), DETERMINISTIC),
                "a blank completion is treated as no completion");
    }

    @Test
    void theContextPromptCarriesTheFrameFacts() {
        // Capture the user prompt to confirm the frame's facts reach the model.
        String[] captured = new String[2];
        LanguageModel model = (system, user) -> { captured[0] = system; captured[1] = user; return Optional.of("x"); };
        new SimulationNarrator(model, props(true)).refine(frame(), DETERMINISTIC);

        assertTrue(captured[0].contains("ONE sentence"), "the system prompt states the one-sentence rule");
        assertTrue(captured[1].contains("FELL_TREE"), "the user prompt carries the intent");
        assertTrue(captured[1].contains("TEMPERATE_FOREST"), "the user prompt carries the biome");
        assertTrue(captured[1].contains(DETERMINISTIC), "the user prompt carries the existing narration");
        assertTrue(captured[1].contains("energy Rested"), "the user prompt carries the state change");
    }
}
