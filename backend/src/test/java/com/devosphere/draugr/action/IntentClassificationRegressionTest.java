package com.devosphere.draugr.action;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
     * classify(String) is private and pure over text plus two collaborator answers, both stubbed here.
     *
     * <p>It asks {@link com.devosphere.draugr.item.PhysicalItemService#actionMatchesProcess} whether the text is
     * really a material process, so it can yield ambiguous noun-driven intents (FISH, MARK-by-carving) to the
     * two-axis matcher; the routing itself is covered by ProcessRoutingTest and live E2E.
     *
     * <p>And since V305 it asks {@code namesADugMineral} whether the text names a mineral that must be broken out
     * of the rock, which replaced the hand-written literal list the GATHER_MINERAL rule used to carry. That one is
     * answered here from a two-entry fake catalogue rather than from a database, which is enough to assert both
     * directions of the rule: the classifier's job is to act on the answer, not to produce it.
     */
    private String classify(String text, boolean processMatches) throws Exception {
        Method m = ChronicleActionService.class.getDeclaredMethod("classify", String.class);
        m.setAccessible(true);
        com.devosphere.draugr.item.PhysicalItemService items =
            new com.devosphere.draugr.item.PhysicalItemService(null, null, null) {
                @Override public boolean actionMatchesProcess(String t) { return processMatches; }
                // THE CATALOGUE RESOLVER, faked the same way and for the same reason (V442). The work-in-hand
                // rule borrows "how long until" only when the sentence names a thing, which is what tells a
                // hide becoming leather from the sun going down. Four names are enough to assert both ways.
                @Override public String[] itemNamedIn(String t) {
                    String v = t == null ? "" : " " + t.toLowerCase(java.util.Locale.ROOT) + " ";
                    for (String name : new String[]{"hide", "leather", "basket", "meat"})
                        if (v.contains(" " + name + " ")) return new String[]{name, name};
                    return null;
                }
                @Override public boolean namesADugMineral(String t) {
                    String v = t == null ? "" : t.toLowerCase(java.util.Locale.ROOT);
                    return v.contains("fire clay") || v.contains("limestone");
                }
                @Override public boolean namesAKeptAnimal(String t) {
                    String v = t == null ? "" : t.toLowerCase(java.util.Locale.ROOT);
                    // The fake catalogue now carries the HEAD NOUNS too (#106). Tending, grooming and feeding
                    // used to hold three separate literal species lists in the classifier; they share one
                    // vocabulary now, and the species half of it is the catalogue's. So a fake that knows only
                    // yak, musk ox and llama would make "tend the sick goat" unclassifiable here while it works
                    // perfectly in play — the species are mountain_goat and bighorn_sheep, and the real method
                    // matches their last word. These are those words.
                    for (String beast : new String[]{"yak", "musk ox", "llama", "goat", "sheep", "horse",
                                                     "buffalo", "fowl", "deer", "duck", "goose", "reindeer",
                                                     "donkey", "elk", "aurochs", "turkey", "pigeon"})
                        if (com.devosphere.draugr.narration.Words.word(v, beast) || v.contains(beast)) return true;
                    return com.devosphere.draugr.narration.Words.word(v, "ox")
                        || com.devosphere.draugr.narration.Words.word(v, "oxen");
                }
                /** Nothing is kept in a test with no world, so taming never yields its feeding branch here. */
                @Override public boolean keepsSuchABeast(String t) { return false; }
                /**
                 * What the Chronicle is carrying, as a fake pack rather than a database (#37). The real method
                 * asks the recursive containment walk for every item in reach and matches its key and head noun;
                 * this stands in for it with the arrival kit plus a knife, which is enough to assert both
                 * directions of the gear-condition rule — a shoe IS carried, a millstone is not.
                 */
                @Override public boolean namesSomethingYouCarry(String t) {
                    String v = t == null ? "" : t.toLowerCase(java.util.Locale.ROOT);
                    // These are the head nouns of what a Chronicle ACTUALLY arrives with plus a knife, read off
                    // a live world: arrival_left_shoe, arrival_right_shoe, arrival_shirt, arrival_trousers,
                    // chert_knife. Deliberately NOT a generous list — a stub more forgiving than the world makes
                    // the test pass on phrasings that fail in play, which is how "check my boots" nearly shipped
                    // as covered when the game has no boots to check.
                    for (String carried : new String[]{"shoe", "shirt", "trousers", "knife"})
                        if (com.devosphere.draugr.narration.Words.word(v, carried)
                            || com.devosphere.draugr.narration.Words.word(v, carried + "s")) return true;
                    return false;
                }
            };
        ChronicleActionService svc = new ChronicleActionService(null, null, null, null, items, null, null, null, null, null, null, null, new com.devosphere.draugr.narration.ActionInputClassifier(), null, null, null, new com.devosphere.draugr.narration.NarrationEngine(), (com.devosphere.draugr.ai.RuntimeAuthoringService) null, (ExaminationService) null, (com.devosphere.draugr.people.ContactService) null, (com.devosphere.draugr.people.TradeService) null, (com.devosphere.draugr.people.ConductService) null, (com.devosphere.draugr.people.AgreementService) null, (com.devosphere.draugr.people.CompanionService) null, (com.devosphere.draugr.people.AudienceService) null, (com.devosphere.draugr.people.MembershipService) null, (com.devosphere.draugr.people.ClaimService) null);
        return ((Enum<?>) m.invoke(svc, text)).name();
    }

    /**
     * #77 V329: the three water structures are built through the assembly matcher, which only runs when no Java
     * intent claims the phrase first. "water" would be taken by FILTER_WATER/COLLECT_WATER, "a bed" by MAKE_BED.
     */
    @Test void waterStructureBuildPhrasesReachTheAssemblyMatcher() throws Exception {
        for (String phrase : new String[]{
                "build a settling basin", "dig a settling basin", "work on the settling basin",
                "build a sand filter bed", "dig a sand filter bed", "lay a sand filter bed", "work on the sand filter bed",
                "build a spring box", "wall the spring head", "protect the spring head", "cover the spring head", "work on the spring box"}) {
            assertEquals("UNKNOWN", classify(phrase), phrase);
        }
    }

    // --- V57 alignment: a large batch of material processes exposed the intent
    // --- classifier's naive substring matching. "salt the fish" is not fishing,
    // --- "carve a spoon" is not marking, "meat" is not "eat", "knap" is not "nap".
    /**
     * #106/#108 tending a sick beast. "tend" already belonged to WEED_CROP and "treat" to TREAT_WOUND, so this
     * rule needs BOTH a tending verb and an animal — and the danger is not that it fails to match, it is that it
     * quietly steals the two phrases that were already spoken for. Both directions asserted.
     */
    /**
     * #37/#165 watering a crop and driving birds off it. Both were UNKNOWN in act nine — a camp in its second
     * week, where a farmer spends most of the season on exactly these two things.
     *
     * <p>The danger is not that they fail to match. FEED_ANIMAL has claimed "water the" since #100, and it claims
     * it together with an animal noun; the crop rule claims it together with a crop noun. Both directions are
     * asserted, because a rule widened to catch one phrase reliably steals another.
     */
    @Test void wateringACropDoesNotStealWateringTheStock() throws Exception {
        assertEquals("WATER_CROP", classify("water the seedlings"));
        assertEquals("WATER_CROP", classify("water the crop"));
        assertEquals("WATER_CROP", classify("water the plot"));
        assertEquals("WATER_CROP", classify("water the rows"));
        assertEquals("WATER_CROP", classify("irrigate the field"));
        assertEquals("WATER_CROP", classify("carry water to the barley"));
        // The ones it must not take. Watering stock went to FEED_ANIMAL from #100 until #106 — and FEED_ANIMAL
        // shakes out a bundle of dry grass and reports on HUNGER, so "water the animals" was answered "none of
        // your draft beasts is hungry": appetite, in reply to thirst. It could not water anything. draft_thirst
        // is simulated, falls on wet ground and at a watering station, and nothing could ask after it, so these
        // are CHECK_STOCK now, which reports the thirst and names what relieves it. The point of the test is
        // unchanged: the crop rule must not take a sentence about animals, and it does not.
        assertEquals("CHECK_STOCK", classify("water the animals"));
        assertEquals("CHECK_STOCK", classify("water the beasts"));
        assertEquals("CHECK_STOCK", classify("water the stock"));
        assertEquals("CHECK_STOCK", classify("water the herd"));
        // And feeding is still feeding, which is the half CHECK_STOCK must not take.
        assertEquals("FEED_ANIMAL", classify("feed the animals"));
        assertEquals("FEED_ANIMAL", classify("feed the beasts"));
        // Nor the water a person handles for themselves.
        assertEquals("COLLECT_WATER", classify("collect water"));
        assertEquals("BOIL_WATER", classify("boil some water"));
        assertEquals("WEED_CROP", classify("tend the crop"));
    }

    /** #37/#165 the birds. The harvest prose has always blamed them for a stand cut late; this is the answer to
     *  it, and it must not take the wildlife a Chronicle drives off for other reasons. */
    @Test void drivingBirdsOffACropIsNotDrivingOffWildlife() throws Exception {
        assertEquals("SCARE_BIRDS", classify("scare the birds off the crop"));
        assertEquals("SCARE_BIRDS", classify("chase the crows off the field"));
        assertEquals("SCARE_BIRDS", classify("keep the rooks off the grain"));
        assertEquals("SCARE_BIRDS", classify("shoo the sparrows off the seedlings"));
        // Not a crop, so not this: a bird driven off elsewhere is somebody else's rule or nobody's.
        assertNotEquals("SCARE_BIRDS", classify("scare the wolf off"));
        assertNotEquals("SCARE_BIRDS", classify("hunt the birds"));
    }
    @Test void tendingAnAnimalDoesNotStealTendingACropOrTreatingAWound() throws Exception {
        assertEquals("TEND_ANIMAL", classify("tend the sick goat"));
        assertEquals("TEND_ANIMAL", classify("treat the sick animal"));
        assertEquals("TEND_ANIMAL", classify("dose the horse"));
        assertEquals("TEND_ANIMAL", classify("nurse the beast through it"));
        // The two it must not take.
        assertEquals("WEED_CROP", classify("tend the crop"));
        assertEquals("WEED_CROP", classify("tend the row"));
        // Wound care is routed only from classifyLegacy. Its verbs were bind/bandage/dress, so "treat the wound"
        // and "clean the wound" reached nothing at all — recorded here as UNKNOWN until #37 act four found a
        // Chronicle bleeding and unable to say so in the plainest words for it. The verbs now include
        // clean/tend/treat/see to/wash, and the assertion that matters is unchanged: TEND_ANIMAL must still not
        // swallow any of them, because a wound is not a beast.
        assertEquals("TREAT_WOUND", classify("bind the wound"));
        assertEquals("TREAT_WOUND", classify("treat the wound"));
        assertEquals("TREAT_WOUND", classify("clean the wound"));
        assertEquals("TREAT_WOUND", classify("tend my wound"));
        assertEquals("TREAT_WOUND", classify("see to the gash"));
        // And a tending verb with nothing to tend is not an animal action.
        assertEquals("MAINTAIN_CAMP", classify("tidy the camp"));
    }

    /**
     * #108 V331: an offal pit is dug through the assembly matcher. HARVEST_CARCASS takes a butchering verb with
     * "carcass", and BUILD_LATRINE takes "refuse pit"/"waste pit" — the pit's phrases carry neither, and butchering
     * a carcass must stay butchering.
     */
    @Test void anOffalPitIsDugNotButchered() throws Exception {
        for (String phrase : new String[]{"dig an offal pit", "dig a carcass pit", "carcass disposal site", "work on the offal pit"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("HARVEST_CARCASS", classify("butcher the carcass"));
        assertEquals("BUILD_LATRINE", classify("dig a refuse pit"));
    }

    /**
     * #108 V330: a salt lick is built through the assembly matcher. "salt" with a gathering verb is GATHER_MINERAL's,
     * so none of its build phrases may carry one — and the other half, gathering rock salt, must stay a gather.
     */
    @Test void aSaltLickIsBuiltNotGathered() throws Exception {
        for (String phrase : new String[]{"set out a salt lick", "build a salt lick", "raise a salt lick", "work on the salt lick"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("GATHER_MINERAL", classify("gather rock salt"));
    }

    /**
     * #77 V332: #77's compost_pit reaches the existing compost bay through the assembly matcher, so its phrases must
     * not be taken by TILL_GROUND ("work the ground") or BUILD_LATRINE ("refuse pit") on the way.
     */
    @Test void aCompostPitReachesTheCompostBay() throws Exception {
        for (String phrase : new String[]{"dig a compost pit", "build a compost pit", "build a compost bay", "dig a manure pit"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
    }

    /**
     * #77 V333: a fen causeway is built through the assembly matcher. The word this structure most naturally wants
     * — "trackway" — is unusable, because TRACK takes any phrase containing "track" and would send a builder off
     * reading the ground for spoor; "trail" goes the same way to MARK. The phrases that survive that must also stay
     * clear of TRAVEL's "walk to" and of REPAIR_STRUCTURE, which takes a repair verb with "bridge". And the other
     * halves must be unharmed: tracking is still tracking, and repairing a bridge is still a repair.
     */
    @Test void aCausewayIsBuiltNotTracked() throws Exception {
        // Every keyword the assembly declares, replayed bare as well as in a sentence: the integration guard
        // (noJavaIntentShadowsAnAssemblysOwnKeywords) replays all of them, and it needs Docker and fifty minutes.
        for (String phrase : new String[]{"build a causeway", "lay a causeway", "raise a causeway", "fen causeway",
                                          "marsh causeway", "bog causeway", "causeway", "plank way", "plank road",
                                          "log road", "corduroy road", "corduroy way", "boardwalk",
                                          "work on the causeway", "build a fen causeway", "build a boardwalk"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("TRACK", classify("follow the trail"));
    }

    /**
     * #77 V336: a laid path is built through the assembly matcher. Every keyword it declares is replayed here, bare
     * and in a sentence, because the integration guard that replays all of them needs Docker and fifty minutes. The
     * words this structure cannot have are the same ones the causeway could not: "track" is TRACK's and "trail" is
     * MARK's. The intents whose vocabulary it brushes against must be unharmed.
     */
    @Test void aLaidPathIsBuiltNotTracked() throws Exception {
        for (String phrase : new String[]{"lay a path", "build a path", "make a path", "stone path", "gravel path",
                                          "paved way", "paved path", "flagged path", "lay a road", "build a road",
                                          "stone road", "work on the path", "build a stone path"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("TRACK", classify("read the ground for tracks"));
        assertEquals("MARK", classify("leave a marker"));
    }

    /**
     * #77 V338: a bee skep is raised through the assembly matcher. RAID_HIVE takes a taking verb — raid, rob,
     * harvest, smoke, take, collect, gather — beside hive/nest/honey/beeswax, so none of the skep's phrases may
     * carry one, and "weave" is avoided so CRAFT_BASKET cannot reach for a thing made of coiled straw. Robbing a
     * hive must stay robbing a hive.
     */
    @Test void aBeeSkepIsRaisedNotRobbed() throws Exception {
        for (String phrase : new String[]{"build a bee skep", "make a bee skep", "raise a bee skep", "set up a bee skep",
                                          "build a skep", "make a skep", "raise a skep", "bee skep", "skep",
                                          "straw hive", "work on the skep"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("RAID_HIVE", classify("raid the hive"));
        assertEquals("RAID_HIVE", classify("take the honey"));
    }

    /**
     * #77 V340: a hide frame is built through the assembly matcher. "rack" would go to CRAFT_SHELF; the natural
     * name — a fleshing frame — carries a word the fleshing process answers to; and the other natural name, a
     * stretching frame, went to STRETCH, which this test caught when it was written. Every keyword is replayed here,
     * and a stretch must stay a stretch.
     */
    @Test void aHideFrameIsBuiltNotWorkedOrStretched() throws Exception {
        for (String phrase : new String[]{"build a hide frame", "make a hide frame", "raise a hide frame",
                                          "set up a hide frame", "hide frame", "lash a hide frame",
                                          "work on the hide frame"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("STRETCH", classify("stretch my back"));
    }

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

    /**
     * #160/V305: the GATHER_MINERAL rule asks the catalogue instead of a hand-written list of mineral names.
     *
     * <p>The list was a copy of part of mineral_definition, and a copy goes stale the moment a mineral is added:
     * fire clay would have been catalogued, placed in the ground, and dug by nobody, and limestone — never in the
     * list at all — has been undiggable by name for as long as it has existed. What matters more than either fix
     * is the direction it must NOT go: the catalogue also holds Field stone and Surface clay, and if the rule
     * asked about every mineral it would take "gather field stone" from GATHER_STONE and "dig clay" from
     * GATHER_CLAY. It asks only about minerals with a tool_required, which those two do not have. Both
     * directions asserted, because only one of them is the regression.
     */
    @Test void aMineralNamedInTheCatalogueIsDugWithoutStealingStoneOrClay() throws Exception {
        assertEquals("GATHER_MINERAL", classify("dig for fire clay"));
        assertEquals("GATHER_MINERAL", classify("prospect for limestone"));
        assertEquals("GATHER_MINERAL", classify("gather fire clay from the bank"));
        // The two the narrowing protects: both are minerals in the same table, and both must keep their intents.
        assertEquals("GATHER_CLAY", classify("gather clay"));
        assertEquals("GATHER_CLAY", classify("dig clay from the bank"));
        assertEquals("GATHER_STONE", classify("gather field stone"));
        // A gather verb is still required. Calcining limestone is material work, not prospecting.
        assertEquals("UNKNOWN", classify("burn the limestone into lime", true));
    }

    /**
     * "look around" is a survey and "read the ground" is tracking, and a test that wants the first must not ask
     * for the second. MineralProvinceIntegrationTest asked for "look around and read the ground" and got back
     * droppings and a reindeer — it was asserting against tracking prose while believing it was reading a survey,
     * and it PASSED the negative half for entirely the wrong reason. Both phrasings pinned here so the mistake
     * is visible at the cheapest level rather than after a fifty-minute suite.
     */
    @Test void lookingAroundSurveysAndReadingTheGroundTracks() throws Exception {
        assertEquals("OBSERVE", classify("look around"));
        assertEquals("OBSERVE", classify("look around and take in the place"));
        assertEquals("TRACK", classify("read the ground for sign"));
    }

    /** #133 ground scavenge: a pointed forest-floor search yields material (FORAGE_GROUND); a bare look-around
     *  still just perceives (SEARCH); attached-bark stripping stays the tool path (STRIP_BARK), loose bark forages. */
    @Test void groundScavengeClassifies() throws Exception {
        assertEquals("FORAGE_GROUND", classify("search the forest floor for twigs"));
        assertEquals("FORAGE_GROUND", classify("look for tinder"));
        assertEquals("FORAGE_GROUND", classify("collect loose feathers from the ground"));
        assertEquals("FORAGE_GROUND", classify("gather driftwood from the shore"));
        assertEquals("FORAGE_GROUND", classify("comb under a log for kindling"));
        assertEquals("FORAGE_GROUND", classify("collect loose bark"));
        // Scavenged bone/antler without a kill (#133).
        assertEquals("FORAGE_GROUND", classify("search the ground for a shed antler"));
        assertEquals("FORAGE_GROUND", classify("collect gnawed bone from under the bushes"));
        // attached bark still needs a tool; a bare search of a place still just looks; butchering a kill is not foraging.
        assertEquals("STRIP_BARK", classify("strip bark from the birch"));
        assertEquals("SEARCH", classify("search the ruins for anything useful"));
        assertEquals("HARVEST_CARCASS", classify("butcher the carcass for bone and hide"));
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
     * #108's shade shelter, and the keyword it is not allowed to claim.
     *
     * <p>V303 first gave the assembly "build a sun shade", and a Java intent runs before the assembly matcher:
     * {@code coverKindOf} has answered to sunshade / sun shade / sun awning / awning / shade overhead since #195,
     * so the phrase went to PLACE_COVER and a keeper asking for a shade shelter would have got a cloth propped
     * over themselves. That is the BUILD_PEN trap of V284 wearing another face, and the integration guard caught
     * it in CI.
     *
     * <p>The two are genuinely different and both are kept: the #195 cover is a person's own shade, thrown up by
     * hand and gone again; the assembly is a posted, thatched structure that stock stand under. So the phrasing
     * was taken off the assembly rather than taken off the cover — and it is asserted here, at the cheapest level
     * that can see it, because this test needs no database and runs in a tenth of a second.
     */
    @Test void theShadeShelterReachesTheAssemblyEngineWithoutTakingTheBareHandSunshade() throws Exception {
        assertEquals("UNKNOWN", classify("build a shade shelter", false));
        assertEquals("UNKNOWN", classify("raise a shade shelter", false));
        assertEquals("UNKNOWN", classify("build a shade screen", false));
        assertEquals("UNKNOWN", classify("build a shade roof", false));
        // And the cover #195 already had keeps every word it answered to.
        assertEquals("PLACE_COVER", classify("rig a sun shade", false));
        assertEquals("PLACE_COVER", classify("put up a sunshade", false));
        assertEquals("PLACE_COVER", classify("set up an awning", false));
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
        // #195 bare-hand cover: a windbreak is placed, distinct from camp upkeep and bedding.
        assertEquals("PLACE_WINDBREAK", classify("make a windbreak", false));
        assertEquals("PLACE_WINDBREAK", classify("raise a brush screen against the wind", false));
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
    /**
     * Examining a lean-to must not build one (#37).
     *
     * <p>The guard for this was written once already and covered question SHAPES — {@code is the lean-to still
     * good} answered <em>"You mark out a low shelter frame against the weather"</em>, which STARTED BUILDING ONE.
     * It left the imperative perception verbs to fall through, so a year later {@code examine the lean-to} still
     * did it. Measured live before this fix.
     *
     * <p>A wrong answer that changes the world is the worst kind this project finds: a missing answer costs a
     * turn, and this cost a turn and left a frame standing that nobody asked for.
     */
    @Test void examiningALeanToMustNotBuildOne() throws Exception {
        for (String p : new String[]{"examine the lean-to", "inspect the lean-to", "look at the lean-to",
                                     "look over the lean-to", "study the lean-to", "have a look at the lean-to"})
            assertEquals("TAKE_STOCK_OF_CAMP", classify(p), p + " must not raise a frame");

        // The question forms the first fix covered must keep working.
        assertEquals("TAKE_STOCK_OF_CAMP", classify("is the lean-to still good"), "which the first fix covered");
        assertEquals("TAKE_STOCK_OF_CAMP", classify("how is the lean-to"));

        // ...AND BUILDING ONE MUST STILL BUILD ONE, which is the whole hazard of widening a rule that owns a
        // construction verb. A guard that swallowed the act would be a worse bug than the one it fixed.
        assertEquals("START_LEAN_TO", classify("make a lean-to"), "building is still building");
        assertEquals("WORK_LEAN_TO", classify("work on the lean-to"));
        assertEquals("WORK_LEAN_TO", classify("continue the lean-to"));
        assertEquals("REPAIR_LEAN_TO", classify("repair the lean-to"));
        assertEquals("ABANDON_LEAN_TO", classify("abandon the lean-to"));
    }

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
        // #75 wild foods: onions, rhizomes, grain heads reach GATHER_PLANT.
        assertEquals("GATHER_PLANT", classify("gather wild onions", false));
        assertEquals("GATHER_PLANT", classify("gather cattail rhizomes from the marsh", false));
        assertEquals("GATHER_PLANT", classify("collect wild grain heads", false));
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
        // The bare request, where there may be no stream to name (#30: the failure now says why there is none).
        assertEquals("DRINK", classify("drink water"));
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
        assertEquals("CRAFT_SHELF", classify("make a shelf", false));
    }

    /**
     * #77 V427: pit, frame and rack are FAMILY words — seven pits, six frames, four racks — so the plain phrase
     * must reach the assembly matcher, which names the family and asks which. A bare rack used to be a stone
     * shelf, one specific thing answering for four that are not shelves. Each phrase is replayed here, and the
     * shelf is still a shelf when it is asked for as one.
     */
    @Test void aFamilyWordReachesTheQuestionNotAPick() throws Exception {
        for (String phrase : new String[]{"build a pit", "dig a pit", "work on the pit",
                                          "build a frame", "raise a frame", "put up a frame",
                                          "build a rack", "raise a rack", "make a rack", "put up a rack",
                                          "work on the rack"})
            assertEquals("UNKNOWN", classify(phrase, false), phrase);
        for (String phrase : new String[]{"build a shelf", "make a shelf", "build some shelves", "build an archive"})
            assertEquals("CRAFT_SHELF", classify(phrase, false), phrase);
        assertEquals("BUILD_FUEL_RACK", classify("build a fuel rack", false));
        assertEquals("BUILD_FIRE_PIT", classify("build a fire pit", false));
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

    /**
     * A wattle WALL and a wattle FENCE are different builds — the wall is a shelter, the fence is not — and the
     * wall has its own staged assembly. BUILD_FENCE claimed "wattle wall" outright, from before that assembly
     * existed, so every one of the assembly's four keywords was shadowed and asking for a wall gave a fence.
     */
    @Test
    void aWattleWallIsNotAWattleFence() throws Exception {
        assertEquals("UNKNOWN", classify("build a wattle wall"));
        assertEquals("UNKNOWN", classify("weave a wattle wall"));
        assertEquals("UNKNOWN", classify("raise a wattle wall"));
        // Fences of every other description still reach the Java intent.
        assertEquals("BUILD_FENCE", classify("build a wattle fence"));
        assertEquals("BUILD_FENCE", classify("raise a palisade"));
        assertEquals("BUILD_FENCE", classify("put up a stockade"));
        assertEquals("BUILD_FENCE", classify("build a fence around the plot"));
    }

    /**
     * The phrase the cave-darkness test leans on (#158). It must be MEASURE — which is sight-work — or that test
     * would be asserting something about an intent it did not mean to exercise. Pinned here so it cannot drift
     * onto another rule and quietly change what that test proves.
     */
    @Test
    void measuringADistanceIsSightWorkAndClassifiesAsMeasure() throws Exception {
        assertEquals("MEASURE", classify("measure how far it is to the far wall"));
        assertEquals("MEASURE", classify("pace out the distance"));
    }

    /**
     * Marking a trail must reach MARK (#75). The rule wanted "leave a marker" contiguously, or "mark" beside a
     * tree, stone, stake or post — so "leave a trail marker", the most natural way to say it and the exact thing
     * the tracking_marker_bundle is for, classified as nothing at all and the bundle stayed unreachable.
     */
    @Test
    void markingATrailReachesTheMarkIntent() throws Exception {
        assertEquals("MARK", classify("leave a trail marker here"));
        assertEquals("MARK", classify("mark the trail behind me"));
        assertEquals("MARK", classify("tag the trail so I can find my way back"));
        // The sentence that exposed it: TRACK's trail branch fires on "trail" beside "find", so the most natural
        // way to say this was read as following a trail rather than laying one.
        assertEquals("MARK", classify("mark the trail so I can find my way back"));
        // Following a trail is still tracking — the marking verb is what separates them, not the noun.
        assertEquals("TRACK", classify("follow the trail"));
        assertEquals("TRACK", classify("find the trail and read the ground"));
        // The older phrasings still work.
        assertEquals("MARK", classify("leave a marker"));
        assertEquals("MARK", classify("build a cairn"));
        assertEquals("MARK", classify("drive a stake"));
        // "Trail" alone is not marking — a trail cake is food, and baking one must not leave a marker.
        assertEquals("UNKNOWN", classify("bake a trail cake"));
    }

    /**
     * The wattle wall was one of six assemblies a hard intent had quietly claimed. In each case the player asked
     * for a structure and the classifier handed them something else entirely — a plank bench became a desk, a
     * smoke rack became a stone shelf, a clay-lined hearth became a friction fire kit, and both "work on the
     * clay hearth" and "work on the earth sheltered hut" became tilling a seedbed, the first of those because
     * "hearth" contains "earth" as a substring. Each phrase must now fall through to the assembly matcher, and
     * the ordinary reading of each intent must survive.
     */
    @Test
    void fiveMoreAssembliesAreNoLongerClaimedByAHardIntent() throws Exception {
        assertEquals("UNKNOWN", classify("build a sleeping bench"));
        assertEquals("UNKNOWN", classify("raise a sleeping bench"));
        assertEquals("UNKNOWN", classify("sleeping bench"));
        assertEquals("CRAFT_DESK", classify("build a desk"));
        assertEquals("SLEEP", classify("bed down for the night"));

        assertEquals("UNKNOWN", classify("build a smoke rack"));
        assertEquals("UNKNOWN", classify("make a smoke rack"));
        assertEquals("CRAFT_SHELF", classify("build a shelf"));

        assertEquals("UNKNOWN", classify("make a clay hearth"));
        assertEquals("CRAFT_FIRE_KIT", classify("carve a fire drill"));

        assertEquals("UNKNOWN", classify("work on the clay hearth"));
        assertEquals("UNKNOWN", classify("work on the earth sheltered hut"));
        assertEquals("TILL_GROUND", classify("break the ground"));
        assertEquals("TILL_GROUND", classify("turn the earth"));
        assertEquals("TILL_GROUND", classify("work the soil"));

        assertEquals("UNKNOWN", classify("build a split rail fence"));
        assertEquals("UNKNOWN", classify("raise a split rail fence"));
        assertEquals("UNKNOWN", classify("build a rail fence"));
    }

    /**
     * V275: the pack basket is woven by the two-axis matcher, so CRAFT_BASKET must yield to it — a plain basket
     * is the Java intent's job, anything worn or carried in bulk is a recipe's. "backpack" contains "pack", which
     * is already one of the words that makes the intent stand aside.
     */
    @Test
    void aBackpackBasketReachesTheRecipeAndAPlainBasketDoesNot() throws Exception {
        assertEquals("UNKNOWN", classify("weave a backpack basket"));
        assertEquals("UNKNOWN", classify("weave a back basket"));
        assertEquals("UNKNOWN", classify("weave a burden basket"));
        // A plain basket is still the Java intent.
        assertEquals("CRAFT_BASKET", classify("weave a basket"));
        assertEquals("CRAFT_BASKET", classify("make a small basket from vines"));
    }

    /**
     * V272: the sewing table was defined, given an item_source, and then left out of the workstation list, so
     * there was no way in the world to have one — while fifty-three sewing and leatherwork recipes asked for no
     * station at all. It must reach the workstation path without disturbing the benches already there.
     */
    @Test
    void aSewingTableIsAWorkstation() throws Exception {
        assertEquals("CRAFT_WORKSTATION", classify("build a sewing table"));
        assertEquals("CRAFT_WORKSTATION", classify("make a sewing bench"));
        assertEquals("CRAFT_WORKSTATION", classify("set up a leatherwork table"));
        // The benches that already worked still do.
        assertEquals("CRAFT_WORKSTATION", classify("build a woodworking bench"));
        assertEquals("CRAFT_WORKSTATION", classify("craft an upright loom"));
        assertEquals("CRAFT_WORKSTATION", classify("build a stoneworking table"));
    }

    /** V270 water's-edge foraging: "shellfish" contains "fish", so gathering it was classified as angling and
     *  never reached the harvest registry that actually holds mussels and snails. */
    @Test void shellfishIsForagedNotAngledFor() throws Exception {
        assertEquals("COLLECT_INSECTS", classify("collect mussels from the mussel bed"));
        assertEquals("COLLECT_INSECTS", classify("gather shellfish along the shallows"));
        assertEquals("COLLECT_INSECTS", classify("pick river snails off the stones"));
        assertEquals("COLLECT_INSECTS", classify("dig caddis grubs out of the shallows"));
        assertEquals("COLLECT_INSECTS", classify("collect clams"));
        // Actual fishing is untouched.
        assertEquals("FISH", classify("fish the stream with a spear"));
        assertEquals("FISH", classify("catch trout in the pool"));
    }

    /**
     * V290's earth oven, and the reason its keywords are checked here rather than only by the shadow invariant.
     *
     * <p>An assembly is only reachable by a phrase the classifier lets fall through as UNKNOWN — a hard intent
     * runs first and takes the phrase entirely. {@code ConstructionRegistryCompleteIntegrationTest} enforces that
     * over every assembly in the catalogue, which is the right net, but it needs Docker: on a machine where
     * Testcontainers cannot reach the daemon it silently does not run, and the collision surfaces 45 minutes into
     * CI instead.
     *
     * <p>It surfaced exactly that way. I probed two of the five keywords I shipped, and the one I did not probe —
     * "work on the earth oven" — was taken by TILL_GROUND, because "earth" is a whole word in it. That is the same
     * collision that made "hearth" match "earth" before word boundaries were introduced, wearing a different coat.
     * These run without Docker, so the next one is caught before it is pushed.
     */
    @Test void theEarthOvensOwnWordsReachTheAssemblyMatcher() throws Exception {
        assertEquals("UNKNOWN", classify("build an earth oven"));
        assertEquals("UNKNOWN", classify("dig an earth oven"));
        assertEquals("UNKNOWN", classify("earth oven"));
        assertEquals("UNKNOWN", classify("work on the oven"));
        assertEquals("UNKNOWN", classify("line the oven pit"));
        // The phrase that was actually stolen, kept as the record of why the wording changed.
        assertEquals("TILL_GROUND", classify("work on the earth oven"));
        // And tilling itself still classifies, so the fix above was a wording change and not a loosening.
        assertEquals("TILL_GROUND", classify("break the earth for a seedbed"));
    }

    /**
     * #77 V341: a twisting post is built through the assembly matcher, so every keyword must reach it unclaimed by a
     * Java intent. None of them is a way of asking to make cord, and making cord must still be making cord.
     */
    @Test void aTwistingPostIsBuiltNotTwisted() throws Exception {
        for (String phrase : new String[]{"build a twisting post", "set a twisting post", "set up a twisting post",
                                          "raise a twisting post", "make a twisting post", "twisting post", "rope post",
                                          "build a rope post", "set a rope post", "work on the twisting post"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
    }

    /**
     * #37 act three: the garment nouns had drifted from what {@code craftGarment} actually makes. The maker
     * already dispatched on "shoe" and "trouser" — a Chronicle asking for either was told they could not think
     * how, about a thing the code knew how to make. The asymmetry matters as much as the routes: a build verb
     * on a garment noun is still a build, so the widening cannot have swallowed one.
     */
    @Test void theGarmentWordsMatchWhatTheMakerCanActuallyMake() throws Exception {
        for (String phrase : new String[]{"sew a pair of shoes", "make a shoe", "stitch hide boots", "make a boot",
                                          "sew trousers", "make a trouser", "make a tunic", "sew a hide coat",
                                          "make a fur cloak", "craft leggings"})
            assertEquals("CRAFT_GARMENT", classify(phrase), phrase);
        // A snowshoe is not a shoe (#37). "shoe" was added here so "sew a pair of shoes" would reach the maker,
        // and it also caught "snowshoes", which contains it -- so asking for snowshoes made hide boots, silently
        // and successfully. They are their own two processes and belong to the material matcher.
        for (String phrase : new String[]{"make snowshoes", "lash snowshoes", "lash a left snowshoe"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
    }

    /**
     * #37 act three: "store the food" — the plainest way anybody says it — reached nothing, because STORE wanted
     * either a container noun with "in"/"into" or the word "away". Raising a store is not filling one, so the
     * build phrasings must still build.
     */
    @Test void aBareStorageVerbStoresAndABuildVerbStillBuilds() throws Exception {
        for (String phrase : new String[]{"store the food", "store the meat", "stow the tools", "stash the berries",
                                          "store the dried fish", "put the meat away", "cache the meat"})
            assertEquals("STORE", classify(phrase), phrase);
        // The other half of the rule. Without these, a widening that ate BUILD_STORAGE_AREA would pass.
        for (String phrase : new String[]{"build a store house", "build a storehouse", "make a storage area",
                                          "raise a store house", "set up a storage platform"})
            assertEquals("BUILD_STORAGE_AREA", classify(phrase), phrase);
        // And the half a first cut of this rule actually broke, which cost a 68-minute CI round: as a NOUN the
        // same word is a BUILDING. These are assembly keywords and a DESIGNATE, and every one of them was taken
        // by STORE until the rule was anchored on the bare verb.
        for (String phrase : new String[]{"wood store", "log store", "fodder store", "hay store",
                                          "work on the wood store", "build a fodder store", "raise a wood store"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        assertEquals("DESIGNATE", classify("designate this the store ground"));
    }

    /**
     * #37 act three: a question about the season or the cold fell through to UNKNOWN and came back with a
     * crafting miss — prose about failing to make something, in answer to a question about the sky. The world
     * computes the felt temperature of the exact ground the Chronicle stands on and shows it on the Body HUD;
     * it simply never said it. FEEL takes the reading.
     */
    @Test void theSkyCanBeAskedAbout() throws Exception {
        for (String phrase : new String[]{"what season is it", "which season is it", "what time of year is it",
                                          "how cold is it", "how warm is it", "how hot is it",
                                          "what is the temperature", "what is the weather", "what's the weather",
                                          "how is the weather", "feel the air"})
            assertEquals("FEEL", classify(phrase), phrase);
        // Warming the body is an act, not a question, and must not have been swallowed by "how warm".
        assertEquals("WARM_BODY", classify("warm myself by the fire"));
        assertEquals("MEASURE", classify("weigh the stone"));
    }

    /**
     * #37 act four. Both of these sit in classifyLegacy, which runs after the whole classify chain, so they can
     * only catch what would otherwise have reached UNKNOWN. Asking where you are is answered by the survey that
     * OBSERVE already writes; a write verb with nothing to set down is a failure to WRITE, not a failure to make
     * something, and writeOrDraw already refuses it in its own words.
     */
    @Test void askingWhereYouAreAndWritingWithoutWordsBothReachSomething() throws Exception {
        for (String phrase : new String[]{"where am i", "where do i stand"})
            assertEquals("OBSERVE", classify(phrase), phrase);
        // "what is this place" was already claimed by EXAMINE earlier in the chain, which answers it properly —
        // classifyLegacy never sees it. Asserted as it behaves, so the rule above is not credited with it.
        assertEquals("EXAMINE", classify("what is this place"));
        for (String phrase : new String[]{"write down what happened today", "write in my journal", "inscribe it on the stone"})
            assertEquals("WRITE", classify(phrase), phrase);
        // Sketching a map is its own act and must not have been taken by the bare write rule.
        assertEquals("SKETCH_MAP", classify("make a map of this place"));
        // And writing WITH content keeps the colon route it always had.
        assertEquals("WRITE", classify("write: the river runs east"));
    }

    /**
     * #37 act five. Reaping answered to "reap", "the crop" and "the field", and to nothing a person would call
     * the thing standing in the field. The article does the work: "the wild grain" does not contain "the grain",
     * so foraging keeps its phrase. Taking it would have broken foraging in order to fix farming.
     */
    @Test void youCanReapWhatYouSowedWithoutBreakingForaging() throws Exception {
        for (String phrase : new String[]{"harvest the grain", "harvest my grain", "bring in the grain", "reap the grain", "harvest the crop"})
            assertEquals("HARVEST_CROP", classify(phrase), phrase);
        // The half that must not move: foraging wild grain off the ground is not reaping a sown stand.
        assertEquals("GATHER_PLANT", classify("harvest the wild grain"));
        assertEquals("GATHER_PLANT", classify("gather wild grain"));
    }

    /**
     * #37 act five: the tree nouns had "a tree" and "trees" but nothing for "an apple tree", which contains
     * neither. plantTree grows oak and pine only, and says so -- a better answer than a crafting miss, because
     * it names what can actually be put in the ground. Felling must still be felling.
     */
    @Test void aTreeNamedInTheSingularIsStillATree() throws Exception {
        for (String phrase : new String[]{"plant an apple tree", "plant a tree", "plant an oak", "replant the tree"})
            assertEquals("PLANT_TREE", classify(phrase), phrase);
        assertEquals("FELL_TREE", classify("fell the tree"));
    }

    /**
     * #37 act seven, played in the words somebody would type without having read anything.
     *
     * <p>"go to sleep" was the worst of them: TRAVEL claims any "go to", and SLEEP declares that exact phrase in
     * classifyLegacy, which runs after the whole chain and so never got a look. Asking to lie down was answered
     * with "you cannot call the way to mind" — a travel failure, in reply to sleep.
     */
    @Test void theWordsForOrdinaryActsReachThem() throws Exception {
        // The wrong answer, and the phrase SLEEP always declared.
        assertEquals("SLEEP", classify("go to sleep"));
        assertEquals("SLEEP", classify("go to bed"));
        // Travelling must still travel — that is the half this could have broken.
        assertEquals("TRAVEL", classify("go to the river"));
        assertEquals("TRAVEL", classify("go to the high ground"));
        assertEquals("TRAVEL", classify("head back to camp"));

        // Asking after the weather in the other plain phrasing. #708 routed "what is the weather".
        assertEquals("FEEL", classify("check the weather"));
        assertEquals("FEEL", classify("check the sky"));

        // Getting out of the weather, said the shortest way.
        for (String phrase : new String[]{"go inside", "get inside", "go indoors", "get under a roof"})
            assertEquals("SHELTER_BODY", classify(phrase), phrase);
        assertEquals("SHELTER_BODY", classify("take shelter from the rain"));

        // Breathing a banked fire back up, which is how a fire is kept overnight.
        for (String phrase : new String[]{"blow on the embers", "fan the embers", "coax the embers back"})
            assertEquals("LIGHT_FIRE", classify(phrase), phrase);
        assertEquals("BANK_FIRE", classify("bank the fire for the night"), "banking is still banking");

        // Looking for water and drawing it are one act once there is water to draw.
        for (String phrase : new String[]{"find water", "find some water", "find a stream"})
            assertEquals("COLLECT_WATER", classify(phrase), phrase);
        // "look for water" belongs to SEARCH, which answers about what it finds, and must keep it.
        assertEquals("SEARCH", classify("look for water"));
    }

    /**
     * #37 act eight — hunting, trapping and the monsters, in the words a hunter would use.
     *
     * <p>Butchering answered only to the word "carcass". A Chronicle standing over what they had just killed and
     * naming it — "gut the deer" — reached nothing, the same way naming the crop missed HARVEST_CROP. The risk
     * in widening it is the fish: {@code gut_fish} carries a bare "gut" keyword, so fish must keep their own
     * process, and the animal nouns here are deliberately mammals only.
     */
    @Test void aHunterCanNameWhatTheyKilled() throws Exception {
        for (String phrase : new String[]{"gut the deer", "dress the boar", "quarter the elk", "skin the hare",
                                          "butcher the carcass", "harvest the animal"})
            assertEquals("HARVEST_CARCASS", classify(phrase), phrase);
        // Stalking is hunting, and reached nothing before.
        assertEquals("CONFRONT_WILDLIFE", classify("stalk the deer"));
        assertEquals("CONFRONT_WILDLIFE", classify("hunt the boar"));
    }

    /**
     * #37 act eight — keeping away from a monster's lair is the choice a sensible Chronicle makes about it, and
     * DISENGAGE knew retreat, flee and hide but none of the words for it. Watching one from cover is scouting,
     * which is the act that exists for exactly that.
     */
    @Test void aLairCanBeAvoidedAndWatched() throws Exception {
        for (String phrase : new String[]{"avoid the lair", "steer clear of the lair", "keep away from the den",
                                          "give it a wide berth"})
            assertEquals("DISENGAGE", classify(phrase), phrase);
        for (String phrase : new String[]{"watch the lair from cover", "observe the den", "study the lair from cover"})
            assertEquals("SCOUT", classify(phrase), phrase);
        // The halves that must not move: retreating is still retreating, and scouting a boundary is still that.
        assertEquals("DISENGAGE", classify("back away slowly"));
        assertEquals("SCOUT", classify("scout the escape route"));
    }

    /**
     * #37 act eight — bait is worms, and digging for it is what COLLECT_INSECTS does. "bait the trap" belongs to
     * LURE and must keep it, which it does because none of the gathering verbs appear in it.
     */
    @Test void diggingForBaitIsDiggingForWorms() throws Exception {
        assertEquals("COLLECT_INSECTS", classify("dig for bait"));
        assertEquals("COLLECT_INSECTS", classify("dig for worms"));
        assertEquals("LURE", classify("bait the trap"), "baiting a trap is not a dig");
    }

    /**
     * #37 — a Java intent must not steal a material process's own keyword by matching a SUBSTRING of it.
     *
     * <p>There is a guard for Java-shadows-assembly and now one for a process being reachable inside the
     * matcher, but nothing guarded this. An audit of all 2,080 substantial process keywords found 66 claimed by
     * a Java intent before the matcher ever saw them, and the worst were pure substring accidents:
     *
     * <ul>
     *   <li>{@code "pee"} inside <b>peel</b> — "peel the rushes" made the Chronicle urinate. Eight keywords.</li>
     *   <li>a bare {@code "lid"} inside the NAME of the lidded basket — making one closed a container.</li>
     *   <li>{@code "sleep"} inside <b>sleeping mat</b> — weaving one went to sleep.</li>
     *   <li>{@code "stretch"} — stretching a HIDE is leatherwork, not a body loosening its limbs.</li>
     *   <li>{@code "wound"} inside <b>woundwort</b> — making the poultice bound a wound instead.</li>
     *   <li>and the costliest: "build a bloomery furnace" went to CRAFT_WORKSTATION, which answered with a
     *       workbench's branches and fibre when a furnace wants clay. Both smelt_copper and alloy_bronze name
     *       that station, so nothing metal was reachable by the phrase the furnace itself declares.</li>
     * </ul>
     *
     * <p>Nineteen recovered, 66 down to 47. The remainder is mostly EQUIP claiming an item's bare name, which is
     * defensible, and is left for a follow-up rather than swept in here.
     */
    @Test void aJavaIntentDoesNotStealAProcessBySubstring() throws Exception {
        // Every one of these is a keyword some process declares, and must now fall through to the matcher.
        // UNKNOWN from classify() IS that fall-through: PROCESS_MATERIAL is assigned later, in the dispatch,
        // once the matcher has actually resolved the phrase. A Java intent name here would mean it was stolen.
        for (String phrase : new String[]{"peel the rushes", "sleeping mat", "stretch the hide",
                                          "woundwort dressing", "build a bloomery furnace", "basket with a lid",
                                          "make a lidded basket"})
            assertEquals("UNKNOWN", classify(phrase, true), phrase);

        // And the acts those intents exist for must still be those acts. This is the half that matters.
        assertEquals("URINATE", classify("urinate"));
        assertEquals("URINATE", classify("go and pee"));
        assertEquals("SLEEP", classify("go to sleep"));
        assertEquals("STRETCH", classify("stretch my legs"));
        assertEquals("TREAT_WOUND", classify("bind the wound"));
        assertEquals("CRAFT_WORKSTATION", classify("build a workbench"));
        assertEquals("CRAFT_BASKET", classify("weave a basket"));
        assertEquals("CLOSE_CONTAINER", classify("put the lid on the pot"));
    }

    /**
     * Going somewhere, which is the thing a player does most often, and the axis nobody had swept (#37).
     *
     * <p>Forty-two sentences about movement and place: <b>26 reached nothing</b>. Two of them are fixed here.
     *
     * <p><b>"head east" reached NOTHING</b> while "go north", "walk south" and "go west" all worked. The move
     * rule knows walk/travel/go/move; "head" was only ever read as part of "head to" and "head for", which are
     * TRAVEL's and want a PLACE rather than a bearing — so one of the commonest ways of saying the commonest
     * thing a player does fell between the two rules and out of the bottom.
     *
     * <p><b>And a KIND of ground could be asked for and never found.</b> {@code planTravel} only knows places the
     * Chronicle has NAMED and can locate, so "which way is the water" had nothing to answer it, though the
     * neighbouring biomes, their elevations and the grid offsets were all sitting in {@code world_chunk}.
     */
    @Test void goingSomewhereAndAskingTheWay() throws Exception {
        // A bearing with any of the verbs for setting off.
        assertEquals("MOVE", classify("head east"));
        assertEquals("MOVE", classify("head north"));
        assertEquals("MOVE", classify("heading south"));
        assertEquals("MOVE", classify("set out west"));
        assertEquals("MOVE", classify("strike out north"));
        // The ones that already worked, which the new rule must not disturb.
        assertEquals("MOVE", classify("go north"));
        assertEquals("MOVE", classify("walk south"));
        assertEquals("MOVE", classify("go west"));
        assertEquals("MOVE", classify("wade north"));

        // A PLACE is still a journey, not a step. This is the distinction the new rule is gated on.
        assertEquals("TRAVEL", classify("head for the high ground"));
        assertEquals("TRAVEL", classify("head back to camp"));
        assertEquals("TRAVEL", classify("go to the old oak"));

        // Asking the way to a kind of ground.
        assertEquals("WHICH_WAY", classify("which way is the water"));
        assertEquals("WHICH_WAY", classify("which way to the woods"));
        assertEquals("WHICH_WAY", classify("where is the nearest high ground"));
        assertEquals("WHICH_WAY", classify("what direction is the sea"));
        assertEquals("WHICH_WAY", classify("how far is the river"));
        assertEquals("WHICH_WAY", classify("is there open ground near here"));
        assertEquals("WHICH_WAY", classify("any marsh nearby"));

        // A bearing on the SUN is not a bearing on the country, and the sky keeps its own questions. This is
        // what gating on a named kind of ground buys: the two rules cannot reach each other's sentences.
        assertEquals("READ_THE_SKY", classify("which way is north"));
        assertEquals("READ_THE_SKY", classify("take a bearing"));
        assertEquals("READ_THE_SKY", classify("what time is it"));
        // And measuring a THING is still measuring. "how far is" only asks the way when it asks about ground.
        assertEquals("MEASURE", classify("how far is the hut"));
        assertEquals("MEASURE", classify("how long is this plank"));
    }

    /**
     * The game could JUDGE a crossing and not make one (#37).
     *
     * <p>{@code can I get across here} answered in detail — the load you carry, a ford in the bottom, a laid way
     * pegged out over the fen — and <b>every verb for actually doing it reached nothing</b>: wade across, swim
     * across, cross to the other side, ford the stream. A crossing names the WATER rather than a bearing, and
     * the move rule wanted a bearing, so the one sentence the judgement invites was the one nobody could say.
     *
     * <p>The act now finds the water with the same query the judgement uses, so the two can never disagree about
     * what is there: one side and it crosses, more than one and it asks which, none and it says so.
     */
    @Test void aCrossingNamesTheWaterRatherThanABearing() throws Exception {
        assertEquals("MOVE", classify("wade across"));
        assertEquals("MOVE", classify("swim across"));
        assertEquals("MOVE", classify("cross to the other side"));
        assertEquals("MOVE", classify("cross the fen"));
        assertEquals("MOVE", classify("ford the stream"));
        assertEquals("MOVE", classify("wade over"));
        // With a bearing it was already a move, and still is.
        assertEquals("MOVE", classify("wade north"));
        assertEquals("MOVE", classify("swim east"));
        // Judging whether you COULD is a different question and keeps its own rule.
        assertEquals("JUDGE_CROSSING", classify("can I get across here"));
        assertEquals("JUDGE_CROSSING", classify("is it safe to cross"));

        // "cross" sits inside crossbar, crossing and crosswise, and this project has shipped the substring
        // defect four times. None of these is a sentence about wading a fen.
        assertNotEquals("MOVE", classify("make a crossbar"));
        assertNotEquals("MOVE", classify("cross my arms"));
        assertEquals("UNKNOWN", classify("peel the rushes", true), "and a process keyword is still the matcher's");
    }

    /**
     * Your own tracks are not quarry (#37).
     *
     * <p>{@code object_transition} has recorded the direction, the from and the to of every move since the table
     * existed, and nothing had ever read it for this: "retrace my steps" and "go back the way I came" reached
     * nothing, and <b>"follow my own tracks back" answered TRACK</b> — which hunts animal sign, so a player
     * asking to go back the way they came was shown <i>"feathers caught in the low growth"</i>.
     */
    @Test void yourOwnTracksAreNotQuarry() throws Exception {
        assertEquals("MOVE", classify("retrace my steps"));
        assertEquals("MOVE", classify("go back the way I came"));
        assertEquals("MOVE", classify("follow my own tracks back"));
        assertEquals("MOVE", classify("back the way we came"));
        // Hunting keeps every sentence that is about an animal's sign, which is all of its own.
        assertEquals("TRACK", classify("look for tracks"));
        assertEquals("TRACK", classify("follow the trail"));
        assertEquals("TRACK", classify("read the ground"));
        assertEquals("TRACK", classify("find the spoor"));
        // And a journey to a place is still a journey. "back to camp" is TRAVEL's and always worked.
        assertEquals("TRAVEL", classify("head back to camp"));
    }

    /**
     * The fire, which a Chronicle tends more often than anything but walking (#37, V406).
     *
     * <p>Forty-four sentences about getting one going, keeping it, killing it and asking after it:
     * <b>30 reached nothing</b>. The shape was the same every time — a rule that wanted the word "fire" beside
     * the verb, while a person standing over one says "it", or talks about the wood.
     *
     * <pre>
     *   light a fire      WORKED      start a fire / make a fire / kindle a fire   NOTHING
     *   stoke the fire    WORKED      put more wood on                            NOTHING
     *   put the fire out  WORKED      smother it / kick it out                    NOTHING
     *   bank the fire     WORKED      bank it for the night / damp it down        NOTHING
     * </pre>
     *
     * <p>And nothing could ask after it at all, over {@code fire_state.fuel_minutes} — the burning time
     * remaining to the minute, which the tick counts down every turn and the body reads for warmth.
     */
    @Test void theFireAnswersToTheWordsPeopleUseAboutIt() throws Exception {
        // Getting one going.
        assertEquals("LIGHT_FIRE", classify("light a fire"), "which always worked");
        assertEquals("LIGHT_FIRE", classify("start a fire"));
        assertEquals("LIGHT_FIRE", classify("make a fire"));
        assertEquals("LIGHT_FIRE", classify("kindle a fire"));
        assertEquals("LIGHT_FIRE", classify("get a fire going"));
        assertEquals("LIGHT_FIRE", classify("strike a spark"));
        assertEquals("LIGHT_FIRE", classify("light the tinder"));

        // Keeping it, including the phrasings that name the WOOD instead of the fire.
        assertEquals("FEED_FIRE", classify("feed the fire"), "which always worked");
        assertEquals("FEED_FIRE", classify("stoke the fire"));
        assertEquals("FEED_FIRE", classify("put more wood on"));
        assertEquals("FEED_FIRE", classify("build the fire up"));
        assertEquals("FEED_FIRE", classify("throw another branch on"));

        // Banking it and killing it, including the ones that say "it".
        assertEquals("BANK_FIRE", classify("bank the fire"), "which always worked");
        assertEquals("BANK_FIRE", classify("bank it for the night"));
        assertEquals("BANK_FIRE", classify("damp it down"));
        assertEquals("EXTINGUISH_FIRE", classify("put the fire out"), "which always worked");
        assertEquals("EXTINGUISH_FIRE", classify("smother it"));
        assertEquals("EXTINGUISH_FIRE", classify("kick it out"));

        // Asking after it.
        assertEquals("CHECK_FIRE", classify("is the fire still going"));
        assertEquals("CHECK_FIRE", classify("how long will it burn"));
        assertEquals("CHECK_FIRE", classify("will it last the night"));
        assertEquals("CHECK_FIRE", classify("is there enough wood"));
        assertEquals("CHECK_FIRE", classify("how much firewood have I got"));
        assertEquals("CHECK_FIRE", classify("is it safe to leave it burning"));

        // Warming yourself AT it is not asking after it, and getting nearer is the same act as sitting by it.
        assertEquals("WARM_BODY", classify("sit by the fire"), "which always worked");
        assertEquals("WARM_BODY", classify("move closer to the fire"));

        // THE RULES THESE MUST NOT TAKE. The fire-tool crafts run earlier and keep their own sentences; the pit
        // is claimed later and is excluded by name; and a hearth described rather than named is still the pit.
        assertEquals("CRAFT_FIRE_KIT", classify("make a fire kit"));
        assertEquals("CRAFT_FIRE_TOOL", classify("carve a fire bow"));
        assertEquals("CRAFT_TINDER", classify("prepare a tinder bundle"));
        assertEquals("BUILD_FIRE_PIT", classify("build a fire pit"));
        assertEquals("BUILD_FIRE_PIT", classify("ring the fire with stones"));
        // And gathering fuel is gathering, not feeding — "enough" and "how much" are questions about the wood.
        assertEquals("GATHER_BRANCHES", classify("gather more wood"));
        assertEquals("GATHER_BRANCHES", classify("gather wood"));
        assertEquals("GATHER_BRANCHES", classify("gather fuel"));
        assertEquals("GATHER_BRANCHES", classify("get some firewood"));
        // "wood" sits inside "wooden" and "woodland": making a wooden thing is the matcher's, not a gather.
        assertEquals("UNKNOWN", classify("make a wooden bowl", true), "a wooden thing is made, not gathered");
    }

    /**
     * Tool care, mending, and a question that built a hut (#37).
     *
     * <p>35 sentences about keeping an edge, mending, and whether what you built is still standing: <b>16
     * reached nothing</b>, and two of the nineteen that answered answered wrongly.
     *
     * <p><b>The worst wrong answer this project has found, because it is the only one that left something
     * behind:</b> {@code is the lean-to still good} → START_LEAN_TO, <i>"You mark out a low shelter frame
     * against the weather."</i> Asked whether their shelter was sound, the player had a new frame marked out for
     * them. {@code classifyLeanTo} ends in a default branch and a question with no building verb fell into it.
     *
     * <p>And {@code hone the blade} / {@code whet the blade} reached the material matcher, which offered to
     * <b>forge a bronze knife</b> — asked to put an edge back on what they hold, the player was offered a new one.
     */
    @Test void askingAboutAThingIsNotBuildingOrForgingIt() throws Exception {
        // THE QUESTION MUST NOT BUILD. Every one of these is a question about a lean-to.
        for (String asked : new String[]{"is the lean-to still good", "is the lean-to sound",
                                         "how is the lean-to", "will the lean-to hold",
                                         "does the lean-to still stand", "what state is the lean-to in"})
            assertNotEquals("START_LEAN_TO", classify(asked),
                "\"" + asked + "\" is a question, and must not put a frame on the ground");
        assertEquals("TAKE_STOCK_OF_CAMP", classify("is the lean-to still good"));
        assertEquals("TAKE_STOCK_OF_CAMP", classify("is the shelter sound"));
        assertEquals("TAKE_STOCK_OF_CAMP", classify("how is the shelter"));

        // ...and the ACTS on a lean-to all keep their own, which is the half that must not break.
        assertEquals("WORK_LEAN_TO", classify("build a lean-to"));
        assertEquals("WORK_LEAN_TO", classify("work on the lean-to"));
        assertEquals("REPAIR_LEAN_TO", classify("repair the lean-to"));
        assertEquals("ABANDON_LEAN_TO", classify("abandon the lean-to"));
        assertEquals("RESUME_LEAN_TO", classify("resume the lean-to"));

        // Putting an edge back on, by every word for it.
        assertEquals("REPAIR_ITEM", classify("sharpen the knife"), "which always worked");
        assertEquals("REPAIR_ITEM", classify("hone the blade"));
        assertEquals("REPAIR_ITEM", classify("whet the blade"));
        assertEquals("REPAIR_ITEM", classify("strop the knife"));
        assertEquals("REPAIR_ITEM", classify("grind the axe"));
        assertEquals("REPAIR_ITEM", classify("put an edge on the blade"));
        // Rehafting is mending: the head is sound and the handle has split.
        assertEquals("REPAIR_ITEM", classify("rehaft the axe"));
        assertEquals("REPAIR_ITEM", classify("replace the handle"));

        // HELD AS WORDS, and this is where it pays: "hone" sits inside HONEY and "whet" inside WHETHER. A
        // contains() would have made gathering honey a sharpening and a question about rain a sharpening too.
        assertNotEquals("REPAIR_ITEM", classify("gather honey"), "honey is not a hone");
        assertNotEquals("REPAIR_ITEM", classify("whether it will rain"), "whether is not a whet");
        assertNotEquals("REPAIR_ITEM", classify("eat the honeycomb"));

        // How a tool is standing up: use_count and condition_state are on every item.
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how worn is the knife"), "which always worked");
        assertEquals("TAKE_STOCK_OF_GEAR", classify("is the knife blunt"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how sharp is the axe"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how much use is left in it"));

        // Whether the night is survivable is the body's own question.
        assertEquals("SENSE_BODY", classify("will I be warm enough tonight"));
        assertEquals("SENSE_BODY", classify("am I dry"));
        // And lying down is sleeping, which "lie down to sleep" always was.
        assertEquals("SLEEP", classify("lie down"));
        assertEquals("SLEEP", classify("lie down to sleep"));
    }

    /**
     * #108 V410: the stall, the stable, the tack room and the harness rack are raised through the assembly
     * matcher. Every keyword each one declares is replayed here, bare and in a sentence, because the integration
     * guard that replays all 500-odd of them needs Docker and fifty minutes.
     *
     * <p>Three words were already owned and had to be worked around rather than discovered in CI:
     *
     * <ul>
     *   <li><b>"pen"</b> belongs to BUILD_PEN, so none of these may contain it — the #513 trap, which already
     *       cost six structures once;</li>
     *   <li><b>"rack"</b> belongs to CRAFT_SHELF, whose exclusion list already names the drying, fuel, wood,
     *       firewood, log, kindling, hay, fodder and smoke racks. The harness, yoke and tack racks are the tenth,
     *       eleventh and twelfth, added in the same place for the same reason;</li>
     *   <li><b>"harness"</b> and <b>"yoke"</b> belong to JUDGE_HAULAGE, but only beside a vehicle or a team — so
     *       a rack to hang one on is free, while harnessing an actual beast to an actual cart must stay exactly
     *       what it was. Both halves are asserted.</li>
     * </ul>
     */
    @Test void theWorkingAnimalsStallAndItsGearAreRaisedNotCrafted() throws Exception {
        for (String phrase : new String[]{
                // the stall — the structure that carries holds_an_animal_still for a draft beast
                "build a draft animal stall", "raise a draft animal stall", "build a draft stall",
                "raise a draft stall", "build a handling stall", "build a handling bay",
                "draft animal stall", "draft stall", "handling stall", "handling bay",
                // the stable
                "build a stable", "raise a stable", "build a horse stable", "raise a horse stable",
                "build the stables", "horse stable", "stables",
                // the tack room
                "build a tack room", "raise a tack room", "build a tack store", "build a harness room",
                "tack room", "tack store", "harness room",
                // and the rack, which had to be named in CRAFT_SHELF's exclusion list to get here at all
                "build a harness rack", "raise a harness rack", "build a yoke rack", "raise a yoke rack",
                "build a tack rack", "harness rack", "yoke rack", "tack rack",
                "work on the stable", "work on the harness rack"})
            assertEquals("UNKNOWN", classify(phrase), phrase);

        // The vocabulary these brush against must be unharmed. A shelf is still a shelf and a pen still a pen.
        assertEquals("CRAFT_SHELF", classify("build a shelf"));
        assertEquals("CRAFT_SHELF", classify("make some shelves"));
        assertEquals("BUILD_PEN", classify("build a pen"));
        assertEquals("BUILD_PEN", classify("build a paddock"));

        // And harnessing a beast to a cart is still that — the half of JUDGE_HAULAGE's vocabulary that a rack
        // must not take. If the exclusion above were written as a bare "!contains(harness)" this would break.
        assertEquals("JUDGE_HAULAGE", classify("harness the oxen to the cart"));
        assertEquals("JUDGE_HAULAGE", classify("yoke the beasts to the sledge"));
    }

    /**
     * #108 V428: a pen that names its own job steps aside from BUILD_PEN so the matcher can reach the structure
     * that does that job — the sick-animal shelter for a quarantine pen, the bull isolation yard for a boar or
     * bull pen. A generic pen is still a generic pen, and a pig pen, a paddock and a calving pen still get one.
     */
    @Test void aPenThatNamesItsJobReachesTheStructureForIt() throws Exception {
        for (String phrase : new String[]{"build a quarantine pen", "quarantine pen", "build a boar pen",
                "build a boar isolation pen", "build a bull pen", "boar pen", "bull pen"})
            assertEquals("UNKNOWN", classify(phrase), phrase);
        for (String phrase : new String[]{"build a pen", "build a pig pen", "build a paddock",
                "build an exercise paddock", "build a calving pen", "make a goat pen"})
            assertEquals("BUILD_PEN", classify(phrase), phrase);
        // The boar outside a pen is still a beast to face, not a thing to build.
        assertEquals("CONFRONT_WILDLIFE", classify("hunt the boar"));
    }

    /**
     * A word is not a substring (#37, found while building #108's stall).
     *
     * <p>CRAFT_DESK matched a bare {@code contains("table")} and CRAFT_SHELF a bare {@code contains("rack")}.
     * "table" sits inside <b>stable, portable, comfortable, suitable, notable</b>; "rack" sits inside <b>track</b>
     * and <b>racket</b>. So an ordinary family of sentences got a confidently wrong answer:
     *
     * <pre>
     *   make a comfortable bed          -> CRAFT_DESK
     *   construct a portable windbreak  -> CRAFT_DESK
     *   build a notable marker          -> CRAFT_DESK
     *   build a portable shelter        -> CRAFT_DESK
     *   build a stable                  -> CRAFT_DESK   (how it was found)
     *   build a track                   -> CRAFT_SHELF  (a laid track is a real staged structure here)
     * </pre>
     *
     * <p>This is the same lesson as "hone" inside <i>honey</i> and "whet" inside <i>whether</i>, and it is the
     * reason the fix is held both ways: every real craft phrase must still reach its own intent, or the cure is
     * worse than the disease.
     */
    /**
     * A bed is three things (#37): somewhere to sleep, ground for a crop, and litter for an animal.
     *
     * <p>Two of the three had rules. The animal's had none, so the whole phrase fell through to
     * {@code classifyLegacy}'s SLEEP rule and <b>the keeper who settled their animals for the night went to
     * sleep themselves</b> — hours of game time gone and the stock still unbedded. And the sleeping sense was
     * gated on the literals {@code "a bed"} / {@code "the bed"}, which an adjective defeats outright.
     *
     * <p>Swept 27 sentences across the three senses: 3 answered wrongly, 6 reached nothing. Now 0 and 0.
     *
     * <p>The order is what makes it safe, and it is asserted here rather than assumed: the animal's litter is
     * settled first, then the crop's ground by TILL_GROUND, and only what survives both reaches MAKE_BED. So the
     * same four words mean three different jobs depending on the noun beside them.
     */
    /**
     * Three features that worked and could not be asked for in the words a person uses (#37).
     *
     * <p>Found by sweeping the classifier for short {@code contains("…")} nouns that sit inside longer English
     * words — the audit that turned up `build a stable` → CRAFT_DESK. That sweep found <b>no further substring
     * collisions</b>, which is the negative result worth having; what it did turn up was this, three times over:
     * <b>the canonical phrasing worked, refused correctly, and the natural phrasing reached nothing.</b>
     *
     * <pre>
     *   boil water             -> "There is no fire burning here to boil water over."   heat the water  -> nothing
     *   coppice the hazel      -> "There is no wood here to coppice."                   prune the branches -> nothing
     *   how worn is the knife  -> "The chert knife is sound..."                          my shoe is worn -> nothing
     * </pre>
     *
     * <p>In every case the work and its refusal were already right; only the words were missing. The third was
     * the worst of them, because it was not a phrasing gap but a <b>vocabulary of four</b>: the rule asked for
     * "worn" beside `the axe`, `the knife`, `the blade` or `my tool`, so every garment, vessel and cord in the
     * game was outside it. That one is fixed by asking the catalogue what the Chronicle actually carries, as
     * minerals and stock already do, rather than by adding a fifth noun.
     */
    @Test void theWorkWasThereAndTheWordsWereNot() throws Exception {
        // I. Heating water IS boiling it, to a person with a pot and a fire.
        assertEquals("BOIL_WATER", classify("boil water"), "which always worked");
        assertEquals("BOIL_WATER", classify("heat the water"));
        assertEquals("BOIL_WATER", classify("warm the water"));
        assertEquals("BOIL_WATER", classify("heat some water"));
        // ...but heating is not always about water. Stones for a pit oven are their own work.
        assertEquals("UNKNOWN", classify("heat some stones"), "a process, not a kettle");

        // II. Pruning and trimming are coppicing. "pollard" is a term of art and was already in; the two words
        // anyone would actually reach for were not.
        assertEquals("COPPICE", classify("coppice the hazel"), "which always worked");
        assertEquals("COPPICE", classify("prune the branches"));
        assertEquals("COPPICE", classify("trim the branches"));
        assertEquals("COPPICE", classify("cut back the willow"));
        // Gated on the wood, so trimming anything else stays with its own work.
        assertEquals("UNKNOWN", classify("trim the wick"), "a lamp, not a tree");

        // III. The gear-condition question, for anything carried rather than for four nouns.
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how worn is the knife"), "which always worked");
        assertEquals("TAKE_STOCK_OF_GEAR", classify("my shoe is worn"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("check my shoes"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("is my shirt still in good order"));
        // And the neighbours it must not take: mending one is repair, taking it off is unequipping.
        assertEquals("REPAIR_ITEM", classify("mend my shoe"));
        assertEquals("UNEQUIP", classify("take off my shoes"));
        // A thing not carried is not something whose condition can be asked after.
        assertEquals("UNKNOWN", classify("is the millstone worn"), "nothing of yours");
        // AND THE LIMIT OF THE FIX, asserted so nobody mistakes it for covered: this asks the catalogue what the
        // Chronicle holds, so it answers to the game's OWN word for a thing and not to a synonym. The arrival kit
        // holds shoes; a player who says "boots" still reaches nothing. That is a vocabulary question for the
        // catalogue to answer, and adding a synonym list here would rebuild the hand-kept list this replaced.
        assertEquals("UNKNOWN", classify("check my boots"), "the game has shoes, not boots — recorded on #37");
    }

    /**
     * The water chain, end to end (#37) — a Chronicle drinks every day and dies without it.
     *
     * <p>Swept 40 sentences across the whole chain: finding water, judging it, drawing it, making it safe,
     * drinking it, carrying it, emptying it. <b>16 reached nothing or answered wrongly. Now 4</b>, and those four
     * want catalogue content rather than routing (catching rain, letting silt settle).
     *
     * <p>The sharpest was the fourth appearance of a shape this project keeps finding:
     *
     * <pre>
     *   is my waterskin full  ->  "Make a waterskin turns on a cutting edge, and there is none within reach."
     * </pre>
     *
     * <b>Asking about a thing is not making it</b> — after the lean-to question that built a hut, the trap
     * question that set a trap, and the snare question that worked a snare. The process matcher took the noun and
     * offered to make the very thing being asked after.
     *
     * <p>And a root cause worth more than the phrasings: {@code containerNoun} listed every dry container in the
     * game — basket, pouch, sack, crate, chest, quiver — and <b>not one thing you carry liquid in</b>. So a
     * waterskin was not a container, and `empty the waterskin` reached nothing while the same sentence about a
     * basket worked.
     */
    /**
     * One phrase, three subjects, and a precedence fault that gave the wrong one (#37).
     *
     * <p>The camp stocktake was written as
     *
     * <pre>
     *   if( GROUP_A || GROUP_B &amp;&amp; !animal &amp;&amp; !beast &amp;&amp; !herd &amp;&amp; !flock &amp;&amp; !namesAKeptAnimal )
     * </pre>
     *
     * and <b>{@code &&} binds tighter than {@code ||}</b>, so every one of those exclusions applied to GROUP_B
     * alone. They were written for the whole rule. The half they never reached is the stocktaking half:
     *
     * <pre>
     *   take stock of the animals  ->  TAKE_STOCK_OF_CAMP, an account of your lean-to
     *   take stock of the herd     ->  the same
     *   is the animal sound        ->  correctly declined — which is what proved the split
     * </pre>
     *
     * <p>A keeper asking after their animals was told about their shelter, by a guard that was already there and
     * silently governed only one branch. The rule is named locals now, which is how the two earlier paren faults
     * in this method were settled. Fixing it left the sentence reaching nothing, so the herd reading was given
     * the phrase as well — demanding a <b>real</b> animal word, because the subject gate counts the bare word
     * "stock", which every one of these sentences contains whether it means the camp or the herd.
     */
    @Test void takingStockOfWhat() throws Exception {
        assertEquals("CHECK_STOCK", classify("take stock of the animals"));
        assertEquals("CHECK_STOCK", classify("take stock of the herd"));
        assertEquals("CHECK_STOCK", classify("take stock of the flock"));
        assertEquals("CHECK_STOCK", classify("taking stock of the animals"));
        assertEquals("CHECK_STOCK", classify("take stock of the goats"));
        assertEquals("SENSE_BODY", classify("take stock of myself"));
        // The two that must keep working, and the one whose correct refusal exposed the fault.
        assertEquals("TAKE_STOCK_OF_CAMP", classify("take stock of the camp"), "which always worked");
        assertEquals("TAKE_STOCK_OF_CAMP", classify("is the shelter sound"), "and so did this");
        assertEquals("UNKNOWN", classify("is the animal sound"), "the exclusion that did reach its branch");
    }

    /**
     * The body, asked after in the words a person uses (#37).
     *
     * <p>Swept 41 sentences: how you are, the wound, cold and wet, rest and illness. The body chain was in far
     * better shape than the water chain — WARM/DRY/COOL/SHELTER_BODY and most of TREAT_WOUND already worked — and
     * 12 still reached nothing or the wrong thing.
     *
     * <p>The gaps all had the same shape: <b>a literal phrasing was in and its obvious twin was not.</b>
     * "how tired" was in and "am I tired" was not; "am I cold" was in and "am I hungry" was not; "staunch" was in
     * and "stop the bleeding" was not. <b>hours_without_food</b> drives starvation exactly as
     * <b>hours_without_water</b> drives thirst, and fixing thirst without hunger would have been the same defect
     * with a different column.
     */
    @Test void theBodyAnsweredInPlainWords() throws Exception {
        // Hunger and thirst, the two columns that kill a Chronicle.
        assertEquals("SENSE_BODY", classify("am i hungry"));
        assertEquals("SENSE_BODY", classify("how hungry am i"));
        assertEquals("SENSE_BODY", classify("am i thirsty"));
        // Tiredness, shivering, sickness, and the general question.
        assertEquals("SENSE_BODY", classify("am i tired"));
        assertEquals("SENSE_BODY", classify("i am shivering"));
        assertEquals("SENSE_BODY", classify("i feel sick"));
        assertEquals("SENSE_BODY", classify("what is wrong with me"));
        // The wound AS A QUESTION rather than as work — asking is not dressing, the same shape as the lean-to.
        assertEquals("SENSE_BODY", classify("check the wound"));
        assertEquals("SENSE_BODY", classify("is the wound healing"));
        // ...and the wound as work, which must stay work.
        assertEquals("TREAT_WOUND", classify("bind the wound"), "which always worked");
        assertEquals("TREAT_WOUND", classify("stop the bleeding"));
        assertEquals("TREAT_WOUND", classify("put a poultice on it"));
        assertEquals("TREAT_WOUND", classify("bandage my arm"));
        // The neighbours, unchanged.
        assertEquals("WARM_BODY", classify("warm myself"));
        assertEquals("DRY_BODY", classify("dry off"));
        assertEquals("SHELTER_BODY", classify("get out of the wind"));
        assertEquals("REST", classify("rest"));
        // A poultice ON A BEAST is husbandry, and the noun is the whole of the difference.
        assertEquals("TEND_ANIMAL", classify("put a poultice on the ewe"));
    }

    /**
     * The food chain, and a complete subsystem nobody could ask about (#37).
     *
     * <p>Swept 36 sentences: finding food, cooking it, eating it, keeping it, counting it. The cooking and
     * preserving half is in good order — `smoke the meat`, `dry the meat`, `salt the meat` and `bake bread` all
     * reach their material process and refuse by naming exactly what is missing, and `cook supper` even asks
     * which dish is meant. <b>The keeping half could not be asked at all.</b>
     *
     * <p>{@code foodStocktake} already answers every one of these precisely: per lot it reports the tier the food
     * keeps on and the span left in it — <i>"(cooked) with two days left in it"</i> — says <i>"gone over and not
     * safe"</i> for what is past, and warns <i>"they will sicken you if you try"</i>. The whole preservation
     * subsystem is wired, RAW through SALTED. The rule reaching it listed literals that covered "the food", "the
     * meat" and "it", and nothing else:
     *
     * <pre>
     *   will it keep            -> answered     will this keep          -> nothing
     *   is the meat still good  -> answered     is this still good      -> nothing
     *   anything gone off       -> answered     has this gone off       -> nothing
     *                                           is the meat spoiled     -> nothing; "spoil" appeared
     *                                                                      nowhere in the rule
     *   how long will the food  -> answered     how long will this keep -> MEASURE, which answered about
     *                                                                      THE DARK: "it is too dark to see
     *                                                                      the fine of it" — a wrong answer
     * </pre>
     */
    /**
     * The clothing and warmth chain (#37) — a Chronicle freezes without it.
     *
     * <p>Swept 37 sentences: making garments, wearing them, asking after their state, being cold, drying them.
     * <b>15 reached nothing or the wrong thing. Now 2</b>, and those two are a data gap recorded on the issue.
     *
     * <p>The worst was a question answered by doing the thing — the fifth time this project has found that shape:
     *
     * <pre>
     *   what am I wearing  ->  EQUIP
     * </pre>
     *
     * <b>"wearing" contains "wear"</b>, so the equip rule claimed it by substring — and EQUIP puts on the FIRST
     * wearable it finds. With everything already worn it answered *"you have nothing unequipped that can be worn
     * or wielded"*; with a spare garment to hand it would have put it on. Asking what you are dressed in is a
     * reading, and the gear reading already walks the kit and reports its condition.
     *
     * <p>The rest were the familiar shape — a literal phrasing in, its plain twin out: `am I cold` reached the
     * body and `I am cold` reached nothing; `am I wet` reached it and `is my cloak wet` did not; `dry my clothes`
     * worked and `dry the cloak by the fire` did not; `put on everything I have` worked and `get dressed` did not.
     */
    /**
     * Taking the stock to water or to grass (#106) — the player-side half of a model that already worked.
     *
     * <p>Thirst and hunger both fall by <b>where the keeper stands</b>: both ticks read the bond's
     * {@code chronicle_id}'s chunk, not the animal's, so a kept herd already goes where its keeper goes. Nothing
     * about the following was missing. What reached nothing was the <b>saying</b> of it — six of nine phrasings,
     * including the three plainest.
     *
     * <p>So it is a MOVE, and the ground names the bearing exactly as it does for a crossing or a climb.
     *
     * <p><b>Placed ahead of the welfare and feeding rules, which is what makes it work.</b> On the first cut it
     * sat after them, and `put the animals out to graze` was still answered by FEED_ANIMAL ("none of your animals
     * is hungry") while `lead the stock to water` got the welfare reading — a reading in answer to a request to
     * ACT. Measured, not assumed: the live replay showed both, which is why the rule moved.
     */
    @Test void takingTheStockToWaterOrGrass() throws Exception {
        for (String p : new String[]{"take the stock to water", "drive the herd to the stream",
                                     "move the animals to fresh grass", "take the goats to grass",
                                     "lead the stock to water", "put the animals out to graze",
                                     "walk the cattle to the pool", "turn the sheep out to pasture"})
            assertEquals("MOVE", classify(p), p);

        // THE NEIGHBOURS, which must keep their own answers. A drive names a DESTINATION; these do not, and
        // `tie up the goat` in particular is the restraint reading — once the worst wrong answer in this project,
        // so it is held here too.
        assertEquals("CHECK_STOCK", classify("water the stock"), "the welfare reading, not a drive");
        assertEquals("CHECK_STOCK", classify("check on the goats"));
        assertEquals("CHECK_STOCK", classify("tie up the goat"), "the restraint answer");
        assertEquals("FEED_ANIMAL", classify("feed the stock"), "hand-feeding is not grazing");

        // And a crossing is still a crossing: the keeper getting OVER water, not bringing stock TO it.
        assertEquals("MOVE", classify("wade across"));
        assertEquals("JUDGE_CROSSING", classify("can i get across here"));
    }

    /**
     * The feet under the beast (#106, V414) — the last model gap on that ticket.
     *
     * <p>The asking already worked: `is he lame` and `is she limping` have always reached CHECK_STOCK, which
     * reports four real, maintained states and had nothing whatever to say about a foot. The ticket warned for
     * four comments that routing this to grooming would claim the COAT improved, which is a lie — so the verb
     * splits are asserted in both directions here.
     */
    /**
     * Fitting gear to a beast is a question about gear you own, not a recipe (#106).
     *
     * <p>Measured across 14 phrasings, nine reached nothing or something wrong — and the two worst were
     * answered by the recipe to MAKE a pack-saddle. The seventh "asking is not doing/making" on this project.
     *
     * <p>The answer already existed: {@code judgeHaulage} reports, per beast and by body size, whether the gear
     * you keep fits it. The ticket called this a missing fitted-to-this-beast STATE; it is a missing word.
     */
    @Test void theGearQuestionIsNotARecipe() throws Exception {
        for (String p : new String[]{"fit the pack saddle", "fit the pack saddle to the goat",
                                     "put the panniers on", "put the pack saddle on the ox",
                                     "does the harness fit", "fit the panniers",
                                     "is the harness the right size", "how much can the ox carry",
                                     "try the yoke on the ox"})
            assertEquals("JUDGE_HAULAGE", classify(p), p);

        // MAKING IS STILL MAKING, which is the whole hazard of a rule keyed on gear nouns. UNKNOWN is the
        // CORRECT answer here and not a miss: it is what reaches the material matcher, which then resolves the
        // recipe. Verified live on a booted backend, where all three come back PROCESS_MATERIAL.
        assertEquals("UNKNOWN", classify("make a pack saddle", true), "a craft must reach the material matcher");
        assertEquals("UNKNOWN", classify("make an ox-yoke", true));
        assertEquals("UNKNOWN", classify("make a leather harness", true));

        // AND PUTTING GEAR AWAY IS STORING IT. "put the" is broad, and the gear noun alone would have taken
        // this off the container rules that own it.
        assertEquals("STORE", classify("put the harness in the basket"));
        assertEquals("STORE", classify("store the harness"));

        // THE WELFARE QUESTION KEEPS THE BEAST, and the gear rule must not take it. "is the ox fit" asks after
        // the animal and reached nothing before this — the welfare words knew "how is" and "what state" and not
        // the plainest one a keeper says. The two rules share ONE gear vocabulary so they cannot drift apart on
        // which sentence is which.
        assertEquals("CHECK_STOCK", classify("is the ox fit"), "a beast's condition, not a strap's size");
        assertEquals("CHECK_STOCK", classify("is the ox in condition"));
        assertEquals("CHECK_STOCK", classify("how is the ox"), "which always worked");
        // ...and the hoof work from the same ticket is untouched by any of it.
        assertEquals("PARE_HOOVES", classify("pare the hooves"));
    }

    /**
     * Asking about the night, and the night itself (#37).
     *
     * <p>These live HERE, and not in the integration test that owns this behaviour, for a reason worth writing
     * down. Every one of the sleeping phrases does not merely route — it SLEEPS, eight hours a time. Asserting
     * five of them through the composer drained the shared world: forty hours of simulated time with nothing
     * drunk, Chronicles dying of thirst one after another, and eventually a sibling test in the same class
     * failing with "No living Chronicle exists" before it had asserted anything of its own.
     *
     * <p>A routing claim is pure over the text. It belongs where it can be made without a world to spend, which
     * is this reflective seam. The integration test keeps ONE real sleep, because that the act still performs is
     * a different claim and worth paying for once.
     */
    @Test void askingAboutTheNightIsNotSleeping() throws Exception {
        // The question forms, which used to reach SLEEP and sleep eight hours in answer to a question.
        assertEquals("SENSE_BODY", classify("did I sleep well"));
        assertEquals("SENSE_BODY", classify("how did I sleep"));
        assertEquals("SENSE_BODY", classify("I slept badly"));
        assertEquals("SENSE_BODY", classify("I slept well"));
        assertEquals("SENSE_BODY", classify("did I rest"));
        assertEquals("SENSE_BODY", classify("wake up"));

        // And the act itself, all five phrasings, at no cost at all. A Chronicle who cannot sleep dies of it,
        // so this is the half that must never quietly break.
        assertEquals("SLEEP", classify("sleep"));
        assertEquals("SLEEP", classify("go to sleep"));
        assertEquals("SLEEP", classify("bed down for the night"));
        assertEquals("SLEEP", classify("take a nap"));
        assertEquals("SLEEP", classify("sleep through the night here"));
        assertEquals("REST", classify("rest a while"));
    }

    @Test void theFeetUnderTheBeast() throws Exception {
        for (String p : new String[]{"pare the hooves", "trim the hooves", "pare the goat's hooves",
                                     "trim his hooves", "see to the goat's feet", "rasp the hooves",
                                     "clip the goat's hooves", "do the hooves"})
            assertEquals("PARE_HOOVES", classify(p), p);

        // NOT THE COAT, which is the exact lie the ticket named. Combing and paring want different things in the
        // hand and report different states, and a grooming answer to a foot question would say the coat came up
        // clean when the keeper asked about a hoof.
        assertEquals("GROOM_ANIMAL", classify("groom the goat"), "which always worked");
        assertEquals("GROOM_ANIMAL", classify("comb the goat's coat"));
        assertEquals("GROOM_ANIMAL", classify("brush the mane"));

        // NOT THE PLAYER'S OWN FEET. What matters here is that this rule does not CLAIM them — a keeper asking
        // after their own feet must not be handed a blade and an animal.
        //
        // `how are my feet` reaches nothing, and reached nothing before this slice: the body model keeps no
        // foot state, so there is no honest answer to route it to and inventing one would be the opposite of
        // this ticket's whole point. Asserted as UNKNOWN rather than quietly left out, so the gap is recorded
        // where the next cycle will see it instead of being rediscovered.
        assertEquals("UNKNOWN", classify("how are my feet", false),
            "the player's own feet have no state in the body model — a real gap, and not this rule's to fill");
        assertEquals("WARM_BODY", classify("warm my feet by the fire"), "but warming them works, and must keep working");

        // AND "TRIM" STILL BELONGS TO THE WOOD. The coppice rule's own comment says it is gated on the wood "so
        // that trimming a wick, a hoof or a hide stays with its own work" — this is the other side of that
        // bargain, asserted rather than assumed.
        assertEquals("COPPICE", classify("trim the hazel stool"), "trimming wood is still coppicing");

        // THE WELFARE QUESTION, which is where the foot is now REPORTED. `is the goat lame` always routed;
        // `is he limping` reached nothing, because the gate wanted a beast NAMED and a keeper standing beside
        // their goat says "he". In this world nothing limps and nothing goes lame but a kept animal, so those
        // two words now carry the subject themselves.
        assertEquals("CHECK_STOCK", classify("is the goat lame"), "which always worked");
        assertEquals("CHECK_STOCK", classify("is he limping"), "the pronoun half of the same sentence");
        assertEquals("CHECK_STOCK", classify("is the goat limping"));

        // ...AND NOT THE PLAYER'S OWN LEG, which is the one thing that widening could wrongly claim. Answering
        // "my leg is lame" with a stock report would be face 6 exactly — a confidently wrong answer in the same
        // even voice as every true one. All four reach nothing, which is a real gap in the body reading and is
        // recorded on #37 rather than papered over here: a missing answer costs a turn, a wrong one costs the
        // player their model of what the game heard.
        for (String mine : new String[]{"my leg is lame", "am i lame", "is my leg lame", "i am limping"})
            assertEquals("UNKNOWN", classify(mine, false), mine + " is the body's business, not the herd's");
    }

    @Test void theClothingChain() throws Exception {
        // ASKING IS NOT DRESSING. The headline fix.
        assertEquals("TAKE_STOCK_OF_GEAR", classify("what am i wearing"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("what have i got on"));
        // ...and the act it was stealing from must still work.
        assertEquals("EQUIP", classify("wear the cloak"), "which always worked");
        assertEquals("EQUIP", classify("put on my cloak"));
        assertEquals("UNEQUIP", classify("take off my cloak"));

        // "DRESS" IS THREE THINGS, and the object decides. My first cut took all three and the standing routing
        // test caught it: `dress the boar` became EQUIP. The clothing sense is the one with NO object.
        assertEquals("EQUIP", classify("get dressed"));
        assertEquals("EQUIP", classify("dress"));
        assertEquals("HARVEST_CARCASS", classify("dress the boar"), "dressing a boar is butchering it");
        assertEquals("TREAT_WOUND", classify("dress the wound"), "and dressing a wound is medicine");

        // Covering up is putting on what you have.
        assertEquals("EQUIP", classify("layer up"));
        assertEquals("EQUIP", classify("wrap up warm"));
        assertEquals("EQUIP", classify("cover my hands"));
        assertEquals("EQUIP", classify("put my hood up"));

        // Stating the condition, not asking after it.
        assertEquals("WARM_BODY", classify("i am cold"));
        assertEquals("WARM_BODY", classify("i am freezing"));
        assertEquals("WARM_BODY", classify("huddle down"));
        assertEquals("SENSE_BODY", classify("am i cold"), "the question still reaches the reading");

        // The garment's wetness is the body's question: a soaked cloak is how a body gets wet, and wetness is
        // what decides how fast it loses heat.
        assertEquals("SENSE_BODY", classify("is my cloak wet"));
        assertEquals("SENSE_BODY", classify("are my clothes wet"));
        assertEquals("SENSE_BODY", classify("am i wet"), "which always worked");

        // Drying it, said about the garment rather than about yourself.
        assertEquals("DRY_BODY", classify("dry my clothes"), "which always worked");
        assertEquals("DRY_BODY", classify("dry the cloak by the fire"));
        assertEquals("DRY_BODY", classify("hang my clothes to dry"));

        // And the neighbours, unchanged.
        assertEquals("CRAFT_GARMENT", classify("make a cloak"));
        assertEquals("REPAIR_ITEM", classify("mend my cloak"));
        assertEquals("LINE_GARMENT", classify("line the cloak with fur"));

        // A NEAR MISS THE REPLAY CAUGHT, held here because only ORDER resolves it. The new drying clause matches
        // a drying word beside a named garment, and "stuff my boots with DRY grass" carries both "dry" and "my
        // boots" — so it would be DRY_BODY if LINE_GARMENT did not claim it first. It does, and these assert
        // that it keeps doing so; an ordering nobody tests is an ordering that will be changed.
        assertEquals("LINE_GARMENT", classify("stuff my boots with dry grass"));
        assertEquals("LINE_GARMENT", classify("stuff my clothes with moss"));
        assertEquals("DRY_BODY", classify("get under cover and dry off"), "and real drying still dries");
    }

    /**
     * Making the tackle is not fishing with it (#37).
     *
     * <p>Measured: {@code make a fishing line} and {@code make a fish hook} both answered <em>"You watch the
     * ground a while. There is no water here that holds anything worth taking"</em> — the request to MAKE a thing
     * performed the activity its noun names. The recipes exist and declare plain keywords ("fish hook", "fish
     * trap", "bast fishing line"), so they were reachable all along by any verb the FISH rule does not claim:
     * {@code carve a fish hook} worked the whole time.
     *
     * <p>The inverse of the asking-is-not-doing family, with the same cause — a sentence carries the noun of the
     * thing it is about, and whatever owns that noun runs before the matcher.
     */
    @Test void makingTheTackleIsNotFishingWithIt() throws Exception {
        // UNKNOWN is the correct answer in this DB-free test: it is what reaches the material matcher, which
        // then resolves the recipe. Verified live, where `make a fish hook` comes back PROCESS_MATERIAL with
        // "Carve a fish hook turns on a cutting edge".
        assertEquals("UNKNOWN", classify("make a fish hook", false), "a craft must reach the material matcher");
        assertEquals("UNKNOWN", classify("make a fishing line", false));
        assertEquals("UNKNOWN", classify("craft a fish hook", false));

        // AND FISHING ITSELF MUST STILL FISH, which is the whole hazard of excluding a verb from a noun-gated
        // rule. All four of these were measured live as FISH after the change.
        assertEquals("FISH", classify("go fishing"), "which always worked");
        assertEquals("FISH", classify("catch a fish"));
        assertEquals("FISH", classify("cast the net"));
        assertEquals("FISH", classify("fish"));
        assertEquals("FISH", classify("spear a pike"));

        // AND THE PAIR V417's "hook" KEYWORD BROKE. Declaring "hook" for the four fish hooks made
        // `hook an eel` an offer to carve tackle, because the FISH rule defers to the material matcher whenever
        // the matcher claims the phrase. AFishYouAlreadyCaughtIntegrationTest caught it — that sentence has been
        // asserted as FISH all along — so a sentence naming a fish by SPECIES no longer defers.
        //
        // Both directions, because this is the exact conflict: the species fishes, the family word crafts.
        assertEquals("FISH", classify("hook an eel", true), "a named species is fishing, matcher or no matcher");
        assertEquals("FISH", classify("land a trout", true));
        assertEquals("FISH", classify("net some perch", true));
        // ...and the family word still reaches its recipes. Stubbed as MATCHING, because that is the real case:
        // V417 declares "hook" as both keyword and subject, so the live matcher does claim these phrases.
        // (With the matcher stubbed ABSENT, `carve a hook` falls to MARK — "carve" is a marking verb, and
        // `carve a spoon` reaching MARK is a defect this project has already fixed once. Asserting the absent
        // case here would be asserting a world that does not exist now that the keyword is declared.)
        assertEquals("UNKNOWN", classify("make a hook", true), "and the family word still reaches its recipes");
        assertEquals("UNKNOWN", classify("carve a hook", true));

        // ...and the SPECIES list is word-bounded, because "eel" sits inside STEEL, WHEEL and PEELED, and
        // "perch" inside PERCHED. A raw contains() here would have made a steel axe into a fishing trip.
        assertEquals("UNKNOWN", classify("make a steel axe", true), "steel is not an eel");
        assertEquals("UNKNOWN", classify("make a cart wheel", true), "nor is a wheel");
    }

    @Test void theFoodChainAndTheKeepingQuestion() throws Exception {
        // Spoiling words need no subject: nothing else in this world goes off, spoils or turns rancid.
        assertEquals("TAKE_STOCK_OF_FOOD", classify("has this gone off"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("is the meat spoiled"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("has the fish turned bad"));
        // The keeping and "still good" forms carry their demonstrative IN FULL, never a bare "still good",
        // because the camp reading owns "is the shelter still good" and the gear reading owns "is my knife
        // still good" — and this rule runs before both of them. Asserted in all three directions.
        assertEquals("TAKE_STOCK_OF_FOOD", classify("will this keep"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("how long will this keep"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("is this still good"));
        assertEquals("TAKE_STOCK_OF_CAMP", classify("is the shelter still good"), "the camp keeps its question");
        assertEquals("TAKE_STOCK_OF_GEAR", classify("is my knife still good"), "and so does the gear");
        // Counting the days of it.
        assertEquals("TAKE_STOCK_OF_FOOD", classify("how many days of food"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("am i going to run out of food"));
        assertEquals("TAKE_STOCK_OF_FOOD", classify("will it keep"), "which always worked");

        // The verb FORAGE_GROUND is named for. Its rule wants a verb AND one of a dozen named objects, so the
        // bare act reached nothing. Held as a word, because "cook foraged greens" is a real process.
        assertEquals("FORAGE_GROUND", classify("forage"));
        assertEquals("FORAGE_GROUND", classify("scavenge"));
        assertEquals("FORAGE_GROUND", classify("search the forest floor"), "which always worked");

        // Breaking a fast is eating, and "eat" is all the eating rule looks for.
        assertEquals("EAT", classify("break my fast"));
        assertEquals("EAT", classify("eat the meat"), "which always worked");
        // "eat the cooked fish" must not cook ANOTHER fish — the past-participle rule, still holding.
        assertEquals("EAT", classify("eat the cooked fish"));

        // TWO OF MY OWN CHANGES MEET HERE, so the meeting is asserted: the drink rule claims "quench" and the
        // fire rule claimed it first for "quench the fire". Order decides, and both must keep working.
        assertEquals("DRINK", classify("quench my thirst"));
        assertEquals("EXTINGUISH_FIRE", classify("quench the fire"));
        assertEquals("EXTINGUISH_FIRE", classify("quench it"), "beside a fire, that is the fire");
    }

    @Test void theWaterChain() throws Exception {
        // I. Judging it. The question forms worked; the verb the intent is NAMED for did not.
        assertEquals("JUDGE_WATER", classify("is this water safe"), "which always worked");
        assertEquals("JUDGE_WATER", classify("judge the water"));
        assertEquals("JUDGE_WATER", classify("taste the water"));
        // But a perception verb that already has an intent is not available to borrow. Both of these are
        // deliberate and were broken by my first, greedier cut of the rule above.
        assertEquals("SMELL", classify("sniff the water"), "smelling is its own act");
        assertEquals("MEASURE", classify("test the depth of the water"), "and measuring is its own act");

        // II. Drawing it. Six verbs were listed and not the plainest two.
        assertEquals("COLLECT_WATER", classify("collect water"), "which always worked");
        assertEquals("COLLECT_WATER", classify("get some water"));
        assertEquals("COLLECT_WATER", classify("scoop up water"));
        assertEquals("COLLECT_WATER", classify("fill the skin"), "what somebody holding one calls it");

        // III. Making it safe. "make the water safe" answered by offering to MAKE A WATERSKIN.
        assertEquals("BOIL_WATER", classify("boil water"), "which always worked");
        assertEquals("BOIL_WATER", classify("make the water safe"));
        assertEquals("FILTER_WATER", classify("filter the water"), "unchanged");

        // IV. Drinking it. Neither "slake" nor "quench" contains "drink", which is all the drink rule looks for.
        assertEquals("DRINK", classify("drink water"), "which always worked");
        assertEquals("DRINK", classify("slake my thirst"));
        assertEquals("DRINK", classify("quench my thirst"));

        // V. Thirst is the BODY's question. hours_without_water is the only water state in the schema and it
        // drives the thirst that kills a Chronicle; "am I dry" reached the body reading and "am I thirsty" did not.
        assertEquals("SENSE_BODY", classify("am i thirsty"));
        assertEquals("SENSE_BODY", classify("how thirsty am i"));
        assertEquals("SENSE_BODY", classify("am i dry"), "which always worked");

        // VI. Carrying and emptying it — and the waterskin question that offered to make one.
        assertEquals("TAKE_STOCK_OF_GEAR", classify("is my waterskin full"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how much water do i have"));
        assertEquals("EMPTY_CONTAINER", classify("empty the waterskin"));
        assertEquals("EMPTY_CONTAINER", classify("pour the water out"));
        // A waterskin is a container now, so storing into one works as it always did for a basket.
        assertEquals("STORE", classify("put the water in the gourd"));
        assertEquals("STORE", classify("put the berries in the basket"), "unchanged");
    }

    /**
     * Asking whether a thing is poison is a question, not a meal (#37) — the food half of the water rule above.
     *
     * <p>The poison model is finished and was unreachable: {@code is_poisonous} sickens whoever eats the death
     * cap, {@code toxic} leaves the flesh on a salamander, and the eating rule's own comment states the design —
     * "the player had to know which mushroom before they ate it". Measured live, nine ways of asking reached
     * nothing, and <b>five were answered by eating it</b>, which is the one outcome the asker was avoiding.
     */
    @Test void theWorldKnewItWasPoison() throws Exception {
        // I. Asking outright. All of these reached nothing at all.
        for (String p : new String[]{"is this poisonous", "is that plant poisonous", "is this mushroom poisonous",
                                     "are these mushrooms safe", "is this toadstool safe", "is this berry safe",
                                     "is the meat safe", "is it edible", "what here is edible",
                                     "is this food still good", "is this still fresh", "has the meat turned"})
            assertEquals("JUDGE_FOOD", classify(p), p);

        // II. The five that were answered by EATING, because the late fallback matches the bare word "eat" and
        // the whole doubt in these sentences is carried by the shape around it.
        for (String p : new String[]{"is this safe to eat", "is the fish safe to eat", "can i eat this",
                                     "which of these mushrooms can i eat", "what can i eat here"})
            assertEquals("JUDGE_FOOD", classify(p), p + " — asked WHETHER, and was answered by eating");

        // III. The verb the intent is named for, which is the trap JUDGE_WATER fell into.
        assertEquals("JUDGE_FOOD", classify("judge the food"));
        // But IDENTIFY is not available to borrow: it is a deliberate EXAMINE assertion with its own comment
        // ("folds into the subject-scoped EXAMINE"), and my first cut took it and broke that test. The same
        // lesson sniff and test taught the water rule. (EXAMINE answering about the GROUND rather than the named
        // mushroom is a real defect and a different one — recorded, not fixed by taking the verb.)
        assertEquals("EXAMINE", classify("identify the mushroom"), "identifying is EXAMINE's, by design");

        // IV. "will this make me sick" went UNKNOWN because MAKING_SOMETHING matches the bare word "make". Held
        // apart from that guard rather than widening it for every rule that shares it.
        assertEquals("JUDGE_FOOD", classify("will this make me sick"));
        assertEquals("JUDGE_FOOD", classify("will this make me ill"));

        // V. WHAT MUST KEEP ITS OWN ANSWER. The water half is untouched, poisoning a weapon is still a craft,
        // and plain eating is still eating — a question rule that swallowed the act would be a worse bug than
        // the one it fixed.
        assertEquals("JUDGE_WATER", classify("is this water safe"), "the water half is its own rule");
        assertEquals("JUDGE_WATER", classify("is the water safe to drink"), "and still is");
        assertEquals("EAT", classify("eat the berries"), "plain eating is still eating");
        assertEquals("EAT", classify("eat something"));
        assertEquals("EAT", classify("break my fast"));
        // Spoilage already had an honest answer with real spans, and two voices on one question is how two
        // answers start to disagree. These stay where they were.
        assertEquals("TAKE_STOCK_OF_FOOD", classify("has this gone off"), "already answered, with spans");
        assertEquals("TAKE_STOCK_OF_FOOD", classify("is the meat spoiled"), "likewise");
        assertEquals("TAKE_STOCK_OF_FOOD", classify("check the food"), "likewise");
        // AND THE SKY. "weather" contains EAT, so a bare contains() on that word routed "what is the weather" to
        // JUDGE_FOOD. word() boundaries it. Asserted here because this rule is the reason it could break.
        assertEquals("FEEL", classify("what is the weather"), "w-eat-her is not a question about food");
        assertEquals("FEEL", classify("what is the temperature"));
    }

    @Test void aBedIsThreeThings() throws Exception {
        // I. Somewhere to sleep. The first four always worked; the rest did not.
        for (String p : new String[]{"make a bed", "make the bed", "build a bed", "lay a bed",
                                     "make a bed of grass", "gather bedding", "lay out my bedding",
                                     "make a sleeping mat", "make a pallet", "arrange a bed",
                                     "lay dry grass for a bed",
                                     "make a comfortable bed", "soften the bed", "freshen the bedding",
                                     "put down a bed of bracken"})
            assertEquals("MAKE_BED", classify(p), p);

        // II. Ground for a crop. Every one of these was already right and must stay right — TILL_GROUND runs
        // before MAKE_BED, which is the only reason widening MAKE_BED to the bare word was safe.
        for (String p : new String[]{"dig the bed over", "make a seedbed", "prepare the bed", "turn the bed",
                                     "work the bed", "hoe the bed", "prepare a seed bed",
                                     "dig a bed for the crop"})
            assertEquals("TILL_GROUND", classify(p), p);

        // III. Litter for an animal — the sense that had no rule at all.
        for (String p : new String[]{"bed down the stock", "bed down the animals", "bed the stall",
                                     "put fresh bedding in the byre"})
            assertEquals("TEND_ANIMAL", classify(p), p);

        // AND THE COUNTER-CASE THAT MUST NOT BREAK. "bed down" with no animal and no byre is a person turning
        // in, and always was. The noun is the whole of the difference, so the rule has to be tested from both
        // sides or it is only half a rule.
        assertEquals("SLEEP", classify("bed down"), "a person turning in");
        assertEquals("SLEEP", classify("bed down here for the night"));
        assertEquals("SLEEP", classify("go to bed"), "which carries no making verb");
        assertEquals("SLEEP", classify("lie down to sleep"));

        // "pen" sits inside OPEN and HAPPEN and "fold" inside FOLDABLE, so the stock-house words are whole
        // words. Without that, these would have been bedding down an animal.
        assertEquals("SLEEP", classify("bed down in the open"), "\"open\" is not a pen");

        // AND THE CATALOGUE CALLS THINGS BEDS TOO. Audited, not guessed: these are every keyword in
        // assembly_definition and material_process holding the whole word bed/beds/bedding. Each must reach its
        // own matcher, not this rule. The standing water-structure test caught the filter bed the moment the
        // widening landed; the mortar one would not have been caught by anything, which is why it was audited.
        assertEquals("UNKNOWN", classify("build a sand filter bed"), "a staged water structure");
        assertEquals("UNKNOWN", classify("dig a sand filter bed"));
        assertEquals("UNKNOWN", classify("build a raised bed platform"), "excluded by \"platform\" all along");
        assertEquals("UNKNOWN", classify("prepare bedding mortar"), "a material process, not a place to sleep");
        assertEquals("UNKNOWN", classify("bed the stone"), "which carries no making verb");
    }

    @Test void aWordIsNotASubstring() throws Exception {
        // The six wrong answers, gone. UNKNOWN here means the assembly/process matchers get their turn.
        assertEquals("UNKNOWN", classify("build a stable"));
        assertEquals("UNKNOWN", classify("build a portable shelter"));
        assertEquals("UNKNOWN", classify("build a suitable shelter"));
        assertEquals("UNKNOWN", classify("make a racket"));
        // "build a notable marker" has moved on, and this test's point is kept rather than its letter. What it
        // guards is that "notable" must not be read as a TABLE; UNKNOWN was only the state of things when it was
        // written, and #37 recorded the phrase as a defect to fix. Putting a marker up is MARK's own act.
        assertEquals("MARK", classify("build a notable marker"),
            "a notable marker is a marker, and still not a table");
        // "make a comfortable bed" was the sixth, and it no longer stops at UNKNOWN: MAKE_BED is held as the
        // word "bed" rather than the literal "a bed", so the adjective no longer defeats it. See
        // {@link #aBedIsThreeThings}.
        assertEquals("MAKE_BED", classify("make a comfortable bed"));

        // And two that now reach the RIGHT intent rather than merely a different wrong one.
        assertEquals("TRACK", classify("build a track"), "a track is read, not shelved");
        assertEquals("PLACE_WINDBREAK", classify("construct a portable windbreak"));
        assertEquals("PLACE_WINDBREAK", classify("place a windbreak"), "the verb in the intent's own name");

        // THE OTHER HALF. Every genuine craft phrase must be untouched, including the plurals, which word() does
        // not match for free — "tables" is not "table".
        assertEquals("CRAFT_DESK", classify("make a table"));
        assertEquals("CRAFT_DESK", classify("make tables"));
        assertEquals("CRAFT_DESK", classify("assemble a desk"));
        assertEquals("CRAFT_DESK", classify("build a bench"));
        assertEquals("CRAFT_WORKSTATION", classify("build a workbench"));
        assertEquals("CRAFT_SHELF", classify("build a shelf"));
        assertEquals("CRAFT_SHELF", classify("make some shelves"));
        assertEquals("CRAFT_SHELF", classify("build an archive"));
        assertEquals("CRAFT_CHAIR", classify("make a stool"));
        assertEquals("CRAFT_CHAIR", classify("build a seat"));
        assertEquals("MAKE_BED", classify("make a bed"), "which always worked and must keep working");
        assertEquals("MAKE_BED", classify("build a bed"));

        // The named "X rack" exclusions are a different mechanism and all still needed: these are genuine uses of
        // the whole word that belong elsewhere, and word boundaries cannot settle a real collision. Note the two
        // destinations — a fuel rack has its own Java intent, while the hay and harness racks are staged
        // structures and must reach the assembly matcher. Both are right; only "a shelf" would be wrong.
        assertEquals("BUILD_FUEL_RACK", classify("build a fuel rack"), "which owns its own intent");
        assertEquals("UNKNOWN", classify("build a hay rack"));
        assertEquals("UNKNOWN", classify("build a harness rack"));
        assertEquals("UNKNOWN", classify("build a drying rack"));
        assertEquals("UNKNOWN", classify("build a sleeping bench"));

        // EVERY keyword of every rack-and-bench assembly, replayed. Auditing the exclusion list against the
        // catalogue — rather than trusting it — is what turned up "smoking rack": the smoke rack declares that
        // word and the exclusion named only "smoke rack", so one of its five keywords was shadowed and the other
        // four were not. A list of literal exceptions is only as good as its last audit.
        assertEquals("UNKNOWN", classify("build a smoke rack"));
        assertEquals("UNKNOWN", classify("make a smoking rack"), "the keyword that was shadowed");
        assertEquals("UNKNOWN", classify("fodder rack"));
        assertEquals("CRAFT_WORKSTATION", classify("build a sewing table"), "a workstation, not a desk");
    }

    /**
     * fo-REST (#37). The REST rule asked {@code value.contains("rest")}, and eight sentences about something
     * else each cost the Chronicle an hour of simulated time — measured on a booted world, not guessed:
     *
     * <pre>
     *   walk into the forest            -> "You remain still for an hour"
     *   the forest floor is damp        -> an hour
     *   restore the fence               -> an hour, and never reached the repair
     *   wrestle the boar / arrest the thief / make restitution / restring my bow
     *   the rest of the herd is fine    -> an hour
     * </pre>
     *
     * <p>The worst is {@code walk into the forest}: a plain instruction to go somewhere, answered by sitting
     * down, because the name of the ground contains the word. Same family as w-EAT-her, ca-BIN and o-PEN ground.
     */
    @Test
    void theForestIsNotARest() throws Exception {
        // NONE of these is a rest, and every one of them was.
        assertNotEquals("REST", classify("walk into the forest"), "fo-REST");
        assertNotEquals("REST", classify("the forest floor is damp"));
        assertNotEquals("REST", classify("wrestle the boar"), "w-REST-le");
        assertNotEquals("REST", classify("arrest the thief"), "ar-REST");
        assertNotEquals("REST", classify("make restitution"), "REST-itution");
        assertNotEquals("REST", classify("restring my bow"), "REST-ring");
        assertNotEquals("REST", classify("the crest of the ridge"), "c-REST");
        // "the rest of" is the whole word and still not a rest: it is a quantity.
        assertNotEquals("REST", classify("the rest of the herd is fine"));
        assertNotEquals("REST", classify("put the rest of the wood on the fire"));

        // AND IT IS A JOURNEY, which is where this file already puts going to a named thing: `go to the river`
        // and `head for the high ground` are asserted as TRAVEL two tests above, so a second path for walking
        // to a kind of ground would have contradicted a decision this suite already holds. The first cut of
        // this fix did exactly that and broke both of those assertions — the gap was never a missing rule but a
        // missing FORM: every way of going TO a thing was listed and no way of going INTO one.
        assertEquals("TRAVEL", classify("walk into the forest"));
        assertEquals("TRAVEL", classify("go into the woods"));
        assertEquals("TRAVEL", classify("head into the marsh"));
        assertEquals("TRAVEL", classify("walk toward the shore"));
        assertEquals("TRAVEL", classify("set off for the hills"));
        assertEquals("TRAVEL", classify("go to the river"), "which this suite already asserted");
        assertEquals("TRAVEL", classify("head for the high ground"), "and this");

        // A BEARING IS STILL A STEP, not a journey: the TRAVEL rule is gated on there being no direction, and
        // widening its verbs must not take the walking rule's own sentences.
        assertEquals("MOVE", classify("walk north"));
        assertEquals("MOVE", classify("head east"));
        assertEquals("MOVE", classify("go into the north"));

        // THE WORK VERBS KEEP THEIRS. "the wood", "the trees" and "timber" are all kinds of ground in this
        // game's vocabulary and all objects of a day's work, so the gather rules must be untouched.
        assertEquals("GATHER_BRANCHES", classify("go and gather wood"));
        assertEquals("GATHER_BRANCHES", classify("go to the wood and cut timber"));

        // AND RESTING STILL RESTS, which is the control: a word-boundary fix that broke the rule it guards
        // would pass every assertion above.
        assertEquals("REST", classify("rest for a while"));
        assertEquals("REST", classify("take a rest"));
        assertEquals("REST", classify("I need to rest"));
        assertEquals("REST", classify("let me rest my legs"));
        assertEquals("REST", classify("sit down for a while"));
        assertEquals("REST", classify("resting here a moment"));
        assertEquals("REST", classify("wait here"));

        // Asking whether you have rested is not resting — the family #788 closed for sleep.
        assertEquals("SENSE_BODY", classify("have I rested"));
        assertEquals("SENSE_BODY", classify("am I rested"));

        // And one that now reaches the RIGHT intent rather than a different wrong one: a fence is a standing
        // thing and "restore" is the word a player reaches for.
        assertEquals("REPAIR_STRUCTURE", classify("restore the fence"));
        assertEquals("REPAIR_STRUCTURE", classify("repair the fence"), "which always worked");
    }

    /**
     * What the keeper says about their stock (#37). #783 built the statement frame for the player's own body —
     * "I am hungry", "I am tired" — and this ticket recorded the keeper's half as still open, leaving it to
     * #106, which has since closed without it.
     *
     * <p>Measured on a booted world: {@code the goats are thirsty} and {@code the goat is sick} reached the
     * stock reading, while {@code the goat is hungry}, {@code my goat is starving}, {@code the ox is tired} and
     * {@code the animals need water} reached <b>nothing at all</b> — every one of them a real, maintained
     * number that reading already reports.
     */
    @Test
    void theKeeperSaysWhatTheirStockWants() throws Exception {
        assertEquals("CHECK_STOCK", classify("the goat is hungry"));
        assertEquals("CHECK_STOCK", classify("my goat is starving"));
        assertEquals("CHECK_STOCK", classify("the ox is tired"));
        assertEquals("CHECK_STOCK", classify("the animals need water"));
        assertEquals("CHECK_STOCK", classify("the goats are thirsty"), "which already worked");
        assertEquals("CHECK_STOCK", classify("the goat is sick"), "and so did this");

        // THE PLAYER'S OWN BODY KEEPS ITS OWN, which is the one thing this could wrongly claim. "I am hungry"
        // naming no beast must never reach a stock report — that would be face 6 exactly.
        assertNotEquals("CHECK_STOCK", classify("I am hungry"));
        assertNotEquals("CHECK_STOCK", classify("I am tired"));
        assertNotEquals("CHECK_STOCK", classify("I need water"));

        // And the acts keep their verbs: asking after a beast is not feeding one. (In this harness nothing is
        // kept — `keepsSuchABeast` is stubbed false — so feeding a goat reaches the TAMING rule's feeding
        // branch rather than FEED_ANIMAL. Which of the two it is depends on the world; that it is neither a
        // stock report nor a rest is what this asserts.)
        assertNotEquals("CHECK_STOCK", classify("feed the goat"));
        assertNotEquals("REST", classify("feed the goat"));
    }

    /**
     * Asking about the light is not striking one (#37, V437).
     *
     * <p>Four finished rules decided whether a Chronicle could see to work — the hour, the rock, the order
     * carried lights are spent in, and whether weather would gut a bare flame — and not one of them could be
     * asked about. Measured on a booted world: {@code is there enough light to work}, {@code it is too dark to
     * see} and {@code what have I got to burn} reached nothing or spent an act on nothing.
     */
    @Test
    void askingAboutTheLightIsNotStrikingOne() throws Exception {
        assertEquals("JUDGE_LIGHT", classify("is there enough light to work"));
        assertEquals("JUDGE_LIGHT", classify("it is too dark to see"));
        assertEquals("JUDGE_LIGHT", classify("what have I got to burn"));
        assertEquals("JUDGE_LIGHT", classify("can I see well enough to write"));
        assertEquals("JUDGE_LIGHT", classify("is it dark in here"));
        // AND THE SKY KEEPS THE SKY. "is it dark" with no place in it is the hour, and READ_THE_SKY answers it
        // with the sun, the light left and the season — which is right outdoors and was being given inside a
        // cave at noon to a question about the cave.
        assertEquals("READ_THE_SKY", classify("is it dark"));
        assertEquals("READ_THE_SKY", classify("is it getting dark"));
        assertEquals("JUDGE_LIGHT", classify("have I got a light"));

        // THE ACTS KEEP THEIRS, which is the half this could have broken: a question about the light must not
        // take the lighting of a fire, and lighting one must not be answered with a reading.
        assertEquals("LIGHT_FIRE", classify("light a fire"));
        assertEquals("LIGHT_FIRE", classify("light the fire"));
        assertNotEquals("JUDGE_LIGHT", classify("put out the fire"));
        // And the body's own darkness words stay the body's: "I cannot see" is sight, not inventory.
        assertNotEquals("JUDGE_LIGHT", classify("make a torch"));
    }

    /**
     * The plainest weather questions of all (#37).
     *
     * <p>The sky rule knew the FORECAST — "is it going to rain", "will it rain", "will it freeze" — and not one
     * way of asking what the weather is DOING. Measured on a booted world, six of eight reached nothing at all
     * while {@code weather_kind}, {@code wind_speed_kph} and {@code ambient_temperature_c} sat on the row
     * behind them: "your hands find no purchase on it", to `is it raining`.
     */
    @Test
    void thePlainestWeatherQuestions() throws Exception {
        assertEquals("READ_THE_SKY", classify("is it raining"));
        assertEquals("READ_THE_SKY", classify("is it snowing"));
        assertEquals("READ_THE_SKY", classify("is it windy"));
        assertEquals("READ_THE_SKY", classify("is it cold out"));
        assertEquals("READ_THE_SKY", classify("what is it doing outside"));
        assertEquals("READ_THE_SKY", classify("is the weather turning"));
        assertEquals("READ_THE_SKY", classify("has it stopped raining"));

        // THE BODY KEEPS ITS OWN, which is why no bare "cold" or "freezing" went into that list: this rule runs
        // long before the body block, so a bare word would have taken "I am freezing" off WARM_BODY.
        assertEquals("WARM_BODY", classify("I am cold"));
        assertEquals("WARM_BODY", classify("I am freezing"));
        assertEquals("DRY_BODY", classify("I am soaked"));

        // And the place question stays the light reading's, not the sky's.
        assertEquals("JUDGE_LIGHT", classify("is it dark in here"));
    }

    /**
     * Asking what a thing takes (#37, V438).
     *
     * <p>Every bill of materials in this game is data, in two tables, and a Chronicle could not ask. Measured
     * on a booted world, the worst answer of that sweep:
     *
     * <pre>
     *   what do I need for a basket  ->  SENSE_BODY: "There is a hard pressure low in you…"
     * </pre>
     *
     * <p>A question about materials answered with the state of the player's bowels, in the same even voice as
     * every true answer. "what do i need" sits inside the body block's frame, so the making rule goes above it
     * — and the body must keep the sentences that really are about the body, which is the other half here.
     */
    @Test
    void askingWhatAThingTakes() throws Exception {
        assertEquals("JUDGE_MAKING", classify("what do I need for a basket"));
        assertEquals("JUDGE_MAKING", classify("what do I need to make cordage"));
        assertEquals("JUDGE_MAKING", classify("what does tanning a hide take"));
        assertEquals("JUDGE_MAKING", classify("what goes into a leather pouch"));
        assertEquals("JUDGE_MAKING", classify("what is clay good for"));
        assertEquals("JUDGE_MAKING", classify("what can I do with the oak bark"));

        // THE BODY KEEPS ITS OWN. "I need water" and "I need to rest" are the body's business and were never
        // the catalogue's; the making rule is held to the "what do I need FOR" frame for exactly this reason.
        assertNotEquals("JUDGE_MAKING", classify("I need water"));
        assertNotEquals("JUDGE_MAKING", classify("I need to rest"));
        assertNotEquals("JUDGE_MAKING", classify("I am hungry"));

        // And the ACTS keep theirs: asking what a basket takes is not weaving one.
        assertNotEquals("JUDGE_MAKING", classify("weave a basket"));
        assertNotEquals("JUDGE_MAKING", classify("tan the hide"));
    }

    /**
     * Asking whether you are dressed for it (#37, V440).
     *
     * <p>153 items carry insulation and 131 shed water; the body is charged against both every turn, with a cap
     * on each and a 1.6x penalty for wearing them wet. None of it could be asked about. Measured on a booted
     * world: {@code will these clothes keep me warm} and {@code is my cloak keeping the rain off} reached
     * nothing, and {@code am I dressed for this weather} reached EQUIP — "You have nothing unequipped that can
     * be worn or wielded", a question answered by an act that found nothing to do.
     */
    @Test
    void askingWhetherYouAreDressedForIt() throws Exception {
        assertEquals("JUDGE_CLOTHING", classify("will these clothes keep me warm"));
        assertEquals("JUDGE_CLOTHING", classify("am I dressed for this weather"));
        assertEquals("JUDGE_CLOTHING", classify("is my cloak keeping the rain off"));
        assertEquals("JUDGE_CLOTHING", classify("what should I wear"));
        assertEquals("JUDGE_CLOTHING", classify("will this cloak keep me warm"));

        // AND THE BODY KEEPS WARMTH-AS-FELT AND WETNESS, which this suite had already decided twice — whether
        // the night is survivable is the body's question, and a soaked cloak is how a body gets wet. My first
        // cut of this rule took both, and these two assertions are what caught it.
        assertEquals("SENSE_BODY", classify("will I be warm enough tonight"));
        assertEquals("SENSE_BODY", classify("are my clothes wet"));
        assertEquals("SENSE_BODY", classify("is my cloak wet"));

        // THE ACTS AND THE OTHER READINGS KEEP THEIRS. "warm my hands" is the body's, a fire's warmth is the
        // fire's, and putting a thing on is still an act — this rule sits above the light reading and must not
        // take either of their sentences.
        assertEquals("WARM_BODY", classify("warm my hands"));
        assertEquals("WARM_BODY", classify("I am cold"));
        assertNotEquals("JUDGE_CLOTHING", classify("warm the water"));
        assertNotEquals("JUDGE_CLOTHING", classify("put on my boots"));
        assertNotEquals("JUDGE_CLOTHING", classify("take off my shoes"));
        assertNotEquals("JUDGE_CLOTHING", classify("is there enough light to work"));
    }

    /**
     * Naming a place is not mapping it, and a note is a writing (#37).
     *
     * <p>Swept the records chain on a booted world. The naming rule wanted a naming verb and "this place", and
     * {@code mark this place on my map} has both — so it took the sentence and read the rest as the NAME:
     *
     * <pre>
     *   mark this place on my map  ->  "You fix a name to this place: on my map."
     * </pre>
     *
     * <p>Nobody calls a place "on my map". Two more forms of the same intention reached nothing at all
     * ({@code add this place to my map}, {@code note this place on the map}), and so did the plainest way of
     * saying you want something written down ({@code make a note of this place}, {@code note down what I
     * found}) — while {@code write this down} had always worked.
     */
    @Test
    void namingAPlaceIsNotMappingIt() throws Exception {
        // Putting a place ON the map is mapping.
        assertEquals("SKETCH_MAP", classify("mark this place on my map"));
        assertEquals("SKETCH_MAP", classify("add this place to my map"));
        assertEquals("SKETCH_MAP", classify("note this place on the map"));
        assertEquals("SKETCH_MAP", classify("draw a map"), "which always worked");

        // Giving it a name is naming.
        assertEquals("DESIGNATE", classify("call this place Stonefield"));
        assertEquals("DESIGNATE", classify("name this place Stonefield"));

        // AND THE MARKER VERBS KEEP THEIRS. The map noun is what the widened rule is gated on, so a trail
        // marker — which has no map in it — is still MARK's.
        assertEquals("MARK", classify("mark the trail"));
        assertEquals("MARK", classify("leave a marker here"));

        // A note is a writing...
        assertEquals("WRITE", classify("make a note of this place"));
        assertEquals("WRITE", classify("note down what I found"));
        assertEquals("WRITE", classify("keep a record of the herd"));
        assertEquals("WRITE", classify("write this down"), "which always worked");

        // ...but the bare word "note" is also how a person says they have observed something, and that belongs
        // to the tracking and the looking. This is why the rule is held to the noting PHRASES.
        assertEquals("TRACK", classify("note the tracks"));
        assertNotEquals("WRITE", classify("observe the tracks"));
    }

    /**
     * Asking what you know of this ground (#37, V441).
     *
     * <p>planTravel decides whether a place can be found again — on a map you carry, or marked and memorised,
     * or walked often enough to be routine, or memorised and visited recently — and chronicle_chunk_visit has
     * counted every arrival since the table existed. None of it could be asked: {@code what do I know about
     * this place} reached nothing, so the only way to learn that a name had faded was to try to travel to it
     * and be told "you cannot call the way to mind", a day's walk later.
     */
    @Test
    void askingWhatYouKnowOfThisGround() throws Exception {
        assertEquals("JUDGE_PLACE", classify("what do I know about this place"));
        assertEquals("JUDGE_PLACE", classify("have I been here before"));
        assertEquals("JUDGE_PLACE", classify("could I find my way back here"));
        assertEquals("JUDGE_PLACE", classify("do I remember this place"));
        assertEquals("JUDGE_PLACE", classify("is this place on my map"));

        // THE NAMING QUESTION KEEPS ITS OWN. "what did I call this place" answers with the NAME and has done
        // since #37's earlier act; this rule sits above it and must not take it.
        assertEquals("OBSERVE", classify("what did I call this place"));
        // And one of the forms it claims was never reaching it: "what is this" is a substring of
        // "what is this place called", and the identify rule holds that substring and sits higher — so a
        // question about the name of the ground was answered as though an object had been held up to the
        // light. The clause that was meant to own it had been dead since it was written.
        assertEquals("OBSERVE", classify("what is this place called"));
        assertEquals("OBSERVE", classify("whats this place called"));
        // ...and the identify rule keeps everything else it had.
        assertEquals("EXAMINE", classify("what is this"));
        assertEquals("EXAMINE", classify("what is this plant"));
        assertEquals("EXAMINE", classify("identify this mushroom"));

        // And the acts are untouched: naming is naming, mapping is mapping, and looking round is looking round.
        assertEquals("DESIGNATE", classify("call this place Stonefield"));
        assertEquals("SKETCH_MAP", classify("mark this place on my map"));
        assertEquals("OBSERVE", classify("where am I"));
    }

    /**
     * What is waiting on you (#37, V442).
     *
     * <p>The stage records have held every unfinished piece since V58 — which stages are set in, what the next
     * one wants, the span it must cure, the weather it cannot be worked in — and construction_project has held
     * a raising at its percentage. Swept on a booted world, none of it could be asked, and three sentences got
     * a confident answer about something else:
     *
     * <pre>
     *   what am I working on / what is unfinished / what jobs are half done -&gt; nothing at all
     *   how far along is the lean-to  -&gt; TAKE_STOCK_OF_CAMP, a list of what already stands
     *   how far along is the hide     -&gt; MEASURE, "you pace it out and reckon by eye"
     *   how is my shelter holding up  -&gt; TAKE_STOCK_OF_GEAR, the clothes on your back
     * </pre>
     */
    @Test
    void askingWhatIsWaitingOnYou() throws Exception {
        assertEquals("JUDGE_WORK_IN_HAND", classify("what am I working on"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what is unfinished"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what jobs are half done"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("how is the work going"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what is left to do on the shelter"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("how far along is the lean-to"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("how much more does the fence need"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("is the shelter finished"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("is the hide ready"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what next for the hide"));

        // THE READINGS THAT ALREADY OWN THEIR SUBJECTS KEEP THEM. This rule sits below every one of these on
        // purpose, and the boundary is the whole reason it is placed where it is rather than earlier.
        assertEquals("CHECK_FIRE", classify("is the fire ready"));
        assertEquals("CHECK_CROP", classify("how is the crop"));
        assertEquals("TAKE_STOCK_OF_CAMP", classify("take stock of the camp"));
        assertEquals("INSPECT", classify("check my work"));
        assertEquals("JUDGE_WATER", classify("is this water safe to drink"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how is my shirt"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("what am I carrying"));

        // AND THE CROPS IN THE PLURAL, which was the only form of that question that failed: "how is the crop"
        // was matched as a literal string, so the singular answered and the way a person with a field says it
        // reached nothing at all.
        assertEquals("CHECK_CROP", classify("how are the crops"));
        assertEquals("CHECK_CROP", classify("how are my crops doing"));

        // THE HOURS STAY WITH THE HOURS. "how long until" is the elapsed-time reading's own question; it is
        // borrowed here only when the sentence names a thing out of the catalogue, which is what tells a hide
        // becoming leather from the sun going down.
        assertEquals("JUDGE_WORK_IN_HAND", classify("how long until the hide is leather"));
        // ...while the hours to dark stay with the sky reading, which has always answered them.
        assertEquals("READ_THE_SKY", classify("how long until dark"));
        assertEquals("READ_THE_SKY", classify("how long until nightfall"), "the sky reading holds every name for dark");
    }

    /**
     * The hours to dark, by whatever name (#37).
     *
     * <p>The sky rule held the exact words "until dark" and the length rule takes any other "how long", so
     * eight ways of asking one question were answered "you pace it out and reckon by eye" — a question about
     * the light answered by pacing out the ground. Measured live, all eight at once.
     *
     * <p>One of them was mine: {@code how long until first light} reached the work-in-hand reading, because
     * that rule borrows "how long until" when the sentence names a catalogue thing and this catalogue holds a
     * rush LIGHT. The sky rule runs before it and now holds the words, which settles it in the right order.
     */
    @Test
    void theHoursToDarkByWhateverName() throws Exception {
        for (String askedInItsOwnWords : new String[]{
                "how long until dark", "how long until nightfall", "how long until dusk",
                "how long until sundown", "how long until sunset", "how long until dawn",
                "how long until first light", "how long until daylight", "how long until it gets dark"}) {
            assertEquals("READ_THE_SKY", classify(askedInItsOwnWords), askedInItsOwnWords);
        }

        // AND THE MEASUREMENTS KEEP THEIRS: a length is still a length, and the days since waking are still
        // reckoned, which is what the length rule's own comment says it is left with.
        assertEquals("MEASURE", classify("how long is this plank"));
        assertEquals("MEASURE", classify("how long have I been here"));
        // ...and a distance to a thing in the country is the bearing reading's, which answers both at once.
        assertEquals("WHICH_WAY", classify("how far is the river"));
    }

    /**
     * The words a player reaches for first (#37/#99).
     *
     * <p>#99's release gate asks that every supported action resolve or fail with a specific observed fact and
     * that nothing fall through to the generic handler. Swept 97 ordinary sentences across fifteen families on
     * a booted world: 76 were answered and <b>15 reached nothing</b> — and for five of those the reading they
     * wanted was already there, reachable only by a different form of words:
     *
     * <pre>
     *   what tracks are here   -&gt; TRACK          |  what has been through here  -&gt; nothing
     *   how heavy is my pack   -&gt; gear reading   |  how much can I carry         -&gt; nothing
     *   scout the ridge        -&gt; SCOUT          |  what is over that ridge      -&gt; nothing
     *   forage around here     -&gt; FORAGE_GROUND  |  gather what is useful        -&gt; nothing
     *   build a shelter        -&gt; the assembly   |  start a shelter              -&gt; nothing
     * </pre>
     *
     * <p>Each of these is the same reading reached by the words people actually use. The last is a verb rather
     * than a phrase: every assembly's keywords are written with "build", "make" or "raise", so one rewrite in
     * the matcher serves all hundred builds instead of a hundred keywords in a migration.
     */
    @Test
    void theWordsAPlayerReachesForFirst() throws Exception {
        // What came through — the question form of reading the ground.
        assertEquals("TRACK", classify("what has been through here"));
        assertEquals("TRACK", classify("what came through here"));
        assertEquals("TRACK", classify("what tracks are here"), "which already worked");

        // What you can still carry, which the gear reading reports on every answer.
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how much can I carry"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("can I carry all this"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("am I overloaded"));
        assertEquals("TAKE_STOCK_OF_GEAR", classify("how heavy is my pack"), "which already worked");

        // What lies beyond the ridge, asked as a question — and the looking keeps its own.
        assertEquals("SCOUT", classify("what is over that ridge"));
        assertEquals("SCOUT", classify("what lies beyond the treeline"));
        assertEquals("SCOUT", classify("scout the ridge"), "which already worked");
        assertNotEquals("SCOUT", classify("climb the ridge"), "climbing is not scouting");

        // A forage said without naming what to pick up.
        assertEquals("FORAGE_GROUND", classify("gather what is useful"));
        assertEquals("FORAGE_GROUND", classify("see what is lying about"));
        assertEquals("FORAGE_GROUND", classify("forage around here"), "which already worked");

        // Cooking what you happen to have, which is the sentence of somebody with a pack at a fire.
        assertEquals("COOK_MEAT", classify("cook what I have"));
        assertEquals("COOK_MEAT", classify("cook something"));
        assertEquals("COOK_MEAT", classify("roast the meat"), "which already worked");

        // And what is waiting on you, asked of yourself rather than of the work.
        assertEquals("JUDGE_WORK_IN_HAND", classify("what should I do next"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what is there to do"));

        // THE ACTS THESE SIT BESIDE ARE UNTOUCHED: a drink is a drink, a fire is lit and not built, and
        // gathering a named thing still gathers that thing.
        assertEquals("DRINK", classify("drink some water"));
        assertEquals("LIGHT_FIRE", classify("start a fire"), "a fire is lit, not raised");
        assertEquals("GATHER_PLANT", classify("pick the berries"), "the plant gather owns the berry picking");
        assertEquals("GATHER_BRANCHES", classify("gather branches"));
    }

    /**
     * Asking what you have worked out for yourself (#37/#99, V444).
     *
     * <p>{@code chronicle_discovery} was written in two places out of five hundred processes, so the question
     * had almost nothing to answer from AND no words to reach it by. Measured live: {@code what have I
     * discovered}, {@code what do I know how to make} and {@code what can I make} were all misses.
     */
    @Test
    void askingWhatYouHaveWorkedOut() throws Exception {
        assertEquals("JUDGE_LEARNING", classify("what have I learned"));
        assertEquals("JUDGE_LEARNING", classify("what have I discovered"));
        assertEquals("JUDGE_LEARNING", classify("what do I know how to make"));
        assertEquals("JUDGE_LEARNING", classify("what can I make"));
        assertEquals("JUDGE_LEARNING", classify("what have I worked out"));

        // AND THE MAKING ITSELF IS UNTOUCHED, which is the boundary that matters: asking what you know how to
        // make is not asking to make something, and MAKING IS NOT DOING cuts both ways (#37).
        assertEquals("CRAFT_BASKET", classify("make a basket"));
        assertEquals("CRAFT_KNIFE", classify("make a knife"));
        assertEquals("JUDGE_WORK_IN_HAND", classify("what am I working on"));
    }

    /**
     * Asking what grows here (#37/#99, V445).
     *
     * <p>chunk_flora carries 1,440 rows and flora_definition 102 kinds with their tools, their poisons and
     * their regrowth; every gathering act reads all of it and the question reached nothing. A keeper could
     * strip a patch of plantain and never be told what was growing under their feet.
     */
    @Test
    void askingWhatGrowsHere() throws Exception {
        assertEquals("JUDGE_FLORA", classify("what grows here"));
        assertEquals("JUDGE_FLORA", classify("what plants are here"));
        assertEquals("JUDGE_FLORA", classify("what plants grow here"));
        assertEquals("JUDGE_FLORA", classify("what is growing here"));
        assertEquals("JUDGE_FLORA", classify("what can I pick here"));

        // AND THE PICKING IS STILL THE PICKING. Nothing is gathered by asking, and asking is not gathering:
        // both directions, because this rule sits among the gathering verbs.
        assertEquals("GATHER_PLANT", classify("pick the plantain"));
        assertEquals("GATHER_PLANT", classify("gather berries"), "the plant gather owns the berries");
        assertEquals("FORAGE_GROUND", classify("forage around here"));
        assertEquals("GATHER_PLANT", classify("gather plant fibre"), "and the fibre, when it is said that way");
    }
}
