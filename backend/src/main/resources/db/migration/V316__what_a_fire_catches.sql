-- #134 — what a roaring fire catches.
--
-- #219 made loose fuel piled beside a roaring hearth catch and burn in dry weather. FireService named what could
-- catch as three literals: dry branches, tinder nests and wood shavings. V274 had already moved the same decision for
-- structures out of that file ("what can catch is a property of the KIND, not a list"), but loose stock stayed a list,
-- and the catalogue grew past it. A heap of dry grass, a stack of fatwood, a roll of birch bark or a pile of char
-- tinder — all tinder by their very purpose — sat beside a roaring fire through a dry night and never caught.
--
-- loose_fuel declares what catches from a roaring fire's heat and embers. FireService reads it.
--
-- Deliberately NOT here: green grass (it smoulders, not catches), charcoal (it needs a draught and sustained heat, not
-- a stray ember), and anything carried on the body, which the spread already leaves alone.

CREATE TABLE loose_fuel (
    item_key  VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    notes     TEXT NOT NULL
);

COMMENT ON TABLE loose_fuel IS
  'Loose dry stock that catches when left on the ground beside a roaring fire in dry weather (#219, #134). '
  'Read by FireService.scorchNearbyFlammables.';

INSERT INTO loose_fuel (item_key, notes) VALUES
  ('dry_branch',         'As before.'),
  ('tinder_nest',        'As before.'),
  ('wood_shaving',       'As before.'),
  ('char_tinder',        'Made to take a spark; an ember is more than enough.'),
  ('dry_twig',           'Kindling, by definition.'),
  ('dry_grass_bundle',   'Dry grass goes up at a touch.'),
  ('birch_bark_shed',    'Birch bark is full of oil and burns even damp.'),
  ('fatwood_stick',      'Resin-soaked heartwood; the best firelighter there is.'),
  ('cattail_fluff',      'Tinder fluff catches from a single spark.'),
  ('fallen_leaf_litter', 'Dry leaf litter carries a ground fire.'),
  ('straw_bundle',       'Dry straw.');

DO $$
DECLARE bad text;
BEGIN
  -- What already caught still catches.
  IF (SELECT count(*) FROM loose_fuel WHERE item_key IN ('dry_branch','tinder_nest','wood_shaving')) <> 3 THEN
    RAISE EXCEPTION 'V316: the three kinds that already caught must still catch';
  END IF;

  -- Nothing nobody can come by.
  SELECT string_agg(lf.item_key, ', ') INTO bad FROM loose_fuel lf
   WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = lf.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V316: loose fuel nobody can obtain: %', bad; END IF;

  -- Green stock and worked charcoal are named in the header as not catching; keep them out.
  SELECT string_agg(item_key, ', ') INTO bad FROM loose_fuel WHERE item_key ~ '^green_' OR item_key = 'charcoal';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V316: stock that does not catch from an ember: %', bad; END IF;
END $$;
