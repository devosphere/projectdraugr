-- V99: chamomile, pine-needle, and wild rice — real ingredients into the cooking system (M1 #75, EPIC #45/#54).
--
-- Net-new catalogue breadth ([[feedback_real_world_simulation]]): three more real ingredients, each with a real
-- culinary use through the V92 cooking system. chamomile flowers and pine needles steep into a herbal infusion
-- (added to the brew_infusion herb group); wild rice boils into porridge (cook_porridge's grain slot becomes a
-- group taking wild grain OR wild rice). Gathered from the flora system in credible biomes.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('chamomile_flower',   'Chamomile flowers',  'MATERIAL', 8,  20, TRUE, FALSE, 0),
('pine_needle_bundle', 'Pine needle bundle', 'MATERIAL', 40, 90, TRUE, FALSE, 0),
('wild_rice_grain',    'Wild rice',          'FOOD',     15, 16, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('chamomile', 'HERB',    'GRASSLAND',        NULL, 18, FALSE),
('wild_rice', 'AQUATIC', 'WETLAND',          NULL, 25, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('chamomile', 'chamomile_flower',   2, 5, NULL),
('pine',      'pine_needle_bundle', 2, 4, NULL),
('wild_rice', 'wild_rice_grain',    2, 5, 'AUTUMN')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('chamomile_flower',   'FLORA_DROP', 'pick chamomile flowers in grassland'),
('pine_needle_bundle', 'FLORA_DROP', 'strip green needles from a pine'),
('wild_rice_grain',    'FLORA_DROP', 'harvest wild rice from wetland margins in autumn')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Tea: chamomile and pine needles steep like the other herbs.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('brew_infusion','herb','chamomile_flower',2),
('brew_infusion','herb','pine_needle_bundle',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

-- Porridge: the grain slot now takes wild grain OR wild rice (was a fixed wild_grain input).
DELETE FROM material_process_input WHERE process_key='cook_porridge' AND item_key='wild_grain';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('cook_porridge','grain','wild_grain',2),
('cook_porridge','grain','wild_rice_grain',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
