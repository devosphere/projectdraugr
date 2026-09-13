-- #161 — what grows when: season eligibility for three plants that had none.
--
-- Foraging takes a flora_drop row only in its own season, or in any season when the row names none. Eighty rows
-- name none, and most of them rightly: roots, bark, fibre, moss and fungus are there to be had all year, and winter
-- depends on them (CatalogueWorldSeedCompatibilityIntegrationTest.noSeasonLeavesTheWorldWithNothingToForage).
-- Three are not like that, and a Chronicle could gather each of them in the dead of winter:
--
--   flax_stalk        flax is pulled when the stems yellow and the seed bolls ripen, at the end of summer
--   chamomile_flower  the flower heads open in summer and are picked then; there is nothing to pick in January
--   water_chestnut    the nuts ripen and drop in autumn, and rot or sink over winter
--
-- Deliberately left any-season: pignut (dug from spring through autumn, and a winter famine food where the ground
-- is soft), cave_mushroom (a cave has no season) and straight_sapling (a stem is a stem all year).

UPDATE flora_drop SET season = 'SUMMER' WHERE item_key = 'flax_stalk'       AND season IS NULL;
UPDATE flora_drop SET season = 'SUMMER' WHERE item_key = 'chamomile_flower' AND season IS NULL;
UPDATE flora_drop SET season = 'AUTUMN' WHERE item_key = 'water_chestnut'   AND season IS NULL;

DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM flora_drop
   WHERE (item_key, season) IN (('flax_stalk','SUMMER'),('chamomile_flower','SUMMER'),('water_chestnut','AUTUMN'));
  IF n <> 3 THEN RAISE EXCEPTION 'V322: expected three re-seasoned drops, found %', n; END IF;

  -- Winter must still offer food on at least four kinds of ground (the guard the compatibility test holds).
  SELECT count(DISTINCT b) INTO n FROM flora_drop d JOIN flora_definition f ON f.flora_key = d.flora_key,
         unnest(string_to_array(f.biome_affinity, ',')) b
   WHERE d.season IS NULL;
  IF n < 4 THEN RAISE EXCEPTION 'V322: out-of-season forage now reaches only % kinds of ground', n; END IF;
END $$;
