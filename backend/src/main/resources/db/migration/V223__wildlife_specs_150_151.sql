-- V223 — completes #74's spec sub-issues #150 (small forest mammals) and #151 (forest/stream/ground birds). Almost
-- every species those cards list was already registered by the #74 batches (red squirrel, gopher, hedgehog, field
-- mouse, chipmunk, shrew, grouse, wood pigeon, carrion crow, blue jay, mallard duck, quail); these four fill the
-- last gaps, each a real registry inhabitant of its biome via the #74 ambient-life mechanism. The greylag goose is
-- TERRESTRIAL so it grazes and hunts as ground game; the kingfisher fishes the streams.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
('forest_rat',     'MAMMALIA','OMNIVORE', 'NOCTURNAL',  'TERRESTRIAL','SMALL',  8, FALSE, FALSE, FALSE, 20, 'TEMPERATE_FOREST,GRASSLAND'),
('snowshoe_hare',  'MAMMALIA','HERBIVORE','CREPUSCULAR','TERRESTRIAL','SMALL', 15, FALSE, FALSE, FALSE, 40, 'HIGHLAND,TEMPERATE_FOREST,MOUNTAIN'),
('kingfisher',     'AVES',    'CARNIVORE','DIURNAL',    'AERIAL',     'TINY',   7, FALSE, FALSE, TRUE,  10, 'WETLAND'),
('greylag_goose',  'AVES',    'HERBIVORE','DIURNAL',    'TERRESTRIAL','MEDIUM',26, FALSE, FALSE, FALSE, 30, 'WETLAND,GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;
