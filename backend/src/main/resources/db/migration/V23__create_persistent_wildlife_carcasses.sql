ALTER TABLE wildlife_population DROP CONSTRAINT wildlife_population_population_count_check;
ALTER TABLE wildlife_population ADD CONSTRAINT wildlife_population_population_count_check CHECK (population_count >= 0 AND population_count <= carrying_capacity);

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('raw_game_meat','Raw game meat','FOOD',650,850,FALSE,FALSE),
('animal_hide','Animal hide','MATERIAL',950,1800,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;

CREATE TABLE wildlife_carcass (
    object_id UUID PRIMARY KEY REFERENCES world_object(id),
    source_population_id UUID NOT NULL REFERENCES wildlife_population(id),
    species_key VARCHAR(80) NOT NULL,
    remaining_meat_units SMALLINT NOT NULL CHECK (remaining_meat_units >= 0),
    hide_available BOOLEAN NOT NULL DEFAULT TRUE,
    killed_by_action_id UUID NOT NULL,
    died_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_wildlife_carcass_location ON wildlife_carcass (object_id);
