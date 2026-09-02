-- V252 — story #93 (75 first-era weapons/projectiles/traps): completes the last three named entries not yet in the
-- catalogue after the V205-V221 weapon/tool/trap wave (70/75 already built). Adds the atlatl (spear-thrower launcher
-- that casts the existing atlatl_dart), a float bobber (line-fishing float), and a tracking marker bundle (a carried
-- kit of markers for trail-marking). Each is a craftable equippable item (dead-end-exempt), category CRAFT with a
-- 'make a X' keyword (self-classifies CRAFT). (#93's wooden_arrow_shaft / reed_arrow_shaft are the existing generic
-- arrow_shaft, produced from wood by shave_arrow_shafts and from reed by ready_reed_shaft — mapped, not duplicated.)
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('atlatl',                 'Atlatl',                 'TOOL', 250, 900, FALSE, TRUE),
('float_bobber',           'Float bobber',           'TOOL', 60,  200, FALSE, TRUE),
('tracking_marker_bundle', 'Tracking marker bundle', 'TOOL', 200, 700, FALSE, TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('atlatl','HAND_RIGHT','ATTACHED'),
('float_bobber','WAIST','ATTACHED'),
('tracking_marker_bundle','WAIST','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('atlatl','TECHNIQUE','a spear-thrower shaped and hooked from wood by hand'),
('float_bobber','TECHNIQUE','a light float bound for a fishing line by hand'),
('tracking_marker_bundle','TECHNIQUE','a bundle of cut markers for marking a trail')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_atlatl','Shape an atlatl','atlatl',1,1,NULL,FALSE,FALSE,35,'items','CRAFT','atlatl,make an atlatl,shape an atlatl,spear thrower', 'You shape a length of wood into a spear-thrower with a hooked spur to seat a dart — an atlatl that casts a dart far past the arm alone.', 'VERIFIED', now()),
('make_float_bobber','Bind a float bobber','float_bobber',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','float bobber,make a float bobber,bind a float bobber', 'You bind a light float to sit on the water and signal a bite on the line.', 'VERIFIED', now()),
('make_tracking_marker_bundle','Cut a tracking marker bundle','tracking_marker_bundle',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','tracking marker bundle,make a tracking marker bundle,marker bundle', 'You cut and bundle a set of markers to leave along a trail as you go.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_atlatl','dry_branch',1),
('make_float_bobber','reed_bundle',1),
('make_tracking_marker_bundle','dry_branch',1),('make_tracking_marker_bundle','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_atlatl','atlatl'),('make_atlatl','spear thrower'),
('make_float_bobber','bobber'),('make_float_bobber','float bobber'),
('make_tracking_marker_bundle','marker bundle'),('make_tracking_marker_bundle','tracking marker')
ON CONFLICT DO NOTHING;
