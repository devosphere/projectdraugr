CREATE TABLE wildlife_population (
    id UUID PRIMARY KEY,
    site_id UUID NOT NULL UNIQUE REFERENCES ecology_site(id),
    species_key VARCHAR(80) NOT NULL,
    ecological_role VARCHAR(30) NOT NULL,
    activity_cycle VARCHAR(20) NOT NULL,
    population_count SMALLINT NOT NULL CHECK (population_count > 0),
    carrying_capacity SMALLINT NOT NULL CHECK (carrying_capacity > 0),
    behavior_state VARCHAR(30) NOT NULL,
    last_simulated_at TIMESTAMPTZ NOT NULL,
    CHECK (population_count <= carrying_capacity)
);

CREATE INDEX idx_wildlife_population_species ON wildlife_population(species_key);
