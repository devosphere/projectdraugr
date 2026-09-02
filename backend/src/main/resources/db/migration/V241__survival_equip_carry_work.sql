-- V241 — story #95, slice 5: carrying and work wear. Eight bare-hand carry/work items worn on the back or waist.
-- fibre_sling is MADE by "make a fibre carry strap" (not "sling", which the Java EQUIP intent owns) and
-- cordage_tool_belt by "make a cordage tool strap" (not "belt", owned by CRAFT_BELT). Canonical phrases lead with
-- 'make' (CRAFT). Checked with the two-axis matcher sim.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('fibre_sling',        'Fibre carry sling',  'CLOTHING', 120, 350,  FALSE, TRUE, 0, 0),
('reed_back_panel',    'Reed back panel',    'CLOTHING', 300, 1200, FALSE, TRUE, 3, 3),
('bark_back_panel',    'Bark back panel',    'CLOTHING', 350, 1300, FALSE, TRUE, 3, 6),
('grass_carry_bundle', 'Grass carry bundle', 'CLOTHING', 180, 1000, FALSE, TRUE, 2, 1),
('reed_tool_loop',     'Reed tool loop',     'CLOTHING', 80,  250,  FALSE, TRUE, 0, 0),
('cordage_tool_belt',  'Cordage tool belt',  'CLOTHING', 130, 350,  FALSE, TRUE, 0, 0),
('simple_knife_sheath','Simple knife sheath','CLOTHING', 100, 300,  FALSE, TRUE, 0, 1),
('stone_tool_wrap',    'Stone tool wrap',    'CLOTHING', 90,  300,  FALSE, TRUE, 0, 1)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fibre_sling','TORSO','CARRIED'),
('reed_back_panel','BACK','CARRIED'),
('bark_back_panel','BACK','CARRIED'),
('grass_carry_bundle','BACK','CARRIED'),
('reed_tool_loop','WAIST','ATTACHED'),
('cordage_tool_belt','WAIST','ATTACHED'),
('simple_knife_sheath','WAIST','ATTACHED'),
('stone_tool_wrap','WAIST','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fibre_sling','TECHNIQUE','a fibre strap slung across the body to carry a load'),
('reed_back_panel','TECHNIQUE','a woven reed panel to bear a load off the back'),
('bark_back_panel','TECHNIQUE','a bark panel to bear a load off the back'),
('grass_carry_bundle','TECHNIQUE','a grass carry-bundle tied off the back'),
('reed_tool_loop','TECHNIQUE','a reed loop to hang a tool from the waist'),
('cordage_tool_belt','TECHNIQUE','a cordage strap of loops to carry tools at the waist'),
('simple_knife_sheath','TECHNIQUE','a folded sheath to carry a blade at the waist'),
('stone_tool_wrap','TECHNIQUE','a fibre wrap to carry stone tools without cutting the hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_fibre_sling','Wind a fibre carry strap','fibre_sling',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','fibre carry strap,make a fibre carry strap,carry strap', 'You wind fibre into a broad strap to sling a load across the body.', 'VERIFIED', now()),
('make_reed_back_panel','Weave a reed back panel','reed_back_panel',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','reed back panel,make a reed back panel', 'You weave a stiff reed panel to bear a load off your back.', 'VERIFIED', now()),
('make_bark_back_panel','Make a bark back panel','bark_back_panel',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','bark back panel,make a bark back panel', 'You bind a bark panel to bear a load off your back.', 'VERIFIED', now()),
('make_grass_carry_bundle','Tie a grass carry bundle','grass_carry_bundle',1,1,NULL,FALSE,FALSE,18,'items','CRAFT','grass carry bundle,make a grass carry bundle', 'You bind dry grass and fibre into a carry-bundle for the back.', 'VERIFIED', now()),
('make_reed_tool_loop','Plait a reed tool loop','reed_tool_loop',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','reed tool loop,make a reed tool loop', 'You plait a reed loop to hang a tool from your waist.', 'VERIFIED', now()),
('make_cordage_tool_belt','Tie a cordage tool strap','cordage_tool_belt',1,1,NULL,FALSE,FALSE,14,'items','CRAFT','cordage tool strap,make a cordage tool strap,tool strap', 'You tie a cordage strap of loops to carry tools at the waist.', 'VERIFIED', now()),
('make_knife_sheath','Fold a simple knife sheath','simple_knife_sheath',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','blade sheath,make a blade sheath,make a simple sheath,fold a blade sheath', 'You fold and bind a sheath to carry a blade safely at the waist.', 'VERIFIED', now()),
('make_stone_tool_wrap','Wind a stone tool wrap','stone_tool_wrap',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','stone tool wrap,make a stone tool wrap', 'You wind a fibre wrap to carry sharp stone tools without cutting your hand.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_fibre_sling','plant_fiber',2),
('make_reed_back_panel','reed_bundle',2),
('make_bark_back_panel','bark_sheet',3),
('make_grass_carry_bundle','dry_grass_bundle',2),('make_grass_carry_bundle','plant_fiber',1),
('make_reed_tool_loop','reed_bundle',1),
('make_cordage_tool_belt','fiber_cordage',1),('make_cordage_tool_belt','plant_fiber',1),
('make_knife_sheath','bark_sheet',1),('make_knife_sheath','plant_fiber',1),
('make_stone_tool_wrap','plant_fiber',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_fibre_sling','carry strap'),
('make_reed_back_panel','back panel'),
('make_bark_back_panel','back panel'),
('make_grass_carry_bundle','carry bundle'),
('make_reed_tool_loop','tool loop'),
('make_cordage_tool_belt','tool strap'),
('make_knife_sheath','blade sheath'),('make_knife_sheath','sheath'),
('make_stone_tool_wrap','tool wrap')
ON CONFLICT DO NOTHING;
