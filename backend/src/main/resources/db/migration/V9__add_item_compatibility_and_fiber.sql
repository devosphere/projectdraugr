CREATE TABLE item_equipment_compatibility (
    item_key VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    body_position VARCHAR(40) NOT NULL,
    layer VARCHAR(30) NOT NULL,
    PRIMARY KEY (item_key, body_position, layer)
);

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('plant_fiber','Plant fiber bundle','MATERIAL',80,250,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('woven_basket','BACK','CARRIED'), ('woven_basket','HAND_LEFT','CARRIED'), ('woven_basket','HAND_RIGHT','CARRIED')
ON CONFLICT DO NOTHING;
