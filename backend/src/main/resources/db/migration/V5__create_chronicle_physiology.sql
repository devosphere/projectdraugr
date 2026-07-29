CREATE TABLE chronicle_physiology (
    chronicle_id UUID PRIMARY KEY REFERENCES chronicle(id),
    last_metabolic_update TIMESTAMPTZ NOT NULL,
    hours_without_food NUMERIC(10, 4) NOT NULL DEFAULT 0 CHECK (hours_without_food >= 0),
    hours_without_water NUMERIC(10, 4) NOT NULL DEFAULT 0 CHECK (hours_without_water >= 0),
    energy_level SMALLINT NOT NULL DEFAULT 85 CHECK (energy_level BETWEEN 0 AND 100),
    core_temperature_c NUMERIC(4, 2) NOT NULL DEFAULT 37.00 CHECK (core_temperature_c BETWEEN 20 AND 45),
    wetness_level SMALLINT NOT NULL DEFAULT 25 CHECK (wetness_level BETWEEN 0 AND 100),
    bladder_level SMALLINT NOT NULL DEFAULT 20 CHECK (bladder_level BETWEEN 0 AND 100),
    bowel_level SMALLINT NOT NULL DEFAULT 10 CHECK (bowel_level BETWEEN 0 AND 100),
    hygiene_level SMALLINT NOT NULL DEFAULT 75 CHECK (hygiene_level BETWEEN 0 AND 100)
);
