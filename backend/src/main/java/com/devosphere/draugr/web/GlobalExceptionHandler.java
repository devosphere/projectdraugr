package com.devosphere.draugr.web;

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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    /** A persistence failure. Reported concisely without a stack trace; the presence of one indicates a bug to fix. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handlePersistence(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A persistence error occurred: " + cause.getMessage());
    }

    /** Anything else. Clean 500 with a concise message, never a stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message == null ? "" : message));
    }

    public record ApiError(int status, String error, String message) { }
}
