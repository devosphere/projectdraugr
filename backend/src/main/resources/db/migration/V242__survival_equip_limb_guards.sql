-- V242 — story #95, slice 7: limb protection and support. Four left/right pairs worn on the knee or lower leg, each
-- side its own slot and distinct left/right keyword: bark splints, fibre knee wraps, reed knee pads and bark shin
-- guards. Same equippable-garment pattern; canonical phrases lead with 'make' (CRAFT). Checked with the two-axis
-- matcher sim. (The paired worn bark splints are limb SUPPORT; the existing bark_splint_set remains the first-aid
-- consumable that TREAT_WOUND uses to set a fracture.)
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('bark_splint_left',    'Bark splint (left)',     'CLOTHING', 110, 340, FALSE, TRUE, 1, 2),
('bark_splint_right',   'Bark splint (right)',    'CLOTHING', 110, 340, FALSE, TRUE, 1, 2),
('fibre_knee_wrap_left','Fibre knee wrap (left)', 'CLOTHING', 70,  200, FALSE, TRUE, 3, 0),
('fibre_knee_wrap_right','Fibre knee wrap (right)','CLOTHING',70,  200, FALSE, TRUE, 3, 0),
('reed_knee_pad_left',  'Reed knee pad (left)',   'CLOTHING', 90,  280, FALSE, TRUE, 2, 1),
('reed_knee_pad_right', 'Reed knee pad (right)',  'CLOTHING', 90,  280, FALSE, TRUE, 2, 1),
('bark_shin_guard_left','Bark shin guard (left)', 'CLOTHING', 180, 500, FALSE, TRUE, 2, 3),
('bark_shin_guard_right','Bark shin guard (right)','CLOTHING',180, 500, FALSE, TRUE, 2, 3)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bark_splint_left','LOWER_LEG_LEFT','PROTECTION'),
('bark_splint_right','LOWER_LEG_RIGHT','PROTECTION'),
('fibre_knee_wrap_left','KNEE_LEFT','CLOTHING'),
('fibre_knee_wrap_right','KNEE_RIGHT','CLOTHING'),
('reed_knee_pad_left','KNEE_LEFT','PROTECTION'),
('reed_knee_pad_right','KNEE_RIGHT','PROTECTION'),
('bark_shin_guard_left','LOWER_LEG_LEFT','PROTECTION'),
('bark_shin_guard_right','LOWER_LEG_RIGHT','PROTECTION')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bark_splint_left','TECHNIQUE','a bark stay bound to the left lower leg by hand'),
('bark_splint_right','TECHNIQUE','a bark stay bound to the right lower leg by hand'),
('fibre_knee_wrap_left','TECHNIQUE','wound from plant fibre round the left knee by hand'),
('fibre_knee_wrap_right','TECHNIQUE','wound from plant fibre round the right knee by hand'),
('reed_knee_pad_left','TECHNIQUE','a plaited reed pad bound over the left knee by hand'),
('reed_knee_pad_right','TECHNIQUE','a plaited reed pad bound over the right knee by hand'),
('bark_shin_guard_left','TECHNIQUE','a bark guard bound over the left shin by hand'),
('bark_shin_guard_right','TECHNIQUE','a bark guard bound over the right shin by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_bark_splint_left','Bind a left bark splint','bark_splint_left',1,1,NULL,FALSE,FALSE,14,'items','CRAFT','make a left bark splint,left bark splint', 'You bind a bark stay along the left lower leg to steady it.', 'VERIFIED', now()),
('make_bark_splint_right','Bind a right bark splint','bark_splint_right',1,1,NULL,FALSE,FALSE,14,'items','CRAFT','make a right bark splint,right bark splint', 'You bind a bark stay along the right lower leg to steady it.', 'VERIFIED', now()),
('make_fibre_knee_wrap_left','Wind a left fibre knee wrap','fibre_knee_wrap_left',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','make a left fibre knee wrap,left fibre knee wrap', 'You wind fibre round the left knee for warmth and support.', 'VERIFIED', now()),
('make_fibre_knee_wrap_right','Wind a right fibre knee wrap','fibre_knee_wrap_right',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','make a right fibre knee wrap,right fibre knee wrap', 'You wind fibre round the right knee for warmth and support.', 'VERIFIED', now()),
('make_reed_knee_pad_left','Bind a left reed knee pad','reed_knee_pad_left',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a left reed knee pad,left reed knee pad', 'You bind a plaited reed pad over the left knee against hard ground.', 'VERIFIED', now()),
('make_reed_knee_pad_right','Bind a right reed knee pad','reed_knee_pad_right',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','make a right reed knee pad,right reed knee pad', 'You bind a plaited reed pad over the right knee against hard ground.', 'VERIFIED', now()),
('make_bark_shin_guard_left','Bind a left bark shin guard','bark_shin_guard_left',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','make a left bark shin guard,left bark shin guard', 'You bind a bark guard over the left shin against knocks and thorns.', 'VERIFIED', now()),
('make_bark_shin_guard_right','Bind a right bark shin guard','bark_shin_guard_right',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','make a right bark shin guard,right bark shin guard', 'You bind a bark guard over the right shin against knocks and thorns.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_bark_splint_left','bark_sheet',1),('make_bark_splint_left','plant_fiber',1),
('make_bark_splint_right','bark_sheet',1),('make_bark_splint_right','plant_fiber',1),
('make_fibre_knee_wrap_left','plant_fiber',2),
('make_fibre_knee_wrap_right','plant_fiber',2),
('make_reed_knee_pad_left','reed_bundle',1),
('make_reed_knee_pad_right','reed_bundle',1),
('make_bark_shin_guard_left','bark_sheet',2),
('make_bark_shin_guard_right','bark_sheet',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_bark_splint_left','bark splint'),('make_bark_splint_left','splint'),
('make_bark_splint_right','bark splint'),('make_bark_splint_right','splint'),
('make_fibre_knee_wrap_left','knee wrap'),
('make_fibre_knee_wrap_right','knee wrap'),
('make_reed_knee_pad_left','knee pad'),
('make_reed_knee_pad_right','knee pad'),
('make_bark_shin_guard_left','shin guard'),
('make_bark_shin_guard_right','shin guard')
ON CONFLICT DO NOTHING;
