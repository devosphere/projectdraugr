INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('dry_branch','Dry branch','FUEL',350,900,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;

CREATE TABLE fire_state (
    construction_id UUID PRIMARY KEY REFERENCES construction_project(object_id),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    fuel_minutes INTEGER NOT NULL DEFAULT 0 CHECK (fuel_minutes >= 0),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
