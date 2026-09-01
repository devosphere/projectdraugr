-- V240 — story #95, slice 4: hand wraps, ankle wraps and sandals. Two paired hand wraps, two paired ankle wraps and
-- two paired bark sandals (each side its own FOOT/HAND slot and distinct left/right keyword), plus a declared reed
-- sandal pair that fills both feet. These are the side-independent counterparts of V110's fibre_hand_wrap /
-- grass_ankle_wrap and V230's bark_sandals pairs; the singular "make a left/right X" phrases do not collide with those
-- pairs' plural keywords. Canonical phrases lead with 'make' (CRAFT). Checked with the two-axis matcher sim.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('fibre_hand_wrap_left',  'Fibre hand wrap (left)',  'CLOTHING', 60, 150, FALSE, TRUE, 4, 0),
('fibre_hand_wrap_right', 'Fibre hand wrap (right)', 'CLOTHING', 60, 150, FALSE, TRUE, 4, 0),
('grass_ankle_wrap_left', 'Grass ankle wrap (left)', 'CLOTHING', 90, 300, FALSE, TRUE, 6, 0),
('grass_ankle_wrap_right','Grass ankle wrap (right)','CLOTHING', 90, 300, FALSE, TRUE, 6, 0),
('bark_sandal_left',      'Bark sandal (left)',      'CLOTHING', 110, 320, FALSE, TRUE, 4, 4),
('bark_sandal_right',     'Bark sandal (right)',     'CLOTHING', 110, 320, FALSE, TRUE, 4, 4),
('reed_sandal_pair',      'Reed sandals (pair)',     'CLOTHING', 220, 640, FALSE, TRUE, 4, 3)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fibre_hand_wrap_left','HAND_LEFT','CLOTHING'),
('fibre_hand_wrap_right','HAND_RIGHT','CLOTHING'),
('grass_ankle_wrap_left','FOOT_LEFT','CLOTHING'),
('grass_ankle_wrap_right','FOOT_RIGHT','CLOTHING'),
('bark_sandal_left','FOOT_LEFT','CLOTHING'),
('bark_sandal_right','FOOT_RIGHT','CLOTHING'),
('reed_sandal_pair','FOOT_LEFT','CLOTHING'),('reed_sandal_pair','FOOT_RIGHT','CLOTHING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fibre_hand_wrap_left','TECHNIQUE','wound from plant fibre round the left hand by hand'),
('fibre_hand_wrap_right','TECHNIQUE','wound from plant fibre round the right hand by hand'),
('grass_ankle_wrap_left','TECHNIQUE','wound from dry grass and fibre round the left ankle by hand'),
('grass_ankle_wrap_right','TECHNIQUE','wound from dry grass and fibre round the right ankle by hand'),
('bark_sandal_left','TECHNIQUE','a bark sole laced to the left foot by hand'),
('bark_sandal_right','TECHNIQUE','a bark sole laced to the right foot by hand'),
('reed_sandal_pair','TECHNIQUE','a pair of reed soles laced to the feet by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_fibre_hand_wrap_left','Wind a left fibre hand wrap','fibre_hand_wrap_left',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','make a left fibre hand wrap,left fibre hand wrap', 'You wind fibre round the left hand and knuckles for warmth and a little protection.', 'VERIFIED', now()),
('make_fibre_hand_wrap_right','Wind a right fibre hand wrap','fibre_hand_wrap_right',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','make a right fibre hand wrap,right fibre hand wrap', 'You wind fibre round the right hand and knuckles for warmth and a little protection.', 'VERIFIED', now()),
('make_grass_ankle_wrap_left','Wind a left grass ankle wrap','grass_ankle_wrap_left',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a left grass ankle wrap,left grass ankle wrap', 'You wind dry grass and fibre round the left ankle and calf against the cold and wet.', 'VERIFIED', now()),
('make_grass_ankle_wrap_right','Wind a right grass ankle wrap','grass_ankle_wrap_right',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a right grass ankle wrap,right grass ankle wrap', 'You wind dry grass and fibre round the right ankle and calf against the cold and wet.', 'VERIFIED', now()),
('make_bark_sandal_left','Lace a left bark sandal','bark_sandal_left',1,1,NULL,FALSE,FALSE,14,'items','CRAFT','make a left bark sandal,lace a left bark sandal,left bark sandal', 'You cut a sole from stiff bark and lace it to the left foot.', 'VERIFIED', now()),
('make_bark_sandal_right','Lace a right bark sandal','bark_sandal_right',1,1,NULL,FALSE,FALSE,14,'items','CRAFT','make a right bark sandal,lace a right bark sandal,right bark sandal', 'You cut a sole from stiff bark and lace it to the right foot.', 'VERIFIED', now()),
('make_reed_sandal_pair','Lace a pair of reed sandals','reed_sandal_pair',1,1,NULL,FALSE,FALSE,20,'items','CRAFT','reed sandal pair,make reed sandals,make a pair of reed sandals,pair of reed sandals', 'You cut soles from reed and lace a pair to your feet.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_fibre_hand_wrap_left','plant_fiber',2),
('make_fibre_hand_wrap_right','plant_fiber',2),
('make_grass_ankle_wrap_left','dry_grass_bundle',1),('make_grass_ankle_wrap_left','plant_fiber',1),
('make_grass_ankle_wrap_right','dry_grass_bundle',1),('make_grass_ankle_wrap_right','plant_fiber',1),
('make_bark_sandal_left','bark_sheet',1),('make_bark_sandal_left','plant_fiber',1),
('make_bark_sandal_right','bark_sheet',1),('make_bark_sandal_right','plant_fiber',1),
('make_reed_sandal_pair','reed_bundle',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_fibre_hand_wrap_left','hand wrap'),('make_fibre_hand_wrap_left','fibre hand wrap'),
('make_fibre_hand_wrap_right','hand wrap'),('make_fibre_hand_wrap_right','fibre hand wrap'),
('make_grass_ankle_wrap_left','ankle wrap'),('make_grass_ankle_wrap_left','grass ankle wrap'),
('make_grass_ankle_wrap_right','ankle wrap'),('make_grass_ankle_wrap_right','grass ankle wrap'),
('make_bark_sandal_left','bark sandal'),('make_bark_sandal_left','sandal'),
('make_bark_sandal_right','bark sandal'),('make_bark_sandal_right','sandal'),
('make_reed_sandal_pair','reed sandal'),('make_reed_sandal_pair','sandal')
ON CONFLICT DO NOTHING;
