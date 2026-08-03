package com.devosphere.draugr.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The QA Critic (DR-0021, role 5) — an independent reviewer of the Runtime Architect's draft, on a
 * DIFFERENT model so it is a genuine second opinion, not the author agreeing with itself. It judges
 * <b>plausibility and balance only</b> (design rule #5): is this authentic primitive technology, does
 * it skip a physically necessary step, is the yield sane, is it an exploit? Physics (mass balance,
 * reachability) is NOT its job — {@link RuntimeProcessGate} owns that in code, and code is authoritative.
 *
 * <p>Fail-closed: when the feature is off or the model gives no usable answer, the verdict is FAIL, so
 * nothing dubious slips through on silence. It raises confidence; it never replaces the human canon gate.
 */
@Component
public class QaCritic {

    public record Verdict(boolean passed, String reasons) { }

    private static final String SYSTEM = """
        You review a proposed primitive-survival crafting recipe. Judge PLAUSIBILITY and BALANCE only —
        NOT arithmetic mass (that is checked separately in code). Ask: is this authentic stone/early-iron
        age technology? Does it skip a physically necessary intermediate step? Is the yield realistic, or
        a game-breaking shortcut / near-infinite loop?

        Reply EXACTLY "PASS" if it is sound, or "FAIL: <one short reason>" if not.""";

    private final LanguageModel model;
    private final AiProperties props;

    public QaCritic(LanguageModel model, AiProperties props) {
        this.model = model;
        this.props = props;
    }

    public Verdict review(ProcessDraft d) {
        if (!props.isUsable() || d == null) return new Verdict(false, "QA unavailable");
        return model.generate(props.getQaModel(), SYSTEM, describe(d))
            .map(reply -> reply.trim().toUpperCase(Locale.ROOT).startsWith("PASS")
                ? new Verdict(true, "PASS")
                : new Verdict(false, reply.trim()))
            .orElse(new Verdict(false, "QA gave no response"));
    }

    public String modelName() { return props.getQaModel(); }

    private String describe(ProcessDraft d) {
        String inputs = d.inputs() == null ? "" : d.inputs().stream()
            .map(i -> i.quantity() + "× " + i.itemKey()).collect(Collectors.joining(", "));
        String newItems = d.newItems() == null || d.newItems().isEmpty() ? "none"
            : d.newItems().stream().map(n -> n.itemKey() + " (" + n.unitMassGrams() + "g)").collect(Collectors.joining(", "));
        return """
            Proposed recipe "%s":
            - tool: %s
            - inputs: %s
            - output: %d× %s
            - new items introduced: %s
            - narration: %s""".formatted(
                d.processKey(), d.toolClass() == null ? "bare hands" : d.toolClass(),
                inputs.isEmpty() ? "(none)" : inputs, Math.max(1, d.outputQty()), d.outputItemKey(), newItems, d.narration());
    }
}
