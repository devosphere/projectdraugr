package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine's contract: it always produces witness-stance prose, for every scene,
 * without a database and without ever advising the player or naming a Body HUD field.
 * This is what allows the AI narrator to be optional rather than required.
 */
class NarrationEngineTest {

    private final NarrationEngine engine = new NarrationEngine();
    private final NarrationPolicy policy = new NarrationPolicy();

    private NarrationEngine.Scene scene(String intent, String outcome) {
        return new NarrationEngine.Scene(intent, outcome, "TEMPERATE_FOREST", "MIDDAY", "CLEAR", null, null, null);
    }

    /** #30: ground() scales immersion with attention — rich on deliberate looking, terse when heads-down — and never advises. */
    @Test void groundScalesImmersionWithAttention() {
        // Deliberate looking takes the place in: the land, the light, and a sound/smell — three grounded sentences.
        String high = engine.ground("You take what is worth taking.", "TEMPERATE_FOREST", "MORNING", "CLEAR", "HIGH", false);
        assertTrue(high.contains("trees stand close"), high);
        assertTrue(high.contains("light coming in low"), high);
        assertTrue(high.contains("bird falls quiet"), high);
        assertDoesNotThrow(() -> policy.validate(high));
        // Heads-down in clear weather gets no scenery tacked on — the chronicle is not looking.
        assertEquals("You twist the fibre.", engine.ground("You twist the fibre.", "TEMPERATE_FOREST", "MORNING", "CLEAR", "LOW", false));
        // But weather beginning is felt even heads-down.
        assertTrue(engine.ground("You twist the fibre.", "TEMPERATE_FOREST", "MORNING", "RAIN", "LOW", true).contains("Rain moves through"));
    }

    @Test void everyCoveredSceneProducesProse() {
        for (String key : engine.coveredScenes()) {
            String[] parts = key.split("\\|");
            String line = engine.narrate(scene(parts[0], parts[1]));
            assertNotNull(line, key);
            assertFalse(line.isBlank(), key);
        }
    }

    @Test void everyCoveredScenePassesTheWitnessStancePolicy() {
        for (String key : engine.coveredScenes()) {
            String[] parts = key.split("\\|");
            String line = engine.narrate(scene(parts[0], parts[1]));
            assertDoesNotThrow(() -> policy.validate(line), key + " => " + line);
        }
    }

    @Test void noProseNamesABodyHudField() {
        List<String> forbidden = List.of("hunger", "thirst", "blood_loss", "illness_severity",
            "energy_level", "injury_severity", "hygiene", "bladder", "stress_level");
        for (String key : engine.coveredScenes()) {
            String[] parts = key.split("\\|");
            String line = engine.narrate(scene(parts[0], parts[1])).toLowerCase();
            for (String f : forbidden) assertFalse(line.contains(f), key + " leaks " + f);
        }
    }

    @Test void noProseAdvisesThePlayer() {
        List<String> advice = List.of("you should", "you need", "try to", "you must", "in order to", "remember to");
        for (String key : engine.coveredScenes()) {
            String[] parts = key.split("\\|");
            String line = engine.narrate(scene(parts[0], parts[1])).toLowerCase();
            for (String a : advice) assertFalse(line.contains(a), key + " advises: " + line);
        }
    }

    @Test void unknownScenesStillProduceCorrectProse() {
        String line = engine.narrate(scene("SOME_FUTURE_INTENT", "SUCCEEDED"));
        assertFalse(line.isBlank());
        assertDoesNotThrow(() -> policy.validate(line));
    }

    @Test void woundSeverityScalesTheRegister() {
        NarrationEngine.Scene minor = new NarrationEngine.Scene("UNKNOWN_X","PARTIAL",null,null,null,"gray_wolf",8,null);
        NarrationEngine.Scene grave = new NarrationEngine.Scene("UNKNOWN_X","PARTIAL",null,null,null,"gray_wolf",85,null);
        assertFalse(engine.narrate(minor).equals(engine.narrate(grave)));
        assertTrue(engine.narrate(grave).contains("whole weight"));
    }

    @Test void narrationIsDeterministicForTheSameScene() {
        assertEquals(engine.narrate(scene("GATHER_PLANT","SUCCEEDED")), engine.narrate(scene("GATHER_PLANT","SUCCEEDED")));
    }

    @Test void settingVariesWithBiomeAndWeather() {
        String forest = engine.narrate(new NarrationEngine.Scene("GATHER_PLANT","SUCCEEDED","TEMPERATE_FOREST","MIDDAY","CLEAR",null,null,null));
        String storm  = engine.narrate(new NarrationEngine.Scene("GATHER_PLANT","SUCCEEDED","TEMPERATE_FOREST","MIDDAY","STORM",null,null,null));
        assertFalse(forest.equals(storm), "weather must change the line");
    }

    @Test void coversTheCoreSurvivalLoop() {
        for (String key : List.of("LIGHT_FIRE|SUCCEEDED","LIGHT_FIRE|FAILED","EAT|SUCCEEDED","DRINK|SUCCEEDED",
                                  "SLEEP|SUCCEEDED","CONFRONT_WILDLIFE|SUCCEEDED","HARVEST_CARCASS|SUCCEEDED",
                                  "GATHER_PLANT|SUCCEEDED","FELL_TREE|SUCCEEDED","FISH|SUCCEEDED","TRACK|SUCCEEDED",
                                  "WRITE|SUCCEEDED","SKETCH_MAP|SUCCEEDED"))
            assertTrue(engine.coveredScenes().contains(key), "missing coverage: " + key);
    }

    @Test void groundingPunctuatesRatherThanTaggingEveryLine() {
        String c = "You take what is worth taking.";
        // Heads-down work in steady, unchanged clear weather adds nothing.
        assertEquals(c, engine.ground(c, "TEMPERATE_FOREST", "MIDDAY", "CLEAR", "LOW", false));
        // Heads-down in steady rain (not changing) — tuned out, still nothing.
        assertEquals(c, engine.ground(c, "TEMPERATE_FOREST", "MIDDAY", "RAIN", "LOW", false));
        // But the moment the rain starts, it is felt even heads-down.
        assertTrue(engine.ground(c, "TEMPERATE_FOREST", "MIDDAY", "RAIN", "LOW", true).contains("Rain"));
        // Moving through rain feels it.
        assertTrue(engine.ground(c, "TEMPERATE_FOREST", "MIDDAY", "RAIN", "MODERATE", false).contains("Rain"));
        // Deliberate looking adds the land even in clear weather; and no HUD state is ever named.
        String high = engine.ground(c, "TEMPERATE_FOREST", "AFTERNOON", "CLEAR", "HIGH", false);
        assertTrue(high.length() > c.length() && high.contains("trees stand close"));
        for (String hud : List.of("hunger","thirst","energy","health"))
            assertFalse(high.toLowerCase().contains(hud), "grounding must name no HUD state");
    }
}
