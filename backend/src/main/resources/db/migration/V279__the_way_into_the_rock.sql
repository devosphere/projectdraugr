-- #158 — the way into the rock.
--
-- A cave bear, a cave troll, a cave screecher and a giant bat swarm were all in the catalogue, and every one of
-- them lived on a bare, open mountaintop, because rock was the closest thing to a cave the world could offer.
-- The generator now derives CAVE_MOUTH from mountain that opens onto walkable ground, and this migration gives
-- that ground its ecology so it is a place rather than a label.
--
-- Two rules kept throughout:
--   * Nothing loses a home. Every affinity below is ADDED to what the entry already had, so the open mountain
--     keeps everything it carried and no existing chain goes dark. The mountain-only ore in particular
--     (obsidian, pumice, lens crystal) is untouched — 44 of the 57 mountain cells are still open rock.
--   * Nothing gains a home it would not have. Sunlit forest fungi stay in the forest; what moves into the cave
--     is what actually lives at a cave mouth — the twilight-zone damp, the roosting animals, and the karst.

-- The animals that were named for caves and had none.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',CAVE_MOUTH'
 WHERE species_key IN ('cave_bear','cave_screecher','cave_troll','common_bat','giant_bat_swarm')
   AND biome_affinity NOT LIKE '%CAVE_MOUTH%';

-- The monsters that shelter in rock. The fliers that merely nest high (harpy, wyvern, frostwing owl, wave roc)
-- are deliberately left on the open mountain: a cliff eyrie is not a cave, and putting them here would make the
-- cave mouth the single most dangerous ground in the world for no reason a player could reason about.
UPDATE monster_profile SET biome_affinity = biome_affinity || ',CAVE_MOUTH'
 WHERE species_key IN ('cave_screecher','cave_troll','giant_bat_swarm','ironmaw_mole','crag_hyena')
   AND biome_affinity NOT LIKE '%CAVE_MOUTH%';

-- Karst and the cave's own stone. Limestone is what caves are dissolved OUT of, so a cave mouth is the most
-- honest place in the world to find it; flint travels in that limestone; and a crystal pocket is a cave find.
UPDATE mineral_definition SET biome_affinity = biome_affinity || ',CAVE_MOUTH'
 WHERE mineral_key IN ('limestone_chunk','flint_stone','granite_cobble','quartzite_cobble','slate_shard','lens_crystal','rock_salt')
   AND biome_affinity NOT LIKE '%CAVE_MOUTH%';

-- The twilight zone at the entrance: damp, shaded, out of the sun. Moss and the two fungi that actually fruit
-- on wet rock and dead wood in shade. The sun-loving field and grassland fungi stay where they are.
UPDATE flora_definition SET biome_affinity = biome_affinity || ',CAVE_MOUTH'
 WHERE flora_key IN ('sphagnum_moss','oyster_fungus','oyster_mushroom')
   AND biome_affinity NOT LIKE '%CAVE_MOUTH%';

-- Guard the promise this migration makes: every one of these keys must exist, or the affinity silently went
-- nowhere and the cave would be barren while looking configured.
DO $$
DECLARE missing text;
BEGIN
  SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
    'cave_bear','cave_screecher','cave_troll','common_bat','giant_bat_swarm']) k
   WHERE k NOT IN (SELECT species_key FROM wildlife_species);
  IF missing IS NOT NULL THEN RAISE EXCEPTION 'V279: unknown wildlife species: %', missing; END IF;

  SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
    'cave_screecher','cave_troll','giant_bat_swarm','ironmaw_mole','crag_hyena']) k
   WHERE k NOT IN (SELECT species_key FROM monster_profile);
  IF missing IS NOT NULL THEN RAISE EXCEPTION 'V279: unknown monster: %', missing; END IF;

  SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
    'limestone_chunk','flint_stone','granite_cobble','quartzite_cobble','slate_shard','lens_crystal','rock_salt']) k
   WHERE k NOT IN (SELECT mineral_key FROM mineral_definition);
  IF missing IS NOT NULL THEN RAISE EXCEPTION 'V279: unknown mineral: %', missing; END IF;

  SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
    'sphagnum_moss','oyster_fungus','oyster_mushroom']) k
   WHERE k NOT IN (SELECT flora_key FROM flora_definition);
  IF missing IS NOT NULL THEN RAISE EXCEPTION 'V279: unknown flora: %', missing; END IF;

  -- The cave must not be barren: something to gather, something to eat, and something that lives there.
  IF (SELECT count(*) FROM mineral_definition WHERE biome_affinity LIKE '%CAVE_MOUTH%') < 5
     OR (SELECT count(*) FROM flora_definition WHERE biome_affinity LIKE '%CAVE_MOUTH%') < 2
     OR (SELECT count(*) FROM wildlife_species WHERE biome_affinity LIKE '%CAVE_MOUTH%') < 4
  THEN RAISE EXCEPTION 'V279: the cave mouth would generate barren'; END IF;
END $$;
