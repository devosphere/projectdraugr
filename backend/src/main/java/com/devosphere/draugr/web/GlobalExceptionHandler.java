package com.devosphere.draugr.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into clean, structured HTTP responses so no endpoint
 * ever returns a raw stack-trace 500. Domain rule violations are surfaced as
 * actionable client errors; anything unexpected is reported without leaking a
 * stack trace, while still including a concise message to aid bug reports.
 *
 * <p>Hard errors (persistence failures and otherwise-unhandled exceptions) are also
 * recorded to {@code system_error_log} via {@link SystemErrorRecorder}, so a fault can't
 * hide behind its own clean response. The two <em>expected</em> outcomes — bad input and
 * world-rule conflicts — are normal client responses, not bugs, and are not recorded.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SystemErrorRecorder errorRecorder;

    public GlobalExceptionHandler(SystemErrorRecorder errorRecorder) {
        this.errorRecorder = errorRecorder;
    }

    /** Invalid input from the caller: malformed action text, unrecognized edit, bad parameter. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** A world-rule violation: no living Chronicle, load exceeds carrying capacity, etc. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * What the player is told when the machinery underneath fails. It says a fault occurred and nothing about
     * what the fault was — the detail belongs in {@code system_error_log}, where a developer reads it (#83).
     */
    private static final String HARD_FAULT_MESSAGE =
        "Something in the world's own workings failed here, and the attempt was set aside rather than half-done. "
      + "Nothing has changed. The fault has been recorded.";

    /**
     * A persistence failure. Recorded in full for triage; reported to the caller as a controlled fault (#83).
     *
     * <p>This used to answer with {@code "A persistence error occurred: " + cause.getMessage()} — the driver's
     * own text, verbatim, into the narration panel. A player gathering firewood could be shown
     * {@code ERROR: column fps.item_id does not exist}. #83 asks for the opposite in as many words: any failure
     * must be "a controlled, logged simulation error rather than a raw database message in the narration panel".
     * The message is not lost, it is filed — {@code errorRecorder} still records the most specific cause, its
     * class and the request path.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handlePersistence(DataAccessException exception, HttpServletRequest request) {
        errorRecorder.record(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getMostSpecificCause(), request.getRequestURI());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, HARD_FAULT_MESSAGE);
    }

    /**
     * Anything else. Also a controlled fault: an unhandled exception's message is no safer than a driver's —
     * it routinely carries the failing SQL, a file path, or an internal class name.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        errorRecorder.record(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception, request.getRequestURI());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, HARD_FAULT_MESSAGE);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message == null ? "" : message));
    }

    public record ApiError(int status, String error, String message) { }
}
