INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('wild_berries','Wild berries','FOOD',180,250,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
