package com.devosphere.draugr.ai;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The runtime procedure-authoring pipeline (DR-0021), invoked at a classifier miss. Two stages:
 *
 * <ol>
 *   <li><b>Compose</b> — the {@link ProcedureInterpreter} expresses the action as an ordered sequence
 *       of EXISTING processes, each run through the gated {@link PhysicalItemService#executeProcess}.</li>
 *   <li><b>Author</b> — only if composition fails AND authoring is enabled: the {@link RuntimeArchitect}
 *       drafts a NEW mechanic scoped to the chronicle, the deterministic {@link RuntimeProcessGate}
 *       enforces physics, the {@link QaCritic} judges plausibility over a bounded ≤N-round loop, and on
 *       pass the scoped rows are written (never canon), the discovery is logged, and it is executed.</li>
 * </ol>
 *
 * <p>Non-load-bearing: with AI off ({@code !isUsable()}) {@code attempt} returns empty immediately and
 * the caller falls back to the ordinary miss — the game is unchanged. The Architect path is behind its
 * own {@code authoring-enabled} sub-switch, so interpretation of existing mechanics can run without ever
 * writing a new one. No LLM is the final authority: gate (physics) → QA (plausibility) → human (canon).
 */
@Service
public class RuntimeAuthoringService {

    private final ProcedureInterpreter interpreter;
    private final RuntimeArchitect architect;
    private final QaCritic qa;
    private final RuntimeProcessGate gate;
    private final PhysicalItemService items;
    private final AiProperties props;
    private final JdbcTemplate jdbc;

    public RuntimeAuthoringService(ProcedureInterpreter interpreter, RuntimeArchitect architect, QaCritic qa,
                                   RuntimeProcessGate gate, PhysicalItemService items, AiProperties props, JdbcTemplate jdbc) {
        this.interpreter = interpreter;
        this.architect = architect;
        this.qa = qa;
        this.gate = gate;
        this.items = items;
        this.props = props;
        this.jdbc = jdbc;
    }

    /** The result of a resolved action ({outcome, narration}), or empty when the pipeline did nothing. */
    @Transactional
    public Optional<String[]> attempt(UUID chronicle, UUID location, String text, Instant at) {
        if (!props.isUsable()) return Optional.empty();
        List<String> inventory = items.reachableItemKeys(chronicle, location);

        // Stage 1 — compose from existing processes.
        String[] composed = runPlan(chronicle, location, interpreter.plan(text, inventory), text, at);
        if (composed != null) return Optional.of(composed);

        // Stage 2 — author a new scoped mechanic (opt-in).
        if (!props.isAuthoringEnabled()) return Optional.empty();
        return author(chronicle, location, text, inventory, at);
    }

    /** Run an ordered plan of existing process keys; null if empty/first-step-fails, else the last result. */
    private String[] runPlan(UUID chronicle, UUID location, List<String> plan, String text, Instant at) {
        if (plan == null || plan.isEmpty()) return null;
        String[] last = null;
        boolean anySucceeded = false;
        for (String key : plan) {
            if (!items.processExists(key)) return anySucceeded ? last : null;
            last = items.executeProcess(chronicle, location, key, text, at);
            if ("SUCCEEDED".equals(last[0])) anySucceeded = true;
            else return anySucceeded ? last : null;
        }
        return last;
    }

    private Optional<String[]> author(UUID chronicle, UUID location, String text, List<String> inventory, Instant at) {
        Optional<ProcessDraft> drafted = architect.draft(text, inventory);
        if (drafted.isEmpty()) { record(chronicle, text, null, "no draft", "n/a", null); return Optional.empty(); }

        ProcessDraft draft = drafted.get();
        RuntimeProcessGate.Result gateResult = gate.check(draft);
        QaCritic.Verdict verdict = gateResult.passed() ? qa.review(draft)
            : new QaCritic.Verdict(false, "blocked before QA by physics: " + gateResult.reason());

        // Bounded author↔critic loop: at most qaMaxRounds revisions.
        int rounds = 0;
        while ((!gateResult.passed() || !verdict.passed()) && rounds < props.getQaMaxRounds()) {
            rounds++;
            String reasons = (gateResult.passed() ? "" : "physics: " + gateResult.reason() + "; ")
                + (verdict.passed() ? "" : "plausibility: " + verdict.reasons());
            Optional<ProcessDraft> revised = architect.revise(draft, reasons);
            if (revised.isEmpty()) break;
            draft = revised.get();
            gateResult = gate.check(draft);
            verdict = gateResult.passed() ? qa.review(draft)
                : new QaCritic.Verdict(false, "blocked before QA by physics: " + gateResult.reason());
        }

        if (!gateResult.passed() || !verdict.passed()) {
            record(chronicle, text, draft.processKey(), gateResult.reason(), verdict.reasons(), architect.modelName());
            return Optional.empty();
        }

        // Passed physics AND plausibility — write the scoped mechanic, log the discovery, and run it.
        String processKey = insertScopedProcess(chronicle, draft);
        record(chronicle, text, processKey, "PASS", "PASS", architect.modelName());
        return Optional.of(items.executeProcess(chronicle, location, processKey, text, at));
    }

    /**
     * Write the drafted mechanic as rows scoped to the chronicle — never canonical. New items and the
     * process get a per-chronicle key suffix so they can never collide with a canonical key, and any
     * input/output that names a new item is rewritten to the suffixed key. Marked VERIFIED so the
     * chronicle can use (and re-match) it immediately; the human canon gate is a separate promotion.
     */
    private String insertScopedProcess(UUID chronicle, ProcessDraft draft) {
        String sfx = "_c" + chronicle.toString().replace("-", "").substring(0, 8);
        String domain = jdbc.query(
            "SELECT domain_key FROM domain_registry ORDER BY (domain_key='items') DESC, domain_key LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);

        Map<String, String> remap = new HashMap<>();
        if (draft.newItems() != null) for (ProcessDraft.NewItem n : draft.newItems()) {
            String nk = trunc(n.itemKey(), 100 - sfx.length()) + sfx;
            remap.put(n.itemKey(), nk);
            jdbc.update(
                "INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, discovered_by_chronicle_id) " +
                "VALUES (?, ?, ?, ?, ?, FALSE, FALSE, ?) ON CONFLICT (item_key) DO NOTHING",
                nk, n.displayName(), n.category() == null ? "MATERIAL" : n.category(),
                Math.max(0, n.unitMassGrams()), Math.max(0, n.unitVolumeMl()), chronicle);
        }

        String pk = trunc(draft.processKey(), 60 - sfx.length()) + sfx;
        String outKey = remap.getOrDefault(draft.outputItemKey(), draft.outputItemKey());
        String tool = draft.toolClass();
        if (tool != null && !tool.equals("CUTTING") && !tool.equals("STRIKING") && !tool.equals("AXE")) tool = null;

        jdbc.update(
            "INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, " +
            "requires_fire, requires_water, duration_minutes, domain_key, keywords, narration, category_key, review_state, discovered_by_chronicle_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, FALSE, FALSE, 30, ?, ?, ?, ?, 'VERIFIED', ?)",
            pk, trunc(draft.processKey(), 118), outKey, Math.max(1, draft.outputQty()), Math.max(1, draft.outputQty()),
            tool, domain, trunc(draft.keywords(), 200), draft.narration() == null ? "You work it into shape." : draft.narration(),
            draft.category() == null ? "PROCESS" : draft.category(), chronicle);

        if (draft.inputs() != null) for (ProcessDraft.Ingredient in : draft.inputs())
            jdbc.update("INSERT INTO material_process_input (process_key, item_key, quantity) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                pk, remap.getOrDefault(in.itemKey(), in.itemKey()), Math.max(1, in.quantity()));

        if (draft.subjects() != null) for (String subject : draft.subjects().split(","))
            if (!subject.isBlank())
                jdbc.update("INSERT INTO process_subject (process_key, subject_term) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    pk, trunc(subject.trim().toLowerCase(), 40));

        return pk;
    }

    private void record(UUID chronicle, String text, String processKey, String gateResult, String qaVerdict, String model) {
        jdbc.update(
            "INSERT INTO chronicle_tech_discovery (chronicle_id, procedure_text, process_key, gate_result, qa_verdict, model) VALUES (?, ?, ?, ?, ?, ?)",
            chronicle, text, processKey, gateResult, qaVerdict, model);
    }

    private static String trunc(String s, int max) { return s == null ? null : (s.length() <= max ? s : s.substring(0, max)); }
}
