-- V247 — story #96, slice 4: arms and hands. Six left/right pairs (rawhide bracers, leather bracers, leather elbow
-- guards, hide gloves, leather gloves, fur mittens), each side its own slot and distinct left/right keyword. rawhide
-- bracers are category PROCESS ('rawhide' is a PROCESS term); the rest are CRAFT. tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('rawhide_bracer_left',      'Rawhide bracer (left)',       'CLOTHING', 120, 350, FALSE, TRUE, 2, 3),
('rawhide_bracer_right',     'Rawhide bracer (right)',      'CLOTHING', 120, 350, FALSE, TRUE, 2, 3),
('leather_bracer_left',      'Leather bracer (left)',       'CLOTHING', 130, 380, FALSE, TRUE, 3, 8),
('leather_bracer_right',     'Leather bracer (right)',      'CLOTHING', 130, 380, FALSE, TRUE, 3, 8),
('leather_elbow_guard_left', 'Leather elbow guard (left)',  'CLOTHING', 150, 400, FALSE, TRUE, 2, 8),
('leather_elbow_guard_right','Leather elbow guard (right)', 'CLOTHING', 150, 400, FALSE, TRUE, 2, 8),
('hide_glove_left',          'Hide glove (left)',           'CLOTHING', 180, 450, FALSE, TRUE, 5, 6),
('hide_glove_right',         'Hide glove (right)',          'CLOTHING', 180, 450, FALSE, TRUE, 5, 6),
('leather_glove_left',       'Leather glove (left)',        'CLOTHING', 150, 400, FALSE, TRUE, 5, 8),
('leather_glove_right',      'Leather glove (right)',       'CLOTHING', 150, 400, FALSE, TRUE, 5, 8),
('fur_mitten_left',          'Fur mitten (left)',           'CLOTHING', 160, 450, FALSE, TRUE, 14, 4),
('fur_mitten_right',         'Fur mitten (right)',          'CLOTHING', 160, 450, FALSE, TRUE, 14, 4)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('rawhide_bracer_left','FOREARM_LEFT','PROTECTION'),('rawhide_bracer_right','FOREARM_RIGHT','PROTECTION'),
('leather_bracer_left','FOREARM_LEFT','PROTECTION'),('leather_bracer_right','FOREARM_RIGHT','PROTECTION'),
('leather_elbow_guard_left','ELBOW_LEFT','PROTECTION'),('leather_elbow_guard_right','ELBOW_RIGHT','PROTECTION'),
('hide_glove_left','HAND_LEFT','CLOTHING'),('hide_glove_right','HAND_RIGHT','CLOTHING'),
('leather_glove_left','HAND_LEFT','CLOTHING'),('leather_glove_right','HAND_RIGHT','CLOTHING'),
('fur_mitten_left','HAND_LEFT','CLOTHING'),('fur_mitten_right','HAND_RIGHT','CLOTHING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('rawhide_bracer_left','TECHNIQUE','a stiff rawhide guard laced to the left forearm'),
('rawhide_bracer_right','TECHNIQUE','a stiff rawhide guard laced to the right forearm'),
('leather_bracer_left','TECHNIQUE','a tanned-leather guard laced to the left forearm'),
('leather_bracer_right','TECHNIQUE','a tanned-leather guard laced to the right forearm'),
('leather_elbow_guard_left','TECHNIQUE','a leather cup laced over the left elbow'),
('leather_elbow_guard_right','TECHNIQUE','a leather cup laced over the right elbow'),
('hide_glove_left','TECHNIQUE','a glove cut and sewn from hide for the left hand'),
('hide_glove_right','TECHNIQUE','a glove cut and sewn from hide for the right hand'),
('leather_glove_left','TECHNIQUE','a glove cut and sewn from leather for the left hand'),
('leather_glove_right','TECHNIQUE','a glove cut and sewn from leather for the right hand'),
('fur_mitten_left','TECHNIQUE','a fur mitten sewn for the left hand'),
('fur_mitten_right','TECHNIQUE','a fur mitten sewn for the right hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_rawhide_bracer_left','Lace a left rawhide bracer','rawhide_bracer_left',1,1,'CUTTING',FALSE,FALSE,18,'items','PROCESS','make a left rawhide bracer,left rawhide bracer', 'You lace a stiff rawhide guard to the left forearm.', 'VERIFIED', now()),
('make_rawhide_bracer_right','Lace a right rawhide bracer','rawhide_bracer_right',1,1,'CUTTING',FALSE,FALSE,18,'items','PROCESS','make a right rawhide bracer,right rawhide bracer', 'You lace a stiff rawhide guard to the right forearm.', 'VERIFIED', now()),
('make_leather_bracer_left','Lace a left leather bracer','leather_bracer_left',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a left leather bracer,left leather bracer', 'You lace a tanned-leather guard to the left forearm.', 'VERIFIED', now()),
('make_leather_bracer_right','Lace a right leather bracer','leather_bracer_right',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a right leather bracer,right leather bracer', 'You lace a tanned-leather guard to the right forearm.', 'VERIFIED', now()),
('make_leather_elbow_guard_left','Lace a left leather elbow guard','leather_elbow_guard_left',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a left leather elbow guard,left leather elbow guard', 'You lace a leather cup over the left elbow.', 'VERIFIED', now()),
('make_leather_elbow_guard_right','Lace a right leather elbow guard','leather_elbow_guard_right',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a right leather elbow guard,right leather elbow guard', 'You lace a leather cup over the right elbow.', 'VERIFIED', now()),
('make_hide_glove_left','Sew a left hide glove','hide_glove_left',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','make a left hide glove,left hide glove', 'You cut and sew a hide glove for the left hand.', 'VERIFIED', now()),
('make_hide_glove_right','Sew a right hide glove','hide_glove_right',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','make a right hide glove,right hide glove', 'You cut and sew a hide glove for the right hand.', 'VERIFIED', now()),
('make_leather_glove_left','Sew a left leather glove','leather_glove_left',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','make a left leather glove,left leather glove', 'You cut and sew a leather glove for the left hand.', 'VERIFIED', now()),
('make_leather_glove_right','Sew a right leather glove','leather_glove_right',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','make a right leather glove,right leather glove', 'You cut and sew a leather glove for the right hand.', 'VERIFIED', now()),
('make_fur_mitten_left','Sew a left fur mitten','fur_mitten_left',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a left fur mitten,left fur mitten', 'You sew a fur mitten for the left hand against the cold.', 'VERIFIED', now()),
('make_fur_mitten_right','Sew a right fur mitten','fur_mitten_right',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a right fur mitten,right fur mitten', 'You sew a fur mitten for the right hand against the cold.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_rawhide_bracer_left','rawhide',1),('make_rawhide_bracer_right','rawhide',1),
('make_leather_bracer_left','tanned_leather',1),('make_leather_bracer_right','tanned_leather',1),
('make_leather_elbow_guard_left','tanned_leather',1),('make_leather_elbow_guard_right','tanned_leather',1),
('make_hide_glove_left','animal_hide',1),('make_hide_glove_right','animal_hide',1),
('make_leather_glove_left','tanned_leather',1),('make_leather_glove_right','tanned_leather',1),
('make_fur_mitten_left','fur_lining',1),('make_fur_mitten_right','fur_lining',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_rawhide_bracer_left','bracer'),('make_rawhide_bracer_right','bracer'),
('make_leather_bracer_left','bracer'),('make_leather_bracer_right','bracer'),
('make_leather_elbow_guard_left','elbow guard'),('make_leather_elbow_guard_right','elbow guard'),
('make_hide_glove_left','glove'),('make_hide_glove_right','glove'),
('make_leather_glove_left','glove'),('make_leather_glove_right','glove'),
('make_fur_mitten_left','mitten'),('make_fur_mitten_right','mitten')
ON CONFLICT DO NOTHING;
