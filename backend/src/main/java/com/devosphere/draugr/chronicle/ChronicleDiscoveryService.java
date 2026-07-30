package com.devosphere.draugr.chronicle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class ChronicleDiscoveryService {
    private final JdbcTemplate jdbc;
    public ChronicleDiscoveryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void record(UUID chronicleId, String discoveryKey, UUID actionId, Instant occurredAt) {
        jdbc.update("INSERT INTO chronicle_discovery (chronicle_id,discovery_key,acquired_at,source_action_id) VALUES (?,?,?,?) ON CONFLICT (chronicle_id,discovery_key) DO NOTHING", chronicleId, discoveryKey, Timestamp.from(occurredAt), actionId);
    }

    @Transactional(readOnly = true)
    public DiscoveryContext activeContext() {
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return new DiscoveryContext(List.of(), List.of());
        List<String> discoveries = jdbc.query("SELECT discovery_key FROM chronicle_discovery WHERE chronicle_id=? ORDER BY acquired_at", (rs, row) -> rs.getString(1), chronicle);
        List<ConstructionRecord> projects = jdbc.query("SELECT cp.object_id,w.display_name,cp.project_kind,cp.state,cp.progress_percent FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN world_object c ON c.current_location_id=w.current_location_id WHERE c.id=? AND w.lifecycle_state='ACTIVE' ORDER BY w.created_at", (rs,row) -> new ConstructionRecord(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)), chronicle);
        return new DiscoveryContext(List.copyOf(discoveries), List.copyOf(projects));
    }
    public record DiscoveryContext(List<String> discoveries,List<ConstructionRecord> constructions) { }
    public record ConstructionRecord(UUID id,String displayName,String projectKind,String state,int progressPercent) { }
}
