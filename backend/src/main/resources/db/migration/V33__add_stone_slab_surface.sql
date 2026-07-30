-- A flat stone slab is the earliest durable writing surface: heavy and awkward to
-- carry, but permanent in a way bark and hide are not. It is split from rock in
-- stony highland terrain. Documentation the Chronicle means to keep goes on stone.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('stone_slab','Stone slab','MATERIAL',2600,1800,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
