package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService.BodyHudSnapshot;
import com.devosphere.draugr.narration.NarrationPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

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
    private static final List<String> HEALTH = List.of("Healthy", "Injured", "Critical");
    private static final List<String> CONDITION = List.of("Normal", "In pain", "Distressed", "Sleep deprived");
    private static final List<String> HUNGER = List.of("Satisfied", "Hungry", "Very Hungry", "Starving", "Critical Starvation");
    private static final List<String> THIRST = List.of("Hydrated", "Thirsty", "Dehydrated", "Critical Dehydration");
    private static final List<String> ENERGY = List.of("Energetic", "Rested", "Tired", "Fatigued", "Exhausted", "Collapsing");
    private static final List<String> TEMPERATURE = List.of("Hypothermic", "Cold", "Comfortable", "Warm", "Hot", "Hyperthermic");
    private static final List<String> WETNESS = List.of("Dry", "Damp", "Wet", "Soaked");
    private static final List<String> BLADDER = List.of("Empty", "Comfortable", "Need to Urinate", "Urgent", "Critical");
    private static final List<String> BOWEL = List.of("Empty", "Normal", "Need Relief", "Urgent", "Critical");
    private static final List<String> HYGIENE = List.of("Clean", "Normal", "Dirty", "Filthy", "Hazardous");

    private static String read(BodyHudSnapshot snapshot) throws Exception {
        Method m = ChronicleActionService.class.getDeclaredMethod("bodyReading", BodyHudSnapshot.class);
        m.setAccessible(true);
        // bodyReading is pure over the snapshot — it touches no collaborator, so none is needed here.
        ChronicleActionService svc = new ChronicleActionService(null, null, null, null, null, null, null, null, null, null, null, null,
            new com.devosphere.draugr.narration.ActionInputClassifier(), null, null, null,
            new com.devosphere.draugr.narration.NarrationEngine(), (com.devosphere.draugr.ai.RuntimeAuthoringService) null,
            (ExaminationService) null, (com.devosphere.draugr.people.ContactService) null,
            (com.devosphere.draugr.people.TradeService) null, (com.devosphere.draugr.people.ConductService) null,
            (com.devosphere.draugr.people.AgreementService) null, (com.devosphere.draugr.people.CompanionService) null,
            (com.devosphere.draugr.people.AudienceService) null, (com.devosphere.draugr.people.MembershipService) null,
            (com.devosphere.draugr.people.ClaimService) null);
        return (String) m.invoke(svc, snapshot);
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

        // And the worst of everything at once, which is the longest sentence the reading will ever build.
        String allAtOnce = read(new BodyHudSnapshot("Critical", "In pain", "Critical Starvation", "Critical Dehydration",
            "Collapsing", "Hyperthermic", "Soaked", "Critical", "Critical", "Hazardous"));
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
            new Case("health", "Injured", new BodyHudSnapshot("Injured", c.condition(), c.hunger(), c.thirst(), c.energy(), c.temperature(), c.wetness(), c.bladder(), c.bowel(), c.hygiene())),
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
}
