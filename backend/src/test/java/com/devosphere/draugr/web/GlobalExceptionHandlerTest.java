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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(response.getBody().message());
        assertEquals(1, recorder.calls, "an otherwise-unhandled exception is a hard error and must be tracked");
        assertEquals("/api/chronicles/active", recorder.lastPath);
    }

    /**
     * #83's definition of done, in as many words: a failure must be "a controlled, logged simulation error rather
     * than a raw database message in the narration panel." The handler used to answer with the driver's own text,
     * so a player gathering firewood could be shown a PostgreSQL grammar error naming a column.
     */
    @Test
    void aDatabaseFaultNeverReachesThePlayerInItsOwnWords() {
        String driverText = "ERROR: column fps.item_id does not exist\n  Position: 44";
        ResponseEntity<GlobalExceptionHandler.ApiError> response =
            handler.handlePersistence(new DataIntegrityViolationException(driverText), request("/api/actions"));

        String shown = response.getBody().message();
        assertFalse(shown.contains("item_id"), "the failing column must not be shown to the player: " + shown);
        assertFalse(shown.contains("ERROR:"), "nor the driver's own error prefix: " + shown);
        assertFalse(shown.toLowerCase().contains("sql") || shown.toLowerCase().contains("column"),
            "nor any database vocabulary at all: " + shown);
        assertFalse(shown.contains("\n"), "and it stays one concise line, never a stack trace");

        // Filed, not lost — the whole message is still there for whoever has to fix it.
        assertEquals(1, recorder.calls);
        assertNotNull(recorder.lastError);
        assertTrue(recorder.lastError.getMessage().contains("item_id"),
            "the detail belongs in the error log, where a developer reads it");
    }

    /** The same rule for anything else: an unhandled exception's message routinely carries SQL or a file path. */
    @Test
    void anUnhandledFaultIsAlsoControlled() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response =
            handler.handleUnexpected(new RuntimeException("SELECT * FROM chronicle WHERE id=? failed at C:\\build\\Foo.java"),
                request("/api/actions"));
        String shown = response.getBody().message();
        assertFalse(shown.contains("SELECT"), "no query fragment reaches the player: " + shown);
        assertFalse(shown.contains("C:\\"), "and no file path either: " + shown);
        assertEquals(1, recorder.calls, "while the fault is still filed for triage");
    }
}
