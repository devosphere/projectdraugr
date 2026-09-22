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

        Then name the carried things your plan is built on, as "context=<item>,<item>" — only items from the
        Carrying list, and only the ones the steps actually consume or use. Write "context=none" if the plan
        needs nothing carried.

        End your reply with a confidence from 0 to 100 on its own, as "confidence=NN".

        Do not explain. Do not invent a key that is not in the list. Do not name a thing that is not being
        carried. Prefer the shortest correct chain.""";

    /**
     * What the interpreter proposes: existing keys in order, how sure it is, and the carried things it says the
     * plan rests on. Never prose, never a new key, and never a thing the Chronicle is not carrying.
     *
     * @param cited     item keys from the Carrying list that the reply named, kept only when they were offered
     * @param imagined  item-shaped words it named that were NOT offered — the reason to throw the plan out (#37)
     */
    public record Plan(List<String> keys, int confidence, List<String> cited, List<String> imagined) {
        public static final Plan NOTHING = new Plan(List.of(), 0, List.of(), List.of());
        /** The shape before the reply cited its context (#37). */
        public Plan(List<String> keys, int confidence) { this(keys, confidence, List.of(), List.of()); }
        public boolean isEmpty() { return keys.isEmpty(); }
        /** Whether this plan was built on something that is not there, and so must not be run at all. */
        public boolean restsOnNothing() { return !imagined.isEmpty(); }
    }

    /** Below this the resolver will not spend a Chronicle's time on the plan at all. */
    public static final int SURE_ENOUGH = 40;

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
    public Plan plan(String actionText, List<String> inventory) {
        if (!props.isInterpreterActive() || actionText == null || actionText.isBlank()) return Plan.NOTHING;
        List<Map<String, Object>> catalog = jdbc.queryForList(
            "SELECT process_key, display_name FROM material_process WHERE review_state='VERIFIED' ORDER BY process_key");
        if (catalog.isEmpty()) return Plan.NOTHING;
        Set<String> valid = catalog.stream().map(r -> (String) r.get("process_key")).collect(Collectors.toSet());
        String user = buildUser(actionText, inventory, catalog);
        Set<String> carried = carriedKeys(inventory);
        return model.generate(props.getInterpreterModel(), SYSTEM, user)
            .map(reply -> new Plan(parse(reply, valid), confidence(reply),
                                   cited(reply, carried, valid), imagined(reply, carried, valid)))
            .filter(plan -> plan.confidence() >= SURE_ENOUGH)
            .orElse(Plan.NOTHING);
    }

    /**
     * The bare item keys behind a quantified inventory line — "dry_branch x5" is the key {@code dry_branch}.
     * What the caller passes is written for the model to weigh; what a citation must match is the thing itself.
     */
    static Set<String> carriedKeys(List<String> inventory) {
        if (inventory == null) return Set.of();
        return inventory.stream()
            .map(line -> line == null ? "" : line.trim().toLowerCase(Locale.ROOT).split("\\s+")[0])
            .filter(k -> !k.isBlank())
            .collect(Collectors.toSet());
    }

    /** The context clause of a reply, or "" when it named none. */
    private static String contextClause(String reply) {
        if (reply == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("context\\s*=\\s*([^\\n]*)").matcher(reply.toLowerCase(Locale.ROOT));
        return m.find() ? m.group(1) : "";
    }

    /** What the reply named that the Chronicle is actually carrying, in its order, de-duplicated. */
    List<String> cited(String reply, Set<String> carried, Set<String> processKeys) {
        List<String> out = new ArrayList<>();
        for (String token : contextClause(reply).split("[^a-z0-9_]+"))
            if (carried.contains(token) && !out.contains(token)) out.add(token);
        return List.copyOf(out);
    }

    /**
     * What the reply named that the Chronicle is NOT carrying (#37) — a model composing from materials that do
     * not exist has not misjudged the catalogue, it has imagined the world, and the plan is thrown out.
     *
     * <p>Process keys are excluded: naming a step in the context clause is untidy, not a hallucination. So is
     * "none", which the prompt asks for explicitly, and so is any word too short to be an item key.
     */
    List<String> imagined(String reply, Set<String> carried, Set<String> processKeys) {
        List<String> out = new ArrayList<>();
        for (String token : contextClause(reply).split("[^a-z0-9_]+")) {
            if (token.length() < 4 || carried.contains(token) || processKeys.contains(token)) continue;
            if ("none".equals(token) || "nothing".equals(token) || out.contains(token)) continue;
            out.add(token);
        }
        return List.copyOf(out);
    }

    /** The confidence the reply states, or an even fifty when it states none. */
    static int confidence(String reply) {
        if (reply == null) return 50;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("confidence\\s*=\\s*(\\d{1,3})").matcher(reply.toLowerCase(Locale.ROOT));
        return m.find() ? Math.min(100, Integer.parseInt(m.group(1))) : 50;
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
