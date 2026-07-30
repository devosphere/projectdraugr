package com.devosphere.draugr.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidInputBecomesA400() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleBadRequest(new IllegalArgumentException("An action must contain 1 to 2500 characters."));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("An action must contain 1 to 2500 characters.", response.getBody().message());
    }

    @Test
    void worldRuleViolationBecomesA409() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleConflict(new IllegalStateException("The Chronicle cannot physically carry that load."));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
    }

    @Test
    void persistenceFailureBecomesACleanMessageWithoutStackTrace() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handlePersistence(new DataIntegrityViolationException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody().message());
        assertFalse(response.getBody().message().contains("\n"), "the reported message must be concise, not a stack trace");
    }

    @Test
    void nullMessageStillProducesABody() {
        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleUnexpected(new RuntimeException());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("RuntimeException", response.getBody().message());
    }
}
