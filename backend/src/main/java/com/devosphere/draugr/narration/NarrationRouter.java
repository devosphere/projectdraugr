package com.devosphere.draugr.narration;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a resolved action is worth spending an AI call on.
 *
 * <p>This runs before any API request is made, so an action routed to
 * DETERMINISTIC costs nothing at all — no tokens, no latency, no failure mode.
 * The great majority of a session's actions (equipping, gathering, walking) are
 * fully served by {@link NarrationEngine} prose and never reach the network.
 *
 * <p>What earns a call is significance, not complexity: death, monsters, the
 * moment an animal finally accepts a chronicle, a serious wound, a chronicle
 * finding what a dead predecessor left behind, and the writing a player does in
 * their own words. See DR-0017.
 *
 * <p>Pure function on the frame — no database, no clock, no state. It is the
 * cheapest thing in the pipeline and must stay that way.
 */
@Component
public class NarrationRouter {

    /** Acts that are always routine enough for engine prose alone. */
    private static final Set<String> ALWAYS_DETERMINISTIC = Set.of(
        "EQUIP", "UNEQUIP", "DROP", "ADD_FUEL", "FEED_FIRE", "DESIGNATE", "MARK",
        "GATHER_PLANT", "GATHER_FIBER", "GATHER_STONE", "GATHER_BERRIES", "GATHER_BRANCHES",
        "GATHER_CLAY", "GATHER_STONE_SLAB", "FELL_TREE", "COLLECT_INSECTS", "LURE",
        "SET_TRAP", "REST", "URINATE", "DEFECATE", "WASH", "STRIP_BARK", "MAKE_CHARCOAL",
        "PERSONAL_ACT", "AGGRESSION_INANIMATE", "NONSENSICAL", "PHYSICALLY_IMPOSSIBLE");

    /** Acts whose significance always justifies the call. */
    private static final Set<String> ALWAYS_AI = Set.of("WRITE", "EDIT_DOCUMENT", "SKETCH_MAP");

    /** Species whose presence alone makes a moment worth narrating richly. */
    private static final Set<String> MONSTERS = Set.of(
        "cave_troll", "dire_wolf", "bog_wraith", "wyvern", "giant_bat_swarm",
        "harpy", "roc", "giant_hornet_queen", "locust_swarm");

    /**
     * The routing decision.
     *
     * @param intent         the resolved intent name
     * @param outcome        the resolved outcome
     * @param attention      HIGH, MODERATE, or LOW
     * @param actionText     what the player actually typed
     * @param stateChanges   how many qualitative transitions this tick produced
     * @param woundSeverity  severity of any wound taken, or 0
     * @param species        the species involved, or null
     * @param chronicleDead  whether this action ended the chronicle
     * @param bondReachedTamed whether an animal accepted the chronicle this action
     */
    public boolean shouldUseAI(String intent, String outcome, String attention, String actionText,
                               int stateChanges, int woundSeverity, String species,
                               boolean chronicleDead, boolean bondReachedTamed) {
        // Death is the one moment in a chronicle that only happens once.
        if (chronicleDead) return true;
        if (outcome != null) {
            String o = outcome.toUpperCase(Locale.ROOT);
            if (o.contains("DEAD") || o.contains("FATAL") || o.contains("DISCOVERY_OTHER_CHRONICLE")) return true;
        }
        // An animal deciding to trust a person is a threshold, not a transaction.
        if (bondReachedTamed) return true;
        // Monsters are rare and are supposed to feel it.
        if (species != null && MONSTERS.contains(species)) return true;
        // A serious wound deserves more than a template.
        if (woundSeverity >= 35) return true;

        if (intent != null) {
            if (ALWAYS_AI.contains(intent)) return true;
            // Deliberate, careful looking is the player asking to be told about the
            // world. That request is worth answering well.
            if ("OBSERVE".equals(intent) && "HIGH".equals(attention)) return true;
            if (ALWAYS_DETERMINISTIC.contains(intent)) return false;
        }
        // A player who wrote at length described something specific; meet them there.
        if (actionText != null && actionText.length() > 300) return true;
        // Several things changing at once is a moment with weight to it.
        if (stateChanges >= 2) return true;

        return false;
    }

    /** Convenience overload for the common case with no wound, monster, death, or bond. */
    public boolean shouldUseAI(String intent, String outcome, String attention, String actionText, int stateChanges) {
        return shouldUseAI(intent, outcome, attention, actionText, stateChanges, 0, null, false, false);
    }

    /** Intents this router will never spend a call on — exposed for tests and cost audits. */
    public List<String> deterministicIntents() { return ALWAYS_DETERMINISTIC.stream().sorted().toList(); }
}
