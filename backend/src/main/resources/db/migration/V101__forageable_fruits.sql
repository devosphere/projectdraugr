-- V101: forageable fruits (M1 #75, EPIC #45/#54).
--
-- Net-new breadth: three more real temperate forageable fruits, each with a real use — eaten, and stewed down
-- into a compote (added to the V92 stew_compote berry slot). crab apples and sloes are tart and come into their
-- own cooked; bilberries are sweet enough to eat off the bush. Gathered from new flora in credible biomes.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('crab_apple', 'Crab apple', 'FOOD', 40, 45, TRUE, FALSE, 0),
('sloe',       'Sloe',       'FOOD', 8,  10, TRUE, FALSE, 0),
('bilberry',   'Bilberry',   'FOOD', 3,  4,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('crab_apple_tree', 'TREE',  'TEMPERATE_FOREST',   NULL, 90, FALSE),
('blackthorn',      'SHRUB', 'TEMPERATE_FOREST,GRASSLAND', NULL, 60, FALSE),
('bilberry_bush',   'SHRUB', 'HIGHLAND,MOUNTAIN',  NULL, 40, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('crab_apple_tree', 'crab_apple', 2, 6, 'AUTUMN'),
('blackthorn',      'sloe',       3, 8, 'AUTUMN'),
('bilberry_bush',   'bilberry',   4, 9, 'SUMMER')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('crab_apple', 'FLORA_DROP', 'gather crab apples under the tree in autumn'),
('sloe',       'FLORA_DROP', 'pick sloes from the blackthorn in autumn'),
('bilberry',   'FLORA_DROP', 'pick bilberries on the highland heath in summer')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- All three stew down into a compote alongside the berries.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('stew_compote','berry','crab_apple',3),('stew_compote','berry','sloe',3),('stew_compote','berry','bilberry',3)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
