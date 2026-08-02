package com.devosphere.draugr.ai;

import com.devosphere.draugr.action.ChronicleActionService.PerceptionFrame;
import com.devosphere.draugr.action.ChronicleActionService.StateChange;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * The Simulation Agent's voice. Given the deterministic prose the world already produced and the
 * structured {@link PerceptionFrame} context, it asks the model for <b>one</b> extra sentence of
 * sensory atmosphere and appends it. It never replaces the deterministic prose and never invents
 * facts — the world describes itself; the AI only adds the feeling.
 *
 * <p>{@link #refine} is total and side-effect-free on failure: disabled, no key, timeout, or a
 * blank/again-empty result all return the deterministic prose unchanged. The caller can treat the
 * return value as "the narration", AI or not. See docs/architecture/narration-engine.md.
 */
@Component
public class SimulationNarrator {

    /** Witness-stance constraints (narration-engine.md § What the Narrator Never Does). */
    private static final String SYSTEM = """
        You are the witness narrator of a persistent survival simulation. You are given the \
        narration the world has already written for an action, plus its structured context. \
        Add EXACTLY ONE sentence — never two — of sensory or atmospheric detail that continues \
        the existing prose.

        Hard rules:
        - Do not repeat or restate anything the existing narration already says.
        - Do not advise, suggest, warn, or hint at what the player should do.
        - Do not name game stats or Body HUD fields (hunger, thirst, energy, blood loss, etc.).
        - Do not explain why anything happened, and do not invent events or objects not in the context.
        - No moral judgement, humour, surprise, or address to the player as "player".
        - Output only the single sentence, with no preamble, quotes, or labels.""";

    private final LanguageModel model;
    private final AiProperties props;

    public SimulationNarrator(LanguageModel model, AiProperties props) {
        this.model = model;
        this.props = props;
    }

    /** The model id the narrator refines with — recorded on the narration overlay so a later review can judge quality per model. */
    public String modelName() {
        return props.getNarrationModel();
    }

    /**
     * @param frame              the resolved action's context
     * @param backendNarration   the deterministic prose (the source of truth, always returned)
     * @return the deterministic prose, plus one AI sentence when the feature is on and the call
     *         succeeds; otherwise the deterministic prose unchanged
     */
    public String refine(PerceptionFrame frame, String backendNarration) {
        if (!props.isEnabled() || frame == null || backendNarration == null || backendNarration.isBlank()) {
            return backendNarration;
        }
        String user = buildUser(frame, backendNarration);
        return model.generate(props.getNarrationModel(), SYSTEM, user)
                .map(String::trim)
                .filter(extra -> !extra.isBlank())
                .map(extra -> backendNarration + " " + extra)
                .orElse(backendNarration);
    }

    private String buildUser(PerceptionFrame frame, String backendNarration) {
        String biome = frame.location() == null ? "unknown" : frame.location().biome();
        String weather = frame.weather() == null ? "clear"
                : frame.weather().kind() + " (intensity " + frame.weather().intensity() + ")";
        String nearby = frame.nearbyObjects() == null || frame.nearbyObjects().isEmpty()
                ? "nothing notable" : String.join(", ", frame.nearbyObjects());
        String changes = frame.sinceLastFrame() == null || frame.sinceLastFrame().isEmpty()
                ? "none"
                : frame.sinceLastFrame().stream()
                    .map(this::describeChange)
                    .collect(Collectors.joining("; "));
        return """
            Existing narration:
            %s

            Context:
            - Intent: %s
            - Outcome: %s
            - Location: %s at %s
            - Weather: %s
            - Nearby: %s
            - State changes this moment: %s""".formatted(
                backendNarration, frame.intent(), frame.outcome(), biome, frame.timeOfDay(),
                weather, nearby, changes);
    }

    private String describeChange(StateChange c) {
        return c.aspect() + " " + c.from() + " → " + c.to();
    }
}
