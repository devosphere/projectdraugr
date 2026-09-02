-- V246 — story #96, slice 3: leather carry gear. Six equippable carry pieces cut and sewn from leather/hide.
-- leather_water_bottle_sling is made by "make a leather bottle carrier" (not "sling", owned by EQUIP). The quiver
-- keywords are longer than the existing weave_quiver/sew_*_quiver so they win on longest-match within CRAFT. All are
-- CRAFT (no rawhide), tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('leather_backpack',           'Leather backpack',           'CLOTHING', 900, 4000, FALSE, TRUE, 2, 8),
('hide_backpack',              'Hide backpack',              'CLOTHING', 850, 4000, FALSE, TRUE, 2, 6),
('leather_quiver',             'Leather quiver',             'CLOTHING', 350, 1200, FALSE, TRUE, 1, 8),
('hide_quiver',                'Hide quiver',                'CLOTHING', 400, 1300, FALSE, TRUE, 1, 6),
('leather_tool_roll',          'Leather tool roll',          'CLOTHING', 300, 1000, FALSE, TRUE, 1, 8),
('leather_water_bottle_sling', 'Leather water-bottle sling', 'CLOTHING', 200, 700,  FALSE, TRUE, 1, 8)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('leather_backpack','BACK','CARRIED'),
('hide_backpack','BACK','CARRIED'),
('leather_quiver','BACK','ATTACHED'),
('hide_quiver','BACK','ATTACHED'),
('leather_tool_roll','WAIST','CARRIED'),
('leather_water_bottle_sling','TORSO','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('leather_backpack','TECHNIQUE','cut and sewn from tanned leather into a back-borne pack'),
('hide_backpack','TECHNIQUE','cut from hide and laced into a back-borne pack'),
('leather_quiver','TECHNIQUE','cut and sewn from tanned leather to carry arrows'),
('hide_quiver','TECHNIQUE','cut from hide and laced to carry arrows'),
('leather_tool_roll','TECHNIQUE','a leather roll of pockets to carry tools'),
('leather_water_bottle_sling','TECHNIQUE','a leather carrier slung to bear a water bottle')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_leather_backpack','Sew a leather backpack','leather_backpack',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','leather backpack,make a leather backpack,sew a leather backpack', 'You cut and sew tanned leather into a back-borne pack with straps.', 'VERIFIED', now()),
('make_hide_backpack','Lace a hide backpack','hide_backpack',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','hide backpack,make a hide backpack,lace a hide backpack', 'You cut hide and lace it into a rough back-borne pack.', 'VERIFIED', now()),
('make_leather_quiver','Sew a leather quiver','leather_quiver',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','leather quiver,make a leather quiver,sew a leather quiver', 'You cut and sew tanned leather into a quiver to carry arrows.', 'VERIFIED', now()),
('make_hide_quiver','Lace a hide quiver','hide_quiver',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','hide quiver,make a hide quiver,lace a hide quiver', 'You cut hide and lace it into a quiver for arrows.', 'VERIFIED', now()),
('make_leather_tool_roll','Sew a leather tool roll','leather_tool_roll',1,1,'CUTTING',FALSE,FALSE,35,'items','CRAFT','leather tool roll,make a leather tool roll,tool roll', 'You sew a leather roll of pockets to carry small tools.', 'VERIFIED', now()),
('make_leather_bottle_sling','Sew a leather bottle carrier','leather_water_bottle_sling',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','leather bottle carrier,make a leather bottle carrier,bottle carrier', 'You cut and sew a leather carrier to sling a water bottle at the side.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_leather_backpack','tanned_leather',2),
('make_hide_backpack','animal_hide',1),
('make_leather_quiver','tanned_leather',1),
('make_hide_quiver','animal_hide',1),
('make_leather_tool_roll','tanned_leather',1),
('make_leather_bottle_sling','tanned_leather',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_leather_backpack','backpack'),
('make_hide_backpack','backpack'),
('make_leather_quiver','quiver'),('make_leather_quiver','leather quiver'),
('make_hide_quiver','quiver'),('make_hide_quiver','hide quiver'),
('make_leather_tool_roll','tool roll'),
('make_leather_bottle_sling','bottle carrier')
ON CONFLICT DO NOTHING;
