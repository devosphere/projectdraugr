INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('cooked_game_meat','Cooked game meat','FOOD',540,700,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
