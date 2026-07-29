package com.devosphere.draugr.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read-only consistency checks. This service deliberately contains no mutation methods. */
@Service
public class PersistentStateAuditor {
    private final JdbcTemplate jdbc;
    public PersistentStateAuditor(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public AuditReport inspect() {
        List<String> violations = new ArrayList<>();
        Integer activeChronicles = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle WHERE life_state = 'LIVING'", Integer.class);
        if (activeChronicles != null && activeChronicles > 1) violations.add("More than one living Chronicle exists.");
        Integer unlocatedObjects = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE lifecycle_state <> 'DESTROYED' AND current_location_id IS NULL AND current_owner_id IS NULL", Integer.class);
        if (unlocatedObjects != null && unlocatedObjects > 0) violations.add(unlocatedObjects + " active object(s) lack a location or owner.");
        Integer chunks = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk", Integer.class);
        if (chunks == null || chunks == 0) violations.add("Canonical geography is missing.");
        return new AuditReport(violations.isEmpty(), List.copyOf(violations));
    }

    public record AuditReport(boolean consistent, List<String> violations) { }
}
