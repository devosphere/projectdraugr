package com.devosphere.draugr.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deterministic physics gate, in isolation — drafts that define all their own item masses need no
 * database, so this runs with a null JdbcTemplate. Proves the floor: mass is conserved and a recipe
 * with no inputs (matter from nothing) is rejected, whatever an AI might have proposed.
 */
class RuntimeProcessGateTest {

    private final RuntimeProcessGate gate = new RuntimeProcessGate(null);

    private ProcessDraft draft(List<ProcessDraft.Ingredient> inputs, String out, int outQty, List<ProcessDraft.NewItem> items) {
        return new ProcessDraft("twist_belt", "PROCESS", "twist,weave,belt", "belt,cordage,fiber", "CUTTING",
            inputs, out, outQty, "You work it into shape.", items);
    }

    @Test void massBalancedDraftPasses() {
        var items = List.of(
            new ProcessDraft.NewItem("plant_fiber", "Plant fiber", "MATERIAL", 100, 100),
            new ProcessDraft.NewItem("utility_belt", "Utility belt", "EQUIPMENT", 150, 300));
        var d = draft(List.of(new ProcessDraft.Ingredient("plant_fiber", 2)), "utility_belt", 1, items); // 200g in, 150g out
        assertTrue(gate.check(d).passed());
    }

    @Test void matterCreationIsRejected() {
        var items = List.of(
            new ProcessDraft.NewItem("plant_fiber", "Plant fiber", "MATERIAL", 100, 100),
            new ProcessDraft.NewItem("utility_belt", "Utility belt", "EQUIPMENT", 500, 300));
        var d = draft(List.of(new ProcessDraft.Ingredient("plant_fiber", 1)), "utility_belt", 1, items); // 100g in, 500g out
        var r = gate.check(d);
        assertFalse(r.passed());
        assertTrue(r.reason().contains("mass balance"), r.reason());
    }

    @Test void aProcessWithNoInputsIsRejected() {
        var items = List.of(new ProcessDraft.NewItem("gold", "Gold", "MATERIAL", 100, 100));
        var d = draft(List.of(), "gold", 1, items);
        assertFalse(gate.check(d).passed());
    }

    @Test void negativeMassIsRejected() {
        var items = List.of(new ProcessDraft.NewItem("void", "Void", "MATERIAL", -5, 0),
            new ProcessDraft.NewItem("thing", "Thing", "MATERIAL", 1, 1));
        var d = draft(List.of(new ProcessDraft.Ingredient("void", 1)), "thing", 1, items);
        assertFalse(gate.check(d).passed());
    }
}
