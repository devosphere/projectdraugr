package com.devosphere.draugr.world.genesis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The arrival viability invariant (M1 #124, EPIC #123): a random start coordinate must give a dangerous-but-
 * viable first survival window, never a coordinate with no physically possible path to water, fuel, shelter
 * material, forage, tools, or retreat, and never one that is immediately fatal.
 *
 * <p>The classification rule itself lives in the {@code arrival_viability()} SQL function (V112) so there is
 * exactly one place it is written — the same rule the runtime enforces here and the {@code dr0112} regression
 * proves against seeded worlds. This service is the thin runtime surface: classify a chunk, and pick a start
 * that is never a REJECTED tile.
 */
@Service
public class ArrivalViabilityService {

    /** A start is chosen only from VIABLE or CHALLENGING coordinates; REJECTED is never offered. */
    public enum Viability { VIABLE, CHALLENGING, REJECTED }

    private final JdbcTemplate jdbc;

    public ArrivalViabilityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Classify a candidate start coordinate. A null/unknown chunk is REJECTED. */
    @Transactional(readOnly = true)
    public Viability classify(UUID chunkId) {
        if (chunkId == null) return Viability.REJECTED;
        String label = jdbc.queryForObject("SELECT arrival_viability(?)", String.class, chunkId);
        return label == null ? Viability.REJECTED : Viability.valueOf(label);
    }

    /**
     * Pick a random start for a world, preferring a comfortable (VIABLE) coordinate and falling back to a harsh
     * but survivable (CHALLENGING) one. Never returns a REJECTED coordinate; empty only if the world holds no
     * survivable start at all (a degenerate seed). The player is given no signal which they got — viability is a
     * generator invariant, not a promise surfaced in the UI.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> pickStart(UUID worldId) {
        UUID viable = jdbc.query(
            "SELECT id FROM world_chunk WHERE world_id=? AND arrival_viability(id)='VIABLE' ORDER BY random() LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, worldId);
        if (viable != null) return Optional.of(viable);
        UUID challenging = jdbc.query(
            "SELECT id FROM world_chunk WHERE world_id=? AND arrival_viability(id)='CHALLENGING' ORDER BY random() LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, worldId);
        return Optional.ofNullable(challenging);
    }
}
