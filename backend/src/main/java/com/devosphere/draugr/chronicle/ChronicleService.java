package com.devosphere.draugr.chronicle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.ZoneOffset;

@Service
public class ChronicleService {
    private final JdbcTemplate jdbc;
    public ChronicleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ChronicleSummary active() { return findSummary("WHERE c.life_state = 'LIVING'"); }

    @Transactional(readOnly = true)
    public ChronicleLocation activeLocation() {
        return jdbc.query("SELECT c.id, w.current_location_id, wc.biome, CASE WHEN EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id = wc.id AND es.site_category = 'RESOURCE' AND lower(es.site_kind) LIKE '%clay%') THEN 'CLAY_DEPOSIT' ELSE wc.biome END FROM chronicle c JOIN world_object w ON w.id = c.id JOIN world_chunk wc ON wc.id = w.current_location_id WHERE c.life_state = 'LIVING'", rs -> rs.next() ? new ChronicleLocation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)) : null);
    }
    @Transactional(readOnly = true)
    public ChronicleEnvironment activeEnvironment() {
        return jdbc.query("SELECT sc.simulated_at,ww.weather_kind,ww.ambient_temperature_c,ww.wind_speed_kph FROM chronicle c JOIN simulation_clock sc ON sc.id=1 LEFT JOIN world_weather ww ON ww.world_id=c.world_id WHERE c.life_state='LIVING'",rs->rs.next()?new ChronicleEnvironment(rs.getTimestamp(1).toInstant(),rs.getString(2)==null?"CLEAR":rs.getString(2),rs.getBigDecimal(3)==null?null:rs.getBigDecimal(3).doubleValue(),rs.getObject(4,Integer.class)):null);
    }

    @Transactional(readOnly = true)
    public List<ChronicleSummary> archive() {
        return jdbc.query("SELECT c.id, c.sequence_number, c.life_state, c.arrived_at, c.died_at, c.death_cause, w.current_location_id FROM chronicle c JOIN world_object w ON w.id = c.id WHERE c.life_state = 'DEAD' ORDER BY c.died_at DESC", (rs, row) -> summary(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class)));
    }

    @Transactional
    public ChronicleSummary awaken() {
        ChronicleSummary current = active();
        if (current != null) throw new IllegalStateException("A living Chronicle already exists.");
        WorldSeed world = jdbc.query("SELECT world_id, seed FROM world_genesis FOR UPDATE", rs -> rs.next() ? new WorldSeed(rs.getObject(1, UUID.class), rs.getLong(2)) : null);
        if (world == null) throw new IllegalStateException("World Genesis must exist before a Chronicle can awaken.");
        Integer priorLives = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle", Integer.class);
        int sequence = (priorLives == null ? 0 : priorLives) + 1;
        UUID spawnChunk = selectSpawn(world, sequence);
        UUID chronicleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'CHRONICLE', ?, ?)", chronicleId, "Unrecorded arrival " + sequence, spawnChunk);
        jdbc.update("INSERT INTO chronicle (id, world_id, sequence_number, life_state, arrived_at) VALUES (?, ?, ?, 'LIVING', ?)", chronicleId, world.id(), sequence, now);
        jdbc.update("INSERT INTO chronicle_body (chronicle_id, health, condition_summary, hunger, thirst, energy, temperature, wetness, bladder, bowel, hygiene) VALUES (?, 'Healthy', 'Unsteady', 'Satisfied', 'Hydrated', 'Rested', 'Comfortable', 'Damp', 'Comfortable', 'Comfortable', 'Normal')", chronicleId);
        jdbc.update("INSERT INTO chronicle_physiology (chronicle_id, last_metabolic_update) VALUES (?, ?)", chronicleId, now);
        jdbc.update("INSERT INTO chronicle_carry_capacity (chronicle_id) VALUES (?)", chronicleId);
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?)", chronicleId);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_AWAKENED', jsonb_build_object('origin', 'Earth', 'spawnChunkId', ?::text))", chronicleId, now, spawnChunk.toString());
        jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_AWAKENED', ?, jsonb_build_object('chronicleSequence', ?, 'origin', 'Earth'))", now, chronicleId, sequence);
        return new ChronicleSummary(chronicleId, sequence, "LIVING", now, null, null, spawnChunk);
    }

    private UUID selectSpawn(WorldSeed world, int sequence) {
        List<UUID> candidates = jdbc.query("SELECT c.id FROM world_chunk c WHERE c.world_id = ? AND c.biome IN ('TEMPERATE_FOREST', 'GRASSLAND') AND NOT EXISTS (SELECT 1 FROM ecology_site e WHERE e.chunk_id = c.id AND e.site_category = 'MONSTER') ORDER BY c.grid_y, c.grid_x", (rs, row) -> rs.getObject(1, UUID.class), world.id());
        if (candidates.isEmpty()) throw new IllegalStateException("No viable Chronicle spawn exists in the canonical world.");
        int index = Math.floorMod((int) (world.seed() ^ (sequence * 0x9E3779B9L)), candidates.size());
        return candidates.get(index);
    }

    private ChronicleSummary findSummary(String clause) {
        return jdbc.query("SELECT c.id, c.sequence_number, c.life_state, c.arrived_at, c.died_at, c.death_cause, w.current_location_id FROM chronicle c JOIN world_object w ON w.id = c.id " + clause, rs -> rs.next() ? summary(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class)) : null);
    }

    private ChronicleSummary summary(UUID id, int sequence, String state, Instant arrived, Instant died, String cause, UUID location) { return new ChronicleSummary(id, sequence, state, arrived, died, cause, location); }
    private record WorldSeed(UUID id, long seed) { }
    public record ChronicleSummary(UUID id, int sequenceNumber, String lifeState, Instant arrivedAt, Instant diedAt, String deathCause, UUID locationId) { }
    public record ChronicleLocation(UUID chronicleId, UUID locationId, String biome, String presentationKey) { }
    public record ChronicleEnvironment(Instant simulatedAt,String weatherKind,Double ambientTemperatureC,Integer windSpeedKph) { }
}
