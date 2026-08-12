-- V118: bare-hand smoke face wrap (M1 #198, EPIC #191).
--
-- The last #198 item. It only earns a place because V118 also gives it something to protect against: an unvented
-- fire inside an enclosed shelter now fouls the air (see ChroniclePhysiologyService smoke exposure). A smoke face
-- wrap, worn over the nose and mouth, cuts how much of that a Chronicle breathes — a stopgap, not a fix (a smoke
-- hood that vents the hearth is the real remedy). Bare-hand, from soft reachable material, no tool.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('smoke_face_wrap', 'Smoke face wrap', 'CLOTHING', 40, 60, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Worn over the face; it is protection, not insulation (insulation_value 0).
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('smoke_face_wrap','FACE','PROTECTION')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('smoke_face_wrap','TECHNIQUE','wound from soft fibre, grass, or moss over the face by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT. Subject terms are the distinctive "face wrap"/"face mask" so it does not collide with the
-- V110 grass/hand "wraps".
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_smoke_wrap','Wind a smoke face wrap','smoke_face_wrap',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','smoke face wrap,face wrap,make a face wrap,tie a face wrap,face mask,smoke mask', 'You wind soft fibre into a wrap and tie it over your nose and mouth — something to breathe through when the air is thick with smoke.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Any one soft material to hand.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('make_smoke_wrap','soft','plant_fiber',1),('make_smoke_wrap','soft','dry_grass_bundle',1),('make_smoke_wrap','soft','moss_bundle',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_smoke_wrap','face wrap'),('make_smoke_wrap','face mask'),('make_smoke_wrap','smoke mask')
ON CONFLICT DO NOTHING;
