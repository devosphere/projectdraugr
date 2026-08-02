package com.devosphere.draugr.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tier. The handler turns exceptions into clean HTTP responses, and — for the two HARD
 * error classes only — records them to the dormant-bug tracker. The expected client outcomes
 * (bad input, world-rule conflict) are normal responses, not bugs, and must NOT be recorded.
 */
class GlobalExceptionHandlerTest {

    /** Captures record() calls without a database, so the wiring is testable in pure isolation. */
    private static final class CapturingRecorder extends SystemErrorRecorder {
        int calls;
        int lastStatus;
        Throwable lastError;
        String lastPath;
        CapturingRecorder() { super(null, null); } // constructor only stores fields; no DB touched
        @Override public void record(int httpStatus, Throwable error, String requestPath) {
            calls++; lastStatus = httpStatus; lastError = error; lastPath = requestPath;
        }
    }

    private final CapturingRecorder recorder = new CapturingRecorder();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(recorder);

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void invalidInputBecomesA400AndIsNotRecorded() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleBadRequest(new IllegalArgumentException("An action must contain 1 to 2500 characters."));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("An action must contain 1 to 2500 characters.", response.getBody().message());
        assertEquals(0, recorder.calls, "a client input error is a normal response, not a tracked bug");
    }

    @Test
    void worldRuleViolationBecomesA409AndIsNotRecorded() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleConflict(new IllegalStateException("The Chronicle cannot physically carry that load."));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
        assertEquals(0, recorder.calls, "a world-rule conflict is a normal response, not a tracked bug");
    }

    @Test
    void persistenceFailureBecomesACleanMessageAndIsRecorded() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handlePersistence(new DataIntegrityViolationException("boom"), request("/api/actions"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody().message());
        assertFalse(response.getBody().message().contains("\n"), "the reported message must be concise, not a stack trace");
        assertEquals(1, recorder.calls, "a persistence failure is a hard error and must be tracked");
        assertEquals(500, recorder.lastStatus);
        assertEquals("/api/actions", recorder.lastPath);
        assertNotNull(recorder.lastError);
    }

    @Test
    void unexpectedErrorStillProducesABodyAndIsRecorded() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleUnexpected(new RuntimeException(), request("/api/chronicles/active"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("RuntimeException", response.getBody().message());
        assertEquals(1, recorder.calls, "an otherwise-unhandled exception is a hard error and must be tracked");
        assertEquals("/api/chronicles/active", recorder.lastPath);
    }
}
