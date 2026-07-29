package com.devosphere.draugr.world.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "world_object")
public class WorldObject {
    @Id @GeneratedValue private UUID id;
    @Column(name = "object_type", nullable = false) private String objectType;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_state", nullable = false) private LifecycleState lifecycleState = LifecycleState.ACTIVE;
    @Column(name = "current_location_id") private UUID currentLocationId;
    @Column(name = "current_owner_id") private UUID currentOwnerId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "destroyed_at") private Instant destroyedAt;

    protected WorldObject() { }
    public WorldObject(String objectType, String displayName, UUID locationId, UUID ownerId) {
        if ((locationId == null) == (ownerId == null)) throw new IllegalArgumentException("An object must have exactly one physical location or owner");
        this.objectType = objectType; this.displayName = displayName; this.currentLocationId = locationId; this.currentOwnerId = ownerId;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public LifecycleState getLifecycleState() { return lifecycleState; }
    public void moveTo(UUID locationId) { ensureActive(); currentLocationId = require(locationId); currentOwnerId = null; }
    public void transferTo(UUID ownerId) { ensureActive(); currentOwnerId = require(ownerId); currentLocationId = null; }
    public void destroy() { ensureActive(); lifecycleState = LifecycleState.DESTROYED; destroyedAt = Instant.now(); currentLocationId = null; currentOwnerId = null; }
    private void ensureActive() { if (lifecycleState != LifecycleState.ACTIVE) throw new IllegalStateException("Only active objects can change state"); }
    private UUID require(UUID id) { if (id == null) throw new IllegalArgumentException("Location or owner is required"); return id; }
}
