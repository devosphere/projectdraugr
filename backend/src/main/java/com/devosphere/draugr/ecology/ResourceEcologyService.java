package com.devosphere.draugr.ecology;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Finite local resource stock with deterministic, simulated-time regeneration. */
@Service
public class ResourceEcologyService {
    private final JdbcTemplate jdbc;
    public ResourceEcologyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public int take(UUID chunkId, String resourceKey, int requestedUnits, Instant simulatedAt) {
        Profile profile = profile(chunkId, resourceKey);
        jdbc.update("INSERT INTO world_chunk_resource (chunk_id,resource_key,available_units,capacity_units,last_regenerated_at) VALUES (?,?,?,?,?) ON CONFLICT (chunk_id,resource_key) DO NOTHING", chunkId, resourceKey, profile.capacity(), profile.capacity(), simulatedAt);
        Stock stock = jdbc.query("SELECT available_units,capacity_units,last_regenerated_at FROM world_chunk_resource WHERE chunk_id=? AND resource_key=? FOR UPDATE", rs -> rs.next() ? new Stock(rs.getInt(1), rs.getInt(2), rs.getTimestamp(3).toInstant()) : null, chunkId, resourceKey);
        if (stock == null) return 0;
        long regenerationSteps = Math.max(0, Duration.between(stock.lastRegeneratedAt(), simulatedAt).toHours() / profile.hoursPerUnit());
        int regenerated = Math.min(stock.capacity(), stock.available() + (int) Math.min(Integer.MAX_VALUE, regenerationSteps));
        int taken = Math.min(Math.max(0, requestedUnits), regenerated);
        jdbc.update("UPDATE world_chunk_resource SET available_units=?,last_regenerated_at=? WHERE chunk_id=? AND resource_key=?", regenerated - taken, simulatedAt, chunkId, resourceKey);
        return taken;
    }

    private Profile profile(UUID chunkId, String resourceKey) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunkId);
        boolean concentrated = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND site_category='RESOURCE')", Boolean.class, chunkId));
        int bonus = concentrated ? 12 : 0;
        return switch (resourceKey) {
            case "plant_fiber" -> new Profile(("WETLAND".equals(biome) ? 48 : 32) + bonus, 8);
            case "wild_berries" -> new Profile(("WETLAND".equals(biome) ? 30 : 18) + bonus, 18);
            case "dry_branch" -> new Profile(("MOUNTAIN".equals(biome) ? 12 : 24) + bonus, 12);
            case "field_stone" -> new Profile(("MOUNTAIN".equals(biome) || "HIGHLAND".equals(biome) ? 42 : 20) + bonus, 36);
            default -> throw new IllegalArgumentException("Unknown ecological resource: " + resourceKey);
        };
    }

    private record Profile(int capacity, int hoursPerUnit) { }
    private record Stock(int available, int capacity, Instant lastRegeneratedAt) { }
}
