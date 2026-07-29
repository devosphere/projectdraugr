-- Ecological concentrations are physical world features. They are not the
-- whole resource model: every biome still provides its baseline supplies.
CREATE TABLE ecology_site (
    id UUID PRIMARY KEY REFERENCES world_object(id),
    world_id UUID NOT NULL REFERENCES world_genesis(world_id),
    chunk_id UUID NOT NULL REFERENCES world_chunk(id),
    site_category VARCHAR(20) NOT NULL CHECK (site_category IN ('RESOURCE', 'WILDLIFE', 'MONSTER', 'RUIN')),
    site_kind VARCHAR(100) NOT NULL,
    baseline_abundance SMALLINT NOT NULL CHECK (baseline_abundance BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ecology_site_chunk ON ecology_site (chunk_id, site_category);
CREATE INDEX idx_ecology_site_world_kind ON ecology_site (world_id, site_kind);
