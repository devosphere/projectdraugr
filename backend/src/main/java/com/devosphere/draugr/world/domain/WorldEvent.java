package com.devosphere.draugr.world.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "world_event")
public class WorldEvent {
    @Id @GeneratedValue private UUID id;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(name = "event_type", nullable = false, updatable = false) private String eventType;
    @Column(name = "aggregate_id", updatable = false) private UUID aggregateId;
    @Column(name = "causation_id", updatable = false) private UUID causationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false, updatable = false) private Map<String, Object> payload;
    protected WorldEvent() { }
    public WorldEvent(Instant occurredAt, String eventType, UUID aggregateId, UUID causationId, Map<String, Object> payload) {
        this.occurredAt = occurredAt; this.recordedAt = Instant.now(); this.eventType = eventType; this.aggregateId = aggregateId; this.causationId = causationId; this.payload = payload;
    }
}
