package com.devosphere.draugr.chronicle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.ZoneOffset;
import java.sql.Timestamp;

@Service
public class ChronicleService {
    private final JdbcTemplate jdbc;
    public ChronicleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ChronicleSummary active() { return findSummary("WHERE c.life_state = 'LIVING'"); }

    @Transactional(readOnly = true)
    public ChronicleLocation activeLocation() {
        return jdbc.query("SELECT c.id, w.current_location_id, wc.biome, CASE WHEN EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id = wc.id AND es.site_category = 'RESOURCE' AND lower(es.site_kind) LIKE '%salt%') THEN 'SALT_DEPOSIT' WHEN EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id = wc.id AND es.site_category = 'RESOURCE' AND lower(es.site_kind) LIKE '%clay%') THEN 'CLAY_DEPOSIT' ELSE wc.biome END FROM chronicle c JOIN world_object w ON w.id = c.id JOIN world_chunk wc ON wc.id = w.current_location_id WHERE c.life_state = 'LIVING'", rs -> rs.next() ? new ChronicleLocation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)) : null);
    }
    @Transactional(readOnly = true)
    public ChronicleEnvironment activeEnvironment() {
        // The world's single sky, then modulated to what is FELT in the chronicle's biome (#28): a
        // mountain is colder and windier and turns the lowlands' rain to snow. See BiomeClimate.
        return jdbc.query("SELECT sc.simulated_at,COALESCE(ww.weather_kind,'CLEAR'),COALESCE(ww.ambient_temperature_c,18.0),COALESCE(ww.wind_speed_kph,6),wc.biome " +
            "FROM chronicle c JOIN simulation_clock sc ON sc.id=1 JOIN world_object w ON w.id=c.id LEFT JOIN world_chunk wc ON wc.id=w.current_location_id LEFT JOIN world_weather ww ON ww.world_id=c.world_id WHERE c.life_state='LIVING'",
            rs -> {
                if (!rs.next()) return null;
                com.devosphere.draugr.simulation.BiomeClimate.Local local = com.devosphere.draugr.simulation.BiomeClimate.at(
                    rs.getString(5), rs.getString(2), rs.getBigDecimal(3).doubleValue(), rs.getInt(4));
                return new ChronicleEnvironment(rs.getTimestamp(1).toInstant(), local.kind(), local.temperatureC(), local.windKph());
            });
    }

    @Transactional(readOnly = true)
    public List<ChronicleSummary> archive() {
        // Every chronicle the world has held, newest first — the living one included,
        // their story simply unfinished. The archive is a record of the world's whole
        // history of inhabitants, not a list of the fallen.
        return jdbc.query("SELECT c.id, c.sequence_number, c.life_state, c.arrived_at, c.died_at, c.death_cause, w.current_location_id FROM chronicle c JOIN world_object w ON w.id = c.id ORDER BY c.sequence_number DESC", (rs, row) -> summary(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class)));
    }

    /**
     * Everything one chronicle's life left behind: who they were, every action they
     * resolved in order, and the state of the body at the end.
     *
     * <p>The ledgers are append-only and objects are never deleted, so a life stays
     * fully readable long after it ends — which is what makes the archive a record
     * rather than a scoreboard. A living chronicle can be read too; their story is
     * simply still being written, and the death snapshot is absent.
     */
    @Transactional(readOnly = true)
    public ChronicleJourney journey(UUID chronicleId) {
        ChronicleSummary summary = jdbc.query(
            "SELECT c.id, c.sequence_number, c.life_state, c.arrived_at, c.died_at, c.death_cause, w.current_location_id " +
            "FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.id=?",
            rs -> rs.next() ? summary(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class)) : null,
            chronicleId);
        if (summary == null) throw new IllegalArgumentException("No such Chronicle.");

        // COALESCE the narration overlay over the base row so the archive and the PDF export show the
        // enriched prose the player saw live (AI sentence / death coda) when one was stored, and the
        // deterministic prose otherwise. The immutable base row remains the join anchor and the fallback.
        List<JourneyEntry> entries = jdbc.query(
            "SELECT ca.resolved_at, ca.intent_type, ca.outcome, COALESCE(can.narration, ca.narration) " +
            "FROM chronicle_action ca LEFT JOIN chronicle_action_narration can ON can.action_id = ca.id " +
            "WHERE ca.chronicle_id=? AND ca.narration IS NOT NULL ORDER BY ca.resolved_at, ca.id",
            (rs, row) -> new JourneyEntry(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3), rs.getString(4)),
            chronicleId);

        String finalBody = jdbc.query("SELECT body_snapshot::text FROM chronicle_death_snapshot WHERE chronicle_id=?",
            rs -> rs.next() ? rs.getString(1) : null, chronicleId);

        Integer discoveries = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_discovery WHERE chronicle_id=?", Integer.class, chronicleId);
        Integer placesNamed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_named_location WHERE chronicle_id=?", Integer.class, chronicleId);

        return new ChronicleJourney(summary, entries, finalBody, discoveries == null ? 0 : discoveries,
            placesNamed == null ? 0 : placesNamed);
    }

    @Transactional
    public ChronicleSummary awaken() {
        ChronicleSummary current = active();
        // The first crossing may be retried by the local UI while it is still
        // learning whether a Chronicle already exists. Rejoining that same
        // body is safe; creating a second living Chronicle is not.
        if (current != null) return current;
        WorldSeed world = jdbc.query("SELECT world_id, seed FROM world_genesis FOR UPDATE", rs -> rs.next() ? new WorldSeed(rs.getObject(1, UUID.class), rs.getLong(2)) : null);
        if (world == null) throw new IllegalStateException("World Genesis must exist before a Chronicle can awaken.");
        Integer priorLives = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle", Integer.class);
        int sequence = (priorLives == null ? 0 : priorLives) + 1;
        UUID spawnChunk = selectSpawn(world, sequence);
        UUID chronicleId = UUID.randomUUID();
        // Baseline the new life to SIMULATED world time, not the wall clock. The
        // physiology tick measures elapsed metabolic hours against the simulated
        // clock (ChroniclePhysiologyService.advanceTo), and that clock is already
        // far ahead of real time once any prior chronicle has acted. Seeding
        // arrived_at / last_metabolic_update to Instant.now() made the very first
        // action compute a huge elapsed span and starve the newborn on turn one
        // (issue #16). The simulation_clock row is the single source of "now".
        Instant now = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id = 1", Timestamp.class).toInstant();
        Timestamp occurredAt = Timestamp.from(now);
        jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'CHRONICLE', ?, ?)", chronicleId, "Unrecorded arrival " + sequence, spawnChunk);
        jdbc.update("INSERT INTO chronicle (id, world_id, sequence_number, life_state, arrived_at) VALUES (?, ?, ?, 'LIVING', ?)", chronicleId, world.id(), sequence, occurredAt);
        jdbc.update("INSERT INTO chronicle_body (chronicle_id, health, condition_summary, hunger, thirst, energy, temperature, wetness, bladder, bowel, hygiene) VALUES (?, 'Healthy', 'Unsteady', 'Satisfied', 'Hydrated', 'Rested', 'Comfortable', 'Damp', 'Comfortable', 'Comfortable', 'Normal')", chronicleId);
        jdbc.update("INSERT INTO chronicle_physiology (chronicle_id, last_metabolic_update) VALUES (?, ?)", chronicleId, occurredAt);
        jdbc.update("INSERT INTO chronicle_carry_capacity (chronicle_id) VALUES (?)", chronicleId);
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?)", chronicleId);
        issueArrivalClothing(chronicleId, occurredAt);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_AWAKENED', jsonb_build_object('origin', 'Earth', 'spawnChunkId', ?::text))", chronicleId, occurredAt, spawnChunk.toString());
        jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_AWAKENED', ?, jsonb_build_object('chronicleSequence', ?, 'origin', 'Earth'))", occurredAt, chronicleId, sequence);
        return new ChronicleSummary(chronicleId, sequence, "LIVING", now, null, null, spawnChunk);
    }

    private UUID selectSpawn(WorldSeed world, int sequence) {
        List<UUID> candidates = jdbc.query("SELECT c.id FROM world_chunk c WHERE c.world_id = ? AND c.biome IN ('TEMPERATE_FOREST', 'GRASSLAND') AND NOT EXISTS (SELECT 1 FROM ecology_site e WHERE e.chunk_id = c.id AND e.site_category = 'MONSTER') ORDER BY c.grid_y, c.grid_x", (rs, row) -> rs.getObject(1, UUID.class), world.id());
        if (candidates.isEmpty()) throw new IllegalStateException("No viable Chronicle spawn exists in the canonical world.");
        int index = Math.floorMod((int) (world.seed() ^ (sequence * 0x9E3779B9L)), candidates.size());
        return candidates.get(index);
    }

    private void issueArrivalClothing(UUID chronicleId, Timestamp occurredAt) {
        issueWornItem(chronicleId, "arrival_shirt", "Everyday shirt", "TORSO", "CLOTHING", occurredAt);
        issueWornItem(chronicleId, "arrival_trousers", "Everyday trousers", "WAIST", "CLOTHING", occurredAt);
        issueWornItem(chronicleId, "arrival_left_shoe", "Left everyday shoe", "FOOT_LEFT", "OUTER", occurredAt);
        issueWornItem(chronicleId, "arrival_right_shoe", "Right everyday shoe", "FOOT_RIGHT", "OUTER", occurredAt);
    }

    private void issueWornItem(UUID chronicleId, String itemKey, String displayName, String bodyPosition, String layer, Timestamp occurredAt) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", itemId, displayName, chronicleId);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", itemId, itemKey);
        jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer,attached_at) VALUES (?,?,?,?,?)", itemId, chronicleId, bodyPosition, layer, occurredAt);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,to_attachment,payload) VALUES (?,?,'ARRIVED_WORN',?,jsonb_build_object('origin','Earth'))", itemId, occurredAt, bodyPosition + ":" + layer);
    }

    private ChronicleSummary findSummary(String clause) {
        return jdbc.query("SELECT c.id, c.sequence_number, c.life_state, c.arrived_at, c.died_at, c.death_cause, w.current_location_id FROM chronicle c JOIN world_object w ON w.id = c.id " + clause, rs -> rs.next() ? summary(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getObject(7, UUID.class)) : null);
    }

    private ChronicleSummary summary(UUID id, int sequence, String state, Instant arrived, Instant died, String cause, UUID location) { return new ChronicleSummary(id, sequence, state, arrived, died, cause, location); }
    private record WorldSeed(UUID id, long seed) { }
    public record ChronicleSummary(UUID id, int sequenceNumber, String lifeState, Instant arrivedAt, Instant diedAt, String deathCause, UUID locationId) { }
    /** One resolved action, as the archive replays it. */
    public record JourneyEntry(Instant at, String intent, String outcome, String narration) { }
    /** A whole life, readable after its end. {@code finalBody} is null while the chronicle still lives. */
    public record ChronicleJourney(ChronicleSummary summary, List<JourneyEntry> entries, String finalBody, int discoveries, int placesNamed) { }
    public record ChronicleLocation(UUID chronicleId, UUID locationId, String biome, String presentationKey) { }
    public record ChronicleEnvironment(Instant simulatedAt,String weatherKind,Double ambientTemperatureC,Integer windSpeedKph) { }
}
