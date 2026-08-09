-- V78: clay / ceramic / water-handling chain (M1 #59, EPIC #54).
--
-- Builds on the existing clay path (form_vessel -> unfired_vessel -> fire_vessel -> clay_pot). Adds tempered
-- clay (better prep), two ceramic vessels, and two water-handling objects. Ceramic vessels are fired (requires
-- a fire in reach), keyed on "clay bowl / clay cup" so they don't collide with the wooden bowl or the generic
-- clay pot. Deferred (their own change): the LIME chain (quicklime/slaked_lime — needs a gatherable limestone
-- + mortar use) and ceramic_grog (needs a potsherd source), and the fragile-unfired-vessel water-sensitivity.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('tempered_clay',     'Tempered clay',     'MATERIAL', 1100, 800,  FALSE, FALSE, 0),
('fired_bowl',        'Fired clay bowl',   'CONTAINER',900,  1400, FALSE, TRUE,  0),
('fired_cup',         'Fired clay cup',    'CONTAINER',300,  500,  FALSE, TRUE,  0),
('clay_water_filter', 'Clay water filter', 'CONTAINER',1000, 1200, FALSE, FALSE, 0),
('water_ladle',       'Water ladle',       'TOOL',     300,  700,  FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('fired_bowl',        2500, 2500),
('fired_cup',         600,  600),
('clay_water_filter', 3000, 3000)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fired_bowl','HAND_RIGHT','CARRIED'), ('fired_bowl','HAND_LEFT','CARRIED'),
('fired_cup','HAND_RIGHT','CARRIED'), ('fired_cup','HAND_LEFT','CARRIED'),
('water_ladle','HAND_RIGHT','CARRIED'), ('water_ladle','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('temper_clay',           'Temper the clay',      'tempered_clay',    1,1,NULL,FALSE,FALSE,40,'items','PROCESS','temper the clay,temper clay,tempered clay,knead the clay,work the clay,wedge the clay', 'You knead grit and water through the clay until it is even and workable, less apt to crack in the firing.', 'VERIFIED', now()),
('form_clay_bowl',        'Form a clay bowl',     'fired_bowl',       1,1,NULL,TRUE, FALSE,90,'items','CRAFT',  'clay bowl,fired bowl,form a clay bowl,make a clay bowl,fire a clay bowl', 'You pinch and smooth a shallow bowl from the tempered clay and set it in the fire until it rings when tapped.', 'VERIFIED', now()),
('form_clay_cup',         'Form a clay cup',      'fired_cup',        1,1,NULL,TRUE, FALSE,70,'items','CRAFT',  'clay cup,fired cup,form a clay cup,make a clay cup,fire a clay cup,clay beaker', 'You shape a small cup from the tempered clay and fire it hard enough to hold water to the lip.', 'VERIFIED', now()),
('make_clay_water_filter','Make a clay water filter','clay_water_filter',1,1,NULL,TRUE,FALSE,110,'items','CRAFT','clay water filter,water filter,make a water filter,charcoal water filter,fire a water filter', 'You form a narrow-necked clay vessel packed with charcoal and sand, fired hard, so water poured through runs clearer.', 'VERIFIED', now()),
('carve_water_ladle',     'Carve a water ladle',  'water_ladle',      1,1,'CUTTING',FALSE,FALSE,35,'items','CRAFT','water ladle,carve a ladle,carve a water ladle,make a ladle,dipper,water dipper', 'You hollow a small bowl into the end of a handle — a ladle to draw and pour water without dipping the whole vessel.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('temper_clay',           'clay_lump', 2),
('form_clay_bowl',        'tempered_clay', 1),
('form_clay_cup',         'tempered_clay', 1),
('make_clay_water_filter','tempered_clay', 1), ('make_clay_water_filter','charcoal', 1),
('carve_water_ladle',     'dry_branch', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('temper_clay','clay'),
('form_clay_bowl','bowl'),
('form_clay_cup','cup'), ('form_clay_cup','beaker'),
('make_clay_water_filter','filter'), ('make_clay_water_filter','water'),
('carve_water_ladle','ladle'), ('carve_water_ladle','dipper')
ON CONFLICT DO NOTHING;
