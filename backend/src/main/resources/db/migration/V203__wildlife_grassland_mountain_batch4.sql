-- V203 — wildlife catalogue, story #74 slice 4: grassland, scrub, rocky ground, and mountain margins. 19 vertebrates
-- that fill the open range and the high rock. Each is surfaced by the #74 ambient-life mechanism; the medium and
-- large grazers (ibex, chamois, bighorn sheep, wild donkey, the antelopes) also seed and hunt through the normal
-- population/confront path. Dry-scrub species (jerboa, kangaroo rat, desert hare) live in the game's open grassland,
-- which is its driest ground. The six invertebrates the story lists for this group (dung beetle, grasshopper,
-- cricket, praying mantis, orb-weaver spider, scorpion) come in the closing invertebrate slice.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- burrowing and small grassland mammals
('prairie_dog',    'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','SMALL', 10, FALSE, FALSE, FALSE, 35, 'GRASSLAND'),
('gopher',         'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','TINY',   8, FALSE, FALSE, TRUE,  25, 'GRASSLAND'),
('groundhog',      'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','SMALL', 15, FALSE, FALSE, TRUE,  25, 'GRASSLAND,TEMPERATE_FOREST'),
('jerboa',         'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY',   5, FALSE, FALSE, FALSE, 20, 'GRASSLAND'),
('kangaroo_rat',   'MAMMALIA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','TINY',   5, FALSE, FALSE, FALSE, 20, 'GRASSLAND'),
('desert_hare',    'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','SMALL', 15, FALSE, FALSE, FALSE, 40, 'GRASSLAND'),
-- rock and mountain mammals
('pika',           'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','TINY',   8, FALSE, FALSE, FALSE, 25, 'MOUNTAIN,HIGHLAND'),
('marmot',         'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','SMALL', 18, FALSE, FALSE, TRUE,  30, 'MOUNTAIN,HIGHLAND'),
('ibex',           'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',40, FALSE, FALSE, FALSE, 45, 'MOUNTAIN,HIGHLAND'),
('chamois',        'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',38, FALSE, FALSE, FALSE, 45, 'MOUNTAIN,HIGHLAND'),
('bighorn_sheep',  'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','LARGE', 45, FALSE, FALSE, TRUE,  40, 'MOUNTAIN,HIGHLAND'),
('wild_donkey',    'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','LARGE', 45, FALSE, FALSE, FALSE, 55, 'GRASSLAND,HIGHLAND'),
-- open-range antelopes
('saiga_antelope', 'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',38, FALSE, FALSE, FALSE, 30, 'GRASSLAND'),
('gazelle',        'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',35, FALSE, FALSE, FALSE, 35, 'GRASSLAND'),
('antelope',       'MAMMALIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',38, FALSE, FALSE, FALSE, 35, 'GRASSLAND'),
-- grassland and scrub reptiles
('badger_lizard',  'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','SMALL', 15, FALSE, FALSE, TRUE,  10, 'GRASSLAND,HIGHLAND'),
('horned_lizard',  'REPTILIA','CARNIVORE','DIURNAL',    'TERRESTRIAL','TINY',  10, FALSE, FALSE, FALSE, 10, 'GRASSLAND'),
('slow_worm',      'REPTILIA','CARNIVORE','CREPUSCULAR','TERRESTRIAL','TINY',   8, FALSE, FALSE, FALSE, 15, 'GRASSLAND,TEMPERATE_FOREST'),
('tortoise',       'REPTILIA','HERBIVORE','DIURNAL',    'TERRESTRIAL','SMALL', 30, FALSE, FALSE, FALSE, 85, 'GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;
