-- Fire does not come from stones and a single branch. Real friction fire needs a
-- kit: a hearth board and a spindle to generate an ember, and a tinder nest fine
-- and dry enough to catch it. Fuel alone never ignites. These are the primitive
-- friction-fire ("bow/hand drill") materials the chronicle must make before a fire.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('hearth_board','Hearth board','TOOL',300,700,FALSE,FALSE),
('fire_spindle','Fire spindle','TOOL',120,300,FALSE,FALSE),
('tinder_nest','Tinder nest','MATERIAL',40,220,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
