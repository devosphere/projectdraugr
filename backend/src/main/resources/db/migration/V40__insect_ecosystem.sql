-- V40: Insect ecosystem — colonies, products, and the two harvest intents.
-- Insects are their own ecological layer: pollinators, decomposers, disease
-- vectors, food, and material producers. See docs/systems/09.2-Insect-Catalogue.md.

-- 1. Add INSECT to the ecology_site category CHECK
ALTER TABLE ecology_site DROP CONSTRAINT IF EXISTS ecology_site_site_category_check;
ALTER TABLE ecology_site ADD CONSTRAINT ecology_site_site_category_check
    CHECK (site_category IN ('RESOURCE','WILDLIFE','MONSTER','RUIN','FLORA','INSECT'));

-- 2. Colony kind definitions — one row per species of colony
CREATE TABLE insect_colony_kind (
    colony_kind       VARCHAR(60)  PRIMARY KEY,
    biome_affinity    VARCHAR(160) NOT NULL,               -- canonical biome codes, comma-joined
    activity_cycle    VARCHAR(20)  NOT NULL,               -- DIURNAL, NOCTURNAL, CREPUSCULAR, ALL
    season_active     VARCHAR(60)  NOT NULL,               -- comma-joined seasons, or ALL
    harvest_intent    VARCHAR(20),                         -- RAID_HIVE, COLLECT_INSECTS, or NULL (not harvestable)
    hazard_kind       VARCHAR(20),                         -- STING, BITE, VENOM, ILLNESS, or NULL
    hazard_min        SMALLINT     NOT NULL DEFAULT 0,
    hazard_max        SMALLINT     NOT NULL DEFAULT 0,
    smoke_suppresses  BOOLEAN      NOT NULL DEFAULT FALSE,  -- honeybee stings suppressed by smoke; hornets are not
    pollination_bonus SMALLINT     NOT NULL DEFAULT 0,      -- % flora regrowth uplift while present in a chunk
    regrowth_days     SMALLINT     NOT NULL DEFAULT 7,
    CONSTRAINT insect_colony_kind_activity_check CHECK (activity_cycle IN ('DIURNAL','NOCTURNAL','CREPUSCULAR','ALL')),
    CONSTRAINT insect_colony_kind_harvest_check CHECK (harvest_intent IS NULL OR harvest_intent IN ('RAID_HIVE','COLLECT_INSECTS')),
    CONSTRAINT insect_colony_kind_hazard_check CHECK (hazard_kind IS NULL OR hazard_kind IN ('STING','BITE','VENOM','ILLNESS'))
);

-- 3. What each colony kind yields, and how reliably
CREATE TABLE insect_colony_product (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    colony_kind  VARCHAR(60)  NOT NULL REFERENCES insect_colony_kind(colony_kind),
    item_key     VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    yield_min    SMALLINT     NOT NULL DEFAULT 1,
    yield_max    SMALLINT     NOT NULL DEFAULT 1,
    rarity       NUMERIC(3,2) NOT NULL DEFAULT 1.0          -- probability this product appears in a harvest (0-1)
);

-- 4. Physical colony instances — extends world_object. Populated at runtime as
-- colonies are seeded into or discovered in the living world (the map does not
-- exist at migration time), mirroring how chunk_flora is filled after genesis.
CREATE TABLE insect_colony (
    object_id         UUID        PRIMARY KEY REFERENCES world_object(id),
    colony_kind       VARCHAR(60) NOT NULL REFERENCES insect_colony_kind(colony_kind),
    chunk_id          UUID        NOT NULL REFERENCES world_chunk(id),
    health            SMALLINT    NOT NULL DEFAULT 100,
    product_ready_at  TIMESTAMPTZ,
    last_disturbed_at TIMESTAMPTZ
);
CREATE INDEX insect_colony_chunk_idx ON insect_colony(chunk_id);

-- 5. Item definitions for insect products
-- Columns: item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('raw_honey',          'Raw Honey',           'FOOD',      120,  100, TRUE, FALSE),
('beeswax',            'Beeswax',             'MATERIAL',   40,   45, TRUE, FALSE),
('propolis',           'Propolis',            'MATERIAL',   20,   20, TRUE, FALSE),
('hornet_venom',       'Hornet Venom',        'MATERIAL',   10,   10, TRUE, FALSE),
('silk_fiber',         'Silk Fiber',          'MATERIAL',   15,   40, TRUE, FALSE),
('chitin_fragment',    'Chitin Fragment',     'MATERIAL',   12,   18, TRUE, FALSE),
('formic_acid',        'Formic Acid',         'MATERIAL',   30,   30, TRUE, FALSE),
('dried_grasshopper',  'Dried Grasshopper',   'FOOD',        6,   12, TRUE, FALSE),
('dried_cricket',      'Dried Cricket',       'FOOD',        5,   10, TRUE, FALSE),
('earthworm',          'Earthworm',           'MATERIAL',    8,   10, TRUE, FALSE),
('spider_silk_thread', 'Spider Silk Thread',  'MATERIAL',    4,    8, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- 6. Colony kind definitions — 11 colonies (biome_affinity uses canonical codes:
-- TEMPERATE_FOREST, HIGHLAND, MOUNTAIN, GRASSLAND, WETLAND)
INSERT INTO insect_colony_kind (colony_kind, biome_affinity, activity_cycle, season_active, harvest_intent, hazard_kind, hazard_min, hazard_max, smoke_suppresses, pollination_bonus, regrowth_days) VALUES
('honeybee_hive',    'TEMPERATE_FOREST,HIGHLAND',                    'DIURNAL',     'SPRING,SUMMER,AUTUMN', 'RAID_HIVE',       'STING',  8, 15, TRUE,  30, 21),
('hornet_nest',      'TEMPERATE_FOREST,HIGHLAND',                    'DIURNAL',     'SUMMER,AUTUMN',        'RAID_HIVE',       'STING', 20, 40, FALSE,  0, 30),
('silk_moth_colony', 'TEMPERATE_FOREST',                             'NOCTURNAL',   'SPRING,SUMMER',        'COLLECT_INSECTS',  NULL,    0,  0, FALSE,  0, 21),
('ant_colony',       'TEMPERATE_FOREST,HIGHLAND',                    'DIURNAL',     'SPRING,SUMMER,AUTUMN', 'COLLECT_INSECTS', 'BITE',   2,  5, FALSE,  0, 14),
('grasshopper_swarm','GRASSLAND',                                    'DIURNAL',     'SUMMER,AUTUMN',        'COLLECT_INSECTS',  NULL,    0,  0, FALSE,  0, 10),
('cricket_colony',   'TEMPERATE_FOREST,GRASSLAND,MOUNTAIN',          'NOCTURNAL',   'SUMMER,AUTUMN',        'COLLECT_INSECTS',  NULL,    0,  0, FALSE,  0, 10),
('earthworm_patch',  'TEMPERATE_FOREST,HIGHLAND,MOUNTAIN,GRASSLAND,WETLAND', 'ALL', 'ALL',                 'COLLECT_INSECTS',  NULL,    0,  0, FALSE, 10,  7),
('mosquito_swarm',   'WETLAND',                                      'CREPUSCULAR', 'SPRING,SUMMER,AUTUMN',  NULL,             'ILLNESS',3,  3, FALSE,  0, 14),
('spider_den',       'MOUNTAIN,TEMPERATE_FOREST',                    'NOCTURNAL',   'ALL',                  'COLLECT_INSECTS', 'VENOM',  5, 10, FALSE,  0, 21),
('firefly_colony',   'TEMPERATE_FOREST,WETLAND',                     'NOCTURNAL',   'SUMMER',                NULL,              NULL,    0,  0, FALSE,  0, 30),
('dragonfly_patch',  'WETLAND',                                      'DIURNAL',     'SPRING,SUMMER',         NULL,              NULL,    0,  0, FALSE,  0, 21)
ON CONFLICT (colony_kind) DO NOTHING;

-- 7. Products per colony kind
INSERT INTO insect_colony_product (colony_kind, item_key, yield_min, yield_max, rarity) VALUES
('honeybee_hive',    'raw_honey',          1, 3, 1.00),
('honeybee_hive',    'beeswax',            1, 2, 0.80),
('honeybee_hive',    'propolis',           1, 1, 0.30),
('hornet_nest',      'hornet_venom',       1, 1, 0.50),
('silk_moth_colony', 'silk_fiber',         2, 5, 1.00),
('ant_colony',       'chitin_fragment',    2, 6, 1.00),
('ant_colony',       'formic_acid',        1, 2, 0.50),
('grasshopper_swarm','dried_grasshopper',  3, 8, 1.00),
('cricket_colony',   'dried_cricket',      3, 8, 1.00),
('earthworm_patch',  'earthworm',          2, 6, 1.00),
('spider_den',       'spider_silk_thread', 1, 3, 0.70);

-- 8. Register the insects domain
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('insects', 'Insects', 'Insect and invertebrate colonies — pollinators, decomposers, disease vectors, and producers of honey, silk, chitin, and bait.', 'V40', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
