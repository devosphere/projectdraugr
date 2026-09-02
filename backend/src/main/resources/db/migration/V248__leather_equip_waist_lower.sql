-- V248 — story #96, slice 5: waist and lower body. Twelve equippable pieces. Belts are made by "make a <material>
-- girdle" (not "belt", owned by CRAFT_BELT); leggings by "make <material> chaps" and trousers by "make hide breeches"
-- (not "leggings"/"trousers", owned by CRAFT_GARMENT). Rawhide pieces (belt, thigh guards) are category PROCESS; the
-- rest CRAFT. Paired thigh guards equip to their own THIGH slot. tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('rawhide_belt',              'Rawhide belt',              'CLOTHING', 120, 300,  FALSE, TRUE, 1, 3),
('leather_belt',              'Leather belt',              'CLOTHING', 130, 320,  FALSE, TRUE, 1, 8),
('leather_utility_belt',      'Leather utility belt',      'CLOTHING', 180, 400,  FALSE, TRUE, 1, 8),
('leather_pouch_belt',        'Leather pouch belt',        'CLOTHING', 200, 450,  FALSE, TRUE, 1, 8),
('leather_leggings',          'Leather leggings',          'CLOTHING', 600, 1600, FALSE, TRUE, 6, 8),
('fur_leggings',              'Fur leggings',              'CLOTHING', 600, 1800, FALSE, TRUE, 15, 4),
('rawhide_thigh_guard_left',  'Rawhide thigh guard (left)','CLOTHING', 150, 400,  FALSE, TRUE, 2, 3),
('rawhide_thigh_guard_right', 'Rawhide thigh guard (right)','CLOTHING',150, 400,  FALSE, TRUE, 2, 3),
('leather_thigh_guard_left',  'Leather thigh guard (left)', 'CLOTHING', 160, 420, FALSE, TRUE, 3, 8),
('leather_thigh_guard_right', 'Leather thigh guard (right)','CLOTHING',160, 420,  FALSE, TRUE, 3, 8),
('leather_kilt',              'Leather kilt',              'CLOTHING', 500, 1200, FALSE, TRUE, 4, 9),
('hide_trousers',             'Hide trousers',             'CLOTHING', 700, 1800, FALSE, TRUE, 6, 6)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('rawhide_belt','WAIST','CLOTHING'),
('leather_belt','WAIST','CLOTHING'),
('leather_utility_belt','WAIST','ATTACHED'),
('leather_pouch_belt','WAIST','ATTACHED'),
('leather_leggings','LEGS','CLOTHING'),
('fur_leggings','LEGS','CLOTHING'),
('rawhide_thigh_guard_left','THIGH_LEFT','PROTECTION'),('rawhide_thigh_guard_right','THIGH_RIGHT','PROTECTION'),
('leather_thigh_guard_left','THIGH_LEFT','PROTECTION'),('leather_thigh_guard_right','THIGH_RIGHT','PROTECTION'),
('leather_kilt','WAIST','CLOTHING'),
('hide_trousers','LEGS','CLOTHING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('rawhide_belt','TECHNIQUE','a stiff rawhide belt cut and bound by hand'),
('leather_belt','TECHNIQUE','a tanned-leather belt cut and fitted by hand'),
('leather_utility_belt','TECHNIQUE','a leather belt of tool loops fitted by hand'),
('leather_pouch_belt','TECHNIQUE','a leather belt hung with pouches by hand'),
('leather_leggings','TECHNIQUE','leg coverings cut and sewn from tanned leather by hand'),
('fur_leggings','TECHNIQUE','leg coverings sewn from fur by hand'),
('rawhide_thigh_guard_left','TECHNIQUE','a stiff rawhide guard laced over the left thigh'),
('rawhide_thigh_guard_right','TECHNIQUE','a stiff rawhide guard laced over the right thigh'),
('leather_thigh_guard_left','TECHNIQUE','a leather guard laced over the left thigh'),
('leather_thigh_guard_right','TECHNIQUE','a leather guard laced over the right thigh'),
('leather_kilt','TECHNIQUE','a leather kilt cut and hung from the waist by hand'),
('hide_trousers','TECHNIQUE','trousers cut from hide and laced by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_rawhide_belt','Bind a rawhide girdle','rawhide_belt',1,1,'CUTTING',FALSE,FALSE,15,'items','PROCESS','rawhide girdle,make a rawhide girdle', 'You cut and bind a stiff rawhide girdle for the waist.', 'VERIFIED', now()),
('make_leather_belt','Fit a leather girdle','leather_belt',1,1,'CUTTING',FALSE,FALSE,18,'items','CRAFT','leather girdle,make a leather girdle,fit a leather girdle', 'You cut and fit a tanned-leather girdle for the waist.', 'VERIFIED', now()),
('make_leather_utility_belt','Fit a leather tool girdle','leather_utility_belt',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','leather tool girdle,make a leather tool girdle,tool girdle', 'You fit a leather girdle with a row of tool loops.', 'VERIFIED', now()),
('make_leather_pouch_belt','Fit a leather pouch girdle','leather_pouch_belt',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','leather pouch girdle,make a leather pouch girdle,pouch girdle', 'You fit a leather girdle hung with small pouches.', 'VERIFIED', now()),
('make_leather_leggings','Sew leather chaps','leather_leggings',1,1,'CUTTING',FALSE,FALSE,50,'items','CRAFT','leather chaps,make leather chaps,sew leather chaps', 'You cut and sew tanned leather into leg coverings — leather leggings.', 'VERIFIED', now()),
('make_fur_leggings','Sew fur chaps','fur_leggings',1,1,'CUTTING',FALSE,FALSE,50,'items','CRAFT','fur chaps,make fur chaps,sew fur chaps', 'You sew fur into warm leg coverings — fur leggings.', 'VERIFIED', now()),
('make_rawhide_thigh_guard_left','Lace a left rawhide thigh guard','rawhide_thigh_guard_left',1,1,'CUTTING',FALSE,FALSE,20,'items','PROCESS','make a left rawhide thigh guard,left rawhide thigh guard', 'You lace a stiff rawhide guard over the left thigh.', 'VERIFIED', now()),
('make_rawhide_thigh_guard_right','Lace a right rawhide thigh guard','rawhide_thigh_guard_right',1,1,'CUTTING',FALSE,FALSE,20,'items','PROCESS','make a right rawhide thigh guard,right rawhide thigh guard', 'You lace a stiff rawhide guard over the right thigh.', 'VERIFIED', now()),
('make_leather_thigh_guard_left','Lace a left leather thigh guard','leather_thigh_guard_left',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a left leather thigh guard,left leather thigh guard', 'You lace a leather guard over the left thigh.', 'VERIFIED', now()),
('make_leather_thigh_guard_right','Lace a right leather thigh guard','leather_thigh_guard_right',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a right leather thigh guard,right leather thigh guard', 'You lace a leather guard over the right thigh.', 'VERIFIED', now()),
('make_leather_kilt','Cut a leather kilt','leather_kilt',1,1,'CUTTING',FALSE,FALSE,35,'items','CRAFT','leather kilt,make a leather kilt,cut a leather kilt', 'You cut and hang a leather kilt from the waist.', 'VERIFIED', now()),
('make_hide_trousers','Lace hide breeches','hide_trousers',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','hide breeches,make hide breeches,lace hide breeches', 'You cut hide and lace it into trousers.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_rawhide_belt','rawhide',1),
('make_leather_belt','tanned_leather',1),
('make_leather_utility_belt','tanned_leather',1),
('make_leather_pouch_belt','tanned_leather',1),
('make_leather_leggings','tanned_leather',2),
('make_fur_leggings','fur_lining',2),
('make_rawhide_thigh_guard_left','rawhide',1),('make_rawhide_thigh_guard_right','rawhide',1),
('make_leather_thigh_guard_left','tanned_leather',1),('make_leather_thigh_guard_right','tanned_leather',1),
('make_leather_kilt','tanned_leather',2),
('make_hide_trousers','animal_hide',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_rawhide_belt','girdle'),
('make_leather_belt','girdle'),
('make_leather_utility_belt','tool girdle'),('make_leather_utility_belt','girdle'),
('make_leather_pouch_belt','pouch girdle'),('make_leather_pouch_belt','girdle'),
('make_leather_leggings','chaps'),
('make_fur_leggings','chaps'),
('make_rawhide_thigh_guard_left','thigh guard'),('make_rawhide_thigh_guard_right','thigh guard'),
('make_leather_thigh_guard_left','thigh guard'),('make_leather_thigh_guard_right','thigh guard'),
('make_leather_kilt','kilt'),
('make_hide_trousers','breeches')
ON CONFLICT DO NOTHING;
