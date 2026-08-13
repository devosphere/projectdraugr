package com.devosphere.draugr.ai;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.audit.PersistentStateAuditor.AuditReport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The Persistent State Auditor's voice (Phase 3). Turns a read-only {@link AuditReport} — the
 * consistency flag and the list of violated invariants — into a plain operator-facing summary for
 * the Overseer surface.
 *
 * <p>The Auditor's boundary is absolute and mirrored here: it <b>only describes</b>. This service
 * reads a report and produces prose; it never proposes a repair, a migration, a deletion, or any
 * write, and it never emits player-facing narration. See core-agent-boundaries.md and
 * docs/architecture/ai-integration.md § Phase 3.
 */
@Service
public class AuditorSummarizer {

    private static final String SYSTEM = """
        You are the voice of a read-only consistency Auditor, writing for the Overseer — the world's \
        operator, never a player. You are given a consistency report: whether the persistent world \
        satisfies its invariants, and any violations found. Summarize it plainly and factually.

        - If the world is consistent, say so in one or two sentences.
        - If there are violations, group and explain them in plain language so the operator \
          understands what is inconsistent and where.

        Hard rules: you ONLY describe. Never propose a repair, fix, migration, deletion, or any \
        change; never tell anyone to act. Do not address a player or write story prose — this is an \
        internal operator summary. Output only the summary.""";

    private final PersistentStateAuditor auditor;
    private final LanguageModel model;
    private final AiProperties props;

    public AuditorSummarizer(PersistentStateAuditor auditor, LanguageModel model, AiProperties props) {
        this.auditor = auditor;
        this.model = model;
        this.props = props;
    }

    /** Run a fresh read-only audit and summarize it. Empty when the feature is off or the model fails. */
    public Optional<String> summarize() {
        return summarize(auditor.inspect());
    }

    /** Summarize a given report — the core, testable without invoking the auditor or a database. */
    public Optional<String> summarize(AuditReport report) {
        if (!props.isAuditorActive() || report == null) return Optional.empty();
        return model.generate(props.getAuditorModel(), SYSTEM, buildUser(report))
                .map(String::trim)
                .filter(s -> !s.isBlank());
    }

    private String buildUser(AuditReport report) {
        List<String> violations = report.violations() == null ? List.of() : report.violations();
        String listed = violations.isEmpty()
                ? "(none)"
                : IntStream.range(0, violations.size())
                    .mapToObj(i -> (i + 1) + ". " + violations.get(i))
                    .collect(Collectors.joining("\n"));
        return """
            Consistency report:
            - World consistent: %s
            - Violations (%d):
            %s""".formatted(report.consistent(), violations.size(), listed);
    }
}
