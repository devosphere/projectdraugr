package com.devosphere.draugr.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The Persistent State Architect (Phase 2 groundwork). Reads the frequency-ranked routing-miss
 * backlog — the gaps players actually walked into — and, for a chosen gap, asks the model to
 * <b>draft</b> a Flyway migration (or vocabulary term) that would close it.
 *
 * <p>Two boundaries are load-bearing and enforced by shape, not just by intent:
 * <ul>
 *   <li><b>Authoring time, never resolution time.</b> Nothing here runs on the action path. It is
 *       an offline surface a human drives; a proposal is text for review, not a write.</li>
 *   <li><b>It proposes; it never commits.</b> {@link #propose} returns a draft. Applying it means a
 *       human puts it through the V53 review gate as a real migration. This service never mutates
 *       schema or data.</li>
 * </ul>
 * See docs/architecture/three-ai-integration.md § Phase 2 and routing-and-coverage-strategy.md.
 */
@Service
public class PersistentStateArchitect {

    private static final String SYSTEM = """
        You are the Persistent State Architect for a survival simulation. You are shown a gap a \
        player walked into — a sentence the deterministic router could not resolve — and its kind. \
        Draft the smallest change that would close it, as a reviewed migration proposal for a human.

        By gap kind:
        - VOCABULARY: nothing was recognised. Propose category_term rows mapping the novel verb(s) to \
          an existing activity_category. Cheapest fix.
        - KEYWORD: the right process exists in the right category but not this phrasing. Propose adding \
          keyword(s) to that process.
        - SUBJECT: right process and verb, wrong/absent material. Propose a process_subject term, or \
          state plainly if the player meant a material the process should not handle.
        - MECHANIC: no process exists to do this at all. Propose a material_process (+ inputs/outputs) \
          — the expensive kind. Only propose one if it is clearly a real primitive-survival mechanic.

        Hard rules:
        - Never invent items, minerals, or flora that do not already exist; reference only known keys.
        - Conserve mass: a process's outputs must not exceed its inputs (the review gate enforces this).
        - Output a single Flyway-style SQL statement (or a short INSERT set), then one line beginning \
          'Rationale:' explaining the change. Nothing else. This is a PROPOSAL for human review, not a \
          committed change.""";

    private final JdbcTemplate jdbc;
    private final LanguageModel model;
    private final AiProperties props;

    public PersistentStateArchitect(JdbcTemplate jdbc, LanguageModel model, AiProperties props) {
        this.jdbc = jdbc;
        this.model = model;
        this.props = props;
    }

    /** The most-frequent unresolved gaps, worst first — the queue the Architect works from. */
    @Transactional(readOnly = true)
    public List<BacklogEntry> backlog(int limit) {
        return jdbc.query(
                "SELECT sample_text, normalised_text, classified_category, near_process_key, hit_count, gap_kind " +
                "FROM routing_miss_backlog ORDER BY hit_count DESC, last_seen DESC LIMIT ?",
                (rs, i) -> new BacklogEntry(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6)),
                limit);
    }

    /**
     * Draft a migration proposal for one gap. Returns empty when the feature is off or the model
     * declines/fails — the backlog simply waits, exactly as it does today with no AI at all.
     * Applying a returned proposal is a deliberate human step through the review gate.
     */
    public Optional<ArchitectProposal> propose(BacklogEntry entry) {
        if (!props.isEnabled() || entry == null) return Optional.empty();
        return model.generate(props.getArchitectModel(), SYSTEM, buildUser(entry))
                .map(String::trim)
                .filter(draft -> !draft.isBlank())
                .map(draft -> new ArchitectProposal(entry.gapKind(), entry.sampleText(), draft));
    }

    private String buildUser(BacklogEntry e) {
        return """
            Unresolved player action: %s
            Normalised: %s
            Gap kind: %s
            Classified category: %s
            Nearest existing process: %s
            Times players hit this: %d""".formatted(
                e.sampleText(), e.normalisedText(), e.gapKind(),
                e.classifiedCategory() == null ? "(none — nothing recognised)" : e.classifiedCategory(),
                e.nearProcessKey() == null ? "(none)" : e.nearProcessKey(),
                e.hitCount());
    }

    /** One frequency-ranked gap from {@code routing_miss_backlog}. */
    public record BacklogEntry(String sampleText, String normalisedText, String classifiedCategory,
                               String nearProcessKey, int hitCount, String gapKind) { }

    /** A drafted migration/term for a gap — text for human review, never auto-applied. */
    public record ArchitectProposal(String gapKind, String sampleText, String draftMigration) { }
}
