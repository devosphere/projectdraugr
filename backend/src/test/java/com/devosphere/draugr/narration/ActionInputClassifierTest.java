package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;

import static com.devosphere.draugr.narration.ActionInputClassifier.InputClass;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The pre-pass filter's contract, verifiable without a database (DR-0019). It must
 * catch personal acts and aggression so they carry consequence but cost no tokens,
 * catch gibberish and the impossible so they cost nothing at all, and — most
 * importantly — leave ordinary survival actions untouched.
 */
class ActionInputClassifierTest {

    private final ActionInputClassifier classifier = new ActionInputClassifier();
    private final NarrationPolicy policy = new NarrationPolicy();

    @Test void masturbationIsAPersonalPhysicalAct() {
        assertEquals(InputClass.PERSONAL_PHYSICAL_ACT, classifier.classify("I masturbate by the fire"));
        assertEquals(InputClass.PERSONAL_PHYSICAL_ACT, classifier.classify("touch myself"));
        assertEquals(InputClass.PERSONAL_PHYSICAL_ACT, classifier.classify("jerk off"));
    }

    @Test void sexualContactWithAnAnimalIsAggressionTowardWildlife() {
        assertEquals(InputClass.AGGRESSION_TOWARD_WILDLIFE, classifier.classify("have sex with the boar"));
        assertEquals(InputClass.AGGRESSION_TOWARD_WILDLIFE, classifier.classify("mount the deer"));
    }

    @Test void profanityAtAnAnimalRoutesToWildlifeNotScenery() {
        assertEquals(InputClass.AGGRESSION_TOWARD_WILDLIFE, classifier.classify("fuck the wolf"));
    }

    @Test void profanityAtSceneryIsAggressionTowardInanimate() {
        assertEquals(InputClass.AGGRESSION_TOWARD_INANIMATE, classifier.classify("fuck the tree"));
        assertEquals(InputClass.AGGRESSION_TOWARD_INANIMATE, classifier.classify("fuck this rock"));
    }

    @Test void bareVentingIsAggressionTowardInanimate() {
        assertEquals(InputClass.AGGRESSION_TOWARD_INANIMATE, classifier.classify("fuck this"));
    }

    @Test void impossibleActsAreIntercepted() {
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("I teleport to the mountain"));
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("cast a spell on the bear"));
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("fly up over the trees"));
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("turn invisible"));
        // Flight asked plainly, and flight as the whole of what was said. Both FELL THROUGH the bare "fly "
        // entry that used to head the list — it was too narrow as well as too broad.
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("I want to fly"));
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("fly"));
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("fly north over the ridge"));
    }

    /**
     * The fly agaric is a mushroom, not an attempt at flight (#37).
     *
     * <p>A bare {@code "fly "} used to head the impossible list, and this classifier runs BEFORE the intent
     * classifier — so every sentence naming the fly agaric was answered "The body reaches toward that and finds
     * no path" and never reached a rule at all. It is a real catalogue flora: gatherable, marked
     * {@code is_poisonous}, and wired to sicken whoever eats it. Four characters and a space made it a token.
     */
    @Test void theFlyAgaricIsAMushroomAndNotAnAttemptAtFlight() {
        for (String said : new String[]{"gather the fly agaric", "eat the fly agaric", "pick the fly agaric",
                                        "look at the fly agaric", "is the fly agaric poisonous",
                                        "is the fly agaric safe to eat", "gather the fly-agaric"})
            assertEquals(InputClass.VALID, classifier.classify(said), said);
        // A WORD BOUNDARY CANNOT SAVE THIS ONE, and that is the lesson: the mushroom's name BEGINS with the
        // whole word "fly", so a bounded token matches it perfectly. The third cut of this fix made the mushroom
        // impossible all over again. Boundaries do the general work; names settle the genuine collisions.
        assertEquals(InputClass.PHYSICALLY_IMPOSSIBLE, classifier.classify("fly"), "and real flight still cannot be done");
    }

    /**
     * And neither is a dragonfly (#37).
     *
     * <p>My first fix for the mushroom replaced the bare {@code "fly "} with phrasal forms — {@code fly up},
     * {@code fly to}, {@code fly off} — and that was <b>still wrong</b>: this catalogue holds four insects whose
     * names end in those letters, and the perception layer says them out loud. So
     * {@code follow the mayfly to the water} contained "fly to". The token is bounded now.
     */
    /**
     * A floorboard is not a boar, and a steel axe is not an eel (#37).
     *
     * <p>Audited against the live catalogue, not guessed: {@code boar} sits inside <b>floorboard</b>,
     * <b>hearth board</b> and <b>plough board</b>; {@code eel} inside <b>steel axe</b>, <b>steel cuirass</b> and
     * <b>cart wheel</b>; {@code owl} inside <b>fired bowl</b>. A raw {@code contains} therefore classified
     * swearing at a plank as an assault on an animal. Low stakes — it decides which flavour of venting comes
     * back — and the same defect as the fly agaric, in the list immediately above it in the same file.
     */
    @Test void aFloorboardIsNotABoarAndASteelAxeIsNotAnEel() {
        // The claim is that these are NOT animals. What they are instead is the intent classifier's business —
        // none of these objects is in the inanimate-noun list either, so they fall through as VALID and the
        // ordinary rules decide. My first assertion here guessed INANIMATE and was wrong about the code rather
        // than finding anything wrong in it.
        for (String said : new String[]{"fuck the floorboard", "fuck the steel axe", "fuck the fired bowl",
                                        "fuck the plough board", "fuck the cart wheel"})
            org.junit.jupiter.api.Assertions.assertNotEquals(
                InputClass.AGGRESSION_TOWARD_WILDLIFE, classifier.classify(said),
                said + " names no animal, and must not be read as an assault on one");
        // And the animals the list exists for are still found, because in every one of them the animal is a
        // whole word — which is why bounding costs nothing here.
        for (String said : new String[]{"fuck the boar", "fuck the deer", "fuck the owl"})
            assertEquals(InputClass.AGGRESSION_TOWARD_WILDLIFE, classifier.classify(said), said);
    }

    @Test void fourInsectsEndInThoseThreeLettersAndNoneOfThemIsFlight() {
        for (String said : new String[]{"follow the mayfly to the water", "watch the dragonfly off the reed",
                                        "look at the damselfly up on the stem", "catch the caddisfly",
                                        "the mayfly away over the pool", "study the dragonfly north of here"})
            assertEquals(InputClass.VALID, classifier.classify(said), said);
    }

    @Test void gibberishIsNonsensical() {
        assertEquals(InputClass.NONSENSICAL, classifier.classify("asdfghjkl zxcvbnm"));
        assertEquals(InputClass.NONSENSICAL, classifier.classify("!!!"));
        assertEquals(InputClass.NONSENSICAL, classifier.classify("12345"));
        assertEquals(InputClass.NONSENSICAL, classifier.classify("@#$%^&"));
        assertEquals(InputClass.NONSENSICAL, classifier.classify("x"));
        assertEquals(InputClass.NONSENSICAL, classifier.classify(""));
        assertEquals(InputClass.NONSENSICAL, classifier.classify(null));
    }

    @Test void ordinarySurvivalActionsAreValid() {
        assertEquals(InputClass.VALID, classifier.classify("light a fire with the bow drill"));
        assertEquals(InputClass.VALID, classifier.classify("gather clay from the riverbank"));
        assertEquals(InputClass.VALID, classifier.classify("fell the oak tree with my stone axe"));
        assertEquals(InputClass.VALID, classifier.classify("eat the berries I gathered"));
        assertEquals(InputClass.VALID, classifier.classify("build a lean-to against the ridge"));
        assertEquals(InputClass.VALID, classifier.classify("confront the wild boar with my spear"));
    }

    @Test void constructionVerbsAreNotMistakenForProfanity() {
        // "screw" and "damn" were deliberately excluded from the profanity set.
        assertEquals(InputClass.VALID, classifier.classify("screw the shelf to the wall"));
        assertEquals(InputClass.VALID, classifier.classify("this is a damn good spot for a camp"));
    }

    @Test void interceptedNarrationIsWitnessStanceAndNeverAdvises() {
        for (InputClass cls : new InputClass[]{
                InputClass.PERSONAL_PHYSICAL_ACT, InputClass.AGGRESSION_TOWARD_WILDLIFE,
                InputClass.AGGRESSION_TOWARD_INANIMATE, InputClass.NONSENSICAL,
                InputClass.PHYSICALLY_IMPOSSIBLE}) {
            String line = classifier.narrate(cls, "some input");
            assertFalse(line.isBlank(), "narration must be non-empty for " + cls);
            // Must pass the same witness-stance policy every narration is held to.
            assertDoesNotThrow(() -> policy.validate(line), "narration must be witness-stance for " + cls);
        }
    }

    @Test void narrationIsDeterministicForTheSameInput() {
        String a = classifier.narrate(InputClass.PERSONAL_PHYSICAL_ACT, "identical text");
        String b = classifier.narrate(InputClass.PERSONAL_PHYSICAL_ACT, "identical text");
        assertEquals(a, b);
    }
}
