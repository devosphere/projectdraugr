-- V121: bare-hand resin torch — a portable light (M1 #125 / #123 cat.1 perception, EPIC #123).
--
-- The dark/sight-work system (consumePortableLight) grades a rush light, a tallow candle, and an oil lamp, but has
-- no torch. A resin torch is the most primitive portable light of all — a dead branch wrapped with fibre and daubed
-- with pine resin — and now that pine resin is scavenge-able by hand (V120) the whole thing is bare-hand. It burns
-- down for the task like a rush light. This fills #125's `resin_torch` and gives a Chronicle a way to work or move
-- in the dark away from a fire without needing tallow or oil (fish/animal products) first.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('resin_torch', 'Resin torch', 'MATERIAL', 200, 400, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('resin_torch', 'TECHNIQUE', 'a dead branch wrapped with fibre and daubed with pine resin by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT, assembled cold (lit from a flame when used). No tool.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_resin_torch','Make a resin torch','resin_torch',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','resin torch,make a resin torch,make a torch,pitch torch,bind a torch', 'You bind fibre to the head of a dead branch and work pine resin through it — a torch that will burn bright and long once it is lit.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_resin_torch','dry_branch',1),('make_resin_torch','pine_resin',1),('make_resin_torch','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_resin_torch','torch'),('make_resin_torch','resin torch')
ON CONFLICT DO NOTHING;
