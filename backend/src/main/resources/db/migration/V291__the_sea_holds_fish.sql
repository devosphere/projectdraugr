-- The sea holds fish (#157).
--
-- #546 gave the world a shore: COAST is derived from any land that touches open water, and the world now makes
-- 24-33 chunks of it. Sea beet and sea buckthorn grow there, an osprey works it, a bonecrab and a saltback
-- crocodilian live on it.
--
-- And there is nothing in the water. Every one of the thirteen aquatic species in the catalogue is a freshwater
-- fish: carp, chub, dace, pike, perch, trout, the lot. Not one names OCEAN or COAST. So a Chronicle standing on
-- the shore with a net or a line is told "There is no water here that holds anything worth taking" — on the sea.
--
-- Verified against the live catalogue rather than inferred from migration text: SELECT over wildlife_species
-- WHERE movement_class='AQUATIC' AND (biome_affinity ILIKE '%OCEAN%' OR ILIKE '%COAST%') returned zero rows, and
-- there is no marine food item of any kind — no cod, herring, crab, oyster, limpet or kelp.
--
-- These five are what a person fishes off a temperate shore with a line, a net or a spear, and they drop
-- raw_fish and fish_bone exactly as the freshwater fish do. Deliberately NO new item keys: raw_fish is already
-- spoilage-tracked, already cookable, already smokable and salted, so every one of these is functional to the
-- end of its chain the moment it exists. A new "sea fish" item would have been a second word for a fish.
INSERT INTO wildlife_species
    (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance,
     ambush_hunter, pack_hunter, territorial, tamability, biome_affinity, toxic, venomous)
VALUES
    -- Shoaling fish, taken in numbers with a net; the reason a coastal camp eats at all.
    ('herring',   'PISCES', 'OMNIVORE',  'DIURNAL',     'AQUATIC', 'SMALL',   8, FALSE, FALSE, FALSE, 0, 'COAST,OCEAN', FALSE, FALSE),
    ('mackerel',  'PISCES', 'CARNIVORE', 'DIURNAL',     'AQUATIC', 'SMALL',  12, FALSE, FALSE, FALSE, 0, 'COAST,OCEAN', FALSE, FALSE),
    -- A big cold-water fish worth a day's work on a line.
    ('cod',       'PISCES', 'CARNIVORE', 'DIURNAL',     'AQUATIC', 'LARGE',  40, FALSE, FALSE, FALSE, 0, 'COAST,OCEAN', FALSE, FALSE),
    -- A flatfish lying in the shallows, which is how it is speared.
    ('flounder',  'PISCES', 'CARNIVORE', 'CREPUSCULAR', 'AQUATIC', 'MEDIUM', 18, TRUE,  FALSE, FALSE, 0, 'COAST',       FALSE, FALSE),
    -- Sea trout run between the two waters, so this one belongs to both and is the honest link between them.
    ('sea_trout', 'PISCES', 'CARNIVORE', 'CREPUSCULAR', 'AQUATIC', 'MEDIUM', 22, FALSE, FALSE, FALSE, 0, 'COAST,RIVER_BANK', FALSE, FALSE)
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO wildlife_drop (id, species_key, item_key, yield_min, yield_max, rarity) VALUES
    (gen_random_uuid(), 'herring',   'raw_fish',  1, 3, 1.00),
    (gen_random_uuid(), 'herring',   'fish_bone', 1, 2, 1.00),
    (gen_random_uuid(), 'mackerel',  'raw_fish',  1, 2, 1.00),
    (gen_random_uuid(), 'mackerel',  'fish_bone', 1, 2, 1.00),
    (gen_random_uuid(), 'cod',       'raw_fish',  2, 4, 1.00),
    (gen_random_uuid(), 'cod',       'fish_bone', 1, 3, 1.00),
    (gen_random_uuid(), 'flounder',  'raw_fish',  1, 2, 1.00),
    (gen_random_uuid(), 'flounder',  'fish_bone', 1, 2, 1.00),
    (gen_random_uuid(), 'sea_trout', 'raw_fish',  1, 2, 1.00),
    (gen_random_uuid(), 'sea_trout', 'fish_bone', 1, 2, 1.00)
ON CONFLICT DO NOTHING;

DO $$
DECLARE problem text;
BEGIN
    -- The shore must actually hold fish now, which is the whole point.
    IF (SELECT count(*) FROM wildlife_species
         WHERE movement_class='AQUATIC' AND kingdom_class='PISCES' AND biome_affinity ILIKE '%COAST%') < 4 THEN
        RAISE EXCEPTION 'V291: the shore still holds no fish';
    END IF;

    -- Every drop must name a real item, or the fish comes up and nothing comes off it.
    SELECT string_agg(DISTINCT d.item_key, ', ') INTO problem
      FROM wildlife_drop d
     WHERE d.species_key IN ('herring','mackerel','cod','flounder','sea_trout')
       AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = d.item_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V291: no such drop item: %', problem; END IF;

    -- And every new fish must actually drop something, or it is a name in a list.
    SELECT string_agg(s.species_key, ', ') INTO problem
      FROM (VALUES ('herring'),('mackerel'),('cod'),('flounder'),('sea_trout')) AS s(species_key)
     WHERE NOT EXISTS (SELECT 1 FROM wildlife_drop d WHERE d.species_key = s.species_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V291: species that yield nothing: %', problem; END IF;

    -- The Auditor's own obtainability rule, asserted where it costs a second rather than an hour in CI.
    SELECT string_agg(d.item_key, ', ') INTO problem
      FROM item_definition d
     WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = d.item_key)
       AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key = d.item_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V291: item(s) with no declared way to be obtained: %', problem; END IF;
END $$;
