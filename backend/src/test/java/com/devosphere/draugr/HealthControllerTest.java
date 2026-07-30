package com.devosphere.draugr;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks the readiness contract that prevents the "world could not be reached"
 * bug on first launch: health must not report ready until the canonical world
 * exists, so the launcher never opens the game window before Awaken can succeed.
 */
class HealthControllerTest {

    @Test
    void reportsInitializingUntilTheWorldExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM world_genesis", Integer.class)).thenReturn(0);
        ResponseEntity<Map<String, String>> response = new HealthController(jdbc).health();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(), "health must not be ready before the world is bootstrapped");
        assertEquals("initializing", response.getBody().get("status"));
    }

    @Test
    void reportsReadyOnceTheWorldIsBootstrapped() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM world_genesis", Integer.class)).thenReturn(1);
        ResponseEntity<Map<String, String>> response = new HealthController(jdbc).health();
        assertEquals(HttpStatus.OK, response.getStatusCode(), "health must be ready once the canonical world exists");
        assertEquals("ready", response.getBody().get("status"));
    }
}
