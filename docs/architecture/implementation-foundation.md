# Implementation foundation

This is the first executable slice of the Project Draugr architecture.

- `world_object` is runtime truth for all physical entities. Its UUID is permanent; objects transition state instead of being deleted.
- An active object has exactly one location or owner. Destroyed objects retain their row and identity.
- `world_event` is a separate immutable archive. PostgreSQL rejects updates and deletes, so historical state cannot accidentally become runtime state.
- `simulation_clock` serializes authoritative time progression. A tick is a transactional server operation and records its result before any narration layer can consume it.
- Flyway is the sole owner of schema evolution. Future civilization domains must be introduced as additive migrations only when simulation evidence warrants them.

The implementation intentionally has no narration or autonomous schema-writing logic. Those belong to the Simulation Agent and Persistent State Architect boundaries respectively, not to the initial deterministic kernel.

## Verification

Unit tests cover the active-object location/owner and lifecycle rules. A PostgreSQL Testcontainers integration test applies the actual Flyway migration and proves that the event archive rejects both updates and deletes. It skips automatically when Docker is unavailable.
