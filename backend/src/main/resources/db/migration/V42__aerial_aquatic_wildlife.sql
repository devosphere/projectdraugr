-- V42: Aerial and aquatic wildlife, and the drop table for every species.
-- Completes the animal kingdoms begun in V41 (Aves, Pisces) and gives every
-- catalogued species its butchery yield, so HARVEST stops returning generic
-- meat and hide for a wolf, a trout, and an eagle alike.

-- 1. Aves and Pisces species
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- Aves
('golden_eagle',     'AVES','CARNIVORE','DIURNAL',    'AERIAL','MEDIUM', 50, TRUE,  FALSE, TRUE,  20, 'MOUNTAIN,HIGHLAND'),
('common_raven',     'AVES','SCAVENGER','DIURNAL',    'AERIAL','SMALL',  22, FALSE, FALSE, TRUE,  45, 'TEMPERATE_FOREST,HIGHLAND,MOUNTAIN'),
('barn_owl',         'AVES','CARNIVORE','NOCTURNAL',  'AERIAL','SMALL',  25, TRUE,  FALSE, TRUE,  30, 'TEMPERATE_FOREST,GRASSLAND'),
('peregrine_falcon', 'AVES','CARNIVORE','DIURNAL',    'AERIAL','SMALL',  35, TRUE,  FALSE, TRUE,  25, 'MOUNTAIN,HIGHLAND'),
('wood_pigeon',      'AVES','HERBIVORE','DIURNAL',    'AERIAL','SMALL',  12, FALSE, FALSE, FALSE, 85, 'TEMPERATE_FOREST,GRASSLAND'),
('mallard_duck',     'AVES','OMNIVORE', 'DIURNAL',    'AERIAL','SMALL',  15, FALSE, FALSE, FALSE, 75, 'WETLAND'),
('grey_heron',       'AVES','CARNIVORE','DIURNAL',    'AERIAL','MEDIUM', 28, TRUE,  FALSE, TRUE,  30, 'WETLAND'),
('vulture',          'AVES','SCAVENGER','DIURNAL',    'AERIAL','MEDIUM', 30, FALSE, FALSE, FALSE, 20, 'HIGHLAND,MOUNTAIN,GRASSLAND'),
('wild_turkey',      'AVES','OMNIVORE', 'DIURNAL',    'TERRESTRIAL','SMALL', 20, FALSE, FALSE, FALSE, 70, 'TEMPERATE_FOREST,GRASSLAND'),
('stork',            'AVES','CARNIVORE','DIURNAL',    'AERIAL','MEDIUM', 25, FALSE, FALSE, FALSE, 40, 'WETLAND,GRASSLAND'),
-- Pisces
('river_trout',      'PISCES','CARNIVORE','DIURNAL',  'AQUATIC','SMALL',  15, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('common_perch',     'PISCES','CARNIVORE','DIURNAL',  'AQUATIC','SMALL',  14, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('catfish',          'PISCES','OMNIVORE', 'NOCTURNAL','AQUATIC','MEDIUM', 30, TRUE,  FALSE, FALSE, 0, 'WETLAND'),
('freshwater_eel',   'PISCES','CARNIVORE','NOCTURNAL','AQUATIC','SMALL',  22, TRUE,  FALSE, FALSE, 0, 'WETLAND'),
('pike',             'PISCES','CARNIVORE','DIURNAL',  'AQUATIC','MEDIUM', 35, TRUE,  FALSE, TRUE,  0, 'WETLAND'),
('carp',             'PISCES','OMNIVORE', 'DIURNAL',  'AQUATIC','MEDIUM', 20, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('crayfish',         'PISCES','SCAVENGER','NOCTURNAL','AQUATIC','TINY',    8, FALSE, FALSE, FALSE, 0, 'WETLAND')
ON CONFLICT (species_key) DO NOTHING;

-- 2. Item definitions for wildlife drops
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('wolf_pelt',        'Wolf Pelt',        'MATERIAL', 1400, 3200, FALSE, FALSE),
('fox_pelt',         'Fox Pelt',         'MATERIAL',  700, 1800, FALSE, FALSE),
('bear_pelt',        'Bear Pelt',        'MATERIAL', 4200, 9000, FALSE, FALSE),
('lynx_pelt',        'Lynx Pelt',        'MATERIAL', 1100, 2600, FALSE, FALSE),
('rabbit_pelt',      'Rabbit Pelt',      'MATERIAL',  220,  600, TRUE,  FALSE),
('deer_hide',        'Deer Hide',        'MATERIAL', 2600, 5600, FALSE, FALSE),
('boar_hide',        'Boar Hide',        'MATERIAL', 2400, 5200, FALSE, FALSE),
('predator_fang',    'Predator Fang',    'MATERIAL',   18,   14, TRUE,  FALSE),
('predator_claw',    'Predator Claw',    'MATERIAL',   14,   12, TRUE,  FALSE),
('deer_antler',      'Deer Antler',      'MATERIAL',  900, 2200, FALSE, FALSE),
('boar_tusk',        'Boar Tusk',        'MATERIAL',  120,  100, TRUE,  FALSE),
('aurochs_horn',     'Aurochs Horn',     'MATERIAL', 1600, 3000, FALSE, FALSE),
('animal_sinew',     'Animal Sinew',     'MATERIAL',   40,   50, TRUE,  FALSE),
('animal_bone',      'Animal Bone',      'MATERIAL',  260,  340, TRUE,  FALSE),
('animal_fat',       'Animal Fat',       'MATERIAL',  300,  320, TRUE,  FALSE),
('snake_venom',      'Snake Venom',      'MATERIAL',   10,   10, TRUE,  FALSE),
('snake_skin',       'Snake Skin',       'MATERIAL',   90,  260, TRUE,  FALSE),
('turtle_shell',     'Turtle Shell',     'MATERIAL',  800, 1600, FALSE, FALSE),
('feather',          'Feather',          'MATERIAL',    3,   30, TRUE,  FALSE),
('raptor_talon',     'Raptor Talon',     'MATERIAL',   12,   10, TRUE,  FALSE),
('bird_egg',         'Bird Egg',         'FOOD',       55,   55, TRUE,  FALSE),
('raw_fowl_meat',    'Raw Fowl Meat',    'FOOD',      320,  400, FALSE, FALSE),
('raw_fish',         'Raw Fish',         'FOOD',      380,  420, FALSE, FALSE),
('fish_bone',        'Fish Bone',        'MATERIAL',   16,   30, TRUE,  FALSE),
('fish_oil',         'Fish Oil',         'MATERIAL',  120,  120, TRUE,  FALSE),
('crayfish_meat',    'Crayfish Meat',    'FOOD',       70,   80, TRUE,  FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- 3. Per-species butchery yields. A wolf gives pelt, fang, sinew; a trout gives
-- fish and oil. rarity is the probability the drop appears in a given harvest.
CREATE TABLE wildlife_drop (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    species_key VARCHAR(80)  NOT NULL REFERENCES wildlife_species(species_key),
    item_key    VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    yield_min   SMALLINT     NOT NULL DEFAULT 1,
    yield_max   SMALLINT     NOT NULL DEFAULT 1,
    rarity      NUMERIC(3,2) NOT NULL DEFAULT 1.0
);
CREATE INDEX wildlife_drop_species_idx ON wildlife_drop(species_key);

INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max, rarity) VALUES
-- Large predators
('gray_wolf',        'wolf_pelt',     1,1,1.00), ('gray_wolf',        'predator_fang', 1,3,0.80), ('gray_wolf',        'animal_sinew',1,2,0.70),
('brown_bear',       'bear_pelt',     1,1,1.00), ('brown_bear',       'predator_claw', 2,4,0.85), ('brown_bear',       'animal_fat',  2,4,0.90),
('cave_bear',        'bear_pelt',     1,1,1.00), ('cave_bear',        'predator_claw', 3,5,0.90), ('cave_bear',        'animal_fat',  3,5,0.90),
('eurasian_lynx',    'lynx_pelt',     1,1,1.00), ('eurasian_lynx',    'predator_fang', 1,2,0.75),
('european_wildcat', 'fox_pelt',      1,1,0.90), ('european_wildcat', 'predator_fang', 1,2,0.60),
('wolverine',        'fox_pelt',      1,1,0.90), ('wolverine',        'predator_claw', 1,3,0.75),
('red_fox',          'fox_pelt',      1,1,1.00), ('forest_fox',       'fox_pelt',      1,1,1.00), ('arctic_fox', 'fox_pelt', 1,1,1.00),
('golden_jackal',    'fox_pelt',      1,1,0.85), ('pine_marten',      'fox_pelt',      1,1,0.80), ('stoat', 'fox_pelt', 1,1,0.70),
('european_badger',  'fox_pelt',      1,1,0.85), ('european_badger',  'animal_fat',    1,2,0.70),
-- Large herbivores
('red_deer',         'deer_hide',     1,1,1.00), ('red_deer',         'deer_antler',   1,2,0.60), ('red_deer',   'animal_sinew',2,4,0.85),
('elk',              'deer_hide',     1,1,1.00), ('elk',              'deer_antler',   1,2,0.70), ('elk',        'animal_sinew',2,4,0.85),
('reindeer',         'deer_hide',     1,1,1.00), ('reindeer',         'deer_antler',   1,2,0.65),
('aurochs',          'deer_hide',     1,2,1.00), ('aurochs',          'aurochs_horn',  1,2,0.80), ('aurochs',    'animal_bone', 2,4,0.80),
('wild_boar',        'boar_hide',     1,1,1.00), ('wild_boar',        'boar_tusk',     1,2,0.65), ('wild_boar',  'animal_fat',  1,3,0.80),
('mountain_goat',    'animal_hide',   1,1,1.00), ('mountain_goat',    'animal_sinew',  1,2,0.60),
('rabbit',           'rabbit_pelt',   1,1,1.00), ('hare',             'rabbit_pelt',   1,1,1.00),
('red_squirrel',     'rabbit_pelt',   1,1,0.70), ('hedgehog',         'animal_bone',   1,1,0.50),
('beaver',           'animal_hide',   1,1,1.00), ('beaver',           'animal_fat',    1,2,0.70),
('river_otter',      'animal_hide',   1,1,1.00), ('river_otter',      'fish_oil',      1,2,0.50),
-- Reptiles and amphibians
('grass_snake',      'snake_skin',    1,1,1.00),
('common_adder',     'snake_skin',    1,1,1.00), ('common_adder',     'snake_venom',   1,1,0.60),
('constrictor_snake','snake_skin',    1,2,1.00),
('monitor_lizard',   'snake_skin',    1,1,0.90), ('monitor_lizard',   'predator_claw', 1,2,0.60),
('crocodilian',      'snake_skin',    1,2,1.00), ('crocodilian',      'predator_fang', 2,4,0.80),
('river_turtle',     'turtle_shell',  1,1,1.00),
-- Birds
('golden_eagle',     'feather',       3,8,1.00), ('golden_eagle',     'raptor_talon',  1,2,0.80),
('peregrine_falcon', 'feather',       2,6,1.00), ('peregrine_falcon', 'raptor_talon',  1,2,0.70),
('barn_owl',         'feather',       2,6,1.00), ('common_raven',     'feather',       2,5,1.00),
('vulture',          'feather',       3,7,1.00), ('grey_heron',       'feather',       2,6,1.00),
('stork',            'feather',       2,6,1.00),
('wood_pigeon',      'raw_fowl_meat', 1,1,1.00), ('wood_pigeon',      'feather',       1,4,0.90),
('mallard_duck',     'raw_fowl_meat', 1,1,1.00), ('mallard_duck',     'feather',       2,5,0.90), ('mallard_duck','bird_egg',1,3,0.40),
('wild_turkey',      'raw_fowl_meat', 1,2,1.00), ('wild_turkey',      'feather',       2,6,0.90),
('marsh_fowl',       'raw_fowl_meat', 1,1,1.00), ('marsh_fowl',       'bird_egg',      1,2,0.40),
-- Fish
('river_trout',      'raw_fish',      1,1,1.00), ('common_perch',     'raw_fish',      1,1,1.00),
('carp',             'raw_fish',      1,2,1.00), ('pike',             'raw_fish',      1,2,1.00),
('catfish',          'raw_fish',      1,2,1.00), ('catfish',          'fish_oil',      1,2,0.50),
('freshwater_eel',   'raw_fish',      1,1,1.00), ('freshwater_eel',   'fish_oil',      1,1,0.60),
('crayfish',         'crayfish_meat', 1,2,1.00);

-- 4. Aquatic catches — how a fish was taken, for provenance and for the
-- capability the method builds. Extends world_object like any physical thing.
CREATE TABLE aquatic_catch (
    object_id   UUID        PRIMARY KEY REFERENCES world_object(id),
    species_key VARCHAR(80) NOT NULL REFERENCES wildlife_species(species_key),
    method_used VARCHAR(20) NOT NULL,
    caught_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT aquatic_catch_method_check CHECK (method_used IN ('BARE_HAND','SPEAR','TRAP','LINE'))
);

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin) VALUES
('aves',    'Aves',    'Birds — raptors, waterfowl, scavengers, and ground fowl, with the aerial movement gate.', 'V42', 'PREBUILT'),
('pisces',  'Pisces',  'Freshwater fish and crayfish, and the methods by which they are taken.', 'V42', 'PREBUILT'),
('butchery','Butchery','Per-species yields from a carcass — pelts, hides, antler, sinew, bone, fat, feather, and venom.', 'V42', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
