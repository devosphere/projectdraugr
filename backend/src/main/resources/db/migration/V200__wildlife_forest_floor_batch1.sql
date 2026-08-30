-- V200 — wildlife catalogue, story #74 slice 1: the temperate-forest floor, understory, and canopy vertebrates.
-- 21 ordinary small and medium creatures that make a wood feel inhabited. Each is a real registry species with a
-- biome affinity, so — with the #74 ambient-life change to presentLife — it is a perceivable inhabitant of the
-- forest whether or not a herd is seeded on the ground, and (for the medium ones) huntable through the normal
-- confront/carcass path when a population is present. The four invertebrates the story also lists (forest snail,
-- earthworm, stag beetle, luna moth) are not vertebrates and cannot live in wildlife_species (its class check is
-- MAMMALIA/REPTILIA/AMPHIBIA/AVES/PISCES); they land in a later slice with an invertebrate home.
--
-- 'common_lizard' already exists, so the gecko here is 'wood_gecko' to avoid the collision.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
('bank_vole',           'MAMMALIA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST,GRASSLAND'),
('wood_mouse',          'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST,GRASSLAND'),
('yellow_necked_mouse', 'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST'),
('forest_dormouse',     'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',    6, FALSE, FALSE, FALSE, 30, 'TEMPERATE_FOREST'),
('striped_field_mouse', 'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST,GRASSLAND'),
('eastern_chipmunk',    'MAMMALIA','OMNIVORE', 'DIURNAL',    'TERRESTRIAL','TINY',    7, FALSE, FALSE, FALSE, 35, 'TEMPERATE_FOREST'),
('flying_squirrel',     'MAMMALIA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',    7, FALSE, FALSE, FALSE, 30, 'TEMPERATE_FOREST'),
('ground_squirrel',     'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','SMALL',  10, FALSE, FALSE, FALSE, 40, 'TEMPERATE_FOREST,GRASSLAND'),
('porcupine',           'MAMMALIA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','MEDIUM', 30, FALSE, FALSE, TRUE,  20, 'TEMPERATE_FOREST,HIGHLAND'),
('shrew',               'MAMMALIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 10, 'TEMPERATE_FOREST,GRASSLAND'),
('water_shrew',         'MAMMALIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS', 'TINY',    5, FALSE, FALSE, FALSE, 10, 'WETLAND,TEMPERATE_FOREST'),
('woodland_lemming',    'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST,HIGHLAND'),
('pine_vole',           'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','TINY',    5, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST'),
('weasel',              'MAMMALIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','TINY',   18, TRUE,  FALSE, TRUE,  15, 'TEMPERATE_FOREST,GRASSLAND'),
('polecat',             'MAMMALIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','SMALL',  28, TRUE,  FALSE, TRUE,  15, 'TEMPERATE_FOREST,WETLAND'),
('raccoon',             'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  30, FALSE, FALSE, TRUE,  30, 'TEMPERATE_FOREST,WETLAND'),
('skunk',               'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  25, FALSE, FALSE, TRUE,  20, 'TEMPERATE_FOREST,GRASSLAND'),
('opossum',             'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  22, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST'),
('ringtail',            'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  24, FALSE, FALSE, TRUE,  20, 'TEMPERATE_FOREST,HIGHLAND'),
('tree_frog',           'AMPHIBIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS', 'TINY',    6, FALSE, FALSE, FALSE, 15, 'TEMPERATE_FOREST,WETLAND'),
('wood_gecko',          'REPTILIA','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',    8, FALSE, FALSE, FALSE, 15, 'TEMPERATE_FOREST,GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;
