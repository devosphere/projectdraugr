package com.devosphere.draugr.action;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression cover for intent classification, driven by defects found in live E2E.
 *
 * <p>Classification is a pure text function, so it is reachable by reflection without
 * a database or a Spring context. Every case here failed once against a running
 * stack; each is kept so it cannot fail silently again.
 */
class IntentClassificationRegressionTest {

    /** No material process matches — the ordinary case for the intent phrases below. */
    private String classify(String text) throws Exception { return classify(text, false); }

    /**
     * classify(String) is private and pure over text plus one collaborator: it asks
     * {@link com.devosphere.draugr.item.PhysicalItemService#actionMatchesProcess} whether
     * the text is really a material process, so it can yield ambiguous noun-driven
     * intents (FISH, MARK-by-carving) to the two-axis matcher. That answer is stubbed
     * here — the routing itself is covered by ProcessRoutingTest and live E2E.
     */
    private String classify(String text, boolean processMatches) throws Exception {
        Method m = ChronicleActionService.class.getDeclaredMethod("classify", String.class);
        m.setAccessible(true);
        com.devosphere.draugr.item.PhysicalItemService items =
            new com.devosphere.draugr.item.PhysicalItemService(null, null, null) {
                @Override public boolean actionMatchesProcess(String t) { return processMatches; }
            };
        ChronicleActionService svc = new ChronicleActionService(null, null, null, null, items, null, null, null, null, null, null, null, new com.devosphere.draugr.narration.ActionInputClassifier(), null);
        return ((Enum<?>) m.invoke(svc, text)).name();
    }

    // --- V57 alignment: a large batch of material processes exposed the intent
    // --- classifier's naive substring matching. "salt the fish" is not fishing,
    // --- "carve a spoon" is not marking, "meat" is not "eat", "knap" is not "nap".
    @Test void processActionsAreNotStolenByGreedyIntents() throws Exception {
        // When the two-axis matcher claims the text, the ambiguous intent must yield,
        // so the dispatch falls through to PROCESS_MATERIAL (classify returns UNKNOWN).
        assertEquals("UNKNOWN", classify("salt the fish for winter", true));
        assertEquals("UNKNOWN", classify("gut the fish", true));
        assertEquals("UNKNOWN", classify("weave a fish trap", true));
        assertEquals("UNKNOWN", classify("carve a wooden spoon", true));
        // Genuine fishing and marking still classify when no process matches.
        assertEquals("FISH", classify("fish the stream with a spear", false));
        assertEquals("MARK", classify("carve a blaze into the tree", false));
    }

    /** Substring accidents that pre-date V57 but its vocabulary made reachable. */
    @Test void wholeWordIntentsIgnoreSubstrings() throws Exception {
        // "meat" contains "eat"; "feathers" contains "eat"; "knap" contains "nap".
        assertEquals("UNKNOWN", classify("salt the meat down", false));
        assertEquals("UNKNOWN", classify("fletch the arrows with feathers", false));
        assertEquals("UNKNOWN", classify("knap stone arrowheads", false));
        // The real words still classify.
        assertEquals("EAT", classify("eat the ripe berries", false));
        assertEquals("SLEEP", classify("take a nap by the fire", false));
    }

    /** A named specific basket yields to its process; the generic basket does not. */
    @Test void specificBasketsYieldToTheProcess() throws Exception {
        assertEquals("UNKNOWN", classify("weave a burden basket", false));
        assertEquals("CRAFT_BASKET", classify("weave a basket", false));
    }

    /** Inspect/rework (M3b) claim their patterns before the generic OBSERVE catch. */
    @Test void inspectAndReworkClassify() throws Exception {
        assertEquals("INSPECT", classify("inspect the quality of my materials", false));
        assertEquals("INSPECT", classify("check the bow for flaws", false));
        assertEquals("REWORK", classify("rework the bow", false));
        assertEquals("REWORK", classify("redo the flawed step", false));
        // "inspect" with no quality/assembly context is still plain looking.
        assertEquals("OBSERVE", classify("inspect the clearing", false));
    }

    /** A drying rack is a staged structure (V58), not a shelf — it must reach the fallback. */
    @Test void dryingRackIsNotAShelf() throws Exception {
        assertEquals("UNKNOWN", classify("build a drying rack", false));   // falls through to the assembly engine
        assertEquals("CRAFT_SHELF", classify("build a storage shelf", false));
        assertEquals("CRAFT_SHELF", classify("make a rack", false));       // a bare rack is still a shelf
    }

    /** "plant " contains the insect token "ant " — gathering fibre is not collecting ants. */
    @Test void plantIsNotAnInsect() throws Exception {
        assertEquals("GATHER_FIBER", classify("gather plant fiber from the undergrowth", false));
        assertEquals("GATHER_PLANT", classify("gather herbs and plants", false));
        // The real insects still classify.
        assertEquals("COLLECT_INSECTS", classify("collect ants from the colony", false));
        assertEquals("COLLECT_INSECTS", classify("dig for earthworms", false));
        // Vines are gatherable growth, not fibre-stripping.
        assertEquals("GATHER_PLANT", classify("gather loose vines from the tree", false));
    }

    // --- Found in E2E: "set a snare across the run" resolved to SNARE, so the
    // --- placed-trap path (V46) was unreachable through its most natural phrasing.
    @Test void settingASnareLeavesAPlacedTrap() throws Exception {
        assertEquals("SET_TRAP", classify("set a snare across the run"));
        assertEquals("SET_TRAP", classify("build a deadfall trap"));
        assertEquals("SET_TRAP", classify("place a fish trap in the shallows"));
    }

    /** A bare snaring attempt with no setting verb stays the immediate hand-worked action. */
    @Test void snaringByHandIsStillImmediate() throws Exception {
        assertEquals("SNARE", classify("snare a rabbit"));
    }

    @Test void checkingATrapIsNotSettingOne() throws Exception {
        assertEquals("CHECK_TRAP", classify("check my trap"));
        assertEquals("CHECK_TRAP", classify("inspect the snare"));
    }

    @Test void sprintTwoIntentsClassify() throws Exception {
        assertEquals("GATHER_PLANT", classify("gather mushrooms from the forest floor"));
        assertEquals("FELL_TREE",    classify("fell the oak tree with my stone axe"));
        assertEquals("RAID_HIVE",    classify("raid the honeybee hive using smoke"));
        assertEquals("COLLECT_INSECTS", classify("dig for earthworms"));
        assertEquals("FISH",         classify("fish the stream with a spear"));
        assertEquals("TRACK",        classify("look for tracks on the ground"));
        assertEquals("TAME",         classify("approach the goat calmly and offer food"));
        assertEquals("LURE",         classify("leave bait to draw them in"));
    }

    @Test void interceptedInputsStillClassify() throws Exception {
        assertEquals("OBSERVE", classify("look around carefully"));
    }

    // --- Garment work must not be swallowed by the furniture or tool craft branches,
    // --- which both match "craft"/"make" and appear earlier in the chain.
    @Test void garmentCraftingIsReachable() throws Exception {
        assertEquals("CRAFT_GARMENT", classify("sew a hide coat"));
        assertEquals("CRAFT_GARMENT", classify("make a fur cloak"));
        assertEquals("CRAFT_GARMENT", classify("stitch hide leggings"));
        assertEquals("CRAFT_GARMENT", classify("weave a tunic"));
        assertEquals("CRAFT_GARMENT", classify("craft hide boots"));
    }

    /** The older craft intents must keep working — garment matching is additive. */
    @Test void existingCraftIntentsAreUnaffected() throws Exception {
        assertEquals("CRAFT_SPEAR", classify("craft a spear"));
        assertEquals("CRAFT_KNIFE", classify("make a knife"));
        assertEquals("CRAFT_HATCHET", classify("craft a stone hatchet")); // the felling tool
        assertEquals("CRAFT_BASKET", classify("weave a basket"));
        assertEquals("CRAFT_DESK", classify("build a desk"));
    }

    @Test void lightingAFireStillClassifies() throws Exception {
        assertEquals("LIGHT_FIRE", classify("light a fire with the bow drill"));
        assertEquals("LIGHT_FIRE", classify("ignite a fire by striking flint against pyrite"));
    }

    // --- The reachability invariant caught that five of the nine V49 fire methods
    // --- had kit nobody could make. These guard the crafting path that fixed it.
    @Test void ignitionKitCanBeMade() throws Exception {
        assertEquals("CRAFT_FIRE_TOOL", classify("carve a fire bow"));
        assertEquals("CRAFT_FIRE_TOOL", classify("make a bearing block"));
        assertEquals("CRAFT_FIRE_TOOL", classify("cut a fire plough board"));
        assertEquals("CRAFT_FIRE_TOOL", classify("prepare an ember bundle"));
        assertEquals("CRAFT_FIRE_TOOL", classify("make charred tinder"));
    }

    /** Making the kit must not be mistaken for trying to light something with it. */
    @Test void makingKitIsNotLightingAFire() throws Exception {
        assertEquals("LIGHT_FIRE", classify("spin the bow drill"));
        assertEquals("CRAFT_FIRE_TOOL", classify("carve a fire bow"));
    }

    // --- V49 added flint, pyrite and crystal as fire kit with no way to obtain them.
    // --- V50 makes them findable; these guard that the search is actually reachable
    // --- and is not mistaken for an attempt to strike a light.
    @Test void mineralsCanBeSearchedFor() throws Exception {
        assertEquals("GATHER_MINERAL", classify("search the rocks for pyrite"));
        assertEquals("GATHER_MINERAL", classify("look for flint in the chalk"));
        assertEquals("GATHER_MINERAL", classify("prospect for quartz crystal"));
        assertEquals("GATHER_MINERAL", classify("gather tool stone"));
        assertEquals("GATHER_MINERAL", classify("dig for ore in the hillside"));
    }

    /** "forest" contains "ore" — a word-boundary bug that stole plant gathering. */
    @Test void oreDoesNotMatchInsideForest() throws Exception {
        assertEquals("GATHER_PLANT", classify("gather mushrooms from the forest floor"));
        assertEquals("GATHER_PLANT", classify("collect herbs in the forest"));
    }

    // --- Found in E2E: naming a real technique without the word "fire" fell through
    // --- to UNKNOWN, so most of the V49 method vocabulary was unreachable in play.
    @Test void namingATechniqueIsAskingForFire() throws Exception {
        assertEquals("LIGHT_FIRE", classify("strike flint against pyrite"));
        assertEquals("LIGHT_FIRE", classify("spin the bow drill"));
        assertEquals("LIGHT_FIRE", classify("work the hand drill between my palms"));
        assertEquals("LIGHT_FIRE", classify("use the fire plough"));
        assertEquals("LIGHT_FIRE", classify("focus the sun onto the tinder with a lens"));
        assertEquals("LIGHT_FIRE", classify("carry an ember from the old hearth"));
    }
}
