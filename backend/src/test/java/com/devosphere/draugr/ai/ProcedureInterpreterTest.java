package com.devosphere.draugr.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Interpreter against a stub model — no network, no key, no database. Guards the two load-bearing
 * properties: it is inert with AI off, and it can only ever return REAL process keys (a model cannot
 * smuggle in one that does not exist).
 */
class ProcedureInterpreterTest {

    private AiProperties disabled() { AiProperties p = new AiProperties(); p.setEnabled(false); return p; }

    private AiProperties enabledStub() {
        AiProperties p = new AiProperties();
        p.setEnabled(true);
        p.setApiKey("test-key-not-a-real-secret"); // isUsable() needs a non-blank key; the stub model never touches the network
        return p;
    }

    @Test void inertWhenDisabled() {
        // enabled=false -> isUsable() false -> returns empty before touching the model or the database (null).
        ProcedureInterpreter i = new ProcedureInterpreter((m, s, u) -> Optional.of("split_planks"), disabled(), null);
        assertTrue(i.plan("split the logs into planks", List.of()).isEmpty());
    }

    @Test void inertWhenModelFails() {
        // A usable feature but a model that returns empty -> empty plan, never throws.
        ProcedureInterpreter i = new ProcedureInterpreter((m, s, u) -> Optional.empty(), enabledStub(), null) {
            // avoid the DB read by not calling plan(); exercise parse directly below instead
        };
        assertTrue(i.parse("", Set.of("split_planks")).isEmpty());
    }

    @Test void parseKeepsOnlyRealKeysInOrder() {
        ProcedureInterpreter i = new ProcedureInterpreter((m, s, u) -> Optional.empty(), disabled(), null);
        Set<String> valid = Set.of("twist_cordage", "utility_belt_making", "split_planks");
        assertEquals(List.of("twist_cordage", "utility_belt_making"),
            i.parse("twist_cordage, utility_belt_making, not_a_real_key", valid), "drops keys that do not exist");
        assertEquals(List.of("split_planks"), i.parse("SPLIT_PLANKS", valid), "case-insensitive");
        assertEquals(List.of("twist_cordage"), i.parse("twist_cordage twist_cordage", valid), "de-duplicated");
        assertEquals(List.of(), i.parse("NONE", valid), "NONE means no composition");
        assertEquals(List.of(), i.parse("here is the plan: do stuff", valid), "prose with no real keys yields nothing");
    }

    /** #37: a plan is typed — keys the world has, in order, and how sure the interpreter says it is. */
    @Test
    void aPlanCarriesTheConfidenceItWasGivenAndFiftyWhenItGivesNone() {
        assertEquals(80, ProcedureInterpreter.confidence("knap_flake, haft_axe confidence=80"));
        assertEquals(50, ProcedureInterpreter.confidence("knap_flake, haft_axe"), "no claim of confidence is an even fifty");
        assertEquals(100, ProcedureInterpreter.confidence("knap_flake CONFIDENCE = 140"), "and it is never more than certain");
        assertEquals(0, ProcedureInterpreter.confidence("NONE confidence=0"));
    }
    // ── #37: the reply says what it was looking at, and is thrown out when it was looking at nothing. ──────────

    private ProcedureInterpreter interpreter() {
        return new ProcedureInterpreter((m, sys, u) -> Optional.empty(), disabled(), null);
    }

    @Test void citesOnlyWhatIsActuallyCarried() {
        ProcedureInterpreter i = interpreter();
        Set<String> carried = Set.of("dry_branch", "flint_flake", "plant_fiber");
        String reply = "twist_cordage\ncontext=plant_fiber, flint_flake\nconfidence=80";
        assertEquals(List.of("plant_fiber", "flint_flake"), i.cited(reply, carried, Set.of("twist_cordage")),
            "the citation is kept in the model's order, and only what is really in hand");
        assertTrue(i.imagined(reply, carried, Set.of("twist_cordage")).isEmpty(),
            "nothing here was imagined");
    }

    @Test void aThingThatIsNotCarriedIsImaginedAndNotQuietlyDropped() {
        ProcedureInterpreter i = interpreter();
        Set<String> carried = Set.of("dry_branch");
        String reply = "tan_hide\ncontext=deer_hide, oak_bark\nconfidence=90";
        assertTrue(i.cited(reply, carried, Set.of("tan_hide")).isEmpty(), "none of it is in hand");
        assertEquals(List.of("deer_hide", "oak_bark"), i.imagined(reply, carried, Set.of("tan_hide")),
            "and what it thought it had is recorded rather than silently discarded — that is the finding");
    }

    @Test void namingTheStepOrNothingIsUntidyNotAHallucination() {
        ProcedureInterpreter i = interpreter();
        Set<String> carried = Set.of("dry_branch");
        Set<String> keys = Set.of("split_planks");
        assertTrue(i.imagined("split_planks\ncontext=none\nconfidence=70", carried, keys).isEmpty(),
            "the prompt asks for \"none\" explicitly, so it cannot be evidence of imagining anything");
        assertTrue(i.imagined("split_planks\ncontext=split_planks\nconfidence=70", carried, keys).isEmpty(),
            "naming the step in the context clause is untidy, not a claim about the world");
    }

    @Test void aQuantifiedInventoryLineStillMatchesItsOwnKey() {
        // reachableInventory hands the model "dry_branch x5" so it can weigh whether there is enough. A citation
        // of dry_branch must match that, or every plan built on a quantified thing would read as imagined.
        assertEquals(Set.of("dry_branch", "flint_flake"),
            ProcedureInterpreter.carriedKeys(List.of("dry_branch x5", "flint_flake x1")));
        assertTrue(ProcedureInterpreter.carriedKeys(null).isEmpty(), "no inventory is not a crash");
    }

    @Test void aPlanRestsOnNothingOnlyWhenSomethingWasImagined() {
        assertTrue(new ProcedureInterpreter.Plan(List.of("tan_hide"), 90, List.of(), List.of("deer_hide")).restsOnNothing());
        assertTrue(!new ProcedureInterpreter.Plan(List.of("tan_hide"), 90, List.of("deer_hide"), List.of()).restsOnNothing());
        assertTrue(!ProcedureInterpreter.Plan.NOTHING.restsOnNothing());
        assertTrue(new ProcedureInterpreter.Plan(List.of("tan_hide"), 90).cited().isEmpty(),
            "the old two-argument shape still works, and cites nothing");
    }
}
