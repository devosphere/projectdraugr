package com.devosphere.draugr;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Launcher/readiness probe. A 200 response proves PostgreSQL is reachable AND the
 * canonical world has finished bootstrapping. The launcher waits for this before
 * opening the game window, so an Awaken click can never land while the world is
 * still generating on first launch (which previously produced "the world could
 * not be reached"). Until the world exists, this returns 503 so the launcher
 * keeps waiting instead of surfacing a premature ready state.
 */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        Integer worlds = jdbc.queryForObject("SELECT COUNT(*) FROM world_genesis", Integer.class);
        if (worlds == null || worlds == 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "initializing"));
        }
        return ResponseEntity.ok(Map.of("status", "ready"));
    }
}
