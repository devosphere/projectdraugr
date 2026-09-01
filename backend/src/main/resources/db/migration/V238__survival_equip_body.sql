-- V238 — story #95, slice 2: body and torso wear. Ten bare-hand CLOTHING pieces from reed/bark/grass/fibre worn on
-- the torso or waist, on the V110/V230 garment pattern. reed_tunic/bark_tunic are MADE by the phrase "weave a reed
-- jerkin"/"lap a bark jerkin" (not "tunic"): the word 'tunic' is owned by the Java CRAFT_GARMENT intent, which makes
-- the generic fiber_tunic from hide/cloth, so a reed/bark tunic needs a non-'tunic' verb-phrase to route to its own
-- recipe. All phrases classify CRAFT (lead verb make/weave/lap/plait) and were checked with the two-axis matcher sim.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('reed_shoulder_cape', 'Reed shoulder cape', 'CLOTHING', 250, 900,  FALSE, TRUE, 6, 5),
('bark_shoulder_cape', 'Bark shoulder cape', 'CLOTHING', 300, 1000, FALSE, TRUE, 5, 10),
('grass_shawl',        'Grass shawl',        'CLOTHING', 250, 900,  FALSE, TRUE, 7, 3),
('reed_tunic',         'Reed tunic',         'CLOTHING', 350, 1200, FALSE, TRUE, 6, 4),
('bark_tunic',         'Bark tunic',         'CLOTHING', 400, 1300, FALSE, TRUE, 5, 8),
('fibre_wrap_shirt',   'Fibre wrap shirt',   'CLOTHING', 300, 1000, FALSE, TRUE, 8, 1),
('grass_skirt',        'Grass skirt',        'CLOTHING', 250, 900,  FALSE, TRUE, 5, 2),
('reed_kilt',          'Reed kilt',          'CLOTHING', 300, 950,  FALSE, TRUE, 5, 3),
('bark_wrap',          'Bark wrap',          'CLOTHING', 350, 1100, FALSE, TRUE, 5, 9),
('reed_rain_cape',     'Reed rain cape',     'CLOTHING', 350, 1300, FALSE, TRUE, 6, 12)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('reed_shoulder_cape','TORSO','OUTER'),
('bark_shoulder_cape','TORSO','OUTER'),
('grass_shawl','TORSO','OUTER'),
('reed_tunic','TORSO','INNER'),
('bark_tunic','TORSO','INNER'),
('fibre_wrap_shirt','TORSO','INNER'),
('grass_skirt','WAIST','CLOTHING'),
('reed_kilt','WAIST','CLOTHING'),
('bark_wrap','TORSO','OUTER'),
('reed_rain_cape','TORSO','OUTER')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('reed_shoulder_cape','TECHNIQUE','woven from reed by hand'),
('bark_shoulder_cape','TECHNIQUE','lapped from bark sheets by hand'),
('grass_shawl','TECHNIQUE','woven from dry grass and fibre by hand'),
('reed_tunic','TECHNIQUE','woven from reed over a fibre frame by hand'),
('bark_tunic','TECHNIQUE','lapped from bark over a fibre frame by hand'),
('fibre_wrap_shirt','TECHNIQUE','wound and knotted from plant fibre by hand'),
('grass_skirt','TECHNIQUE','woven from dry grass by hand'),
('reed_kilt','TECHNIQUE','plaited from reed by hand'),
('bark_wrap','TECHNIQUE','a bark body wrap belted on by hand'),
('reed_rain_cape','TECHNIQUE','thatched thick with reed to shed rain by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_reed_shoulder_cape','Weave a reed shoulder cape','reed_shoulder_cape',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','reed shoulder cape,make a reed shoulder cape,weave a reed shoulder cape', 'You weave reed into a stiff cape over the shoulders that turns wind and a little rain.', 'VERIFIED', now()),
('make_bark_shoulder_cape','Lap a bark shoulder cape','bark_shoulder_cape',1,1,NULL,FALSE,FALSE,35,'items','CRAFT','bark shoulder cape,make a bark shoulder cape,lap a bark shoulder cape', 'You lap bark sheets over the shoulders into a cape that sheds the rain well.', 'VERIFIED', now()),
('make_grass_shawl','Weave a grass shawl','grass_shawl',1,1,NULL,FALSE,FALSE,28,'items','CRAFT','grass shawl,make a grass shawl,weave a grass shawl', 'You weave dry grass and fibre into a warm shawl for the shoulders.', 'VERIFIED', now()),
('make_reed_tunic','Weave a reed jerkin','reed_tunic',1,1,NULL,FALSE,FALSE,45,'items','CRAFT','reed jerkin,weave a reed jerkin,make a reed jerkin,reed body jerkin', 'You weave reed over a fibre frame into a sleeveless body jerkin — a reed tunic against the weather.', 'VERIFIED', now()),
('make_bark_tunic','Lap a bark jerkin','bark_tunic',1,1,NULL,FALSE,FALSE,50,'items','CRAFT','bark jerkin,lap a bark jerkin,make a bark jerkin,bark body jerkin', 'You lap bark over a fibre frame into a stiff body jerkin — a bark tunic that turns the rain.', 'VERIFIED', now()),
('make_fibre_wrap_shirt','Knot a fibre wrap shirt','fibre_wrap_shirt',1,1,NULL,FALSE,FALSE,40,'items','CRAFT','fibre wrap shirt,make a fibre wrap shirt,knot a fibre wrap shirt,wrap shirt', 'You wind and knot plant fibre into a close wrap shirt for the body.', 'VERIFIED', now()),
('make_grass_skirt','Weave a grass skirt','grass_skirt',1,1,NULL,FALSE,FALSE,25,'items','CRAFT','grass skirt,make a grass skirt,weave a grass skirt', 'You weave dry grass into a skirt hung from a fibre waistband.', 'VERIFIED', now()),
('make_reed_kilt','Plait a reed kilt','reed_kilt',1,1,NULL,FALSE,FALSE,28,'items','CRAFT','reed kilt,make a reed kilt,plait a reed kilt', 'You plait reed into a stiff kilt hung from the waist.', 'VERIFIED', now()),
('make_bark_wrap','Belt on a bark wrap','bark_wrap',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','bark wrap,make a bark wrap,belt on a bark wrap', 'You belt a broad sheet of bark about the body as a rough wrap against the wet.', 'VERIFIED', now()),
('make_reed_rain_cape','Thatch a reed rain cape','reed_rain_cape',1,1,NULL,FALSE,FALSE,40,'items','CRAFT','reed rain cape,make a reed rain cape,thatch a reed rain cape', 'You thatch reed thick over a fibre backing into a cape that turns even driving rain.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_reed_shoulder_cape','reed_bundle',2),
('make_bark_shoulder_cape','bark_sheet',3),
('make_grass_shawl','dry_grass_bundle',3),('make_grass_shawl','plant_fiber',1),
('make_reed_tunic','reed_bundle',2),('make_reed_tunic','plant_fiber',1),
('make_bark_tunic','bark_sheet',4),('make_bark_tunic','plant_fiber',1),
('make_fibre_wrap_shirt','plant_fiber',4),
('make_grass_skirt','dry_grass_bundle',3),('make_grass_skirt','plant_fiber',1),
('make_reed_kilt','reed_bundle',2),
('make_bark_wrap','bark_sheet',3),
('make_reed_rain_cape','reed_bundle',2),('make_reed_rain_cape','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_reed_shoulder_cape','shoulder cape'),('make_reed_shoulder_cape','cape'),
('make_bark_shoulder_cape','shoulder cape'),('make_bark_shoulder_cape','cape'),
('make_grass_shawl','shawl'),
('make_reed_tunic','jerkin'),
('make_bark_tunic','jerkin'),
('make_fibre_wrap_shirt','wrap shirt'),('make_fibre_wrap_shirt','shirt'),
('make_grass_skirt','skirt'),
('make_reed_kilt','kilt'),
('make_bark_wrap','bark wrap'),
('make_reed_rain_cape','rain cape'),('make_reed_rain_cape','cape')
ON CONFLICT DO NOTHING;
