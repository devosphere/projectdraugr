package com.devosphere.draugr.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The Runtime Architect (DR-0021, role 3). When the Interpreter cannot compose a novel procedure from
 * existing processes, this drafts ONE new {@link ProcessDraft} — data scoped to the discovering
 * chronicle, never schema, never canon. It only proposes; the deterministic {@link RuntimeProcessGate}
 * and the {@link QaCritic} judge it, and only a human promotes it to canon.
 *
 * <p>Gated and total like every other role: returns empty when the feature is off, the model fails, or
 * the reply is not parseable JSON / is NONE. A genuinely new <em>domain</em> needing new tables is out
 * of runtime scope — the Architect should return NONE, and the gap stays a human authoring-time
 * migration (DR-0009/DR-0013).
 */
@Component
public class RuntimeArchitect {

    private static final String SYSTEM = """
        You author ONE primitive-survival crafting/processing step as strict JSON, for a realistic
        procedure the game has no mechanic for yet.

        Rules:
        - Conserve mass: the output's mass must NOT exceed the total mass of the inputs.
        - Prefer EXISTING item keys for inputs. Define any genuinely new item in "newItems" with a
          realistic gram mass and ml volume.
        - It must be authentic primitive technology (stone-age / early-iron-age), one step, no shortcuts.
        - If it would need a whole new domain of tables, or is not real primitive tech, reply exactly NONE.

        Reply with ONLY the JSON object (no prose, no code fences), matching exactly this shape:
        {"processKey":"lower_snake_case","category":"PROCESS","keywords":"comma,separated,verbs",
         "subjects":"comma,separated,materials","toolClass":"CUTTING",
         "inputs":[{"itemKey":"plant_fiber","quantity":2}],
         "outputItemKey":"twisted_cord","outputQty":1,
         "narration":"one witness-stance sentence, no advice, no HUD terms",
         "newItems":[{"itemKey":"twisted_cord","displayName":"Twisted cord","category":"MATERIAL","unitMassGrams":180,"unitVolumeMl":120}]}
        Use "toolClass":null for bare-handed work. Use "newItems":[] when nothing new is introduced.""";

    private final LanguageModel model;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public RuntimeArchitect(LanguageModel model, AiProperties props) {
        this.model = model;
        this.props = props;
    }

    public Optional<ProcessDraft> draft(String procedureText, List<String> inventory) {
        if (!props.isUsable() || procedureText == null || procedureText.isBlank()) return Optional.empty();
        String user = "Procedure:\n" + procedureText + "\n\nCarrying: "
            + (inventory == null || inventory.isEmpty() ? "nothing of note" : String.join(", ", inventory));
        return parse(model.generate(props.getArchitectModel(), SYSTEM, user));
    }

    /** Redraft after the gate or QA rejected the previous attempt (the bounded author↔critic loop). */
    public Optional<ProcessDraft> revise(ProcessDraft previous, String reasons) {
        if (!props.isUsable()) return Optional.empty();
        String user = "Your previous draft was rejected for: " + reasons + "\n\nPrevious JSON:\n"
            + toJson(previous) + "\n\nReturn a corrected JSON draft in the same shape, or NONE.";
        return parse(model.generate(props.getArchitectModel(), SYSTEM, user));
    }

    public String modelName() { return props.getArchitectModel(); }

    /** Extract the JSON object from the reply and deserialize; empty on NONE / not-JSON / malformed. */
    Optional<ProcessDraft> parse(Optional<String> reply) {
        if (reply.isEmpty()) return Optional.empty();
        String s = reply.get();
        int open = s.indexOf('{'), close = s.lastIndexOf('}');
        if (open < 0 || close <= open) return Optional.empty();
        try {
            ProcessDraft d = mapper.readValue(s.substring(open, close + 1), ProcessDraft.class);
            return d.processKey() == null || d.processKey().isBlank() ? Optional.empty() : Optional.of(d);
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }

    private String toJson(ProcessDraft d) {
        try { return mapper.writeValueAsString(d); } catch (Exception e) { return "{}"; }
    }
}
