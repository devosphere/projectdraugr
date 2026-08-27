-- V181 — grain flatbread: the loaf the cultivated harvest is for (EPIC #45/#54 cooking; the terminal of #162 farming).
--
-- Grain flour was a dead end on the plate: ground from grain, then eaten as raw flour, because nothing baked it. Yet
-- acorn flour already bakes into a flatbread (V92) — the pattern is here, grain simply never got it. With cultivation
-- now a full loop (till → sow → reap → thresh → grind), the flour it makes wants its proper end: worked with water
-- into a dough and baked flat on a hot stone into bread, the keeping staple that is the whole point of growing grain.
-- Mirrors bake_flatbread exactly (same masses, same water slot, same fire), so the mass balance that recipe already
-- passes holds here; the keywords are grain-specific so "bake grain bread" resolves to this and "acorn bread" to that.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('grain_flatbread', 'Grain flatbread', 'FOOD', 180, 160, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('grain_flatbread', 'TECHNIQUE', 'baked from grain flour')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('bake_grain_flatbread', 'Bake grain flatbread', 'grain_flatbread', 1, 2, NULL, TRUE, FALSE, 30, 'items', 'PROCESS',
 'grain flatbread,grain bread,wheaten flatbread,wheaten bread,wheat bread,bake grain bread,bake grain flatbread,make grain bread,bake wheaten bread',
 'You work the grain flour and water into a stiff dough and bake it flat on a hot stone into a dense, keeping bread.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('bake_grain_flatbread', 'grain_flour', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('bake_grain_flatbread', 'water', 'clean_water', 1),
('bake_grain_flatbread', 'water', 'filtered_water', 1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('bake_grain_flatbread', 'flatbread'),
('bake_grain_flatbread', 'bread')
ON CONFLICT DO NOTHING;
