-- V237 — story #95 (60 primitive survival equipment entries), slice 1: head, neck and face coverings. Six bare-hand
-- CLOTHING pieces from reachable leaf/reed/bark/grass/fibre, each an equippable item with a real anatomy slot and
-- bounded climate benefit, on the V110/V230 garment pattern (item_definition + item_equipment_compatibility +
-- item_source + material_process CRAFT + input + subject). Phrases lead with 'make'/'weave' (CRAFT category terms) and
-- carry no fire-lighting/belt/sling/tunic/cloth word, so each classifies CRAFT and routes to its own recipe.
-- (#95's smoke_cloth_face_wrap is not added here: the existing smoke_face_wrap, made by make_smoke_wrap, already is
-- that smoke face covering — a second identical item would be a token.)
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('leaf_sunshade',   'Leaf sunshade',   'CLOTHING', 100, 500, FALSE, TRUE, 2, 3),
('reed_headband',   'Reed headband',   'CLOTHING', 60,  120, FALSE, TRUE, 1, 0),
('bark_headband',   'Bark headband',   'CLOTHING', 60,  120, FALSE, TRUE, 1, 1),
('grass_cap',       'Grass cap',       'CLOTHING', 90,  350, FALSE, TRUE, 3, 2),
('fibre_neck_wrap', 'Fibre neck wrap', 'CLOTHING', 90,  250, FALSE, TRUE, 4, 0),
('fibre_face_wrap', 'Fibre face wrap', 'CLOTHING', 50,  150, FALSE, TRUE, 1, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('leaf_sunshade','HEAD','OUTER'),
('reed_headband','HEAD','CLOTHING'),
('bark_headband','HEAD','CLOTHING'),
('grass_cap','HEAD','OUTER'),
('fibre_neck_wrap','NECK','CLOTHING'),
('fibre_face_wrap','FACE','CLOTHING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('leaf_sunshade','TECHNIQUE','broad leaves stitched over a fibre brim by hand'),
('reed_headband','TECHNIQUE','plaited from reed by hand'),
('bark_headband','TECHNIQUE','a strip of bark bound to the brow by hand'),
('grass_cap','TECHNIQUE','woven from dry grass by hand'),
('fibre_neck_wrap','TECHNIQUE','wound from plant fibre by hand'),
('fibre_face_wrap','TECHNIQUE','wound from plant fibre by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_leaf_sunshade','Weave a leaf sunshade','leaf_sunshade',1,1,NULL,FALSE,FALSE,20,'items','CRAFT','leaf sunshade,make a leaf sunshade,weave a leaf sunshade,sun shade', 'You stitch broad leaves over a light fibre brim into a shade that keeps the sun off your head and neck.', 'VERIFIED', now()),
('make_reed_headband','Plait a reed headband','reed_headband',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','reed headband,make a reed headband,plait a reed headband', 'You plait a band of reed to bind your hair and keep the sweat and wet from your eyes.', 'VERIFIED', now()),
('make_bark_headband','Bind a bark headband','bark_headband',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','bark headband,make a bark headband,bind a bark headband', 'You bind a strip of soft bark round your brow as a headband.', 'VERIFIED', now()),
('make_grass_cap','Weave a grass cap','grass_cap',1,1,NULL,FALSE,FALSE,18,'items','CRAFT','grass cap,make a grass cap,weave a grass cap', 'You weave dry grass into a close cap that turns the sun and a little rain.', 'VERIFIED', now()),
('make_fibre_neck_wrap','Wind a fibre neck wrap','fibre_neck_wrap',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','fibre neck wrap,make a fibre neck wrap,wind a fibre neck wrap', 'You wind plant fibre into a wrap for your neck against the cold and the sun.', 'VERIFIED', now()),
('make_fibre_face_wrap','Wind a fibre face wrap','fibre_face_wrap',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','fibre face wrap,make a fibre face wrap,wind a fibre face wrap', 'You wind a light fibre wrap for your face against dust, sun and biting cold.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_leaf_sunshade','big_leaf',4),('make_leaf_sunshade','plant_fiber',1),
('make_reed_headband','reed_bundle',1),
('make_bark_headband','bark_sheet',1),
('make_grass_cap','dry_grass_bundle',2),('make_grass_cap','plant_fiber',1),
('make_fibre_neck_wrap','plant_fiber',2),
('make_fibre_face_wrap','plant_fiber',1),('make_fibre_face_wrap','nettle_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_leaf_sunshade','sunshade'),('make_leaf_sunshade','sun shade'),
('make_reed_headband','headband'),
('make_bark_headband','headband'),
('make_grass_cap','cap'),('make_grass_cap','grass cap'),
('make_fibre_neck_wrap','neck wrap'),
('make_fibre_face_wrap','face wrap')
ON CONFLICT DO NOTHING;
