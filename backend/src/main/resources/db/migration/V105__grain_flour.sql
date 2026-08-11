-- V105: grind grain into flour, and bake it (M1 #75, EPIC #45/#54).
--
-- Net-new breadth: wild grain could only be boiled into porridge. Ground on a stone it becomes flour, and flour
-- bakes into flatbread — the grain-to-bread path a real diet turns on. Grinding needs a stone (STRIKING);
-- flatbread's flour slot now takes acorn flour OR grain flour.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('grain_flour', 'Grain flour', 'FOOD', 15, 18, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('grain_flour', 'TECHNIQUE', 'ground from wild grain on a stone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('grind_flour','Grind grain into flour','grain_flour',1,1,'STRIKING',FALSE,FALSE,30,'items','PROCESS','grind grain into flour,grind the grain,grind flour,mill the grain,mill flour,make flour,grain flour', 'You grind the grain between two stones, back and forth, until it breaks down to a coarse pale flour.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('grind_flour','wild_grain',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('grind_flour','flour'),('grind_flour','grain')
ON CONFLICT DO NOTHING;

-- Flatbread's flour slot takes acorn flour OR grain flour (was fixed acorn_flour).
DELETE FROM material_process_input WHERE process_key='bake_flatbread' AND item_key='acorn_flour';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('bake_flatbread','flour','acorn_flour',2),('bake_flatbread','flour','grain_flour',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
