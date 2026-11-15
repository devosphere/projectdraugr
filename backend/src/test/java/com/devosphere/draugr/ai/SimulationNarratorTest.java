package com.devosphere.draugr.ai;

import com.devosphere.draugr.action.ChronicleActionService.PerceptionFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Simulation Narrator seam — role 3 of the generative pipeline (Interpreter→Architect→Narrator, #244).
 * It appends exactly one AI sentence of atmosphere to the deterministic prose when the feature is on, and
 * returns the deterministic prose unchanged on anything less than a clean success. Proven here as a fast
 * unit test (no Spring, no DB) against a stub {@link LanguageModel}, so the narration half of #244 is a
 * CI-guarded contract without a real key. Together with the compose/author integration tests, all three
 * AI roles are now proven to fire.
 */
class SimulationNarratorTest {

    private static AiProperties props(boolean enabled, String key) {
        AiProperties p = new AiProperties();
        p.setEnabled(enabled);
        p.setApiKey(key);
        return p; // per-agent flags (incl. narration) default on, so master + key ⇒ narration active
    }

    private static PerceptionFrame frame(String backend) {
        return new PerceptionFrame("OBSERVE", "SUCCEEDED", null, "morning", null, "HIGH", null, null, List.of(), backend, null);
    }

    @Test
    void appendsExactlyOneAiSentenceToTheDeterministicProseWhenActive() {
        LanguageModel stub = (model, system, user) -> Optional.of("A cold hush settles over the wet ground.");
        SimulationNarrator narrator = new SimulationNarrator(stub, props(true, "test-key"), java.util.Set::of);

        String out = narrator.refine(frame("You look around."), "You look around.");

        assertEquals("You look around. A cold hush settles over the wet ground.", out,
            "with the feature on, the narrator appends one AI sentence to the deterministic prose");
    }

    @Test
    void returnsTheDeterministicProseUnchangedWhenTheMasterIsOff() {
        LanguageModel stub = (model, system, user) -> Optional.of("this sentence must never appear");
        SimulationNarrator narrator = new SimulationNarrator(stub, props(false, "test-key"), java.util.Set::of); // master OFF

        assertEquals("You look around.", narrator.refine(frame("You look around."), "You look around."),
            "with the master off the AI layer is inert — deterministic prose only");
    }

    @Test
    void returnsTheDeterministicProseWhenTheModelYieldsNothing() {
        LanguageModel stub = (model, system, user) -> Optional.empty(); // outage / refusal / blank
        SimulationNarrator narrator = new SimulationNarrator(stub, props(true, "test-key"), java.util.Set::of);

        assertEquals("You look around.", narrator.refine(frame("You look around."), "You look around."),
            "a model that produces nothing degrades voice, never correctness");
    }

    @Test
    void sendsTheWitnessConstraintsAndTheExistingNarrationToTheModel() {
        AtomicReference<String> sysSeen = new AtomicReference<>();
        AtomicReference<String> userSeen = new AtomicReference<>();
        LanguageModel capturing = (model, system, user) -> { sysSeen.set(system); userSeen.set(user); return Optional.of("."); };
        SimulationNarrator narrator = new SimulationNarrator(capturing, props(true, "test-key"), java.util.Set::of);

        narrator.refine(frame("You crouch by the stream."), "You crouch by the stream.");

        assertNotNull(sysSeen.get(), "the narrator must call the model");
        assertTrue(sysSeen.get().contains("witness narrator"), "the system prompt must carry the witness-stance role");
        assertTrue(sysSeen.get().contains("EXACTLY ONE sentence"), "the system prompt must hold the one-sentence constraint");
        assertTrue(userSeen.get().contains("You crouch by the stream."), "the user turn must include the deterministic narration as context");
    }

    /**
     * #37: the narrator's brief says not to invent objects, and this is what holds it to that. A sentence that puts
     * something of the world into the scene — a knife, a wolf — is a claim about world state, and the receipt for
     * this moment is the only thing that can support it.
     */
    @Test
    void aSentenceThatNamesSomethingTheReceiptDoesNotContainIsNotAppended() {
        java.util.Set<String> world = java.util.Set.of("bone knife", "dried fish", "grey wolf");
        LanguageModel invents = (model, system, user) -> java.util.Optional.of("A grey wolf watches from the treeline and does not move.");
        SimulationNarrator narrator = new SimulationNarrator(invents, props(true, "test-key"), () -> world);

        assertEquals("You look around.", narrator.refine(frame("You look around."), "You look around."),
            "a wolf the world never reported cannot be put on the page by the narrator");

        LanguageModel stays = (model, system, user) -> java.util.Optional.of("The wind moves over the water and keeps moving.");
        SimulationNarrator kept = new SimulationNarrator(stays, props(true, "test-key"), () -> world);
        assertEquals("You look around. The wind moves over the water and keeps moving.",
            kept.refine(frame("You look around."), "You look around."), "atmosphere that claims nothing is kept");
    }

    /** And what the moment DOES contain may be named again: the guard is about claims, not about vocabulary. */
    @Test
    void aSentenceThatNamesWhatIsAlreadyThereIsKept() {
        java.util.Set<String> world = java.util.Set.of("bone knife");
        LanguageModel stub = (model, system, user) -> java.util.Optional.of("The bone knife is slick in your hand.");
        SimulationNarrator narrator = new SimulationNarrator(stub, props(true, "test-key"), () -> world);

        assertTrue(narrator.refine(frame("You cut the cord with the bone knife."), "You cut the cord with the bone knife.")
            .endsWith("The bone knife is slick in your hand."));
    }

    /** The same frame, with a hearth on the ground in whatever state is given. */
    private static PerceptionFrame frameWithHearth(String backend, String hearth) {
        return new PerceptionFrame("SLEEP", "SUCCEEDED", null, "before dawn", null, "LOW", null, null,
            List.of(), backend, hearth);
    }

    /**
     * #903 — A THING'S STATE IS A CLAIM ABOUT THE WORLD, AS MUCH AS ITS EXISTENCE IS.
     *
     * <p>Live: a Chronicle in snow before dawn built a ring of four field stones and had never once struck a
     * light in that life. The narrator wrote that ash and snow were melting between the stones and warmth was
     * rising from gathered wood. {@code claimsWhatIsNotThere} passed it, correctly on its own terms — the fire
     * pit really is on that ground; it is the FIRE that is not. The distinction is what a hypothermic
     * Chronicle reads the prose for when deciding whether it is safe to lie down.
     */
    @Test
    void aColdHearthIsNeverGivenAFire() {
        String cold = "stone fire pit, UNLIT AND COLD — no flame, no embers, no smoke, no warmth from it";
        LanguageModel invents = (model, system, user) ->
            Optional.of("Ash and snow melt between the stones, and warmth rises from the burning wood.");
        SimulationNarrator narrator = new SimulationNarrator(invents, props(true, "test-key"), java.util.Set::of);

        String deterministic = "You settle stone into a low, deliberate ring. The fire pit remains where you made it.";
        assertEquals(deterministic, narrator.refine(frameWithHearth(deterministic, cold), deterministic),
            "a sentence that puts a fire in a cold hearth is refused, and the deterministic prose stands alone");

        // ATMOSPHERE THAT CLAIMS NO FIRE IS STILL WELCOME — the guard is about the fire, not about the weather.
        LanguageModel atmosphere = (model, system, user) ->
            Optional.of("Snow sifts into the ring and does not melt.");
        SimulationNarrator kind = new SimulationNarrator(atmosphere, props(true, "test-key"), java.util.Set::of);
        assertTrue(kind.refine(frameWithHearth(deterministic, cold), deterministic)
            .endsWith("Snow sifts into the ring and does not melt."));

        // AND A LIT HEARTH MAY BE DESCRIBED AS ONE. A guard that silenced the narrator at a real fire would
        // have moved the defect rather than fixed it.
        SimulationNarrator atTheFire = new SimulationNarrator(invents, props(true, "test-key"), java.util.Set::of);
        String lit = "stone fire pit, alight with about 2 hour(s) of fuel in it";
        assertTrue(atTheFire.refine(frameWithHearth(deterministic, lit), deterministic)
            .endsWith("warmth rises from the burning wood."));

        // ...as may a cold hearth whose own deterministic line is about fire: "the fire has gone out" may be
        // elaborated on, because the receipt itself raised the subject.
        String wentOut = "The last of the embers blackens and the fire goes out.";
        LanguageModel echoes = (model, system, user) -> Optional.of("The embers tick as they cool.");
        SimulationNarrator afterwards = new SimulationNarrator(echoes, props(true, "test-key"), java.util.Set::of);
        assertTrue(afterwards.refine(frameWithHearth(wentOut, cold), wentOut).endsWith("The embers tick as they cool."));
    }

    /** The hearth's condition is given to the narrator as a fact, not left to be inferred from a count (#903). */
    @Test
    void theNarratorIsToldWhatTheHearthIsDoing() {
        AtomicReference<String> asked = new AtomicReference<>();
        LanguageModel capture = (model, system, user) -> { asked.set(user); return Optional.empty(); };
        SimulationNarrator narrator = new SimulationNarrator(capture, props(true, "test-key"), java.util.Set::of);

        narrator.refine(frameWithHearth("You lie down.", "stone fire pit, UNLIT AND COLD — no flame, no embers, no smoke, no warmth from it"), "You lie down.");
        assertNotNull(asked.get());
        assertTrue(asked.get().contains("Hearth on this ground: stone fire pit, UNLIT AND COLD"), asked.get());

        narrator.refine(frame("You lie down."), "You lie down.");
        assertTrue(asked.get().contains("Hearth on this ground: none built here"), asked.get());
    }

    private static PerceptionFrame frameFor(String intent, String backend) {
        return new PerceptionFrame(intent, "SUCCEEDED", null, "morning", null, "HIGH", null, null, List.of(), backend, null);
    }

    /**
     * #922: crafting a crayfish trap came back with "...as you submerge the trap". Nothing was submerged — the
     * trap was in the pack. A sentence that puts a just-made thing to use claims an act that has its own action
     * and its own record, and was never taken.
     */
    @Test
    void aThingJustMadeIsNotNarratedIntoUse() {
        String made = "You weave a small withy funnel trap. The Crayfish trap is in your pack.";
        LanguageModel invents = (model, system, user) -> Optional.of(
            "The morning sun glints off the water as you submerge the trap, your shoulders heavy with the effort of the work.");
        SimulationNarrator narrator = new SimulationNarrator(invents, props(true, "test-key"), java.util.Set::of);

        assertEquals(made, narrator.refine(frameFor("PROCESS_MATERIAL", made), made),
            "a sentence that uses what was only made is refused, and the deterministic prose stands alone");
        assertEquals(made, narrator.refine(frameFor("CRAFT_NET", made), made), "for every making intent");

        // Atmosphere about the making is still welcome.
        LanguageModel atmosphere = (model, system, user) -> Optional.of("The cut withies smell green and sharp.");
        assertTrue(new SimulationNarrator(atmosphere, props(true, "test-key"), java.util.Set::of)
            .refine(frameFor("PROCESS_MATERIAL", made), made).endsWith("The cut withies smell green and sharp."));

        // And SETTING the trap may be narrated as setting it — that act happened.
        String set = "You lower the crayfish trap into the water and weight it where the bottom is still.";
        assertTrue(narrator.refine(frameFor("SET_TRAP", set), set).endsWith("your shoulders heavy with the effort of the work."),
            "the guard is about making, not about the act of setting a trap");
    }

    @Test
    void theNarratorIsToldThatAMadeThingIsOnlyMade() {
        AtomicReference<String> sys = new AtomicReference<>();
        LanguageModel capture = (model, system, user) -> { sys.set(system); return Optional.empty(); };
        new SimulationNarrator(capture, props(true, "test-key"), java.util.Set::of).refine(frame("You look around."), "You look around.");
        assertTrue(sys.get().contains("MADE something"), sys.get());
    }
}
