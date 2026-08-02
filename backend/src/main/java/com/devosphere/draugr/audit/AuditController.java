package com.devosphere.draugr.audit;

import com.devosphere.draugr.ai.AuditorSummarizer;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Operator/Overseer read surface for consistency — the raw report, and its AI summary when enabled. */
@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class AuditController {
    private final PersistentStateAuditor auditor;
    private final AuditorSummarizer summarizer;
    public AuditController(PersistentStateAuditor auditor, AuditorSummarizer summarizer) {
        this.auditor = auditor;
        this.summarizer = summarizer;
    }

    @GetMapping public PersistentStateAuditor.AuditReport inspect() { return auditor.inspect(); }

    /**
     * The report plus a read-only prose summary of it (the Auditor AI, Phase 3). {@code summary} is
     * null when the AI layer is off — the raw {@code consistent}/{@code violations} are always present,
     * so the surface works with or without a key.
     */
    @GetMapping("/summary")
    public AuditSummary summary() {
        PersistentStateAuditor.AuditReport report = auditor.inspect();
        return new AuditSummary(report.consistent(), report.violations(), summarizer.summarize(report).orElse(null));
    }

    public record AuditSummary(boolean consistent, List<String> violations, String summary) { }
}
