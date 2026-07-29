-- Runtime ecological stock is distinct from immutable ecology-site identity.
-- A Chronicle can take baseline biome supplies away from a chunk, but never
-- deletes the chunk or its ecological history.
CREATE TABLE world_chunk_resource (
    chunk_id UUID NOT NULL REFERENCES world_chunk(id),
    resource_key VARCHAR(80) NOT NULL,
    available_units SMALLINT NOT NULL CHECK (available_units >= 0),
    capacity_units SMALLINT NOT NULL CHECK (capacity_units > 0),
    last_regenerated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (chunk_id, resource_key),
    CHECK (available_units <= capacity_units)
);

CREATE INDEX idx_world_chunk_resource_availability ON world_chunk_resource (resource_key, available_units);
