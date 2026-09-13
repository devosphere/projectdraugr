package com.devosphere.draugr.web;

import com.devosphere.draugr.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.sql.Array;
import java.util.List;
import java.util.UUID;

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
 *
 * <p><b>The regression record (#83, V321).</b> A class name and a message are not enough to reproduce a fault. Each
 * row also carries the SQLSTATE, the failing statement, the schema version, the living Chronicle, the action that was
 * being resolved and the object ids the error names. The action's own ledger row rolls back with the fault, so the
 * action is read from the request: {@code ChronicleActionController} leaves its text and idempotency key as request
 * attributes, and they are still bound to the thread while the exception handler runs. Every detail is best-effort
 * and may be null.
 */
@Component
public class SystemErrorRecorder {

    /** Request attribute names the action controller sets so a fault can be tied to the action that caused it. */
    public static final String ACTION_TEXT_ATTRIBUTE = "draugr.action.text";
    public static final String ACTION_KEY_ATTRIBUTE = "draugr.action.idempotencyKey";

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
     *
     * <p>The class and message filed are the most specific cause's, as they always were; the whole exception is
     * searched for the SQL, the SQLSTATE and the object ids, because the wrapper is what carries the statement.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(int httpStatus, Throwable error, String requestPath) {
        try {
            Throwable specific = NestedExceptionUtils.getMostSpecificCause(error);
            String message = cap(specific.getMessage());
            String actionText = cap(requestAttribute(ACTION_TEXT_ATTRIBUTE) instanceof String s ? s : null);
            UUID actionKey = requestAttribute(ACTION_KEY_ATTRIBUTE) instanceof UUID u ? u : null;
            List<UUID> ids = ErrorDetail.objectIds(error);
            jdbc.update(
                "INSERT INTO system_error_log (http_status, error_class, error_message, request_path, ai_was_live, " +
                "  sql_state, failing_sql, schema_version, chronicle_id, action_text, action_idempotency_key, object_ids) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setInt(1, httpStatus);
                    ps.setString(2, specific.getClass().getName());
                    ps.setString(3, message);
                    ps.setString(4, requestPath);
                    ps.setBoolean(5, aiProperties != null && aiProperties.isUsable());
                    ps.setString(6, ErrorDetail.sqlState(error));
                    ps.setString(7, cap(ErrorDetail.failingSql(error)));
                    ps.setString(8, schemaVersion());
                    ps.setObject(9, livingChronicle());
                    ps.setString(10, actionText);
                    ps.setObject(11, actionKey);
                    Array array = ids.isEmpty() ? null : ps.getConnection().createArrayOf("uuid", ids.toArray());
                    ps.setArray(12, array);
                });
        } catch (Exception recordingFailure) {
            // Never let logging the error become a new error. The user still gets the
            // handler's clean response; we just couldn't file this one for triage.
            log.warn("Could not record system error to system_error_log: {}", recordingFailure.getMessage());
        }
    }

    private static String cap(String text) {
        return text != null && text.length() > MESSAGE_CAP ? text.substring(0, MESSAGE_CAP) : text;
    }

    private static Object requestAttribute(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

    private String schemaVersion() {
        try {
            return jdbc.query("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null);
        } catch (Exception e) { return null; }
    }

    private UUID livingChronicle() {
        try {
            return jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING' LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        } catch (Exception e) { return null; }
    }
}
