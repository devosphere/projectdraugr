-- V138 — local habitat disturbance measurement + wildlife avoidance (EPIC #207, stories #208/#209, first slice).
--
-- The disturbance contract (#207): physical activity -> measurable local disturbance -> habitat perception ->
-- response -> persistent history. This is the first vertical slice of that loop: a per-chunk disturbance level
-- that a disruptive act raises (a fight/kill, a felled tree), that DECAYS over time (a place left alone grows
-- quiet again), and an immutable event log of what caused it. WildlifeSimulationService reads the level and the
-- wildlife that live there grow wary and quit the ground while it stays disturbed — a transient avoidance, NOT a
-- despawn, teleport, or silent decline (the #207 safeguards). Migration/decline/return are later slices.
--
-- Bounded to a chunk, with intensity (0..100) and a decaying duration, and immutable evidence history (#208).
CREATE TABLE chunk_disturbance (
    chunk_id          UUID PRIMARY KEY REFERENCES world_chunk(id),
    disturbance_level SMALLINT    NOT NULL DEFAULT 0 CHECK (disturbance_level BETWEEN 0 AND 100),
    last_updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Immutable provenance: every rise in disturbance records what caused it, where, how much, and when.
CREATE TABLE chunk_disturbance_event (
    id          UUID PRIMARY KEY,
    chunk_id    UUID        NOT NULL REFERENCES world_chunk(id),
    source_kind VARCHAR(40) NOT NULL,
    amount      SMALLINT    NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_chunk_disturbance_event_chunk ON chunk_disturbance_event (chunk_id, occurred_at);
