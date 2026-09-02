-- V249 — story #96, slice 6: legs. Four left/right pairs (rawhide/leather shin guards, rawhide/leather knee guards),
-- each side its own LOWER_LEG/KNEE slot and keyword. Rawhide pieces are category PROCESS; leather CRAFT. CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('rawhide_shin_guard_left',  'Rawhide shin guard (left)',  'CLOTHING', 180, 500, FALSE, TRUE, 2, 3),
('rawhide_shin_guard_right', 'Rawhide shin guard (right)', 'CLOTHING', 180, 500, FALSE, TRUE, 2, 3),
('leather_shin_guard_left',  'Leather shin guard (left)',  'CLOTHING', 190, 520, FALSE, TRUE, 3, 8),
('leather_shin_guard_right', 'Leather shin guard (right)', 'CLOTHING', 190, 520, FALSE, TRUE, 3, 8),
('rawhide_knee_guard_left',  'Rawhide knee guard (left)',  'CLOTHING', 150, 420, FALSE, TRUE, 2, 3),
('rawhide_knee_guard_right', 'Rawhide knee guard (right)', 'CLOTHING', 150, 420, FALSE, TRUE, 2, 3),
('leather_knee_guard_left',  'Leather knee guard (left)',  'CLOTHING', 160, 440, FALSE, TRUE, 3, 8),
('leather_knee_guard_right', 'Leather knee guard (right)', 'CLOTHING', 160, 440, FALSE, TRUE, 3, 8)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('rawhide_shin_guard_left','LOWER_LEG_LEFT','PROTECTION'),('rawhide_shin_guard_right','LOWER_LEG_RIGHT','PROTECTION'),
('leather_shin_guard_left','LOWER_LEG_LEFT','PROTECTION'),('leather_shin_guard_right','LOWER_LEG_RIGHT','PROTECTION'),
('rawhide_knee_guard_left','KNEE_LEFT','PROTECTION'),('rawhide_knee_guard_right','KNEE_RIGHT','PROTECTION'),
('leather_knee_guard_left','KNEE_LEFT','PROTECTION'),('leather_knee_guard_right','KNEE_RIGHT','PROTECTION')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('rawhide_shin_guard_left','TECHNIQUE','a stiff rawhide guard laced over the left shin'),
('rawhide_shin_guard_right','TECHNIQUE','a stiff rawhide guard laced over the right shin'),
('leather_shin_guard_left','TECHNIQUE','a leather guard laced over the left shin'),
('leather_shin_guard_right','TECHNIQUE','a leather guard laced over the right shin'),
('rawhide_knee_guard_left','TECHNIQUE','a stiff rawhide cup laced over the left knee'),
('rawhide_knee_guard_right','TECHNIQUE','a stiff rawhide cup laced over the right knee'),
('leather_knee_guard_left','TECHNIQUE','a leather cup laced over the left knee'),
('leather_knee_guard_right','TECHNIQUE','a leather cup laced over the right knee')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_rawhide_shin_guard_left','Lace a left rawhide shin guard','rawhide_shin_guard_left',1,1,'CUTTING',FALSE,FALSE,20,'items','PROCESS','make a left rawhide shin guard,left rawhide shin guard', 'You lace a stiff rawhide guard over the left shin.', 'VERIFIED', now()),
('make_rawhide_shin_guard_right','Lace a right rawhide shin guard','rawhide_shin_guard_right',1,1,'CUTTING',FALSE,FALSE,20,'items','PROCESS','make a right rawhide shin guard,right rawhide shin guard', 'You lace a stiff rawhide guard over the right shin.', 'VERIFIED', now()),
('make_leather_shin_guard_left','Lace a left leather shin guard','leather_shin_guard_left',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a left leather shin guard,left leather shin guard', 'You lace a leather guard over the left shin.', 'VERIFIED', now()),
('make_leather_shin_guard_right','Lace a right leather shin guard','leather_shin_guard_right',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a right leather shin guard,right leather shin guard', 'You lace a leather guard over the right shin.', 'VERIFIED', now()),
('make_rawhide_knee_guard_left','Lace a left rawhide knee guard','rawhide_knee_guard_left',1,1,'CUTTING',FALSE,FALSE,18,'items','PROCESS','make a left rawhide knee guard,left rawhide knee guard', 'You lace a stiff rawhide cup over the left knee.', 'VERIFIED', now()),
('make_rawhide_knee_guard_right','Lace a right rawhide knee guard','rawhide_knee_guard_right',1,1,'CUTTING',FALSE,FALSE,18,'items','PROCESS','make a right rawhide knee guard,right rawhide knee guard', 'You lace a stiff rawhide cup over the right knee.', 'VERIFIED', now()),
('make_leather_knee_guard_left','Lace a left leather knee guard','leather_knee_guard_left',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a left leather knee guard,left leather knee guard', 'You lace a leather cup over the left knee.', 'VERIFIED', now()),
('make_leather_knee_guard_right','Lace a right leather knee guard','leather_knee_guard_right',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a right leather knee guard,right leather knee guard', 'You lace a leather cup over the right knee.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_rawhide_shin_guard_left','rawhide',1),('make_rawhide_shin_guard_right','rawhide',1),
('make_leather_shin_guard_left','tanned_leather',1),('make_leather_shin_guard_right','tanned_leather',1),
('make_rawhide_knee_guard_left','rawhide',1),('make_rawhide_knee_guard_right','rawhide',1),
('make_leather_knee_guard_left','tanned_leather',1),('make_leather_knee_guard_right','tanned_leather',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_rawhide_shin_guard_left','shin guard'),('make_rawhide_shin_guard_right','shin guard'),
('make_leather_shin_guard_left','shin guard'),('make_leather_shin_guard_right','shin guard'),
('make_rawhide_knee_guard_left','knee guard'),('make_rawhide_knee_guard_right','knee guard'),
('make_leather_knee_guard_left','knee guard'),('make_leather_knee_guard_right','knee guard')
ON CONFLICT DO NOTHING;
