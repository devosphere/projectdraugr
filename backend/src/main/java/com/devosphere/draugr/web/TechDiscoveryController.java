package com.devosphere.draugr.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only review surface over {@code chronicle_tech_discovery} (DR-0021). Every runtime-authored
 * mechanic — drafted by the Architect, checked by the physics gate and the QA critic — lands here for a
 * human to review and, if good, promote toward canon. Reading only; the pipeline writes.
 */
@RestController
@RequestMapping("/api/system/tech-discoveries")
public class TechDiscoveryController {

    private final JdbcTemplate jdbc;

    public TechDiscoveryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Recent discoveries, newest first. {@code openOnly=true} (default) hides ones already triaged. */
    @GetMapping
    public List<TechDiscovery> list(@RequestParam(defaultValue = "true") boolean openOnly,
                                    @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        String where = openOnly ? "WHERE resolved = FALSE" : "";
        return jdbc.query(
            "SELECT id, chronicle_id, discovered_at, procedure_text, process_key, gate_result, qa_verdict, model, promoted, resolved "
                + "FROM chronicle_tech_discovery " + where + " ORDER BY discovered_at DESC LIMIT " + capped,
            (rs, i) -> new TechDiscovery(
                rs.getObject("id", UUID.class),
                rs.getObject("chronicle_id", UUID.class),
                rs.getTimestamp("discovered_at").toInstant(),
                rs.getString("procedure_text"),
                rs.getString("process_key"),
                rs.getString("gate_result"),
                rs.getString("qa_verdict"),
                rs.getString("model"),
                rs.getBoolean("promoted"),
                rs.getBoolean("resolved")));
    }

    public record TechDiscovery(UUID id, UUID chronicleId, Instant discoveredAt, String procedureText,
                                String processKey, String gateResult, String qaVerdict, String model,
                                boolean promoted, boolean resolved) { }
}
