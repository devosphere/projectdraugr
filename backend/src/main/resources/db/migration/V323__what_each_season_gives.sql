-- #161 — what each season gives: season eligibility for supplies that are not plants.
--
-- V322 gave the flora their seasons. Everything else a Chronicle takes from living things had none: a tamed goat
-- gave milk in January, a wild sheep shed a fleece in December, a hive gave up honey in midwinter, and a duck shot in
-- November had an egg in it. None of that is how any of it works:
--
--   eggs      wild-type fowl lay in spring and summer; laying stops as the days shorten (pigeons run a little later)
--   milk      a dam gives milk after she has young, which for wild-type stock means spring into autumn
--   wool      primitive sheep and goats do not grow wool forever — they moult it in late spring, and it is plucked then
--   honey     honey and wax are taken at the end of summer; a hive robbed in winter starves
--   insects   crickets and grasshoppers are adults from midsummer until the first frosts
--   bird eggs a wild bird carries an egg only in its nesting season
--
-- available_months SMALLINT[] is NULL for all year, as flora_drop.season is. in_season() is the ONE definition of
-- whether a list of months includes now, reading the world clock exactly as wildlife_abroad (V312) does, and every
-- reader of these three tables calls it.
--
-- Deliberately all year: water buffalo (a tropical breeder that calves in any month), and everything else in these
-- tables — chitin, silk, venom, grubs, snails, mussels, earthworms, meat, hide and bone.

CREATE OR REPLACE FUNCTION in_season(p_months SMALLINT[]) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
  SELECT p_months IS NULL
      OR EXTRACT(MONTH FROM (SELECT simulated_at FROM simulation_clock WHERE id = 1) AT TIME ZONE 'UTC')::int = ANY (p_months)
$$;

COMMENT ON FUNCTION in_season(SMALLINT[]) IS
  'Whether the world clock''s current month is in the list; NULL means all year (#161, V323). The one definition read by '
  'tamed yields, colony products and wildlife drops.';

ALTER TABLE tamed_yield           ADD COLUMN available_months SMALLINT[];
ALTER TABLE insect_colony_product ADD COLUMN available_months SMALLINT[];
ALTER TABLE wildlife_drop         ADD COLUMN available_months SMALLINT[];

UPDATE tamed_yield SET available_months = '{3,4,5,6,7,8}'        WHERE yield_kind = 'EGG' AND species_key <> 'wood_pigeon';
UPDATE tamed_yield SET available_months = '{3,4,5,6,7,8,9,10}'   WHERE yield_kind = 'EGG' AND species_key = 'wood_pigeon';
UPDATE tamed_yield SET available_months = '{4,5,6,7,8,9,10}'     WHERE yield_kind = 'MILK' AND species_key <> 'water_buffalo';
UPDATE tamed_yield SET available_months = '{5,6}'                WHERE yield_kind = 'WOOL';

UPDATE insect_colony_product SET available_months = '{7,8,9}'    WHERE item_key IN ('raw_honey','beeswax');
UPDATE insect_colony_product SET available_months = '{6,7,8,9}'  WHERE item_key IN ('dried_cricket','dried_grasshopper');

UPDATE wildlife_drop SET available_months = '{4,5,6}'            WHERE item_key = 'bird_egg';

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every month is a real month and no list is empty (an empty list would mean "never", which is a deletion).
  SELECT string_agg(src, ', ') INTO bad FROM (
    SELECT 'tamed_yield ' || species_key || '/' || item_key AS src, available_months m FROM tamed_yield
    UNION ALL SELECT 'insect_colony_product ' || colony_kind || '/' || item_key, available_months FROM insect_colony_product
    UNION ALL SELECT 'wildlife_drop ' || species_key || '/' || item_key, available_months FROM wildlife_drop) x
   WHERE m IS NOT NULL AND (cardinality(m) = 0 OR EXISTS (SELECT 1 FROM unnest(m) v WHERE v NOT BETWEEN 1 AND 12));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V323: impossible month lists: %', bad; END IF;

  -- The seasons were set where they were meant to be, and only there.
  SELECT count(*) INTO n FROM tamed_yield WHERE available_months IS NOT NULL;
  IF n <> 14 THEN RAISE EXCEPTION 'V323: expected 14 seasonal tamed yields (all but water buffalo milk), found %', n; END IF;
  IF EXISTS (SELECT 1 FROM tamed_yield WHERE species_key = 'water_buffalo' AND available_months IS NOT NULL) THEN
    RAISE EXCEPTION 'V323: water buffalo calve in any month and must stay all year';
  END IF;
  SELECT count(*) INTO n FROM insect_colony_product WHERE available_months IS NOT NULL;
  IF n <> 4 THEN RAISE EXCEPTION 'V323: expected 4 seasonal colony products, found %', n; END IF;

  -- Meat, hide and bone are there whenever the animal is: no carcass yield may be seasonal but the egg.
  SELECT string_agg(DISTINCT item_key, ', ') INTO bad FROM wildlife_drop WHERE available_months IS NOT NULL AND item_key <> 'bird_egg';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V323: carcass yields made seasonal: %', bad; END IF;
END $$;
