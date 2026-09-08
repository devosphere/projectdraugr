-- What lives inside the rock (#158).
--
-- V279 made the cave MOUTH and gave it the twilight-zone ecology: moss, the fungi that fruit on wet rock, the
-- animals that roost at an entrance. CAVE_INTERIOR is the other half — the chamber behind the doorway, which the
-- generator now derives from the mouths. It is dark at noon, out of the weather, and reachable only through a
-- mouth, and the catalogue has to have something in it or it is a place with nothing in it: the #161 gate refuses
-- any generated ground holding no mineral, no plant and no animal, which is exactly the check that should refuse
-- a biome added for its name.
--
-- The ticket says to gate subterranean fungi until this topology exists. It exists now, so here is one.

-- 1. The one thing that grows without light. A cave fungus fruits on wood and droppings washed or carried in,
--    and it does so all year, because a cave holds one temperature while the seasons turn outside — which is the
--    whole reason people have grown mushrooms in caves for as long as they have had caves. NULL season is that
--    fact, not an omission: it is the only forageable in the catalogue with no season, and deliberately so.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('cave_mushroom', 'Cave mushroom', 'FOOD', 22, 48, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('cave_fungus', 'FUNGI', 'CAVE_INTERIOR,CAVE_MOUTH', NULL, 16, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('cave_fungus', 'cave_mushroom', 2, 4, NULL)
ON CONFLICT DO NOTHING;

-- And the row that says the mushroom can be got at all. The Auditor holds every item to having a DECLARED
-- source and does not read flora_drop to work one out: the question it asks is "does the catalogue claim a way
-- to get this", not "could a way be derived". So an item can drop off a plant and still count as unobtainable.
-- Without this line CI goes red on every test that checks the Auditor, which is most of them — it did.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
('cave_mushroom', 'FLORA_DROP', 'flora_drop')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- 2. The animals that go all the way in. Bats roost deep and the two cave predators hunt there; the cave BEAR is
--    deliberately left at the mouth, because a bear dens where it can get its bulk in and out, not down a passage.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',CAVE_INTERIOR'
 WHERE species_key IN ('common_bat','giant_bat_swarm','cave_screecher','cave_troll')
   AND biome_affinity NOT LIKE '%CAVE_INTERIOR%';

UPDATE monster_profile SET biome_affinity = biome_affinity || ',CAVE_INTERIOR'
 WHERE species_key IN ('giant_bat_swarm','cave_screecher','cave_troll','ironmaw_mole')
   AND biome_affinity NOT LIKE '%CAVE_INTERIOR%';

-- 3. The cave's own stone. A cave is what limestone dissolves into, so the inside of one is the most honest place
--    in the world to find it; flint travels in that limestone, and a crystal pocket is a deep find before it is
--    anything else. The open-mountain ore (obsidian, pumice) stays on the open mountain where it belongs.
UPDATE mineral_definition SET biome_affinity = biome_affinity || ',CAVE_INTERIOR'
 WHERE mineral_key IN ('limestone_chunk','flint_stone','lens_crystal','rock_salt','quartzite_cobble','slate_shard')
   AND biome_affinity NOT LIKE '%CAVE_INTERIOR%';

-- 4. Guard the promise this migration makes, the way V279 did: every key must exist, or an affinity silently went
--    nowhere and the cave would be barren while looking configured. This is the failure mode that is invisible
--    from the migration text and obvious from the database.
DO $$
DECLARE missing text;
BEGIN
    SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
        'common_bat','giant_bat_swarm','cave_screecher','cave_troll']) k
     WHERE NOT EXISTS (SELECT 1 FROM wildlife_species w WHERE w.species_key = k);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'V288: no such wildlife species: %', missing; END IF;

    SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
        'limestone_chunk','flint_stone','lens_crystal','rock_salt','quartzite_cobble','slate_shard']) k
     WHERE NOT EXISTS (SELECT 1 FROM mineral_definition m WHERE m.mineral_key = k);
    IF missing IS NOT NULL THEN RAISE EXCEPTION 'V288: no such mineral: %', missing; END IF;

    -- And the point of the whole migration: the new ground must not be barren on any of the three counts the
    -- #161 gate checks, or this has added a biome with nothing in it.
    IF NOT EXISTS (SELECT 1 FROM mineral_definition WHERE biome_affinity LIKE '%CAVE_INTERIOR%')
       OR NOT EXISTS (SELECT 1 FROM flora_definition WHERE biome_affinity LIKE '%CAVE_INTERIOR%')
       OR NOT EXISTS (SELECT 1 FROM wildlife_species WHERE biome_affinity LIKE '%CAVE_INTERIOR%')
    THEN RAISE EXCEPTION 'V288: CAVE_INTERIOR would generate barren — it must hold stone, growth and life';
    END IF;

    -- The Auditor's own rule, asserted here where it costs a second rather than in CI where it costs an hour.
    -- The first cut of this migration added cave_mushroom with a flora_drop and no item_source, and every test
    -- in the suite that checks Auditor consistency went red on one line: "1 item definition(s) have no way to
    -- be obtained." A catalogue migration should answer that question before it is asked.
    SELECT string_agg(d.item_key, ', ') INTO missing
      FROM item_definition d
     WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = d.item_key)
       AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key = d.item_key);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V288: item(s) with no declared way to be obtained: %', missing;
    END IF;
END $$;
