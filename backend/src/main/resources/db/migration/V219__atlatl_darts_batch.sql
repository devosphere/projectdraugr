-- V219 — story #93 catalogue batch 12: atlatl darts. A dart thrown by hand is a light javelin, so it is functional
-- on the existing thrown model — weapon_profile JAVELIN (V205) — without a launcher mechanic:
--   dart_foreshaft — a detachable barbed bone foreshaft (the point end of a dart), consumed making a dart
--   atlatl_dart    — a long, light throwing dart with a foreshaft; JAVELIN reach in confront
-- Clean phrasing: 'haft'/'carve' are CRAFT verbs and 'atlatl'/'dart'/'foreshaft' carry no category weight, so each
-- routes to its own recipe. The dart is equippable (dead-end-clean); the foreshaft is consumed by the dart recipe.
-- (The atlatl launcher itself is held back — it has no confront mechanic yet; a dart thrown by hand already works.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('dart_foreshaft', 'Dart foreshaft', 'MATERIAL', 50,  40,   TRUE, FALSE, 0),
('atlatl_dart',    'Atlatl dart',    'WEAPON',   400, 1200, TRUE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('dart_foreshaft', 'TECHNIQUE', 'a barbed bone foreshaft for a dart'),
('atlatl_dart',    'TECHNIQUE', 'a long light dart with a detachable foreshaft')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('atlatl_dart','HAND_RIGHT','CARRIED'),('atlatl_dart','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_dart_foreshaft', 'Carve a dart foreshaft', 'dart_foreshaft', 1,2,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a dart foreshaft,whittle a dart foreshaft,dart foreshaft,foreshaft',
 'You carve a short barbed foreshaft from bone — it seats in the dart shaft and stays in the wound when the shaft falls away.','VERIFIED',now()),
('haft_atlatl_dart',     'Haft an atlatl dart',    'atlatl_dart',    1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'haft an atlatl dart,haft and fletch an atlatl dart,atlatl dart,throwing dart',
 'You fit a foreshaft to a long, light shaft and fletch the tail — a dart made to be cast far and fast.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_dart_foreshaft', 'animal_bone',1),
('haft_atlatl_dart',     'dry_branch',1),('haft_atlatl_dart','dart_foreshaft',1),('haft_atlatl_dart','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_dart_foreshaft','dart foreshaft'),('carve_dart_foreshaft','foreshaft'),
('haft_atlatl_dart','atlatl dart'),('haft_atlatl_dart','throwing dart')
ON CONFLICT DO NOTHING;

-- Combat function (V205): a hand-cast dart is thrown reach, like a javelin.
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
('atlatl_dart', 'JAVELIN', 'PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;
