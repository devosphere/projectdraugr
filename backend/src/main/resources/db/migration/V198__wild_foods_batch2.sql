-- V198 — wild food plants, batch 2 (story #85). 18 more functional forageables — mushrooms, roots, greens, fruits, and
-- nuts — each grows in a plausible biome, is gathered, and nourishes through the EAT path. All edible (is_poisonous
-- FALSE); mushrooms are FUNGI, gatherable like any non-tree flora. Mirrors V88/V197: obtainable FLORA_DROP source,
-- edible FOOD, dead-end-clean (a food is eaten), probe-clean (each item reachable).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('chanterelle',        'Chanterelle',         'FOOD', 18, 40, TRUE, FALSE, 0),
('cep_porcini',        'Cep',                 'FOOD', 60, 90, TRUE, FALSE, 0),
('oyster_mushroom',    'Oyster mushroom',     'FOOD', 30, 70, TRUE, FALSE, 0),
('field_mushroom',     'Field mushroom',      'FOOD', 25, 55, TRUE, FALSE, 0),
('giant_puffball',     'Giant puffball',      'FOOD', 300,900,TRUE, FALSE, 0),
('hedgehog_mushroom',  'Hedgehog mushroom',   'FOOD', 22, 50, TRUE, FALSE, 0),
('arrowhead_tuber',    'Arrowhead tuber',     'FOOD', 35, 38, TRUE, FALSE, 0),
('water_chestnut',     'Water chestnut',      'FOOD', 18, 20, TRUE, FALSE, 0),
('rampion_root',       'Rampion root',        'FOOD', 30, 32, TRUE, FALSE, 0),
('chickweed',          'Chickweed',           'FOOD', 10, 22, TRUE, FALSE, 0),
('sea_beet_leaf',      'Sea beet leaf',       'FOOD', 14, 28, TRUE, FALSE, 0),
('watercress',         'Watercress',          'FOOD', 12, 25, TRUE, FALSE, 0),
('ground_elder_leaf',  'Ground elder leaf',   'FOOD', 10, 22, TRUE, FALSE, 0),
('elderberry',         'Elderberry',          'FOOD', 6,  7,  TRUE, FALSE, 0),
('wild_cherry',        'Wild cherry',         'FOOD', 8,  9,  TRUE, FALSE, 0),
('walnut_kernel',      'Walnut kernel',       'FOOD', 12, 14, TRUE, FALSE, 0),
('juniper_berry',      'Juniper berry',       'FOOD', 4,  5,  TRUE, FALSE, 0),
('marsh_samphire',     'Marsh samphire',      'FOOD', 14, 26, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('chanterelle_fungus',  'FUNGI' , 'TEMPERATE_FOREST',            NULL, 20, FALSE),
('cep_fungus',          'FUNGI' , 'TEMPERATE_FOREST,HIGHLAND',   NULL, 20, FALSE),
('oyster_fungus',       'FUNGI' , 'TEMPERATE_FOREST',            NULL, 18, FALSE),
('field_fungus',        'FUNGI' , 'GRASSLAND',                   NULL, 18, FALSE),
('puffball_fungus',     'FUNGI' , 'GRASSLAND,TEMPERATE_FOREST',  NULL, 25, FALSE),
('hedgehog_fungus',     'FUNGI' , 'TEMPERATE_FOREST',            NULL, 20, FALSE),
('arrowhead',           'HERB',   'WETLAND',                     NULL, 22, FALSE),
('water_chestnut_plant','HERB',   'WETLAND',                     NULL, 22, FALSE),
('rampion',             'HERB',   'GRASSLAND,TEMPERATE_FOREST',  NULL, 22, FALSE),
('chickweed_plant',     'HERB',   'GRASSLAND,TEMPERATE_FOREST',  NULL, 15, FALSE),
('sea_beet',            'HERB',   'COAST,GRASSLAND',             NULL, 20, FALSE),
('watercress_plant',    'HERB',   'WETLAND',                     NULL, 15, FALSE),
('ground_elder',        'HERB',   'TEMPERATE_FOREST,GRASSLAND',  NULL, 15, FALSE),
('elder_shrub',         'SHRUB',  'TEMPERATE_FOREST,GRASSLAND',  NULL, 30, FALSE),
('wild_cherry_shrub',   'SHRUB',  'TEMPERATE_FOREST',            NULL, 40, FALSE),
('walnut_shrub',        'SHRUB',  'TEMPERATE_FOREST',            NULL, 40, FALSE),
('juniper_shrub',       'SHRUB',  'HIGHLAND,MOUNTAIN',           NULL, 40, FALSE),
('samphire',            'HERB',   'COAST,WETLAND',               NULL, 20, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('chanterelle_fungus',  'chanterelle',       2, 5, 'AUTUMN'),
('cep_fungus',          'cep_porcini',       1, 3, 'AUTUMN'),
('oyster_fungus',       'oyster_mushroom',   2, 5, 'AUTUMN'),
('field_fungus',        'field_mushroom',    2, 6, 'AUTUMN'),
('puffball_fungus',     'giant_puffball',    1, 1, 'AUTUMN'),
('hedgehog_fungus',     'hedgehog_mushroom', 2, 5, 'AUTUMN'),
('arrowhead',           'arrowhead_tuber',   1, 3, NULL),
('water_chestnut_plant','water_chestnut',    2, 5, NULL),
('rampion',             'rampion_root',      1, 2, NULL),
('chickweed_plant',     'chickweed',         2, 5, NULL),
('sea_beet',            'sea_beet_leaf',     2, 5, NULL),
('watercress_plant',    'watercress',        2, 5, NULL),
('ground_elder',        'ground_elder_leaf', 2, 5, NULL),
('elder_shrub',         'elderberry',        4, 10,'AUTUMN'),
('wild_cherry_shrub',   'wild_cherry',       3, 8, 'SUMMER'),
('walnut_shrub',        'walnut_kernel',     2, 5, 'AUTUMN'),
('juniper_shrub',       'juniper_berry',     3, 8, 'AUTUMN'),
('samphire',            'marsh_samphire',    2, 5, 'SUMMER')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('chanterelle',       'FLORA_DROP', 'gather chanterelles in forest in autumn'),
('cep_porcini',       'FLORA_DROP', 'gather ceps in forest and highland in autumn'),
('oyster_mushroom',   'FLORA_DROP', 'gather oyster mushrooms on forest wood'),
('field_mushroom',    'FLORA_DROP', 'gather field mushrooms in grassland'),
('giant_puffball',    'FLORA_DROP', 'gather a giant puffball in field or wood'),
('hedgehog_mushroom', 'FLORA_DROP', 'gather hedgehog mushrooms in forest'),
('arrowhead_tuber',   'FLORA_DROP', 'dig arrowhead tubers from wetland'),
('water_chestnut',    'FLORA_DROP', 'gather water chestnuts from wetland'),
('rampion_root',      'FLORA_DROP', 'dig rampion roots in grassland and forest'),
('chickweed',         'FLORA_DROP', 'pick chickweed in grassland and forest'),
('sea_beet_leaf',     'FLORA_DROP', 'pick sea beet on coast and grassland'),
('watercress',        'FLORA_DROP', 'gather watercress from clean wetland water'),
('ground_elder_leaf', 'FLORA_DROP', 'pick young ground elder in forest and grassland'),
('elderberry',        'FLORA_DROP', 'gather elderberries in autumn'),
('wild_cherry',       'FLORA_DROP', 'pick wild cherries in summer'),
('walnut_kernel',     'FLORA_DROP', 'gather walnuts in autumn'),
('juniper_berry',     'FLORA_DROP', 'gather juniper berries on highland in autumn'),
('marsh_samphire',    'FLORA_DROP', 'gather samphire on the coast in summer')
ON CONFLICT (item_key, source_kind) DO NOTHING;
