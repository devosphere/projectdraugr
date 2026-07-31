-- V41: Wildlife species registry — the canonical catalogue of land animals.
-- Populations (V7) carry only a species_key; this table is the single source of
-- truth for what a species IS: its class, role, movement, size, combat
-- resistance, and the behavioural flags (ambush, pack, territorial) the FSM and
-- the encounter/taming/tracking systems read. Movement class and these flags are
-- intrinsic to a species, so they live here, keyed by species, rather than being
-- denormalised onto every population row. See docs/systems/11.1-Species-Registry.md.

CREATE TABLE wildlife_species (
    species_key      VARCHAR(80)  PRIMARY KEY,
    kingdom_class    VARCHAR(20)  NOT NULL,   -- MAMMALIA, REPTILIA, AMPHIBIA, AVES, PISCES
    ecological_role  VARCHAR(20)  NOT NULL,   -- CARNIVORE, HERBIVORE, OMNIVORE, SCAVENGER
    activity_cycle   VARCHAR(20)  NOT NULL,   -- DIURNAL, NOCTURNAL, CREPUSCULAR
    movement_class   VARCHAR(20)  NOT NULL,   -- TERRESTRIAL, AERIAL, AQUATIC, AMPHIBIOUS
    size_tier        VARCHAR(20)  NOT NULL,   -- TINY, SMALL, MEDIUM, LARGE, HUGE
    base_resistance  SMALLINT     NOT NULL,   -- combat resistance in the encounter model
    ambush_hunter    BOOLEAN      NOT NULL DEFAULT FALSE,  -- strikes without an ALERT tell
    pack_hunter      BOOLEAN      NOT NULL DEFAULT FALSE,  -- coordinates; hunts as PACK_HUNT
    territorial      BOOLEAN      NOT NULL DEFAULT FALSE,  -- defends a range when intruded upon
    tamability       SMALLINT     NOT NULL DEFAULT 0,      -- 0-100; 0 = untamable
    biome_affinity   VARCHAR(160) NOT NULL,
    CONSTRAINT wildlife_species_class_check CHECK (kingdom_class IN ('MAMMALIA','REPTILIA','AMPHIBIA','AVES','PISCES')),
    CONSTRAINT wildlife_species_role_check CHECK (ecological_role IN ('CARNIVORE','HERBIVORE','OMNIVORE','SCAVENGER')),
    CONSTRAINT wildlife_species_movement_check CHECK (movement_class IN ('TERRESTRIAL','AERIAL','AQUATIC','AMPHIBIOUS')),
    CONSTRAINT wildlife_species_tamability_check CHECK (tamability BETWEEN 0 AND 100)
);

INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- Mammalia — carnivores and omnivores
('gray_wolf',       'MAMMALIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','MEDIUM', 55, FALSE, TRUE,  TRUE,  15, 'TEMPERATE_FOREST,HIGHLAND,MOUNTAIN'),
('red_fox',         'MAMMALIA','OMNIVORE', 'CREPUSCULAR','TERRESTRIAL','SMALL',  35, FALSE, FALSE, TRUE,  30, 'TEMPERATE_FOREST,GRASSLAND,HIGHLAND'),
('forest_fox',      'MAMMALIA','OMNIVORE', 'CREPUSCULAR','TERRESTRIAL','SMALL',  35, FALSE, FALSE, TRUE,  30, 'TEMPERATE_FOREST'),
('arctic_fox',      'MAMMALIA','OMNIVORE', 'CREPUSCULAR','TERRESTRIAL','SMALL',  35, FALSE, FALSE, TRUE,  30, 'MOUNTAIN,HIGHLAND'),
('golden_jackal',   'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  45, FALSE, TRUE,  TRUE,  25, 'GRASSLAND,HIGHLAND'),
('european_wildcat','MAMMALIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','SMALL',  48, TRUE,  FALSE, TRUE,  10, 'TEMPERATE_FOREST,HIGHLAND'),
('eurasian_lynx',   'MAMMALIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','MEDIUM', 62, TRUE,  FALSE, TRUE,   8, 'TEMPERATE_FOREST,MOUNTAIN'),
('brown_bear',      'MAMMALIA','OMNIVORE', 'CREPUSCULAR','TERRESTRIAL','LARGE',  95, FALSE, FALSE, TRUE,  10, 'TEMPERATE_FOREST,HIGHLAND,MOUNTAIN'),
('cave_bear',       'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','HUGE',  110, FALSE, FALSE, TRUE,   5, 'MOUNTAIN,HIGHLAND'),
('stoat',           'MAMMALIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','TINY',   20, TRUE,  FALSE, TRUE,  20, 'TEMPERATE_FOREST,GRASSLAND'),
('european_badger', 'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','MEDIUM', 45, FALSE, FALSE, TRUE,  20, 'TEMPERATE_FOREST,GRASSLAND'),
('pine_marten',     'MAMMALIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','SMALL',  30, TRUE,  FALSE, TRUE,  25, 'TEMPERATE_FOREST'),
('wolverine',       'MAMMALIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','MEDIUM', 70, FALSE, FALSE, TRUE,   8, 'MOUNTAIN,HIGHLAND'),
('river_otter',     'MAMMALIA','CARNIVORE','DIURNAL',    'AMPHIBIOUS', 'SMALL',  35, FALSE, FALSE, TRUE,  35, 'WETLAND'),
-- Mammalia — herbivores
('red_deer',        'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','LARGE',  45, FALSE, FALSE, FALSE, 40, 'TEMPERATE_FOREST,HIGHLAND'),
('elk',             'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','LARGE',  60, FALSE, FALSE, TRUE,  30, 'TEMPERATE_FOREST,WETLAND'),
('aurochs',         'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','HUGE',   90, FALSE, FALSE, TRUE,  25, 'GRASSLAND,TEMPERATE_FOREST'),
('wild_boar',       'MAMMALIA','OMNIVORE', 'DIURNAL',    'TERRESTRIAL','MEDIUM', 65, FALSE, FALSE, TRUE,  20, 'TEMPERATE_FOREST,GRASSLAND'),
('mountain_goat',   'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM', 40, FALSE, FALSE, FALSE, 60, 'MOUNTAIN,HIGHLAND'),
('reindeer',        'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','LARGE',  45, FALSE, FALSE, FALSE, 50, 'HIGHLAND,MOUNTAIN'),
('hare',            'MAMMALIA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','SMALL',  15, FALSE, FALSE, FALSE, 40, 'GRASSLAND,TEMPERATE_FOREST'),
('rabbit',          'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','SMALL',  12, FALSE, FALSE, FALSE, 80, 'GRASSLAND,TEMPERATE_FOREST'),
('red_squirrel',    'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','TINY',    8, FALSE, FALSE, FALSE, 55, 'TEMPERATE_FOREST'),
('beaver',          'MAMMALIA','HERBIVORE','CREPUSCULAR','AMPHIBIOUS', 'MEDIUM', 30, FALSE, FALSE, TRUE,  35, 'WETLAND'),
('common_mole',     'MAMMALIA','CARNIVORE','ALL',        'TERRESTRIAL','TINY',    8, FALSE, FALSE, TRUE,  20, 'GRASSLAND,TEMPERATE_FOREST'),
('field_mouse',     'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 30, 'GRASSLAND,TEMPERATE_FOREST'),
('hedgehog',        'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',   15, FALSE, FALSE, FALSE, 90, 'TEMPERATE_FOREST,GRASSLAND'),
('common_bat',      'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'AERIAL',     'TINY',    6, FALSE, FALSE, FALSE, 10, 'TEMPERATE_FOREST,MOUNTAIN'),
('marsh_fowl',      'AVES',    'OMNIVORE', 'DIURNAL',    'TERRESTRIAL','SMALL',  18, FALSE, FALSE, FALSE, 80, 'WETLAND'),
-- Reptilia
('grass_snake',     'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','SMALL',  20, FALSE, FALSE, FALSE, 15, 'GRASSLAND,WETLAND,TEMPERATE_FOREST'),
('common_adder',    'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','SMALL',  30, TRUE,  FALSE, FALSE,  5, 'HIGHLAND,GRASSLAND,TEMPERATE_FOREST'),
('constrictor_snake','REPTILIA','CARNIVORE','NOCTURNAL', 'TERRESTRIAL','MEDIUM', 45, TRUE,  FALSE, FALSE,  5, 'TEMPERATE_FOREST,WETLAND'),
('common_lizard',   'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','TINY',   10, FALSE, FALSE, FALSE, 20, 'GRASSLAND,HIGHLAND'),
('monitor_lizard',  'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM', 55, TRUE,  FALSE, TRUE,  10, 'WETLAND,GRASSLAND'),
('river_turtle',    'REPTILIA','OMNIVORE', 'DIURNAL',    'AMPHIBIOUS', 'SMALL',  35, FALSE, FALSE, FALSE, 95, 'WETLAND'),
('crocodilian',     'REPTILIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS', 'LARGE', 100, TRUE,  FALSE, TRUE,   5, 'WETLAND'),
-- Amphibia
('common_frog',     'AMPHIBIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS', 'TINY',    8, FALSE, FALSE, FALSE, 20, 'WETLAND'),
('common_toad',     'AMPHIBIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',   10, FALSE, FALSE, FALSE, 20, 'WETLAND,TEMPERATE_FOREST'),
('fire_salamander', 'AMPHIBIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',   12, FALSE, FALSE, FALSE, 15, 'TEMPERATE_FOREST,WETLAND'),
('great_crested_newt','AMPHIBIA','CARNIVORE','NOCTURNAL','AMPHIBIOUS', 'TINY',    8, FALSE, FALSE, FALSE, 20, 'WETLAND')
ON CONFLICT (species_key) DO NOTHING;

-- Register the taxonomic domains this registry covers
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin) VALUES
('mammalia', 'Mammalia', 'Land mammals — carnivores, omnivores, herbivores, and their combat, movement, and taming traits.', 'V41', 'PREBUILT'),
('reptilia', 'Reptilia', 'Snakes, lizards, turtles, and crocodilians, including ambush and venom traits.', 'V41', 'PREBUILT'),
('amphibia', 'Amphibia', 'Frogs, toads, salamanders, and newts of the wetland margins.', 'V41', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
