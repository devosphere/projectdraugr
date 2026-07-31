-- V43: Monster system. The MONSTER ecology_site category has existed since V3
-- but has never been backed by anything; this migration gives it substance.
--
-- Monsters are not villains and not scripted encounters -- they are naturally
-- occurring organisms that differ from Earth's wildlife. They therefore reuse
-- the wildlife machinery wherever it fits (populations, carcasses, drops) and
-- add only what is genuinely theirs: a special mechanic, a sight radius, and a
-- resistance scale that runs past anything a bear can bring.
-- See docs/systems/12-Monsters.md and 11.1-Species-Registry.md.

CREATE TABLE monster_profile (
    species_key      VARCHAR(80)  PRIMARY KEY,
    movement_class   VARCHAR(20)  NOT NULL,
    resistance       SMALLINT     NOT NULL,
    aggression       VARCHAR(20)  NOT NULL,   -- PASSIVE, DEFENSIVE, AGGRESSIVE, APEX
    activity_cycle   VARCHAR(20)  NOT NULL,
    sight_radius     SMALLINT     NOT NULL DEFAULT 1,   -- in chunks
    special_mechanic VARCHAR(40),
    biome_affinity   VARCHAR(160) NOT NULL,
    CONSTRAINT monster_profile_movement_check CHECK (movement_class IN ('TERRESTRIAL','AERIAL','AQUATIC','AMPHIBIOUS')),
    CONSTRAINT monster_profile_aggression_check CHECK (aggression IN ('PASSIVE','DEFENSIVE','AGGRESSIVE','APEX'))
);

INSERT INTO monster_profile (species_key, movement_class, resistance, aggression, activity_cycle, sight_radius, special_mechanic, biome_affinity) VALUES
-- Ground
('cave_troll',        'TERRESTRIAL', 85, 'AGGRESSIVE','NOCTURNAL',  1, NULL,            'MOUNTAIN,HIGHLAND'),
('dire_wolf',         'TERRESTRIAL', 55, 'AGGRESSIVE','CREPUSCULAR',2, 'ALWAYS_HUNTING','TEMPERATE_FOREST,HIGHLAND'),
('bog_wraith',        'TERRESTRIAL', 70, 'AGGRESSIVE','NOCTURNAL',  1, 'DISEASE_WOUND', 'WETLAND'),
-- Aerial
('wyvern',            'AERIAL',     120, 'APEX',      'NOCTURNAL',  3, 'FIRE_BREATH',   'MOUNTAIN'),
('giant_bat_swarm',   'AERIAL',      30, 'AGGRESSIVE','NOCTURNAL',  1, 'SWARM_WOUNDS',  'MOUNTAIN'),
('harpy',             'AERIAL',      65, 'AGGRESSIVE','CREPUSCULAR',2, 'ITEM_THEFT',    'HIGHLAND,MOUNTAIN'),
('roc',               'AERIAL',     150, 'APEX',      'DIURNAL',    3, 'GRAB_AND_CARRY','HIGHLAND'),
-- Flying insect
('giant_hornet_queen','AERIAL',      45, 'DEFENSIVE', 'DIURNAL',    1, 'VENOM_WOUND',   'TEMPERATE_FOREST,HIGHLAND'),
('locust_swarm',      'AERIAL',      25, 'PASSIVE',   'DIURNAL',    1, 'FLORA_DESTROY', 'GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;

-- Monster remains. Reuses wildlife_drop by first registering each monster in
-- wildlife_species: a monster is an organism, and the butchery machinery built
-- in V42 applies to it unchanged. kingdom_class MONSTRUM is added for them.
ALTER TABLE wildlife_species DROP CONSTRAINT IF EXISTS wildlife_species_class_check;
ALTER TABLE wildlife_species ADD CONSTRAINT wildlife_species_class_check
    CHECK (kingdom_class IN ('MAMMALIA','REPTILIA','AMPHIBIA','AVES','PISCES','MONSTRUM'));

INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
('cave_troll',        'MONSTRUM','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','HUGE',   85, TRUE,  FALSE, TRUE,  0, 'MOUNTAIN,HIGHLAND'),
('dire_wolf',         'MONSTRUM','CARNIVORE','CREPUSCULAR','TERRESTRIAL','LARGE',  55, FALSE, TRUE,  TRUE,  0, 'TEMPERATE_FOREST,HIGHLAND'),
('bog_wraith',        'MONSTRUM','CARNIVORE','NOCTURNAL',  'TERRESTRIAL','MEDIUM', 70, TRUE,  FALSE, TRUE,  0, 'WETLAND'),
('wyvern',            'MONSTRUM','CARNIVORE','NOCTURNAL',  'AERIAL',     'HUGE',  120, FALSE, FALSE, TRUE,  0, 'MOUNTAIN'),
('giant_bat_swarm',   'MONSTRUM','CARNIVORE','NOCTURNAL',  'AERIAL',     'SMALL',  30, FALSE, TRUE,  FALSE, 0, 'MOUNTAIN'),
('harpy',             'MONSTRUM','CARNIVORE','CREPUSCULAR','AERIAL',     'MEDIUM', 65, TRUE,  FALSE, TRUE,  0, 'HIGHLAND,MOUNTAIN'),
('roc',               'MONSTRUM','CARNIVORE','DIURNAL',    'AERIAL',     'HUGE',  150, FALSE, FALSE, TRUE,  0, 'HIGHLAND'),
('giant_hornet_queen','MONSTRUM','OMNIVORE', 'DIURNAL',    'AERIAL',     'MEDIUM', 45, FALSE, FALSE, TRUE,  0, 'TEMPERATE_FOREST,HIGHLAND'),
('locust_swarm',      'MONSTRUM','HERBIVORE','DIURNAL',    'AERIAL',     'SMALL',  25, FALSE, TRUE,  FALSE, 0, 'GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('wyvern_scale',          'Wyvern Scale',          'MATERIAL',  180,  160, TRUE,  FALSE),
('wyvern_fang',           'Wyvern Fang',           'MATERIAL',  340,  280, TRUE,  FALSE),
('wyvern_wing_membrane',  'Wyvern Wing Membrane',  'MATERIAL', 2200, 6000, FALSE, FALSE),
('harpy_feather',         'Harpy Feather',         'MATERIAL',   12,   90, TRUE,  FALSE),
('harpy_talon',           'Harpy Talon',           'MATERIAL',   85,   70, TRUE,  FALSE),
('giant_stinger',         'Giant Stinger',         'MATERIAL',  140,  120, TRUE,  FALSE),
('troll_hide',            'Troll Hide',            'MATERIAL', 6500,12000, FALSE, FALSE),
('troll_bone',            'Troll Bone',            'MATERIAL', 1800, 2400, FALSE, FALSE),
('dire_wolf_pelt',        'Dire Wolf Pelt',        'MATERIAL', 2600, 5200, FALSE, FALSE),
('dire_wolf_fang',        'Dire Wolf Fang',        'MATERIAL',   45,   38, TRUE,  FALSE),
('roc_feather',           'Roc Feather',           'MATERIAL',   90,  600, TRUE,  FALSE),
('roc_talon',             'Roc Talon',             'MATERIAL',  900,  800, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max, rarity) VALUES
('cave_troll',      'troll_hide',           1,1,1.00), ('cave_troll', 'troll_bone',      1,3,0.80),
('dire_wolf',       'dire_wolf_pelt',       1,1,1.00), ('dire_wolf',  'dire_wolf_fang',  2,4,0.85),
('wyvern',          'wyvern_scale',         4,9,1.00), ('wyvern',     'wyvern_fang',     1,3,0.75), ('wyvern','wyvern_wing_membrane',1,2,0.55),
('harpy',           'harpy_feather',        3,7,1.00), ('harpy',      'harpy_talon',     1,2,0.70),
('roc',             'roc_feather',          3,6,1.00), ('roc',        'roc_talon',       1,2,0.60),
('giant_hornet_queen','giant_stinger',      1,1,0.85), ('giant_hornet_queen','hornet_venom',1,2,0.70),
('giant_bat_swarm', 'animal_bone',          1,2,0.50);

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('monsters', 'Monsters', 'Naturally occurring organisms unlike Earth wildlife -- trolls, wyverns, harpies, rocs, and swarms, with their special mechanics.', 'V43', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
