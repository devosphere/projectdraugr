-- V106: soapstone carved into a bowl (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (stone family) with a real use: soapstone is soft enough to carve and holds heat, so it is
-- worked into a bowl — a real container. Gathered as a mineral; carved with a blade into a soapstone_bowl that
-- functions as a container (capacity auto-wired via container_capacity_default).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('soapstone_piece', 'Soapstone',      'MATERIAL',  2000, 1000, TRUE,  FALSE, 0),
('soapstone_bowl',  'Soapstone bowl', 'CONTAINER', 1400, 1600, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('soapstone_bowl', 1500, 1500)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('soapstone_piece', 'Soapstone', 'HIGHLAND,MOUNTAIN', 0.40, 'STRIKING', 1, 2, 'A soft, heat-holding stone that carves easily.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('soapstone_piece', 'MINERAL',   'quarry soapstone in highland/mountain rock'),
('soapstone_bowl',  'TECHNIQUE', 'carved from a piece of soapstone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_soapstone_bowl','Carve a soapstone bowl','soapstone_bowl',1,1,'CUTTING',FALSE,FALSE,80,'items','CRAFT','soapstone bowl,carve a soapstone bowl,carve a bowl,carve a stone bowl,make a soapstone bowl,hollow a bowl', 'You hollow and smooth the soft soapstone patiently into a deep, heavy bowl that will hold food or water and take heat without cracking.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_soapstone_bowl','soapstone_piece',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_soapstone_bowl','bowl'),('carve_soapstone_bowl','soapstone')
ON CONFLICT DO NOTHING;
