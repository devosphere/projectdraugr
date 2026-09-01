-- V239 — story #95, slice 3: belts and bracers. Three waist belts and two pairs of forearm bracers (left/right
-- equip independently). Belts are MADE by "make a <material> girdle" (not "belt"): the word 'belt' is owned by the
-- Java CRAFT_BELT intent, so a fibre/bark/cordage belt needs a non-'belt' phrase to route to its own recipe. Paired
-- bracers carry side-specific FOREARM_LEFT/RIGHT slots and distinct left/right keywords so one cannot fill both
-- sides. Canonical phrases lead with 'make' (CRAFT). Checked with the two-axis matcher sim.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('fibre_belt',        'Fibre belt',        'CLOTHING', 80,  200, FALSE, TRUE, 1, 0),
('bark_belt',         'Bark belt',         'CLOTHING', 90,  220, FALSE, TRUE, 1, 1),
('cordage_belt',      'Cordage belt',      'CLOTHING', 90,  200, FALSE, TRUE, 1, 0),
('reed_bracer_left',  'Reed bracer (left)',  'CLOTHING', 120, 350, FALSE, TRUE, 2, 2),
('reed_bracer_right', 'Reed bracer (right)', 'CLOTHING', 120, 350, FALSE, TRUE, 2, 2),
('bark_bracer_left',  'Bark bracer (left)',  'CLOTHING', 110, 340, FALSE, TRUE, 2, 3),
('bark_bracer_right', 'Bark bracer (right)', 'CLOTHING', 110, 340, FALSE, TRUE, 2, 3)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fibre_belt','WAIST','CLOTHING'),
('bark_belt','WAIST','CLOTHING'),
('cordage_belt','WAIST','CLOTHING'),
('reed_bracer_left','FOREARM_LEFT','PROTECTION'),
('reed_bracer_right','FOREARM_RIGHT','PROTECTION'),
('bark_bracer_left','FOREARM_LEFT','PROTECTION'),
('bark_bracer_right','FOREARM_RIGHT','PROTECTION')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fibre_belt','TECHNIQUE','knotted from plant fibre by hand'),
('bark_belt','TECHNIQUE','cut and bound from a bark strip by hand'),
('cordage_belt','TECHNIQUE','tied from a length of cordage by hand'),
('reed_bracer_left','TECHNIQUE','plaited from reed round the left forearm by hand'),
('reed_bracer_right','TECHNIQUE','plaited from reed round the right forearm by hand'),
('bark_bracer_left','TECHNIQUE','a bark guard bound to the left forearm by hand'),
('bark_bracer_right','TECHNIQUE','a bark guard bound to the right forearm by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_fibre_belt','Knot a fibre girdle','fibre_belt',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','fibre girdle,make a fibre girdle,knot a fibre girdle', 'You knot plant fibre into a girdle to belt at the waist.', 'VERIFIED', now()),
('make_bark_belt','Bind a bark girdle','bark_belt',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','bark girdle,make a bark girdle,bind a bark girdle', 'You cut and bind a strip of bark into a stiff girdle for the waist.', 'VERIFIED', now()),
('make_cordage_belt','Tie a cordage girdle','cordage_belt',1,1,NULL,FALSE,FALSE,8,'items','CRAFT','cordage girdle,make a cordage girdle,tie a cordage girdle', 'You tie a length of cordage into a simple girdle at the waist.', 'VERIFIED', now()),
('make_reed_bracer_left','Plait a left reed bracer','reed_bracer_left',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','make a left reed bracer,left reed bracer', 'You plait reed round the left forearm into a light bracer.', 'VERIFIED', now()),
('make_reed_bracer_right','Plait a right reed bracer','reed_bracer_right',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','make a right reed bracer,right reed bracer', 'You plait reed round the right forearm into a light bracer.', 'VERIFIED', now()),
('make_bark_bracer_left','Bind a left bark bracer','bark_bracer_left',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a left bark bracer,left bark bracer', 'You bind a bark guard to the left forearm.', 'VERIFIED', now()),
('make_bark_bracer_right','Bind a right bark bracer','bark_bracer_right',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a right bark bracer,right bark bracer', 'You bind a bark guard to the right forearm.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_fibre_belt','plant_fiber',2),
('make_bark_belt','bark_sheet',2),
('make_cordage_belt','fiber_cordage',1),
('make_reed_bracer_left','reed_bundle',1),
('make_reed_bracer_right','reed_bundle',1),
('make_bark_bracer_left','bark_sheet',1),
('make_bark_bracer_right','bark_sheet',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_fibre_belt','girdle'),
('make_bark_belt','girdle'),
('make_cordage_belt','girdle'),
('make_reed_bracer_left','reed bracer'),('make_reed_bracer_left','bracer'),
('make_reed_bracer_right','reed bracer'),('make_reed_bracer_right','bracer'),
('make_bark_bracer_left','bark bracer'),('make_bark_bracer_left','bracer'),
('make_bark_bracer_right','bark bracer'),('make_bark_bracer_right','bracer')
ON CONFLICT DO NOTHING;
