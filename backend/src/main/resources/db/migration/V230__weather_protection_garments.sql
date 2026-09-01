-- V230 — stories #146 (improvised first-era clothing) and #147 (rain/cold protection from forest materials).
-- Seven hand-made weather garments filling the gaps left after V110's wraps/hat/hood: a grass rain cape, a bark rain
-- cape, bark sandals (plain and grass-lined), a simple hide body wrap, a woven headband, and a hide hood. Each is a
-- bounded-benefit CLOTHING item (insulation + water_resistance, read by physiology when worn), made bare-handed or
-- with a blade from reachable forest materials, following anatomy. NOT armour (no defence value). Same data pattern
-- as V110. Process nouns (cape, sandals, hide wrap, headband, hood) are all clear of the Java CRAFT_GARMENT trigger,
-- which owns only coat/cloak/legging/tunic/boots/garment/clothing and "hide"+"wear".
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('grass_rain_cape',         'Grass rain cape',          'CLOTHING', 300, 1200, FALSE, TRUE, 10, 8),
('bark_rain_cape',          'Bark rain cape',           'CLOTHING', 350, 1400, FALSE, TRUE, 6, 14),
('bark_sandals',            'Bark sandals',             'CLOTHING', 200, 600,  FALSE, TRUE, 5, 4),
('grass_lined_bark_sandals','Grass-lined bark sandals', 'CLOTHING', 240, 700,  FALSE, TRUE, 9, 4),
('simple_hide_wrap',        'Simple hide wrap',         'CLOTHING', 600, 1500, FALSE, TRUE, 14, 6),
('woven_headband',          'Woven headband',           'CLOTHING', 60,  150,  FALSE, TRUE, 2, 0),
('hide_hood',               'Hide hood',                'CLOTHING', 300, 800,  FALSE, TRUE, 8, 7)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('grass_rain_cape','TORSO','OUTER'),
('bark_rain_cape','TORSO','OUTER'),
('bark_sandals','FOOT_LEFT','CLOTHING'),('bark_sandals','FOOT_RIGHT','CLOTHING'),
('grass_lined_bark_sandals','FOOT_LEFT','CLOTHING'),('grass_lined_bark_sandals','FOOT_RIGHT','CLOTHING'),
('simple_hide_wrap','TORSO','OUTER'),
('woven_headband','HEAD','CLOTHING'),
('hide_hood','HEAD','OUTER')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('grass_rain_cape','TECHNIQUE','woven from dry grass and fibre by hand'),
('bark_rain_cape','TECHNIQUE','shingled from bark sheets and fibre by hand'),
('bark_sandals','TECHNIQUE','laced from bark and fibre by hand'),
('grass_lined_bark_sandals','TECHNIQUE','laced from bark and lined with grass by hand'),
('simple_hide_wrap','TECHNIQUE','wrapped from a tanned hide by hand'),
('woven_headband','TECHNIQUE','woven from plant fibre by hand'),
('hide_hood','TECHNIQUE','folded from a tanned hide by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_grass_cape','Weave a grass rain cape','grass_rain_cape',1,1,NULL,FALSE,FALSE,40,'items','CRAFT','grass cape,grass rain cape,make a grass cape,weave a grass cape', 'You weave dry grass thick over a fibre net into a long cape that sheds the rain and breaks the wind off your back.', 'VERIFIED', now()),
('make_bark_cape','Shingle a bark rain cape','bark_rain_cape',1,1,NULL,FALSE,FALSE,45,'items','CRAFT','bark rain cape,bark cape,make a bark rain cape,shingle a bark cape', 'You lap sheets of bark down a fibre backing like shingles into a stiff cape that turns even driving rain.', 'VERIFIED', now()),
('make_bark_sandals','Lace bark sandals','bark_sandals',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','bark sandals,bark sandal pair,make bark sandals,lace bark sandals', 'You cut soles from stiff bark and lace them to your feet with fibre — rough footing, but it keeps the cold and wet ground off your soles.', 'VERIFIED', now()),
('make_grass_lined_sandals','Line bark sandals with grass','grass_lined_bark_sandals',1,1,NULL,FALSE,FALSE,35,'items','CRAFT','grass-lined bark sandals,grass lined bark sandals,line bark sandals with grass,make grass-lined sandals', 'You lace bark soles and pack them with soft dry grass — warmer underfoot than bare bark against the winter ground.', 'VERIFIED', now()),
('make_hide_wrap','Wrap a simple hide','simple_hide_wrap',1,1,NULL,FALSE,FALSE,25,'items','CRAFT','hide wrap,simple hide wrap,make a hide wrap,wrap in hide', 'You belt a whole tanned hide about your body like a mantle — heavy and warm, a plain wrap against the cold.', 'VERIFIED', now()),
('make_headband','Weave a headband','woven_headband',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','headband,woven headband,make a headband,weave a headband', 'You twist and weave a band of fibre to bind your hair and keep the sweat and the wet from your eyes.', 'VERIFIED', now()),
('make_hide_hood','Fold a hide hood','hide_hood',1,1,NULL,FALSE,FALSE,25,'items','CRAFT','hide hood,make a hide hood,fold a hide hood,lace a hide hood', 'You fold and lace a hood of soft tanned hide that ties under the chin, turning rain and holding warmth about your head and neck.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_grass_cape','dry_grass_bundle',3),('make_grass_cape','plant_fiber',2),
('make_bark_cape','bark_sheet',3),('make_bark_cape','plant_fiber',1),
('make_bark_sandals','bark_sheet',2),('make_bark_sandals','plant_fiber',1),
('make_grass_lined_sandals','bark_sheet',2),('make_grass_lined_sandals','dry_grass_bundle',1),('make_grass_lined_sandals','plant_fiber',1),
('make_hide_wrap','tanned_leather',1),
('make_headband','plant_fiber',1),('make_headband','nettle_fiber',1),
('make_hide_hood','tanned_leather',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_grass_cape','cape'),
('make_bark_cape','cape'),
('make_bark_sandals','sandals'),('make_bark_sandals','sandal'),
('make_grass_lined_sandals','sandals'),('make_grass_lined_sandals','sandal'),
('make_hide_wrap','hide wrap'),
('make_headband','headband'),
('make_hide_hood','hood')
ON CONFLICT DO NOTHING;
