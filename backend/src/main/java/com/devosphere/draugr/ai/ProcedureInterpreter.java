package com.devosphere.draugr.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Procedure Interpreter (DR-0021, role 1). When the deterministic classifier misses, it asks the
 * model whether the player's realistic procedure decomposes into a sequence of processes the world
 * <b>already has</b> — and returns those existing {@code process_key}s in order, or nothing.
 *
 * <p>It composes; it never invents. The keys it returns are executed by the same deterministic
 * {@code runProcess} machinery, so every physical gate (inputs, tools, mass, capability) still applies —
 * that gate is the backstop against a nearest-neighbour false match. Parsing is defensive: only tokens
 * that are actual verified process keys survive, so the model cannot smuggle in a key that does not
 * exist. Total and side-effect-free: disabled, no key, timeout, or a blank/NONE reply all return an
 * empty plan, and the caller falls back to the ordinary miss behaviour — the game never depends on it.
 */
@Component
public class ProcedureInterpreter {

    private static final String SYSTEM = """
        You map a survival-game action to a sequence of EXISTING crafting/processing steps.

        You are given the player's action, what they are carrying, and a list of available process keys
        with short descriptions. Reply with ONLY the process keys that, run in order, accomplish the
        action — comma-separated, each key EXACTLY as listed, lowest-level step first. Use only keys from
        the list. If no ordered combination of the listed processes accomplishes the action, reply with
        the single word NONE.

        Do not explain. Do not invent a key that is not in the list. Prefer the shortest correct chain.""";

    private final LanguageModel model;
    private final AiProperties props;
    private final JdbcTemplate jdbc;

    public ProcedureInterpreter(LanguageModel model, AiProperties props, JdbcTemplate jdbc) {
        this.model = model;
        this.props = props;
        this.jdbc = jdbc;
    }

    /**
     * An ordered list of existing process keys that compose the action, or empty when the feature is
     * off, the model fails, or nothing in the catalogue composes it.
     *
     * @param actionText what the player typed
     * @param inventory  the item keys the chronicle can reach (context for a viable chain)
     */
    @Transactional(readOnly = true)
    public List<String> plan(String actionText, List<String> inventory) {
        if (!props.isUsable() || actionText == null || actionText.isBlank()) return List.of();
        List<Map<String, Object>> catalog = jdbc.queryForList(
            "SELECT process_key, display_name FROM material_process WHERE review_state='VERIFIED' ORDER BY process_key");
        if (catalog.isEmpty()) return List.of();
        Set<String> valid = catalog.stream().map(r -> (String) r.get("process_key")).collect(Collectors.toSet());
        String user = buildUser(actionText, inventory, catalog);
        return model.generate(props.getInterpreterModel(), SYSTEM, user)
            .map(reply -> parse(reply, valid))
            .orElse(List.of());
    }

    /** Keep only tokens that are real verified process keys, in the model's order, de-duplicated. */
    List<String> parse(String reply, Set<String> valid) {
        if (reply == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : reply.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
            if (valid.contains(token) && !out.contains(token)) out.add(token);
        return List.copyOf(out);
    }

    private String buildUser(String actionText, List<String> inventory, List<Map<String, Object>> catalog) {
        String carried = inventory == null || inventory.isEmpty() ? "nothing of note"
            : inventory.stream().distinct().collect(Collectors.joining(", "));
        String processes = catalog.stream()
            .map(r -> "- " + r.get("process_key") + ": " + r.get("display_name"))
            .collect(Collectors.joining("\n"));
        return """
            Action:
            %s

            Carrying: %s

            Available process keys:
            %s""".formatted(actionText, carried, processes);
    }
}
