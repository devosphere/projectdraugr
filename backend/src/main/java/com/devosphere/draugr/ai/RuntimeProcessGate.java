package com.devosphere.draugr.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * The deterministic physics floor under runtime authoring (DR-0021, role 4). It is NOT AI — it is the
 * same class of check the V53 migration gate runs, made callable at insert time, and it has the final
 * word on physics regardless of what the Architect or QA critic say. A draft that fails here is never
 * written, no matter how plausible it reads.
 *
 * <p>Checks: the draft names a real, reachable set of inputs (canonical items or ones it defines
 * itself); it conserves mass (output mass ≤ input mass — no matter created from nothing); and it is
 * routable (has a category and keywords). This is what keeps a stochastic author from ever producing a
 * near-infinite resource loop or an item out of thin air, even in a chronicle's private sandbox.
 */
@Component
public class RuntimeProcessGate {

    private final JdbcTemplate jdbc;
    public RuntimeProcessGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Result(boolean passed, String reason) {
        public static Result ok() { return new Result(true, "mass-balanced and reachable"); }
        public static Result fail(String reason) { return new Result(false, reason); }
    }

    @Transactional(readOnly = true)
    public Result check(ProcessDraft d) {
        if (d == null || blank(d.processKey())) return Result.fail("no process key");
        if (blank(d.category())) return Result.fail("no category — unroutable");
        if (blank(d.keywords())) return Result.fail("no keywords — unreachable by any phrasing");
        if (blank(d.outputItemKey())) return Result.fail("no output");
        if (d.inputs() == null || d.inputs().isEmpty()) return Result.fail("a process with no inputs creates matter from nothing");

        // Known masses: the draft's own new items first, then whatever it references from the catalogue.
        Map<String, Integer> mass = new HashMap<>();
        if (d.newItems() != null) for (ProcessDraft.NewItem n : d.newItems()) {
            if (n.unitMassGrams() < 0) return Result.fail("new item '" + n.itemKey() + "' has negative mass");
            mass.put(n.itemKey(), n.unitMassGrams());
        }

        long inputMass = 0;
        for (ProcessDraft.Ingredient in : d.inputs()) {
            if (in.quantity() <= 0) return Result.fail("input '" + in.itemKey() + "' has a non-positive quantity");
            Integer m = massOf(mass, in.itemKey());
            if (m == null) return Result.fail("input '" + in.itemKey() + "' does not exist — unreachable");
            inputMass += (long) m * in.quantity();
        }
        Integer outM = massOf(mass, d.outputItemKey());
        if (outM == null) return Result.fail("output '" + d.outputItemKey() + "' does not exist");
        long outputMass = (long) outM * Math.max(1, d.outputQty());

        if (outputMass > inputMass)
            return Result.fail("mass balance violated: output " + outputMass + "g exceeds input " + inputMass + "g (matter created)");
        return Result.ok();
    }

    /** Mass from the draft's own new items, else the canonical catalogue, else null (unknown item). */
    private Integer massOf(Map<String, Integer> known, String itemKey) {
        if (known.containsKey(itemKey)) return known.get(itemKey);
        return jdbc.query("SELECT unit_mass_grams FROM item_definition WHERE item_key=?",
            rs -> rs.next() ? rs.getInt(1) : null, itemKey);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
