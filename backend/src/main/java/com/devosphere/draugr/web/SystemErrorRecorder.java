package com.devosphere.draugr.web;

import com.devosphere.draugr.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durably records hard runtime errors so a bug can't hide dormant.
 *
 * <p>The {@link GlobalExceptionHandler} converts a persistence failure or an otherwise
 * unhandled exception into a clean HTTP response — but a clean response is also a quiet
 * one: without this, a fault (like the append-only-immutability violation that motivated
 * the table) stays invisible until a live playthrough stumbles on it. Every hard error is
 * written to {@code system_error_log} instead, where it surfaces for triage.
 *
 * <p>Two properties are essential and both are honored here:
 * <ul>
 *   <li><b>It runs in its own transaction</b> ({@code REQUIRES_NEW}). By the time the
 *       {@code @RestControllerAdvice} fires, the action's {@code @Transactional} boundary
 *       has already rolled back; a fresh transaction is needed or the INSERT would be
 *       swept up in that same rollback and never persist.</li>
 *   <li><b>It never throws.</b> Recording an error must not itself become an error that
 *       masks the original. Any failure to log is swallowed (after a warn), so the user
 *       still gets the handler's clean response.</li>
 * </ul>
 *
 * <p>{@code ai_was_live} captures whether the AI layer was switched on at failure time — a
 * proxy for "could a model call have been wasted on this path." Under the current ordering
 * the Simulation Agent's call is the last thing an action does, so a hard error precedes it
 * and costs no token; this flag lets us verify that, and would betray any regression that
 * reintroduced a spend-then-fail path.
 */
@Component
public class SystemErrorRecorder {

    private static final Logger log = LoggerFactory.getLogger(SystemErrorRecorder.class);
    private static final int MESSAGE_CAP = 4000;

    private final JdbcTemplate jdbc;
    private final AiProperties aiProperties;

    public SystemErrorRecorder(JdbcTemplate jdbc, AiProperties aiProperties) {
        this.jdbc = jdbc;
        this.aiProperties = aiProperties;
    }

    /**
     * Persist one hard error. Best-effort: on any failure to record, warns and returns
     * without throwing, so the caller's clean error response is never disrupted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(int httpStatus, Throwable error, String requestPath) {
        try {
            String message = error.getMessage();
            if (message != null && message.length() > MESSAGE_CAP) message = message.substring(0, MESSAGE_CAP);
            jdbc.update(
                "INSERT INTO system_error_log (http_status, error_class, error_message, request_path, ai_was_live) VALUES (?, ?, ?, ?, ?)",
                httpStatus, error.getClass().getName(), message, requestPath, aiProperties.isUsable());
        } catch (Exception recordingFailure) {
            // Never let logging the error become a new error. The user still gets the
            // handler's clean response; we just couldn't file this one for triage.
            log.warn("Could not record system error to system_error_log: {}", recordingFailure.getMessage());
        }
    }
}
