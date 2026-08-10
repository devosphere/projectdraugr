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
        ChronicleActionService svc = new ChronicleActionService(null, null, null, null, items, null, null, null, null, null, null, null, new com.devosphere.draugr.narration.ActionInputClassifier(), null, null, null, new com.devosphere.draugr.narration.NarrationEngine(), (com.devosphere.draugr.ai.RuntimeAuthoringService) null, (ExaminationService) null);
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

    /**
     * The #61 staged shelters route through the assembly engine, which is reached only when
     * classify() yields UNKNOWN. Two natural build phrases used to be stolen by greedy intents —
     * "fishing landing" by FISH and "sleeping platform" by SLEEP — so those are now guarded, while
     * genuine fishing and sleeping still classify.
     */
    @Test void stagedShelterPhrasesReachTheAssemblyEngine() throws Exception {
        assertEquals("UNKNOWN", classify("build a fishing landing", false));
        assertEquals("UNKNOWN", classify("build a landing stage", false));
        assertEquals("UNKNOWN", classify("build a raised sleeping platform", false));
        assertEquals("UNKNOWN", classify("build a wattle and daub hut", false));
        assertEquals("UNKNOWN", classify("build an earth sheltered hut", false));
        assertEquals("UNKNOWN", classify("build a wood store", false));
        assertEquals("UNKNOWN", classify("build a footbridge", false));
        assertEquals("UNKNOWN", classify("build a clay lined hearth", false));
        // The real verbs still classify.
        assertEquals("FISH", classify("fish the river with a line", false));
        assertEquals("SLEEP", classify("sleep for a few hours", false));
    }

    /**
     * #71 camp upkeep: make_bed and maintain_camp classify, without stealing "bed down" (sleep) or the
     * raised-platform assembly phrase; and the cooking-verb aliases reach COOK_MEAT when flesh is named.
     */
    @Test void campUpkeepAndCookingAliasesClassify() throws Exception {
        assertEquals("MAKE_BED", classify("make a bed of dry grass", false));
        assertEquals("MAKE_BED", classify("prepare bedding for the night", false));
        assertEquals("MAKE_BED", classify("lay a bed of reeds", false));
        assertEquals("MAINTAIN_CAMP", classify("tidy the camp", false));
        assertEquals("MAINTAIN_CAMP", classify("arrange the campsite", false));
        assertEquals("MAINTAIN_CAMP", classify("protect the camp supplies", false));
        // "bed down for the night" is sleeping, and the raised platform is an assembly, not a bed.
        assertEquals("SLEEP", classify("bed down for the night", false));
        assertEquals("UNKNOWN", classify("build a raised sleeping platform", false));
        // Cooking verbs reach COOK_MEAT when flesh is named.
        assertEquals("COOK_MEAT", classify("grill the meat over the fire", false));
        assertEquals("COOK_MEAT", classify("stew the game in a pot", false));
        assertEquals("COOK_MEAT", classify("bake the meat in the coals", false));
    }

    /**
     * #70 structure repair/maintain routes on structure nouns (a construction, not a carried tool), while item
     * repair (#69) and the lean-to's own repair path keep what is theirs.
     */
    @Test void structureRepairClassifiesApartFromItemRepair() throws Exception {
        assertEquals("REPAIR_STRUCTURE", classify("mend the fence", false));
        assertEquals("REPAIR_STRUCTURE", classify("weatherproof the hut", false));
        assertEquals("REPAIR_STRUCTURE", classify("patch the roof thatch", false));
        assertEquals("REPAIR_STRUCTURE", classify("shore up the bridge", false));
        // Carried gear still goes to item repair; the lean-to keeps its own repair path.
        assertEquals("REPAIR_ITEM", classify("repair my stone knife", false));
        assertEquals("REPAIR_ITEM", classify("mend the fishing net", false));
        assertEquals("REPAIR_LEAN_TO", classify("repair the lean-to", false));
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

    /**
     * The examination verbs (#25): ANALYZE and INVESTIGATE own their words and carry the two new
     * masteries (insight, knowledge); EXAMINE claims a focused inspect of one pointed-at object.
     */
    @Test void examinationVerbsClassify() throws Exception {
        assertEquals("ANALYZE", classify("analyze the strange stone"));
        assertEquals("ANALYZE", classify("analyse my knife"));
        assertEquals("INVESTIGATE", classify("investigate the ruined wall"));
        // A pointed determiner marks a specific object to examine.
        assertEquals("EXAMINE", classify("examine my knife"));
        assertEquals("EXAMINE", classify("inspect this branch"));
        assertEquals("EXAMINE", classify("examine that carcass"));
        // Bare/scenery looking stays the whole-surroundings survey, not a focused examination.
        assertEquals("OBSERVE", classify("examine the area"));
        assertEquals("OBSERVE", classify("look around the clearing"));
    }

    /**
     * #33: "inspect/examine the <subject>" scopes to that subject (routes to the subject-resolving EXAMINE),
     * not the broad OBSERVE survey — while genuine scenery ("the area/clearing/around") stays OBSERVE.
     */
    @Test void inspectingASubjectScopesToIt() throws Exception {
        assertEquals("EXAMINE", classify("inspect the branch"));
        assertEquals("EXAMINE", classify("inspect the woven basket"));
        assertEquals("EXAMINE", classify("examine the carcass"));
        assertEquals("EXAMINE", classify("inspect the fire pit"));
        assertEquals("EXAMINE", classify("examine a strange stone"));
        // Scenery-scoped looks remain the whole-scene survey.
        assertEquals("OBSERVE", classify("inspect the clearing"));
        assertEquals("OBSERVE", classify("inspect the area around me"));
        assertEquals("OBSERVE", classify("examine the surroundings"));
        // Quality/assembly inspection still claims its context first (M3b).
        assertEquals("INSPECT", classify("inspect the quality of my materials"));
    }

    /** #32: bathing/taking a bath is washing; #36/#43/#44: making a net is an explicit craft, not fishing. */
    @Test void bathIsWashingAndMakingANetIsNotFishing() throws Exception {
        assertEquals("WASH", classify("take a bath in the stream"));
        assertEquals("WASH", classify("bathe in the river"));
        assertEquals("WASH", classify("wash myself"));
        // "fishing net" / "weave a fish net" contain "fish" but are CRAFTING a net — the explicit CRAFT_NET
        // route (#36/#43/#44), never angling. It holds even when a process matcher would claim it.
        assertEquals("CRAFT_NET", classify("weave a fishing net", true));
        assertEquals("CRAFT_NET", classify("knot a fish net from cordage"));
        assertEquals("CRAFT_NET", classify("make a landing net"));
        assertEquals("CRAFT_NET", classify("braid a net from the cordage"));
        // Using a net to fish is still fishing, not net-making.
        assertEquals("FISH", classify("cast the net across the pool"));
        assertEquals("FISH", classify("haul the net in"));
        // Genuine fishing still classifies.
        assertEquals("FISH", classify("fish the stream with a spear"));
    }

    /** #35: the primitive utility belt is an explicit craft; wearing one is not making one. */
    @Test void makingAUtilityBeltClassifies() throws Exception {
        assertEquals("CRAFT_BELT", classify("make a primitive utility belt"));
        assertEquals("CRAFT_BELT", classify("craft a tool belt from cordage"));
        assertEquals("CRAFT_BELT", classify("weave a utility belt"));
        assertEquals("CRAFT_BELT", classify("fashion a belt with tool loops"));
        // Wearing/putting on a belt carries no craft verb — it equips, it does not build.
        assertEquals("EQUIP", classify("put on my utility belt"));
        assertEquals("EQUIP", classify("wear the belt"));
    }

    /**
     * Physical logistics (#29/#40/#41): storing into a container, picking a dropped/stored object back up,
     * and the boundary against DROP and the gather verbs.
     */
    @Test void storeAndPickUpClassify() throws Exception {
        // Storing INTO a container is containment, not dropping and not gathering.
        assertEquals("STORE", classify("put the stones in the basket"));
        assertEquals("STORE", classify("place the knife into the pouch"));
        assertEquals("STORE", classify("stow the cordage inside the pack basket"));
        // Plain dropping and setting-down stay DROP (no container / no "in").
        assertEquals("DROP", classify("put down the basket"));
        assertEquals("DROP", classify("drop the stone axe"));
        // Taking something back up — from the ground or out of a store.
        assertEquals("PICK_UP", classify("pick up the woven basket"));
        assertEquals("PICK_UP", classify("grab the stone knife"));
        assertEquals("PICK_UP", classify("retrieve the basket i left here"));
        assertEquals("PICK_UP", classify("take the cordage out of the basket"));
        assertEquals("PICK_UP", classify("pick it back up off the ground"));
    }

    /**
     * M1 #67 action-catalogue aliases: the exact take/place/store/retrieve synonyms must resolve to their
     * canonical manipulation intent, never a generic validation handler.
     */
    @Test void manipulationCatalogueAliasesResolve() throws Exception {
        // take / retrieve family -> PICK_UP
        assertEquals("PICK_UP", classify("lift the log"));
        assertEquals("PICK_UP", classify("fetch the basket"));
        assertEquals("PICK_UP", classify("unpack the cordage from the pack basket"));
        assertEquals("PICK_UP", classify("take the knife from storage"));
        assertEquals("PICK_UP", classify("recover my spear"));
        // place / drop family -> DROP
        assertEquals("DROP", classify("lay down the bundle"));
        assertEquals("DROP", classify("place the stone on the ground"));
        assertEquals("DROP", classify("set the log down here"));
        assertEquals("DROP", classify("put the axe aside"));
        // store / cache family -> STORE
        assertEquals("STORE", classify("put the meat away"));
        assertEquals("STORE", classify("cache the dried meat"));
        assertEquals("STORE", classify("stockpile the firewood"));
        assertEquals("STORE", classify("stow the tools in the chest"));
        // Boundary: bathing, gathering raw growth, and setting traps are NOT manipulation-catalogue verbs.
        assertEquals("WASH", classify("take a bath in the stream"));
        assertEquals("GATHER_FIBER", classify("collect plant fiber from the undergrowth", false));
        assertEquals("SET_TRAP", classify("place a fish trap in the shallows"));
    }

    /** M1 #67 open/close/seal: container access verbs, scoped to a container noun. */
    @Test void containerAccessVerbsClassify() throws Exception {
        assertEquals("OPEN_CONTAINER", classify("open the basket"));
        assertEquals("OPEN_CONTAINER", classify("unstopper the clay pot"));
        assertEquals("OPEN_CONTAINER", classify("take the lid off the chest"));
        assertEquals("CLOSE_CONTAINER", classify("close the basket"));
        assertEquals("CLOSE_CONTAINER", classify("put the lid on the pot"));
        assertEquals("CLOSE_CONTAINER", classify("seal the pouch"));
        // Storing into a container is still STORE, not an access change.
        assertEquals("STORE", classify("put the stone in the basket"));
    }

    /** M1 #65 non-visual senses: listen / smell / feel / search resolve to their canonical perception intents. */
    @Test void sensoryPerceptionVerbsClassify() throws Exception {
        assertEquals("LISTEN", classify("listen closely for anything moving"));
        assertEquals("SMELL", classify("smell the air"));
        assertEquals("SMELL", classify("sniff the water"));
        assertEquals("FEEL", classify("feel the ground for damp"));
        assertEquals("FEEL", classify("touch the bark"));
        assertEquals("SEARCH", classify("search the ground here", false));
        assertEquals("SEARCH", classify("check beneath the fallen leaves", false));
        assertEquals("SEARCH", classify("rummage through the leaf litter", false));
        // Boundaries: specific prospecting and tracking still claim their own searches.
        assertEquals("GATHER_MINERAL", classify("search the rocks for flint"));
        assertEquals("TRACK", classify("look for tracks on the ground"));
        // A bare look-around is still the whole-scene survey, not a sense.
        assertEquals("OBSERVE", classify("look around carefully"));
    }

    /** M1 #65 read / measure / identify: the remaining perception verbs, with their substring boundaries. */
    @Test void readMeasureIdentifyClassify() throws Exception {
        // read / review_record -> READ
        assertEquals("READ", classify("read my journal"));
        assertEquals("READ", classify("reread the stone tablet"));
        assertEquals("READ", classify("consult the record"));
        assertEquals("READ", classify("study the writing on the slab"));
        // "read the ground/tracks" is tracking, not reading a page; "bread" must not match "read".
        assertEquals("TRACK", classify("read the ground for tracks"));
        assertEquals("EAT", classify("eat the bread"));
        // measure -> MEASURE
        assertEquals("MEASURE", classify("weigh the stone in my hand"));
        assertEquals("MEASURE", classify("count how many branches I have"));
        assertEquals("MEASURE", classify("pace out the distance to the treeline"));
        assertEquals("MEASURE", classify("test the depth of the water"));
        // "account"/"discount" must not match "count".
        assertEquals("UNKNOWN", classify("give an account of the day", false));
        // identify -> folds into the subject-scoped EXAMINE
        assertEquals("EXAMINE", classify("identify the mushroom"));
        assertEquals("EXAMINE", classify("what kind of tree is this"));
    }

    /**
     * M1 #68 gathering aliases + WASH scoping: forage/harvest/take-all/gather-up reach the specific gathers,
     * and "wash/rinse the material" is no longer stolen by the body-washing WASH intent.
     */
    @Test void gatheringAliasesAndWashScoping() throws Exception {
        // The specific gathers now accept the full gather-verb set, not just gather/collect.
        assertEquals("GATHER_FIBER", classify("forage for plant fiber", false));
        assertEquals("GATHER_BRANCHES", classify("harvest firewood from the forest floor", false));
        assertEquals("GATHER_BRANCHES", classify("gather up the dry branches", false));
        assertEquals("GATHER_STONE", classify("take all the loose stones", false));
        // (Berries route to GATHER_PLANT's flora path, which handles them — that rule wins earlier.)
        assertEquals("GATHER_PLANT", classify("collect wild berries", false));
        // #55 building stock: saplings and straw route to GATHER_PLANT (their flora sources).
        assertEquals("GATHER_PLANT", classify("gather a straight sapling", false));
        assertEquals("GATHER_PLANT", classify("harvest straw from the meadow", false));
        // #75 fibre materials: milkweed / flax / hemp / root fibre reach their flora via GATHER_PLANT.
        assertEquals("GATHER_PLANT", classify("gather milkweed from the meadow", false));
        assertEquals("GATHER_PLANT", classify("harvest flax", false));
        assertEquals("GATHER_PLANT", classify("collect hemp stalks", false));
        assertEquals("GATHER_PLANT", classify("gather root fibre from the wet ground", false));
        // #75 nut foods: acorns and tree nuts reach GATHER_PLANT (their flora sources).
        assertEquals("GATHER_PLANT", classify("gather acorns under the oak", false));
        assertEquals("GATHER_PLANT", classify("collect hazelnuts", false));
        assertEquals("GATHER_PLANT", classify("forage for chestnuts", false));
        // WASH is body-washing only; material washing/panning falls through to the process catalogue.
        assertEquals("WASH", classify("wash myself in the stream"));
        assertEquals("WASH", classify("take a bath in the river"));
        assertEquals("WASH", classify("rinse my hands"));
        assertEquals("UNKNOWN", classify("wash the sediment from the gravel", false));
        assertEquals("UNKNOWN", classify("rinse the fleece", false));
        assertEquals("UNKNOWN", classify("pan the gravel for gold", false));
    }

    /** M1 #69 crafting/transformation: repair/mend an item (distinct from lean-to repair and from REFINE). */
    @Test void repairAndCraftVerbsClassify() throws Exception {
        assertEquals("REPAIR_ITEM", classify("repair my knife"));
        assertEquals("REPAIR_ITEM", classify("mend the woven basket"));
        assertEquals("REPAIR_ITEM", classify("reinforce the spear"));
        assertEquals("REPAIR_ITEM", classify("sharpen the stone axe"));
        assertEquals("REPAIR_ITEM", classify("fix the fishing net"));
        // Repairing a shelter is still the lean-to path, not item repair.
        assertEquals("REPAIR_LEAN_TO", classify("repair the lean-to"));
        // Improving an already-sound thing is REFINE, not repair.
        assertEquals("REFINE", classify("improve my knife"));
        // Making a named tool still routes to its explicit craft.
        assertEquals("CRAFT_KNIFE", classify("make a stone knife"));
        // A process verb yields to the two-axis matcher when it claims the text.
        assertEquals("UNKNOWN", classify("carve a wooden spoon", true));
    }

    /** M1 #66 body care against the environment: warm/dry/cool/shelter/stretch, distinct from material processing. */
    @Test void bodyCareAgainstEnvironmentClassify() throws Exception {
        assertEquals("WARM_BODY", classify("warm myself by the fire"));
        assertEquals("WARM_BODY", classify("warm up my hands"));
        assertEquals("DRY_BODY", classify("dry off by the fire"));
        assertEquals("DRY_BODY", classify("dry my clothes"));
        assertEquals("COOL_BODY", classify("cool off in the shade"));
        assertEquals("COOL_BODY", classify("get out of the sun"));
        assertEquals("SHELTER_BODY", classify("take shelter from the rain"));
        assertEquals("SHELTER_BODY", classify("get under cover"));
        assertEquals("STRETCH", classify("stretch and loosen my limbs"));
        // Boundary: drying a MATERIAL is processing, not body-drying; washing stays body care.
        assertEquals("UNKNOWN", classify("dry the herbs on the rack", false));
        assertEquals("WASH", classify("wash my hands"));
        // Rest and sleep still classify (not swallowed by the new body-care rules).
        assertEquals("REST", classify("rest for a while"));
        assertEquals("SLEEP", classify("bed down for the night"));
    }

    /** M1 #70 dismantle/salvage: taking a construction apart, even a lean-to (before the lean-to build check). */
    @Test void dismantleAndSalvageClassify() throws Exception {
        assertEquals("DISMANTLE", classify("dismantle the lean-to"));
        assertEquals("DISMANTLE", classify("take apart the fire pit"));
        assertEquals("DISMANTLE", classify("pull down the shelter"));
        assertEquals("DISMANTLE", classify("salvage the timber from the wall"));
        assertEquals("DISMANTLE", classify("tear down the fence"));
        // Building/working a lean-to still routes to the lean-to path (not stolen by DISMANTLE).
        assertEquals("WORK_LEAN_TO", classify("build a lean-to"));
        assertEquals("WORK_LEAN_TO", classify("work on the lean-to"));
    }

    /** M1 #71 fire management: extinguish/bank/tend, checked before the ignition rules. */
    @Test void fireManagementClassify() throws Exception {
        assertEquals("EXTINGUISH_FIRE", classify("put out the fire"));
        assertEquals("EXTINGUISH_FIRE", classify("extinguish the fire"));
        assertEquals("EXTINGUISH_FIRE", classify("douse the fire with water"));
        assertEquals("BANK_FIRE", classify("bank the fire for the night"));
        assertEquals("BANK_FIRE", classify("cover the coals to keep the embers"));
        assertEquals("FEED_FIRE", classify("tend the fire"));
        assertEquals("FEED_FIRE", classify("keep the fire going"));
        // Lighting a fire is still LIGHT_FIRE, not extinguishing.
        assertEquals("LIGHT_FIRE", classify("light a fire with the bow drill"));
    }

    /** M1 #72 terrain crossing + disengage: wade/ford/swim/climb toward a direction is movement; retreat/flee/hide break off. */
    @Test void terrainCrossingAndDisengageClassify() throws Exception {
        assertEquals("MOVE", classify("wade across the stream to the north"));
        assertEquals("MOVE", classify("swim north across the river"));
        assertEquals("MOVE", classify("climb up the slope to the east"));
        assertEquals("MOVE", classify("ford the river heading west"));
        assertEquals("DISENGAGE", classify("back away from the bear"));
        assertEquals("DISENGAGE", classify("flee the wolves"));
        assertEquals("DISENGAGE", classify("retreat to safer ground"));
        assertEquals("DISENGAGE", classify("hide from the boar"));
        assertEquals("DISENGAGE", classify("run away"));
        // "hide" as a noun (working leather) is untouched — needs a process/harvest verb, routed elsewhere.
        assertEquals("UNKNOWN", classify("tan the animal hide", true));
    }

    /** M1 #71 water handling: collect / boil / filter water route distinctly; drinking still classifies. */
    @Test void waterHandlingClassify() throws Exception {
        assertEquals("COLLECT_WATER", classify("collect water from the stream"));
        assertEquals("COLLECT_WATER", classify("fill my waterskin"));
        assertEquals("COLLECT_WATER", classify("fetch water"));
        assertEquals("BOIL_WATER", classify("boil the water to make it safe"));
        assertEquals("FILTER_WATER", classify("filter the water through the clay filter"));
        // Drinking is still DRINK; collecting is not gathering.
        assertEquals("DRINK", classify("drink from the stream"));
        assertEquals("DRINK", classify("take a drink of water"));
    }

    /** Workstations (V69) claim their words before the generic desk/table rule; a plain table is still a desk. */
    @Test void workstationsClassifyBeforePlainFurniture() throws Exception {
        assertEquals("CRAFT_WORKSTATION", classify("build a woodworking bench"));
        assertEquals("CRAFT_WORKSTATION", classify("set up a loom"));
        assertEquals("CRAFT_WORKSTATION", classify("make a stoneworking table"));
        assertEquals("CRAFT_DESK", classify("build a table"));
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

    /** "split the oak log into planks" is log processing, not felling another tree (#17). */
    @Test void splittingALogIsNotFellingATree() throws Exception {
        // When the two-axis matcher claims the text (split_planks / timber_from_log),
        // FELL_TREE must yield so the log is processed, not another tree dropped.
        assertEquals("UNKNOWN", classify("split the oak log into planks with my hatchet", true));
        assertEquals("UNKNOWN", classify("saw the log into planks", true));
        assertEquals("UNKNOWN", classify("square the pine log into a baulk", true));
        // Bare "log" no longer triggers felling on its own — it needs a felling verb.
        assertEquals("UNKNOWN", classify("haul the oak log back to camp", false));
        // A genuine felling still classifies when no process matches.
        assertEquals("FELL_TREE", classify("fell the oak tree with my stone axe", false));
        assertEquals("FELL_TREE", classify("chop down the pine", false));
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
        // #75 knappable stone: chert and obsidian reach the mineral gather.
        assertEquals("GATHER_MINERAL", classify("gather chert from the rock"));
        assertEquals("GATHER_MINERAL", classify("collect obsidian in the mountains"));
        // Salt is a mineral you gather (sea salt at the shore, rock salt in a deposit) — #salt chain.
        assertEquals("GATHER_MINERAL", classify("gather sea salt at the shore"));
        assertEquals("GATHER_MINERAL", classify("dig for rock salt in the flat"));
        // But "salt the …" as preservation carries no gathering verb and is not mineral-gathering.
        assertEquals("UNKNOWN", classify("salt the meat down for winter"));
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
