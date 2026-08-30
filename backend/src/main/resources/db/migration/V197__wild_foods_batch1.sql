-- V197 — wild food plants, batch 1 (EPIC #54/#99 / story #85 flora with harvest products and survival uses). Building
-- the flora catalogue out as FUNCTIONAL content, not a dead list: each plant here is a real temperate forageable that
-- grows in a plausible biome (lazily entered into chunk_flora on gathering), drops an edible product, and nourishes
-- through the existing EAT path — attainable, workable, and terminally useful end to end. No processing step is needed:
-- a food is eaten. Mirrors V88 exactly (obtainable FLORA_DROP source, edible FOOD, probe-clean, dead-end-clean).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('wild_carrot_root',   'Wild carrot root',   'FOOD', 45, 50, TRUE, FALSE, 0),
('burdock_root',       'Burdock root',       'FOOD', 70, 70, TRUE, FALSE, 0),
('sorrel_leaf',        'Sheep sorrel leaf',  'FOOD', 8,  18, TRUE, FALSE, 0),
('wild_strawberry',    'Wild strawberry',    'FOOD', 6,  8,  TRUE, FALSE, 0),
('bilberry',           'Bilberry',           'FOOD', 4,  5,  TRUE, FALSE, 0),
('blackberry',         'Blackberry',         'FOOD', 7,  9,  TRUE, FALSE, 0),
('rose_hip',           'Rose hip',           'FOOD', 9,  12, TRUE, FALSE, 0),
('rowan_berry',        'Rowan berry',        'FOOD', 5,  6,  TRUE, FALSE, 0),
('beech_mast',         'Beech mast',         'FOOD', 8,  10, TRUE, FALSE, 0),
('pignut_tuber',       'Pignut tuber',       'FOOD', 20, 22, TRUE, FALSE, 0),
('silverweed_root',    'Silverweed root',    'FOOD', 30, 32, TRUE, FALSE, 0),
('fat_hen_greens',     'Fat-hen greens',     'FOOD', 12, 25, TRUE, FALSE, 0),
('hawthorn_haw',       'Hawthorn haw',       'FOOD', 6,  8,  TRUE, FALSE, 0),
('sloe',               'Sloe',               'FOOD', 8,  9,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('wild_carrot',    'HERB',  'GRASSLAND',                   NULL, 25, FALSE),
('burdock',        'HERB',  'TEMPERATE_FOREST,GRASSLAND',  NULL, 30, FALSE),
('sheep_sorrel',   'HERB',  'GRASSLAND,HIGHLAND',          NULL, 18, FALSE),
('wild_strawberry','HERB',  'TEMPERATE_FOREST,GRASSLAND',  NULL, 20, FALSE),
('bilberry_shrub', 'SHRUB', 'HIGHLAND',                    NULL, 30, FALSE),
('bramble_berry',  'SHRUB', 'TEMPERATE_FOREST,GRASSLAND',  NULL, 25, FALSE),
('dog_rose',       'SHRUB', 'TEMPERATE_FOREST,HIGHLAND',   NULL, 30, FALSE),
('rowan_tree',     'SHRUB', 'HIGHLAND,MOUNTAIN',           NULL, 40, FALSE),
('beech_tree',     'SHRUB', 'TEMPERATE_FOREST',            NULL, 40, FALSE),
('pignut',         'HERB',  'TEMPERATE_FOREST,GRASSLAND',  NULL, 25, FALSE),
('silverweed',     'HERB',  'WETLAND,GRASSLAND',           NULL, 22, FALSE),
('fat_hen',        'HERB',  'GRASSLAND',                   NULL, 18, FALSE),
('hawthorn_shrub', 'SHRUB', 'TEMPERATE_FOREST,GRASSLAND',  NULL, 40, FALSE),
('blackthorn',     'SHRUB', 'TEMPERATE_FOREST',            NULL, 40, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('wild_carrot',    'wild_carrot_root', 1, 2, NULL),
('burdock',        'burdock_root',     1, 2, NULL),
('sheep_sorrel',   'sorrel_leaf',      2, 5, NULL),
('wild_strawberry','wild_strawberry',  3, 8, 'SUMMER'),
('bilberry_shrub', 'bilberry',         4, 10,'SUMMER'),
('bramble_berry',  'blackberry',       3, 9, 'AUTUMN'),
('dog_rose',       'rose_hip',         2, 6, 'AUTUMN'),
('rowan_tree',     'rowan_berry',      4, 10,'AUTUMN'),
('beech_tree',     'beech_mast',       3, 8, 'AUTUMN'),
('pignut',         'pignut_tuber',     1, 3, NULL),
('silverweed',     'silverweed_root',  1, 3, NULL),
('fat_hen',        'fat_hen_greens',   2, 5, NULL),
('hawthorn_shrub', 'hawthorn_haw',     3, 8, 'AUTUMN'),
('blackthorn',     'sloe',             2, 6, 'AUTUMN')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('wild_carrot_root', 'FLORA_DROP', 'dig wild carrot roots in grassland'),
('burdock_root',     'FLORA_DROP', 'dig burdock roots in forest and grassland'),
('sorrel_leaf',      'FLORA_DROP', 'pick sheep sorrel leaves in grassland and highland'),
('wild_strawberry',  'FLORA_DROP', 'pick wild strawberries in forest and grassland in summer'),
('bilberry',         'FLORA_DROP', 'pick bilberries on highland heath in summer'),
('blackberry',       'FLORA_DROP', 'pick blackberries from bramble in autumn'),
('rose_hip',         'FLORA_DROP', 'gather rose hips from dog rose in autumn'),
('rowan_berry',      'FLORA_DROP', 'gather rowan berries in highland in autumn'),
('beech_mast',       'FLORA_DROP', 'gather beech mast under beech in autumn'),
('pignut_tuber',     'FLORA_DROP', 'dig pignut tubers in forest and grassland'),
('silverweed_root',  'FLORA_DROP', 'dig silverweed roots on wet ground and grassland'),
('fat_hen_greens',   'FLORA_DROP', 'gather fat-hen greens in grassland'),
('hawthorn_haw',     'FLORA_DROP', 'gather hawthorn haws in autumn'),
('sloe',             'FLORA_DROP', 'gather sloes from blackthorn in autumn')
ON CONFLICT (item_key, source_kind) DO NOTHING;
