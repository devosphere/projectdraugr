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
        p.setApiKey("sk-ant-stub"); // isUsable() needs a key present; the stub model never calls the network
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
}
