-- V201 — wildlife catalogue, story #74 slice 2: forest, highland, and grassland birds. 25 species that give the
-- open sky and the treeline their ordinary voices. With the #74 ambient-life change, each is a perceivable
-- inhabitant of its biome (aerial birds are surfaced by presentLife just like land fauna — the ambient query
-- excludes only AQUATIC). The four ground game birds (grouse, partridge, quail, pheasant) are TERRESTRIAL, so they
-- also seed and hunt through the normal population/confront path as classic small game. 'wood_pigeon' already
-- exists and is left untouched.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- songbirds and passerines of the wood and hedge
('blackbird',       'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  8, FALSE, FALSE, FALSE, 20, 'TEMPERATE_FOREST,GRASSLAND'),
('song_thrush',     'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  8, FALSE, FALSE, FALSE, 20, 'TEMPERATE_FOREST,GRASSLAND'),
('mistle_thrush',   'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  9, FALSE, FALSE, TRUE,  18, 'TEMPERATE_FOREST,HIGHLAND'),
('robin',           'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, TRUE,  30, 'TEMPERATE_FOREST,GRASSLAND'),
('wren',            'AVES','CARNIVORE','DIURNAL','AERIAL','TINY',  5, FALSE, FALSE, TRUE,  15, 'TEMPERATE_FOREST'),
('chaffinch',       'AVES','HERBIVORE','DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 25, 'TEMPERATE_FOREST,GRASSLAND'),
('goldfinch',       'AVES','HERBIVORE','DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 25, 'GRASSLAND,TEMPERATE_FOREST'),
('yellowhammer',    'AVES','HERBIVORE','DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 25, 'GRASSLAND,HIGHLAND'),
('meadowlark',      'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  8, FALSE, FALSE, FALSE, 22, 'GRASSLAND'),
('skylark',         'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  7, FALSE, FALSE, FALSE, 22, 'GRASSLAND,HIGHLAND'),
('sparrow',         'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 30, 'GRASSLAND,TEMPERATE_FOREST'),
('swallow',         'AVES','CARNIVORE','DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 15, 'GRASSLAND,WETLAND'),
('swift',           'AVES','CARNIVORE','DIURNAL','AERIAL','TINY',  6, FALSE, FALSE, FALSE, 10, 'GRASSLAND,TEMPERATE_FOREST'),
('woodpecker',      'AVES','OMNIVORE', 'DIURNAL','AERIAL','SMALL',10, FALSE, FALSE, TRUE,  18, 'TEMPERATE_FOREST'),
('nuthatch',        'AVES','OMNIVORE', 'DIURNAL','AERIAL','TINY',  7, FALSE, FALSE, FALSE, 20, 'TEMPERATE_FOREST'),
('blue_jay',        'AVES','OMNIVORE', 'DIURNAL','AERIAL','SMALL',12, FALSE, FALSE, TRUE,  22, 'TEMPERATE_FOREST'),
('magpie',          'AVES','OMNIVORE', 'DIURNAL','AERIAL','SMALL',14, FALSE, FALSE, TRUE,  25, 'GRASSLAND,TEMPERATE_FOREST'),
('carrion_crow',    'AVES','SCAVENGER','DIURNAL','AERIAL','SMALL',16, FALSE, FALSE, TRUE,  25, 'GRASSLAND,TEMPERATE_FOREST,HIGHLAND'),
-- ground game birds — TERRESTRIAL, so they seed, forage, and hunt as small game
('grouse',          'AVES','HERBIVORE','DIURNAL','TERRESTRIAL','SMALL', 20, FALSE, FALSE, FALSE, 20, 'HIGHLAND,TEMPERATE_FOREST'),
('partridge',       'AVES','OMNIVORE', 'DIURNAL','TERRESTRIAL','SMALL', 18, FALSE, FALSE, FALSE, 25, 'GRASSLAND'),
('quail',           'AVES','OMNIVORE', 'DIURNAL','TERRESTRIAL','TINY',  12, FALSE, FALSE, FALSE, 25, 'GRASSLAND'),
('pheasant',        'AVES','OMNIVORE', 'DIURNAL','TERRESTRIAL','MEDIUM',24, FALSE, FALSE, TRUE,  25, 'GRASSLAND,TEMPERATE_FOREST'),
-- raptors of the open sky
('kestrel',         'AVES','CARNIVORE','DIURNAL','AERIAL','SMALL', 22, TRUE,  FALSE, TRUE,  10, 'GRASSLAND,HIGHLAND'),
('red_tailed_hawk', 'AVES','CARNIVORE','DIURNAL','AERIAL','MEDIUM',32, TRUE,  FALSE, TRUE,   8, 'GRASSLAND,TEMPERATE_FOREST,HIGHLAND'),
('osprey',          'AVES','CARNIVORE','DIURNAL','AERIAL','MEDIUM',30, TRUE,  FALSE, TRUE,   8, 'WETLAND,COAST')
ON CONFLICT (species_key) DO NOTHING;
