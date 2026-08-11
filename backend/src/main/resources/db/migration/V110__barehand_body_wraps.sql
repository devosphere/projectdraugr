-- V110: bare-hand body wraps and improvised protection (M1 #198, EPIC #191).
--
-- Soft worn things a Chronicle makes by hand for warmth, sun, and rain — no blade, no tool. All processes are
-- tool_class NULL. These give bounded climate benefit (insulation) and follow anatomy (paired foot/hand, head);
-- they are NOT armour (no defence value) and their protection is modest, as improvised wraps are.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('grass_ankle_wrap', 'Grass ankle wraps', 'CLOTHING', 120, 300, FALSE, TRUE, 8),
('fibre_hand_wrap',  'Fibre hand wraps',  'CLOTHING', 80,  200, FALSE, TRUE, 6),
('reed_hat',         'Reed hat',          'CLOTHING', 150, 900, FALSE, TRUE, 4),
('bark_hood',        'Bark hood',         'CLOTHING', 200, 700, FALSE, TRUE, 5),
('moss_pad',         'Moss pad',          'CLOTHING', 90,  200, FALSE, TRUE, 3)
ON CONFLICT (item_key) DO NOTHING;

-- Anatomy: ankle wraps and hand wraps are worn as a pair (left + right); hat/hood on the head; pad at the waist.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('grass_ankle_wrap','FOOT_LEFT','CLOTHING'),('grass_ankle_wrap','FOOT_RIGHT','CLOTHING'),
('fibre_hand_wrap','HAND_LEFT','CLOTHING'),('fibre_hand_wrap','HAND_RIGHT','CLOTHING'),
('reed_hat','HEAD','OUTER'),
('bark_hood','HEAD','OUTER'),
('moss_pad','WAIST','CLOTHING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('grass_ankle_wrap','TECHNIQUE','wound from dry grass and fibre by hand'),
('fibre_hand_wrap','TECHNIQUE','wound from plant fibre by hand'),
('reed_hat','TECHNIQUE','plaited from reed by hand'),
('bark_hood','TECHNIQUE','folded from bark by hand'),
('moss_pad','TECHNIQUE','pressed from moss by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_grass_wraps','Wind grass ankle wraps','grass_ankle_wrap',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','grass ankle wraps,grass wraps,make grass wraps,ankle wraps,wind grass wraps', 'You wind dry grass and fibre around each ankle and calf and tie it off — rough leggings against the cold and wet.', 'VERIFIED', now()),
('make_hand_wraps','Wind fibre hand wraps','fibre_hand_wrap',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','hand wraps,fibre hand wraps,make hand wraps,wind hand wraps', 'You wind fibre around your hands and knuckles for warmth and a little protection at the palms.', 'VERIFIED', now()),
('make_reed_hat','Plait a reed hat','reed_hat',1,1,NULL,FALSE,FALSE,25,'items','CRAFT','reed hat,make a reed hat,plait a reed hat,woven reed hat,sun hat', 'You plait the reeds by hand into a broad, light hat that turns the sun and sheds a little rain.', 'VERIFIED', now()),
('make_bark_hood','Fold a bark hood','bark_hood',1,1,NULL,FALSE,FALSE,20,'items','CRAFT','bark hood,make a bark hood,fold a bark hood,rain hood', 'You fold a hood of bark and tie it under the chin — stiff and rough, but it keeps the rain off your head and neck.', 'VERIFIED', now()),
('make_moss_pad','Press a moss pad','moss_pad',1,1,NULL,FALSE,FALSE,8,'items','CRAFT','moss pad,make a moss pad,press a moss pad,absorbent pad', 'You press soft moss into a thick pad — absorbent, warm against the skin, to be tucked where it is needed.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_grass_wraps','dry_grass_bundle',2),('make_grass_wraps','plant_fiber',1),
('make_hand_wraps','plant_fiber',3),
('make_reed_hat','reed_bundle',2),('make_reed_hat','plant_fiber',1),
('make_bark_hood','bark_sheet',2),('make_bark_hood','plant_fiber',1),
('make_moss_pad','moss_bundle',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_grass_wraps','wraps'),('make_grass_wraps','ankle'),
('make_hand_wraps','hand wraps'),('make_hand_wraps','wraps'),
('make_reed_hat','hat'),
('make_bark_hood','hood'),
('make_moss_pad','pad')
ON CONFLICT DO NOTHING;
