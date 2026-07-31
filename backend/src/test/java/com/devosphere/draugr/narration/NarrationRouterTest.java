package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The router's contract, and with it the game's API bill. Every branch is covered
 * here because a mistake in this class is not a wrong sentence — it is either a
 * silent cost leak or a moment that should have been narrated well and was not.
 */
class NarrationRouterTest {

    private final NarrationRouter router = new NarrationRouter();

    // --- Always AI: the moments that only happen once -----------------------

    @Test void deathAlwaysGetsTheCall() {
        assertTrue(router.shouldUseAI("SLEEP","SUCCEEDED","LOW","sleep",0,0,null,true,false));
    }
    @Test void fatalOutcomeAlwaysGetsTheCall() {
        assertTrue(router.shouldUseAI("CONFRONT_WILDLIFE","FATAL_TRAUMA","LOW","fight",0,0,null,false,false));
    }
    @Test void deadOutcomeAlwaysGetsTheCall() {
        assertTrue(router.shouldUseAI("REST","CHRONICLE_DEAD","LOW","rest",0,0,null,false,false));
    }
    @Test void findingAnotherChroniclesLegacyGetsTheCall() {
        assertTrue(router.shouldUseAI("OBSERVE","DISCOVERY_OTHER_CHRONICLE","MODERATE","look",0,0,null,false,false));
    }
    @Test void tamingThresholdGetsTheCall() {
        assertTrue(router.shouldUseAI("TAME","SUCCEEDED","LOW","approach the goat",0,0,"mountain_goat",false,true));
    }
    @Test void monsterEncounterGetsTheCall() {
        assertTrue(router.shouldUseAI("CONFRONT_WILDLIFE","PARTIAL","LOW","fight it",0,10,"wyvern",false,false));
    }
    @Test void everyMonsterSpeciesGetsTheCall() {
        for (String m : new String[]{"cave_troll","dire_wolf","bog_wraith","wyvern","giant_bat_swarm","harpy","roc","giant_hornet_queen","locust_swarm"})
            assertTrue(router.shouldUseAI("CONFRONT_WILDLIFE","PARTIAL","LOW","x",0,1,m,false,false), m);
    }
    @Test void seriousWoundGetsTheCall() {
        assertTrue(router.shouldUseAI("CONFRONT_WILDLIFE","PARTIAL","LOW","fight",0,35,"gray_wolf",false,false));
    }
    @Test void writingAlwaysGetsTheCall() {
        assertTrue(router.shouldUseAI("WRITE","SUCCEEDED","LOW","write: notes",0));
        assertTrue(router.shouldUseAI("EDIT_DOCUMENT","SUCCEEDED","LOW","append",0));
        assertTrue(router.shouldUseAI("SKETCH_MAP","SUCCEEDED","LOW","map",0));
    }
    @Test void deliberateObservationGetsTheCall() {
        assertTrue(router.shouldUseAI("OBSERVE","SUCCEEDED","HIGH","look around carefully",0));
    }

    // --- Always deterministic: the routine majority --------------------------

    @Test void equippingNeverGetsTheCall() {
        assertFalse(router.shouldUseAI("EQUIP","SUCCEEDED","LOW","equip spear",0));
        assertFalse(router.shouldUseAI("UNEQUIP","SUCCEEDED","LOW","take off",0));
        assertFalse(router.shouldUseAI("DROP","SUCCEEDED","LOW","drop it",0));
    }
    @Test void routineGatheringNeverGetsTheCall() {
        for (String i : new String[]{"GATHER_PLANT","GATHER_CLAY","GATHER_STONE","GATHER_FIBER","GATHER_BERRIES","FELL_TREE","COLLECT_INSECTS"})
            assertFalse(router.shouldUseAI(i,"SUCCEEDED","LOW","gather",0), i);
    }
    @Test void interceptedInputsNeverGetTheCall() {
        assertFalse(router.shouldUseAI("PERSONAL_ACT","SUCCEEDED","LOW","masturbate",0));
        assertFalse(router.shouldUseAI("AGGRESSION_INANIMATE","SUCCEEDED","LOW","fuck this tree",0));
        assertFalse(router.shouldUseAI("NONSENSICAL","NO_EFFECT","LOW","asdf",0));
        assertFalse(router.shouldUseAI("PHYSICALLY_IMPOSSIBLE","NO_EFFECT","LOW","fly away",0));
    }
    @Test void deterministicIntentsBeatLongText() {
        String essay = "x".repeat(500);
        assertFalse(router.shouldUseAI("EQUIP","SUCCEEDED","LOW",essay,0),
            "an explicitly routine act stays routine however much was typed");
    }
    @Test void deterministicIntentsBeatStateChanges() {
        assertFalse(router.shouldUseAI("GATHER_CLAY","SUCCEEDED","LOW","dig clay",5));
    }
    @Test void placingTrapsAndLuresIsRoutine() {
        assertFalse(router.shouldUseAI("SET_TRAP","SUCCEEDED","LOW","set a snare",0));
        assertFalse(router.shouldUseAI("LURE","SUCCEEDED","LOW","leave bait",0));
    }

    // --- Conditional ---------------------------------------------------------

    @Test void aLongDeliberateDescriptionEarnsTheCall() {
        assertTrue(router.shouldUseAI("LIGHT_FIRE","SUCCEEDED","MODERATE","x".repeat(301),0));
    }
    @Test void aShortDescriptionOfTheSameActDoesNot() {
        assertFalse(router.shouldUseAI("LIGHT_FIRE","SUCCEEDED","MODERATE","light the fire",0));
    }
    @Test void severalSimultaneousChangesEarnTheCall() {
        assertTrue(router.shouldUseAI("TRAVEL","SUCCEEDED","MODERATE","go north",2));
    }
    @Test void oneChangeDoesNot() {
        assertFalse(router.shouldUseAI("TRAVEL","SUCCEEDED","MODERATE","go north",1));
    }
    @Test void minorWoundDoesNotEarnTheCall() {
        assertFalse(router.shouldUseAI("CONFRONT_WILDLIFE","PARTIAL","LOW","fight",0,12,"hare",false,false));
    }
    @Test void lowAttentionRoutineActionIsDeterministic() {
        assertFalse(router.shouldUseAI("COOK_MEAT","SUCCEEDED","LOW","cook meat",0));
    }
    @Test void nullsAreSafe() {
        assertFalse(router.shouldUseAI(null,null,null,null,0,0,null,false,false));
    }

    // --- Cost shape ----------------------------------------------------------

    @Test void atypicalSessionStaysOverwhelminglyDeterministic() {
        // A realistic 20-action stretch: mostly work, a couple of moments that matter.
        Object[][] session = {
            {"GATHER_FIBER","SUCCEEDED","LOW","gather fiber",0}, {"GATHER_STONE","SUCCEEDED","LOW","get stones",0},
            {"CRAFT_BASKET","SUCCEEDED","LOW","weave a basket",0}, {"EQUIP","SUCCEEDED","LOW","equip basket",0},
            {"GATHER_CLAY","SUCCEEDED","LOW","dig clay",0}, {"MOVE","SUCCEEDED","MODERATE","go north",0},
            {"GATHER_PLANT","SUCCEEDED","LOW","pick mushrooms",0}, {"FELL_TREE","SUCCEEDED","LOW","fell the oak",0},
            {"BUILD_FIRE_PIT","SUCCEEDED","LOW","build a fire pit",0}, {"LIGHT_FIRE","FAILED","LOW","light fire",0},
            {"LIGHT_FIRE","SUCCEEDED","LOW","light fire",0}, {"COOK_MEAT","SUCCEEDED","LOW","cook the meat",0},
            {"EAT","SUCCEEDED","LOW","eat",0}, {"DRINK","SUCCEEDED","LOW","drink",0},
            {"SET_TRAP","SUCCEEDED","LOW","set a snare",0}, {"REST","SUCCEEDED","LOW","rest",0},
            {"COLLECT_INSECTS","SUCCEEDED","LOW","dig for worms",0}, {"FISH","SUCCEEDED","LOW","fish with spear",0},
            {"SLEEP","SUCCEEDED","LOW","sleep",0}, {"TRACK","SUCCEEDED","LOW","look for tracks",0}};
        int calls = 0;
        for (Object[] a : session)
            if (router.shouldUseAI((String)a[0],(String)a[1],(String)a[2],(String)a[3],(Integer)a[4])) calls++;
        assertTrue(calls <= 3, "expected a routine stretch to stay nearly free, but it made " + calls + " calls");
    }
}
