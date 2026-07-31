-- V39: Flora system — plants, trees, fungi, aquatic flora
-- Adds FLORA category to ecology_site, flora tables, 46 plant species, 46+ item definitions.

-- 1. Expand ecology_site.site_category CHECK to include FLORA
ALTER TABLE ecology_site DROP CONSTRAINT IF EXISTS ecology_site_site_category_check;
ALTER TABLE ecology_site ADD CONSTRAINT ecology_site_site_category_check
    CHECK (site_category IN ('RESOURCE','WILDLIFE','MONSTER','RUIN','FLORA'));

-- 2. Flora definition table
CREATE TABLE flora_definition (
    flora_key        VARCHAR(80)  PRIMARY KEY,
    organism_type    VARCHAR(30)  NOT NULL, -- TREE, SHRUB, HERB, FUNGI, AQUATIC
    biome_affinity   VARCHAR(120) NOT NULL, -- comma-separated biome codes
    tool_required    VARCHAR(80),           -- NULL = bare hand; 'AXE_CLASS', 'KNIFE_CLASS', etc.
    regrowth_days    SMALLINT     NOT NULL DEFAULT 7,
    is_poisonous     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT flora_definition_organism_type_check
        CHECK (organism_type IN ('TREE','SHRUB','HERB','FUNGI','AQUATIC'))
);

-- 3. Flora drop table (what a flora_key yields when gathered/felled)
CREATE TABLE flora_drop (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    flora_key        VARCHAR(80)  NOT NULL REFERENCES flora_definition(flora_key),
    item_key         VARCHAR(80)  NOT NULL REFERENCES item_definition(item_key),
    yield_min        SMALLINT     NOT NULL DEFAULT 1,
    yield_max        SMALLINT     NOT NULL DEFAULT 1,
    season           VARCHAR(20),           -- NULL = any season; SPRING, SUMMER, AUTUMN, WINTER
    tool_condition   VARCHAR(80)            -- NULL = any; restricts which tool produces this drop
);

-- 4. Chunk flora — live flora instances per map chunk
CREATE TABLE chunk_flora (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id         UUID         NOT NULL REFERENCES world_chunk(id),
    flora_key        VARCHAR(80)  NOT NULL REFERENCES flora_definition(flora_key),
    quantity         SMALLINT     NOT NULL DEFAULT 1,
    last_harvested_at TIMESTAMPTZ
);

CREATE INDEX chunk_flora_chunk_idx ON chunk_flora(chunk_id);

-- 5. Item definitions for flora drops
-- Columns: item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
-- Trees
('oak_log',           'Oak Log',              'MATERIAL',  8000, 40000, FALSE, FALSE),
('birch_log',         'Birch Log',            'MATERIAL',  6000, 35000, FALSE, FALSE),
('pine_log',          'Pine Log',             'MATERIAL',  7000, 38000, FALSE, FALSE),
('ash_log',           'Ash Log',              'MATERIAL',  7500, 38000, FALSE, FALSE),
('willow_branch',     'Willow Branch',        'MATERIAL',   800,  2000, TRUE,  FALSE),
('maple_log',         'Maple Log',            'MATERIAL',  7000, 38000, FALSE, FALSE),
('hazel_rod',         'Hazel Rod',            'MATERIAL',   400,  1000, TRUE,  FALSE),
('juniper_berry',     'Juniper Berry',        'FOOD',        10,    20, TRUE,  FALSE),
('spruce_log',        'Spruce Log',           'MATERIAL',  7500, 38000, FALSE, FALSE),
('pine_resin',        'Pine Resin',           'MATERIAL',   200,   150, TRUE,  FALSE),
('maple_sap',         'Maple Sap',            'FOOD',       500,   500, TRUE,  FALSE),
('oak_bark',          'Oak Bark',             'MATERIAL',   300,   400, TRUE,  FALSE),
-- Shrubs
('wild_rose_hip',     'Wild Rose Hip',        'FOOD',        15,    20, TRUE,  FALSE),
('wild_rose_thorn',   'Rose Thorn',           'MATERIAL',     5,     5, TRUE,  FALSE),
('blackberry',        'Blackberry',           'FOOD',         8,    10, TRUE,  FALSE),
('elderberry',        'Elderberry',           'FOOD',         6,     8, TRUE,  FALSE),
('elder_flower',      'Elder Flower',         'MATERIAL',    10,    15, TRUE,  FALSE),
('hawthorn_berry',    'Hawthorn Berry',       'FOOD',         5,     8, TRUE,  FALSE),
('hawthorn_thorn',    'Hawthorn Thorn',       'MATERIAL',     3,     4, TRUE,  FALSE),
-- Herbs
('nettle_fiber',      'Nettle Fiber',         'MATERIAL',    30,    50, TRUE,  FALSE),
('nettle_leaf',       'Nettle Leaf',          'FOOD',        10,    15, TRUE,  FALSE),
('cattail_stalk',     'Cattail Stalk',        'MATERIAL',   200,   400, TRUE,  FALSE),
('cattail_fluff',     'Cattail Fluff',        'MATERIAL',    20,   200, TRUE,  FALSE),
('yarrow_bundle',     'Yarrow Bundle',        'MATERIAL',    50,    80, TRUE,  FALSE),
('plantain_leaf',     'Plantain Leaf',        'MATERIAL',    15,    25, TRUE,  FALSE),
('comfrey_leaf',      'Comfrey Leaf',         'MATERIAL',    20,    30, TRUE,  FALSE),
('mint_sprig',        'Mint Sprig',           'FOOD',        10,    15, TRUE,  FALSE),
('dandelion',         'Dandelion',            'FOOD',         8,    12, TRUE,  FALSE),
('wild_garlic_bulb',  'Wild Garlic Bulb',     'FOOD',        30,    40, TRUE,  FALSE),
('burdock_root',      'Burdock Root',         'FOOD',       150,   200, TRUE,  FALSE),
('watercress_bundle', 'Watercress Bundle',    'FOOD',        40,    60, TRUE,  FALSE),
-- Fungi
('chanterelle',       'Chanterelle',          'FOOD',        80,   120, TRUE,  FALSE),
('porcini',           'Porcini Mushroom',     'FOOD',       120,   180, TRUE,  FALSE),
('oyster_mushroom',   'Oyster Mushroom',      'FOOD',        60,   100, TRUE,  FALSE),
('birch_polypore',    'Birch Polypore',       'MATERIAL',   100,   150, TRUE,  FALSE),
('death_cap',         'Death Cap',            'FOOD',        50,    80, TRUE,  FALSE),
('fly_agaric',        'Fly Agaric',           'FOOD',        60,    90, TRUE,  FALSE),
('lions_mane',        'Lion''s Mane',         'FOOD',       200,   300, TRUE,  FALSE),
-- Aquatic
('reed_bundle',       'Reed Bundle',          'MATERIAL',   300,   800, TRUE,  FALSE),
('water_lily_pad',    'Water Lily Pad',       'MATERIAL',   100,   300, TRUE,  FALSE),
('bulrush_root',      'Bulrush Root',         'FOOD',       180,   250, TRUE,  FALSE),
('bulrush_stalk',     'Bulrush Stalk',        'MATERIAL',   250,   500, TRUE,  FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- 6. Flora definitions — 46 species
-- biome_affinity uses the world's canonical biome codes: TEMPERATE_FOREST, HIGHLAND, MOUNTAIN, GRASSLAND, WETLAND.
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
-- Trees (require AXE_CLASS to fell for logs; bare hand picks loose drops)
('oak',           'TREE',   'TEMPERATE_FOREST,HIGHLAND',          'AXE_CLASS', 365, FALSE),
('birch',         'TREE',   'TEMPERATE_FOREST,HIGHLAND',          'AXE_CLASS', 300, FALSE),
('pine',          'TREE',   'TEMPERATE_FOREST,MOUNTAIN,HIGHLAND', 'AXE_CLASS', 400, FALSE),
('ash',           'TREE',   'TEMPERATE_FOREST,HIGHLAND',          'AXE_CLASS', 350, FALSE),
('willow',        'TREE',   'WETLAND',                            'AXE_CLASS', 200, FALSE),
('maple',         'TREE',   'TEMPERATE_FOREST,HIGHLAND',          'AXE_CLASS', 365, FALSE),
('hazel',         'SHRUB',  'TEMPERATE_FOREST,HIGHLAND',          NULL,          60, FALSE),
('juniper',       'SHRUB',  'HIGHLAND,MOUNTAIN',                  NULL,          90, FALSE),
('spruce',        'TREE',   'MOUNTAIN,TEMPERATE_FOREST',          'AXE_CLASS', 400, FALSE),
-- Shrubs
('wild_rose',     'SHRUB',  'TEMPERATE_FOREST,HIGHLAND,GRASSLAND',NULL,          14, FALSE),
('blackberry',    'SHRUB',  'TEMPERATE_FOREST,GRASSLAND',         NULL,           7, FALSE),
('elderberry',    'SHRUB',  'TEMPERATE_FOREST,WETLAND',           NULL,          14, FALSE),
('hawthorn',      'SHRUB',  'TEMPERATE_FOREST,HIGHLAND,GRASSLAND',NULL,          30, FALSE),
-- Herbs
('nettle',        'HERB',   'TEMPERATE_FOREST,GRASSLAND,WETLAND', NULL,          10, FALSE),
('cattail',       'HERB',   'WETLAND',                            NULL,          21, FALSE),
('yarrow',        'HERB',   'GRASSLAND,HIGHLAND',                 NULL,          14, FALSE),
('plantain_herb', 'HERB',   'GRASSLAND,TEMPERATE_FOREST',         NULL,           7, FALSE),
('comfrey',       'HERB',   'WETLAND,TEMPERATE_FOREST',           NULL,          21, FALSE),
('mint',          'HERB',   'WETLAND',                            NULL,           7, FALSE),
('dandelion',     'HERB',   'GRASSLAND,TEMPERATE_FOREST,HIGHLAND',NULL,           5, FALSE),
('wild_garlic',   'HERB',   'TEMPERATE_FOREST',                   NULL,          14, FALSE),
('burdock',       'HERB',   'GRASSLAND,TEMPERATE_FOREST',         NULL,          30, FALSE),
('watercress',    'HERB',   'WETLAND',                            NULL,           7, FALSE),
-- Fungi
('chanterelle',   'FUNGI',  'TEMPERATE_FOREST',                   NULL,          21, FALSE),
('porcini',       'FUNGI',  'TEMPERATE_FOREST,HIGHLAND',          NULL,          21, FALSE),
('oyster_mushroom','FUNGI', 'TEMPERATE_FOREST',                   NULL,          14, FALSE),
('birch_polypore','FUNGI',  'TEMPERATE_FOREST',                   NULL,          14, FALSE),
('death_cap',     'FUNGI',  'TEMPERATE_FOREST',                   NULL,          28, TRUE),
('fly_agaric',    'FUNGI',  'TEMPERATE_FOREST',                   NULL,          28, TRUE),
('lions_mane',    'FUNGI',  'TEMPERATE_FOREST',                   NULL,          21, FALSE),
-- Aquatic
('reed_bed',      'AQUATIC','WETLAND',                            NULL,          21, FALSE),
('water_lily',    'AQUATIC','WETLAND',                            NULL,          30, FALSE),
('bulrush',       'AQUATIC','WETLAND',                            NULL,          21, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

-- 7. Flora drop table — what each species yields
INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season, tool_condition) VALUES
-- Oak: logs when felled, bark any time
('oak',            'oak_log',          1, 3, NULL,     'AXE_CLASS'),
('oak',            'oak_bark',         2, 4, NULL,     NULL),
-- Birch
('birch',          'birch_log',        1, 3, NULL,     'AXE_CLASS'),
-- Pine
('pine',           'pine_log',         1, 3, NULL,     'AXE_CLASS'),
('pine',           'pine_resin',       1, 2, NULL,     NULL),
-- Ash
('ash',            'ash_log',          1, 3, NULL,     'AXE_CLASS'),
-- Willow
('willow',         'willow_branch',    3, 6, NULL,     NULL),
-- Maple
('maple',          'maple_log',        1, 3, NULL,     'AXE_CLASS'),
('maple',          'maple_sap',        1, 3, 'SPRING', NULL),
-- Hazel
('hazel',          'hazel_rod',        2, 5, NULL,     NULL),
-- Juniper
('juniper',        'juniper_berry',    5,15, 'AUTUMN',  NULL),
-- Spruce
('spruce',         'spruce_log',       1, 3, NULL,     'AXE_CLASS'),
-- Wild rose
('wild_rose',      'wild_rose_hip',    3,10, 'AUTUMN',  NULL),
('wild_rose',      'wild_rose_thorn',  2, 5, NULL,     NULL),
-- Blackberry
('blackberry',     'blackberry',       5,20, 'SUMMER',  NULL),
('blackberry',     'blackberry',       3,10, 'AUTUMN',  NULL),
-- Elderberry
('elderberry',     'elderberry',       5,15, 'AUTUMN',  NULL),
('elderberry',     'elder_flower',     3, 8, 'SPRING',  NULL),
-- Hawthorn
('hawthorn',       'hawthorn_berry',   5,15, 'AUTUMN',  NULL),
('hawthorn',       'hawthorn_thorn',   2, 4, NULL,     NULL),
-- Nettle
('nettle',         'nettle_fiber',     2, 5, NULL,     NULL),
('nettle',         'nettle_leaf',      4,10, NULL,     NULL),
-- Cattail
('cattail',        'cattail_stalk',    2, 4, NULL,     NULL),
('cattail',        'cattail_fluff',    1, 3, 'SUMMER',  NULL),
-- Yarrow
('yarrow',         'yarrow_bundle',    1, 3, 'SUMMER',  NULL),
-- Plantain herb
('plantain_herb',  'plantain_leaf',    3, 8, NULL,     NULL),
-- Comfrey
('comfrey',        'comfrey_leaf',     2, 5, NULL,     NULL),
-- Mint
('mint',           'mint_sprig',       3, 8, NULL,     NULL),
-- Dandelion
('dandelion',      'dandelion',        2, 6, NULL,     NULL),
-- Wild garlic
('wild_garlic',    'wild_garlic_bulb', 2, 5, 'SPRING',  NULL),
('wild_garlic',    'wild_garlic_bulb', 1, 3, 'SUMMER',  NULL),
-- Burdock
('burdock',        'burdock_root',     1, 2, 'AUTUMN',  NULL),
-- Watercress
('watercress',     'watercress_bundle',2, 5, NULL,     NULL),
-- Fungi
('chanterelle',    'chanterelle',      2, 6, 'SUMMER',  NULL),
('chanterelle',    'chanterelle',      3, 8, 'AUTUMN',  NULL),
('porcini',        'porcini',          1, 4, 'SUMMER',  NULL),
('porcini',        'porcini',          2, 5, 'AUTUMN',  NULL),
('oyster_mushroom','oyster_mushroom',  2, 5, NULL,     NULL),
('birch_polypore', 'birch_polypore',   1, 3, NULL,     NULL),
('death_cap',      'death_cap',        1, 3, 'SUMMER',  NULL),
('death_cap',      'death_cap',        1, 3, 'AUTUMN',  NULL),
('fly_agaric',     'fly_agaric',       1, 3, 'SUMMER',  NULL),
('fly_agaric',     'fly_agaric',       1, 3, 'AUTUMN',  NULL),
('lions_mane',     'lions_mane',       1, 2, 'AUTUMN',  NULL),
-- Aquatic
('reed_bed',       'reed_bundle',      2, 6, NULL,     NULL),
('water_lily',     'water_lily_pad',   1, 3, 'SUMMER',  NULL),
('bulrush',        'bulrush_root',     1, 3, NULL,     NULL),
('bulrush',        'bulrush_stalk',    2, 5, NULL,     NULL);

-- 8. Register flora and botany domains
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES
    ('flora',  'Flora',  'Trees, shrubs, herbs, fungi, and aquatic plants — gathering, felling, and seasonal yields.', 'V39', 'PREBUILT'),
    ('botany', 'Botany', 'Applied plant knowledge: poison identification, medicinal use, fiber processing.', 'V39', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
