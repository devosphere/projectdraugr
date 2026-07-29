CREATE TABLE world_genesis (
    world_id UUID PRIMARY KEY REFERENCES world_object(id),
    seed BIGINT NOT NULL,
    generator_version VARCHAR(50) NOT NULL,
    width_chunks INTEGER NOT NULL CHECK (width_chunks > 0),
    height_chunks INTEGER NOT NULL CHECK (height_chunks > 0),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (width_chunks <= 256 AND height_chunks <= 256)
);

CREATE TABLE world_chunk (
    id UUID PRIMARY KEY REFERENCES world_object(id),
    world_id UUID NOT NULL REFERENCES world_genesis(world_id),
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    elevation SMALLINT NOT NULL CHECK (elevation BETWEEN 0 AND 1000),
    moisture SMALLINT NOT NULL CHECK (moisture BETWEEN 0 AND 1000),
    biome VARCHAR(50) NOT NULL,
    UNIQUE (world_id, grid_x, grid_y)
);

CREATE INDEX idx_world_chunk_world_biome ON world_chunk (world_id, biome);
