-- V202 — wildlife catalogue, story #74 slice 3: wetland, stream, and freshwater life. 17 vertebrates and fish that
-- make a marsh or a river feel alive. The mammals, turtles, snakes, and amphibians are surfaced by the #74
-- ambient-life mechanism (movement_class <> 'AQUATIC'); the fish are surfaced by the existing fish line and make
-- their water fishable (fishability reads AQUATIC species by biome). The eight invertebrates the story also lists
-- for this group (freshwater mussel and snail; dragonfly, damselfly, water strider, water beetle, mayfly,
-- caddisfly) are not vertebrates and wait for the invertebrate slice.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- wetland mammals
('muskrat',            'MAMMALIA','HERBIVORE','CREPUSCULAR','AMPHIBIOUS','SMALL', 20, FALSE, FALSE, TRUE,  25, 'WETLAND'),
('water_vole',         'MAMMALIA','HERBIVORE','CREPUSCULAR','AMPHIBIOUS','TINY',  8, FALSE, FALSE, FALSE, 25, 'WETLAND'),
('mink',               'MAMMALIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS','SMALL', 28, TRUE,  FALSE, TRUE,  15, 'WETLAND'),
('marsh_rabbit',       'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','SMALL',12, FALSE, FALSE, FALSE, 60, 'WETLAND,GRASSLAND'),
('marsh_shrew',        'MAMMALIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS','TINY',  5, FALSE, FALSE, FALSE, 10, 'WETLAND'),
-- wetland reptiles
('pond_turtle',        'REPTILIA','OMNIVORE', 'DIURNAL',    'AMPHIBIOUS','SMALL', 35, FALSE, FALSE, FALSE, 90, 'WETLAND'),
('softshell_turtle',   'REPTILIA','CARNIVORE','DIURNAL',    'AMPHIBIOUS','MEDIUM',45, TRUE,  FALSE, TRUE,  40, 'WETLAND'),
('water_snake',        'REPTILIA','CARNIVORE','DIURNAL',    'AMPHIBIOUS','SMALL', 25, FALSE, FALSE, FALSE, 15, 'WETLAND'),
-- wetland amphibians
('mudpuppy',           'AMPHIBIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS','SMALL', 10, FALSE, FALSE, FALSE, 15, 'WETLAND'),
('bullfrog',           'AMPHIBIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS','SMALL', 12, FALSE, FALSE, TRUE,  20, 'WETLAND'),
('tree_newt',          'AMPHIBIA','CARNIVORE','NOCTURNAL',  'AMPHIBIOUS','TINY',  6, FALSE, FALSE, FALSE, 20, 'WETLAND,TEMPERATE_FOREST'),
-- freshwater fish (AQUATIC — surfaced by the fish line, make the water fishable)
('lamprey',            'PISCES',  'CARNIVORE','NOCTURNAL',  'AQUATIC',   'SMALL', 15, TRUE,  FALSE, FALSE,  5, 'WETLAND'),
('minnow',             'PISCES',  'OMNIVORE', 'DIURNAL',    'AQUATIC',   'TINY',   3, FALSE, FALSE, FALSE,  5, 'WETLAND'),
('dace',               'PISCES',  'OMNIVORE', 'DIURNAL',    'AQUATIC',   'SMALL',  6, FALSE, FALSE, FALSE,  5, 'WETLAND'),
('chub',               'PISCES',  'OMNIVORE', 'DIURNAL',    'AQUATIC',   'SMALL', 10, FALSE, FALSE, FALSE,  5, 'WETLAND'),
('freshwater_bream',   'PISCES',  'OMNIVORE', 'CREPUSCULAR','AQUATIC',   'MEDIUM',15, FALSE, FALSE, FALSE,  5, 'WETLAND'),
('freshwater_sturgeon','PISCES',  'CARNIVORE','NOCTURNAL',  'AQUATIC',   'LARGE', 45, FALSE, FALSE, FALSE,  5, 'WETLAND')
ON CONFLICT (species_key) DO NOTHING;
