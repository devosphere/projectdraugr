-- V204 — wildlife catalogue, story #74 slice 5 (closing): the invertebrates. The story lists 18 invertebrates across
-- its four groups, and they map cleanly onto the species registry's own columns (biome affinity, activity cycle,
-- movement class, size tier, ecological role) — the only thing stopping them was the class check, which admitted
-- vertebrates and fish only. Widen it to the invertebrate classes and register them, so a wood floor, a marsh
-- surface, and an open meadow carry their small crawling and flying life too, all surfaced by the #74 ambient
-- mechanism (none are AQUATIC, so none are mistaken for fish). This completes the 100-species catalogue.
-- The live constraint (widened by V43 for monsters) admits MONSTRUM as well as the five vertebrate/fish classes;
-- keep every existing class and add the invertebrate ones, so no existing row is orphaned.
ALTER TABLE wildlife_species DROP CONSTRAINT IF EXISTS wildlife_species_class_check;
ALTER TABLE wildlife_species ADD CONSTRAINT wildlife_species_class_check
    CHECK (kingdom_class IN ('MAMMALIA','REPTILIA','AMPHIBIA','AVES','PISCES','MONSTRUM',
                             'INSECTA','ARACHNIDA','GASTROPODA','ANNELIDA','BIVALVIA'));

INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
-- forest floor and understory invertebrates
('forest_snail',        'GASTROPODA','HERBIVORE','NOCTURNAL',  'TERRESTRIAL','TINY', 2, FALSE, FALSE, FALSE, 0, 'TEMPERATE_FOREST'),
('earthworm',           'ANNELIDA',  'SCAVENGER','NOCTURNAL',  'TERRESTRIAL','TINY', 2, FALSE, FALSE, FALSE, 0, 'TEMPERATE_FOREST,GRASSLAND'),
('stag_beetle',         'INSECTA',   'HERBIVORE','NOCTURNAL',  'AERIAL',     'TINY', 3, FALSE, FALSE, FALSE, 0, 'TEMPERATE_FOREST'),
('luna_moth',           'INSECTA',   'HERBIVORE','NOCTURNAL',  'AERIAL',     'TINY', 2, FALSE, FALSE, FALSE, 0, 'TEMPERATE_FOREST'),
-- wetland and freshwater invertebrates (kept off AQUATIC so they are not read as fish)
('freshwater_mussel',   'BIVALVIA',  'OMNIVORE', 'DIURNAL',    'AMPHIBIOUS', 'TINY', 4, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('freshwater_snail',    'GASTROPODA','HERBIVORE','DIURNAL',    'AMPHIBIOUS', 'TINY', 2, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('dragonfly',           'INSECTA',   'CARNIVORE','DIURNAL',    'AERIAL',     'TINY', 3, FALSE, FALSE, FALSE, 0, 'WETLAND,GRASSLAND'),
('damselfly',           'INSECTA',   'CARNIVORE','DIURNAL',    'AERIAL',     'TINY', 2, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('water_strider',       'INSECTA',   'CARNIVORE','DIURNAL',    'AMPHIBIOUS', 'TINY', 2, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('water_beetle',        'INSECTA',   'CARNIVORE','NOCTURNAL',  'AMPHIBIOUS', 'TINY', 3, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('mayfly',              'INSECTA',   'HERBIVORE','CREPUSCULAR','AERIAL',     'TINY', 2, FALSE, FALSE, FALSE, 0, 'WETLAND'),
('caddisfly',           'INSECTA',   'HERBIVORE','NOCTURNAL',  'AERIAL',     'TINY', 2, FALSE, FALSE, FALSE, 0, 'WETLAND'),
-- grassland, scrub, and rocky-ground invertebrates
('dung_beetle',         'INSECTA',   'SCAVENGER','DIURNAL',    'TERRESTRIAL','TINY', 3, FALSE, FALSE, FALSE, 0, 'GRASSLAND'),
('grasshopper',         'INSECTA',   'HERBIVORE','DIURNAL',    'TERRESTRIAL','TINY', 2, FALSE, FALSE, FALSE, 0, 'GRASSLAND'),
('cricket',             'INSECTA',   'OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','TINY', 2, FALSE, FALSE, FALSE, 0, 'GRASSLAND,TEMPERATE_FOREST'),
('praying_mantis',      'INSECTA',   'CARNIVORE','DIURNAL',    'TERRESTRIAL','TINY', 3, TRUE,  FALSE, FALSE, 0, 'GRASSLAND'),
('orb_weaver_spider',   'ARACHNIDA', 'CARNIVORE','NOCTURNAL',  'TERRESTRIAL','TINY', 3, TRUE,  FALSE, FALSE, 0, 'GRASSLAND,TEMPERATE_FOREST'),
('scorpion',            'ARACHNIDA', 'CARNIVORE','NOCTURNAL',  'TERRESTRIAL','TINY', 5, TRUE,  FALSE, TRUE,  0, 'GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;

-- Register the invertebrate taxonomic domains this slice adds.
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin) VALUES
('insecta',    'Insecta',    'Insects — beetles, moths, dragonflies, and the crawling and flying life of the ground and air.', 'V204', 'PREBUILT'),
('arachnida',  'Arachnida',  'Spiders and scorpions of the scrub and forest floor.', 'V204', 'PREBUILT'),
('mollusca',   'Mollusca',   'Snails and freshwater mussels of the wood floor and the water margin.', 'V204', 'PREBUILT'),
('annelida',   'Annelida',   'Segmented worms of the soil.', 'V204', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
