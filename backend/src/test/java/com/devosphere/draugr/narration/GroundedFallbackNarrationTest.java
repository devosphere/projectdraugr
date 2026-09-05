package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #30 names one line in the game outright: template narration "must not use generic phrases such as 'nothing
 * answers to the attempt' for a normal recognised action", and must witness the visible setting and the
 * weather/time. The unresolved-attempt line did neither — it was grounded at LOW attention, which adds nothing
 * at all, so the one narration a player sees when the world cannot help them was the barest in the game.
 *
 * <p>These are pure-function checks on the engine, so they run without Docker or a Spring context.
 */
class GroundedFallbackNarrationTest {

    private final NarrationEngine engine = new NarrationEngine();

    private static final String FLAT =
        "You work at it for a while, but nothing here answers to the attempt, and the moment passes into the rest.";

    @Test
    void anUnresolvedAttemptIsWitnessedInARealPlace() {
        String grounded = engine.ground(FLAT, "TEMPERATE_FOREST", "MORNING", "CLEAR", "HIGH", false);
        assertNotEquals(FLAT, grounded, "the flat line must not reach the player unaccompanied by the world");
        assertTrue(grounded.startsWith(FLAT), "the witness core is preserved; setting is added, never substituted");
        assertTrue(grounded.contains("trees"), "the forest the Chronicle is standing in must be present: " + grounded);
        assertTrue(grounded.split("(?<=\\.)\\s+").length >= 3,
            "a failed attempt is where a person looks up — this should read as more than one bare sentence: " + grounded);
    }

    @Test
    void theGroundedFallbackStillRefusesToHint() {
        String grounded = engine.ground(FLAT, "WETLAND", "DUSK", "RAIN", "HIGH", false);
        String lower = grounded.toLowerCase(java.util.Locale.ROOT);
        // Setting only. A failure is exactly where a hint would be most tempting and most forbidden.
        for (String forbidden : new String[]{"you need", "you should", "try ", "first make", "requires", "instead"})
            assertFalse(lower.contains(forbidden),
                "the fallback must witness, never advise — found \"" + forbidden + "\" in: " + grounded);
    }

    @Test
    void weatherReachesTheLineEvenWhenTheChronicleIsHeadsDown() {
        // Heads-down work tunes steady weather out, but a front CHANGING is felt whatever you are doing.
        assertEquals(FLAT, engine.ground(FLAT, "GRASSLAND", "MIDDAY", "RAIN", "LOW", false),
            "steady rain during heads-down work is tuned out");
        assertTrue(engine.ground(FLAT, "GRASSLAND", "MIDDAY", "RAIN", "LOW", true).length() > FLAT.length(),
            "the moment the weather turns, it is felt");
    }

    /** #156 put running water in the world; an unmapped biome silently drops every grounding clause. */
    @Test
    void aRiverBankIsAPlaceTheProseKnows() {
        String grounded = engine.ground(FLAT, "RIVER_BANK", "AFTERNOON", "CLEAR", "HIGH", false);
        assertNotEquals(FLAT, grounded, "a river bank must ground the prose like any other ground");
        assertTrue(grounded.toLowerCase(java.util.Locale.ROOT).contains("river"),
            "standing on a river bank, the river should be in the line: " + grounded);
    }

    /** Same scene, same prose — narration is deterministic, so history and tests both stay stable. */
    @Test
    void groundingIsDeterministic() {
        assertEquals(engine.ground(FLAT, "MOUNTAIN", "DAWN", "SNOW", "HIGH", false),
                     engine.ground(FLAT, "MOUNTAIN", "DAWN", "SNOW", "HIGH", false));
    }
}
