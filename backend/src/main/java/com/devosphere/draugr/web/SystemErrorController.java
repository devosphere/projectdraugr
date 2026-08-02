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
 * Read-only triage surface over {@code system_error_log}. Lets an operator (or a future
 * cycle) see which hard errors have actually fired, so a bug that produced a clean HTTP
 * response can't stay dormant. Recording happens in {@link SystemErrorRecorder}; this only
 * reads.
 */
@RestController
@RequestMapping("/api/system/errors")
public class SystemErrorController {

    private final JdbcTemplate jdbc;

    public SystemErrorController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Recent errors, newest first. {@code openOnly=true} (default) hides ones already triaged. */
    @GetMapping
    public List<SystemError> list(@RequestParam(defaultValue = "true") boolean openOnly,
                                  @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        String where = openOnly ? "WHERE resolved = FALSE" : "";
        return jdbc.query(
            "SELECT id, occurred_at, http_status, error_class, error_message, request_path, ai_was_live, resolved "
                + "FROM system_error_log " + where + " ORDER BY occurred_at DESC LIMIT " + capped,
            (rs, i) -> new SystemError(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("http_status"),
                rs.getString("error_class"),
                rs.getString("error_message"),
                rs.getString("request_path"),
                rs.getBoolean("ai_was_live"),
                rs.getBoolean("resolved")));
    }

    public record SystemError(UUID id, Instant occurredAt, int httpStatus, String errorClass,
                              String errorMessage, String requestPath, boolean aiWasLive, boolean resolved) { }
}
