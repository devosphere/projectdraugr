package com.devosphere.draugr.ecology;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
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
        Timestamp simulatedTs = Timestamp.from(simulatedAt);
        jdbc.update("INSERT INTO world_chunk_resource (chunk_id,resource_key,available_units,capacity_units,last_regenerated_at) VALUES (?,?,?,?,?) ON CONFLICT (chunk_id,resource_key) DO NOTHING", chunkId, resourceKey, profile.capacity(), profile.capacity(), simulatedTs);
        Stock stock = jdbc.query("SELECT available_units,capacity_units,last_regenerated_at FROM world_chunk_resource WHERE chunk_id=? AND resource_key=? FOR UPDATE", rs -> rs.next() ? new Stock(rs.getInt(1), rs.getInt(2), rs.getTimestamp(3).toInstant()) : null, chunkId, resourceKey);
        if (stock == null) return 0;
        long regenerationSteps = Math.max(0, Duration.between(stock.lastRegeneratedAt(), simulatedAt).toHours() / profile.hoursPerUnit());
        int regenerated = Math.min(stock.capacity(), stock.available() + (int) Math.min(Integer.MAX_VALUE, regenerationSteps));
        int taken = Math.min(Math.max(0, requestedUnits), regenerated);
        jdbc.update("UPDATE world_chunk_resource SET available_units=?,last_regenerated_at=? WHERE chunk_id=? AND resource_key=?", regenerated - taken, simulatedTs, chunkId, resourceKey);
        return taken;
    }

    /** A full natural tree stand for a wooded chunk not yet recorded (matches PhysicalItemService.NATURAL_STAND). */
    private static final int NATURAL_STAND = 16;
    /** Biomes that carry trees by nature (the ones fellTree yields a fallback species for). */
    private static final java.util.Set<String> WOODED_BIOMES =
        java.util.Set.of("FOREST", "TEMPERATE_FOREST", "MOUNTAIN", "HIGHLAND", "WETLAND", "RIVER_BANK");

    private Profile profile(UUID chunkId, String resourceKey) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunkId);
        boolean concentrated = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND site_category='RESOURCE')", Boolean.class, chunkId));
        int bonus = concentrated ? 12 : 0;
        return switch (resourceKey) {
            case "plant_fiber" -> new Profile(("WETLAND".equals(biome) ? 48 : 32) + bonus, 8);
            case "wild_berries" -> new Profile(("WETLAND".equals(biome) ? 30 : 18) + bonus, 18);
            // Deadfall (#200/#201): standing woodland sheds branches, so a well-treed chunk is a far richer source
            // of firewood than bare ground — and clearing the wood removes that bonus, taking the deadfall back down
            // toward the bare-ground base (never below it, so gathering firewood anywhere still works as before).
            case "dry_branch" -> {
                java.util.Map<String,Object> t = jdbc.queryForMap(
                    "SELECT COUNT(*) AS n, COALESCE(SUM(cf.quantity),0) AS q FROM chunk_flora cf " +
                    "JOIN flora_definition fd ON fd.flora_key=cf.flora_key WHERE cf.chunk_id=? AND fd.organism_type='TREE'", chunkId);
                int rows = ((Number) t.get("n")).intValue();
                int stand = ((Number) t.get("q")).intValue();
                // Un-recorded wooded ground carries a full natural stand (matching fellTree's lazy seeding, #200), so a
                // standing wood sheds deadfall before an axe ever touches it. A recorded stand cut to nothing (a row at
                // zero) stays bare — clearing the wood really does take the deadfall bonus away.
                if (rows == 0 && WOODED_BIOMES.contains(biome)) stand = NATURAL_STAND;
                int woodland = Math.min(24, stand * 4);
                yield new Profile(("MOUNTAIN".equals(biome) ? 12 : 24) + bonus + woodland, 12);
            }
            case "field_stone" -> new Profile(("MOUNTAIN".equals(biome) || "HIGHLAND".equals(biome) ? 42 : 20) + bonus, 36);
            default -> throw new IllegalArgumentException("Unknown ecological resource: " + resourceKey);
        };
    }

    private record Profile(int capacity, int hoursPerUnit) { }
    private record Stock(int available, int capacity, Instant lastRegeneratedAt) { }
}
