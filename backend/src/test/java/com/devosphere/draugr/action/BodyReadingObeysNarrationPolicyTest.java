package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService.Ailments;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService.BodyHudSnapshot;
import com.devosphere.draugr.narration.NarrationPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body reading (#37) says what a body TELLS you, never what the Body HUD would label it.
 *
 * <p>{@link NarrationPolicy} rejects narration that turns the narrator into a hint system: "you are thirsty",
 * "your bladder", "your energy" and the rest are the HUD's words, and the HUD is where they belong. The reading
 * has to carry the same facts as sensation instead — a dry mouth, a pressure low down, limbs slow to answer.
 *
 * <p>The first cut of the reading used the HUD's vocabulary and every body question 400'd against a running
 * stack. That cost a rebuild to find. This runs every line the reading can emit through the policy, without a
 * database or a Spring context, so the next one is caught before it is pushed.
 */
class BodyReadingObeysNarrationPolicyTest {

    /** Every label {@code ChroniclePhysiologyService} can put in each field of the snapshot. */
    private static final List<String> HEALTH = List.of("Healthy", "Injured", "Sick", "Ailing", "Critical");

    /** Every shape the three scalars behind the health label can take, including the ones that produce no line. */
    private static final List<Ailments> AILMENTS = List.of(
        new Ailments(0, 0, 0, 0), new Ailments(12, 0, 0, 0), new Ailments(50, 0, 0, 0), new Ailments(95, 0, 0, 0),
        new Ailments(0, 20, 0, 0), new Ailments(0, 64, 0, 0), new Ailments(0, 99, 0, 0),
        new Ailments(0, 0, 500, 0), new Ailments(0, 0, 2900, 0), new Ailments(60, 99, 2900, 0), new Ailments(30, 40, 100, 0),
        // ...and every direction the sickness can be going, which the reading now says out loud (#909).
        new Ailments(0, 64, 0, 3), new Ailments(0, 64, 0, -4), new Ailments(0, 64, 0, 0));
    private static final List<String> CONDITION = List.of("Normal", "In pain", "Distressed", "Sleep deprived");
    private static final List<String> HUNGER = List.of("Satisfied", "Hungry", "Very Hungry", "Starving", "Critical Starvation");
    private static final List<String> THIRST = List.of("Hydrated", "Thirsty", "Dehydrated", "Critical Dehydration");
    private static final List<String> ENERGY = List.of("Energetic", "Rested", "Tired", "Fatigued", "Exhausted", "Collapsing");
    private static final List<String> TEMPERATURE = List.of("Hypothermic", "Cold", "Comfortable", "Warm", "Hot", "Hyperthermic");
    private static final List<String> WETNESS = List.of("Dry", "Damp", "Wet", "Soaked");
    private static final List<String> BLADDER = List.of("Empty", "Comfortable", "Need to Urinate", "Urgent", "Critical");
    private static final List<String> BOWEL = List.of("Empty", "Normal", "Need Relief", "Urgent", "Critical");
    private static final List<String> HYGIENE = List.of("Clean", "Normal", "Dirty", "Filthy", "Hazardous");

    private static String read(BodyHudSnapshot snapshot) throws Exception { return read(snapshot, new Ailments(0, 0, 0, 0)); }

    private static String read(BodyHudSnapshot snapshot, Ailments ails) throws Exception {
        Method m = ChronicleActionService.class.getDeclaredMethod("bodyReading", BodyHudSnapshot.class, Ailments.class);
        m.setAccessible(true);
        // bodyReading is pure over the snapshot — it touches no collaborator, so none is needed here.
        ChronicleActionService svc = new ChronicleActionService(null, null, null, null, null, null, null, null, null, null, null, null, null,
            new com.devosphere.draugr.narration.ActionInputClassifier(), null, null, null,
            new com.devosphere.draugr.narration.NarrationEngine(), (com.devosphere.draugr.ai.RuntimeAuthoringService) null,
            (ExaminationService) null, (com.devosphere.draugr.people.ContactService) null,
            (com.devosphere.draugr.people.TradeService) null, (com.devosphere.draugr.people.ConductService) null,
            (com.devosphere.draugr.people.AgreementService) null, (com.devosphere.draugr.people.CompanionService) null,
            (com.devosphere.draugr.people.AudienceService) null, (com.devosphere.draugr.people.MembershipService) null,
            (com.devosphere.draugr.people.ClaimService) null);
        return (String) m.invoke(svc, snapshot, ails);
    }

    private static BodyHudSnapshot contented() {
        return new BodyHudSnapshot("Healthy", "Normal", "Satisfied", "Hydrated", "Energetic", "Comfortable", "Dry", "Comfortable", "Normal", "Clean");
    }

    @Test
    void everyLineTheReadingCanEmitPassesTheNarrationPolicy() throws Exception {
        NarrationPolicy policy = new NarrationPolicy();
        // One field away from contented at a time, so every branch of the reading is reached and checked on its
        // own — a failure names the exact label whose wording strayed back into the HUD's vocabulary.
        record Field(String name, List<String> values, java.util.function.Function<String, BodyHudSnapshot> with) { }
        BodyHudSnapshot c = contented();
        List<Field> fields = List.of(
            new Field("health", HEALTH, v -> new BodyHudSnapshot(v, c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("condition", CONDITION, v -> new BodyHudSnapshot(c.health(), v, c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("hunger", HUNGER, v -> new BodyHudSnapshot(c.health(), c.condition(), v, c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("thirst", THIRST, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), v, c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("energy", ENERGY, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), v, c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("temperature", TEMPERATURE, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), v, c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Field("wetness", WETNESS, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), v, c.bladder(), c.bowel(), c.hygiene())),
            new Field("bladder", BLADDER, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), v, c.bowel(), c.hygiene())),
            new Field("bowel", BOWEL, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), v, c.hygiene())),
            new Field("hygiene", HYGIENE, v -> new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), v)));

        for (Field f : fields)
            for (String v : f.values()) {
                String said = read(f.with().apply(v));
                assertDoesNotThrow(() -> policy.validate(said),
                    () -> "the reading for " + f.name() + "=" + v + " uses the HUD's words: " + said);
            }

        // EVERY SHAPE OF WHAT IS ACTUALLY WRONG (#886, #914, #921). The reading no longer opens on the health
        // LABEL — it reads injury, illness and blood loss, which is why an illness-only Chronicle stopped being
        // told about an unclosed hurt. Those lines are narration like any other and must pass the policy too.
        for (Ailments ails : AILMENTS) {
            String said = read(contented(), ails);
            assertDoesNotThrow(() -> new NarrationPolicy().validate(said),
                () -> "the reading for " + ails + " uses the HUD's words: " + said);
        }

        // And the worst of everything at once, which is the longest sentence the reading will ever build.
        String allAtOnce = read(new BodyHudSnapshot("Critical", "In pain", "Critical Starvation", "Critical Dehydration",
            "Collapsing", "Hyperthermic", "Soaked", "Critical", "Critical", "Hazardous"), new Ailments(95, 99, 2900, 0));
        assertDoesNotThrow(() -> policy.validate(allAtOnce), () -> "the worst case uses the HUD's words: " + allAtOnce);
    }

    /**
     * A body with nothing wrong must still be ANSWERED. A reading that only ever speaks when something is amiss
     * would leave "how am I doing" silent exactly when the answer is good news, which is a different bug.
     */
    @Test
    void aBodyWithNothingWrongIsStillAnswered() throws Exception {
        String said = read(contented());
        assertTrue(said.contains("Nothing presses"), () -> "a contented body must still get an answer: " + said);
        assertDoesNotThrow(() -> new NarrationPolicy().validate(said));
    }

    /** Every distress the HUD can show must produce a line, or the reading would quietly omit it. */
    @Test
    void everyDistressTheHudCanShowIsSaidInSomeForm() throws Exception {
        BodyHudSnapshot c = contented();
        record Case(String field, String value, BodyHudSnapshot snapshot) { }
        List<Case> distress = List.of(
            new Case("thirst", "Thirsty", new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), "Thirsty", c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Case("hunger", "Hungry", new BodyHudSnapshot(c.health(), c.condition(), "Hungry", c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Case("energy", "Exhausted", new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), "Exhausted", c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Case("temperature", "Hypothermic", new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), "Hypothermic", c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
            new Case("wetness", "Soaked", new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), "Soaked", c.bladder(), c.bowel(), c.hygiene())),
            new Case("hygiene", "Filthy", new BodyHudSnapshot(c.health(), c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), "Filthy")));
        for (Case d : distress) {
            String said = read(d.snapshot());
            assertTrue(!said.contains("Nothing presses"),
                () -> d.field() + "=" + d.value() + " must not read as a body with nothing wrong: " + said);
        }
    }

    /**
     * A SICKNESS IS NOT A WOUND (#886, #914).
     *
     * <p>Chronicle 41: illness 64, injury 0, nothing bleeding. "Am I sick?" answered "There is a hurt in you
     * that has not closed", and "bandage my wound" answered, correctly, that nothing was open — the reading
     * had asked the one-word HEALTH LABEL, which cannot tell the two apart, while the bandaging read the
     * scalars. Both halves are asserted here, because fixing one and not the other only moves the defect.
     */
    @Test
    void anIllnessIsReadAsSicknessAndAWoundAsAWound() throws Exception {
        String sick = read(contented(), new Ailments(0, 64, 0, 0));
        assertTrue(sick.contains("sickness"), () -> "illness must be read as sickness: " + sick);
        assertTrue(!sick.contains("hurt") && !sick.contains("closed"),
            () -> "a body with no injury must never be told of an unclosed hurt: " + sick);

        String hurt = read(contented(), new Ailments(50, 0, 0, 0));
        assertTrue(hurt.contains("hurt"), () -> "an injury must be read as a hurt: " + hurt);
        assertTrue(!hurt.contains("sickness"), () -> "a body with no illness must not be told it is sick: " + hurt);

        // BOTH AT ONCE SAYS BOTH. Chronicle 49 — illness 93, injury 30 from ten unplaceable strains — was
        // told only "something in you is badly wrong", which names nothing a player can act on.
        String both = read(new BodyHudSnapshot("Critical", "Normal", "Satisfied", "Hydrated", "Energetic",
            "Comfortable", "Dry", "Comfortable", "Normal", "Clean"), new Ailments(30, 93, 0, 0));
        assertTrue(both.contains("sickness"), () -> "a critical illness must be named: " + both);
        assertTrue(both.contains("ache") || both.contains("hurt"), () -> "the strain must be named too: " + both);
        assertTrue(!both.contains("badly wrong"),
            () -> "the generic line must give way whenever the scalars can explain the state: " + both);

        // ...and it is still there for a Critical the scalars cannot explain — blood loss short of the
        // injury thresholds, say — so the fix never silences the reading.
        String unexplained = read(new BodyHudSnapshot("Critical", "Normal", "Satisfied", "Hydrated", "Energetic",
            "Comfortable", "Dry", "Comfortable", "Normal", "Clean"), new Ailments(0, 0, 0, 0));
        assertTrue(unexplained.contains("badly wrong"), () -> "an unexplained Critical must still be said: " + unexplained);
    }

    /**
     * "HOW SICK AM I" AND "AM I GETTING BETTER" ARE TWO QUESTIONS (#909).
     *
     * <p>A player who has just washed, dried off, got warm and dressed the wound needs the second one
     * answered, or they cannot tell whether any of it worked — and before illness could fall at all there
     * was no second answer to give.
     */
    @Test
    void theReadingSaysWhichWayTheSicknessIsGoing() throws Exception {
        String worse = read(contented(), new Ailments(0, 64, 0, 3));
        assertTrue(worse.contains("getting worse"), () -> "a rising sickness must say so: " + worse);

        String easing = read(contented(), new Ailments(0, 64, 0, -4));
        assertTrue(easing.contains("easing"), () -> "a mending sickness must say so: " + easing);

        String holding = read(contented(), new Ailments(0, 64, 0, 0));
        assertTrue(holding.contains("holding"), () -> "a sickness going nowhere must say so: " + holding);

        // And a body with no sickness is told nothing about a direction it has not got.
        String well = read(contented(), new Ailments(0, 0, 0, -4));
        assertTrue(!well.contains("easing") && !well.contains("holding"), () -> "no sickness, no trend: " + well);
    }

    /**
     * #957 — "AND AND". The clause join puts ", and " before the last part, and some parts carry their own
     * leading "and" because they read as a continuation of the one above. Live, in a self-check on a sick
     * and bleeding body:
     *
     * <pre>
     *   "...a sickness is working in you: your skin is hot and your strength is going out of you,
     *    and and it is getting worse, not better"
     * </pre>
     *
     * <p>Two sentences of the player's own body, in the most-read text in the game. Every combination that
     * can put the trend clause second-to-last is swept, because one hand-picked case would not have caught
     * it either.
     */
    @Test
    void theReadingNeverSaysAndAnd() throws Exception {
        BodyHudSnapshot c = contented();
        for (int illness : new int[]{0, 15, 40, 60, 90})
            for (int injury : new int[]{0, 20, 50, 90})
                for (int blood : new int[]{0, 500, 1800, 3000})
                    for (int trend : new int[]{-1, 0, 1}) {
                        String read = read(c, new Ailments(injury, illness, blood, trend));
                        assertFalse(read.contains("and and"), () -> "doubled conjunction: " + read);
                        assertFalse(read.contains(", and , and"), () -> "doubled join: " + read);
                        assertFalse(read.contains("  "), () -> "doubled space: " + read);
                    }
    }
}
