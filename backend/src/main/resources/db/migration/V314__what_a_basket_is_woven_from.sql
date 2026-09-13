-- #134 — what a basket is woven from.
--
-- The comment above basketWeaveUnitsInReach has said since #34 that "a basket is woven from whatever flexible stock is
-- to hand — split withies, vines, plant fibre, or twisted cordage". The code beneath it accepted three of those four:
-- plant fibre, vine and cordage, named in two places. Willow withies — the material basketry is most associated with,
-- anywhere willow grows — were never accepted, and neither were reeds, rushes, cattail leaves, nettle, bast or split
-- root, every one of which the world lets a Chronicle gather and every one of which people have woven baskets from.
--
-- weaving_stock declares what counts and how much. The dispatch guard and the craft both read it through the same
-- reach query, so they cannot disagree about what is in reach.
--
-- PRESERVED EXACTLY: plant fibre 1 unit, vine 1, cordage 2; plant fibre spent before vine, and cordage — scarcer and
-- wanted by fifty-seven recipes — spared until last. Eight units still make a basket.
--
-- NEW, with the reasoning for each weight:
--   willow_branch 3   a branch is split into several withies, and withies are the strongest stock here
--   reed_bundle 2     a bundle is several reeds
--   withy_rope 2      already twisted, like cordage, and spared nearly as late
--   cattail_leaf, bulrush_stalk, nettle_fiber, long_grass_fibre, soft_bast_strip, root_fibre_bundle 1 each
-- NOT stock, deliberately: milkweed fibre is fine cordage fibre, not basket material, and hazel rods are for frames
-- and hurdles, which is where the recipes that already consume them put them.

CREATE TABLE weaving_stock (
    item_key     VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    weave_units  SMALLINT NOT NULL,
    spare_order  SMALLINT NOT NULL,
    notes        TEXT NOT NULL,
    CONSTRAINT weaving_stock_units_sane CHECK (weave_units BETWEEN 1 AND 4),
    CONSTRAINT weaving_stock_order_sane CHECK (spare_order BETWEEN 0 AND 9)
);

COMMENT ON TABLE weaving_stock IS
  'Flexible stock a basket can be woven from (#134): weave units per physical length, and the order it is spent in '
  '(lowest first, so scarce stock is spared). Read by PhysicalItemService.basketWeaveUnitsInReach and craftBasket.';

INSERT INTO weaving_stock (item_key, weave_units, spare_order, notes) VALUES
  ('plant_fiber',       1, 0, 'The commonest stock; spent first.'),
  ('cattail_leaf',      1, 0, 'Cattail leaves plait into soft baskets and mats.'),
  ('bulrush_stalk',     1, 0, 'Rush is woven wherever it grows at a water''s edge.'),
  ('vine',              1, 1, 'A pliant vine, as before.'),
  ('nettle_fiber',      1, 1, 'Nettle fibre twines well enough for a light basket.'),
  ('long_grass_fibre',  1, 1, 'Long grass coils into a basket with patience.'),
  ('soft_bast_strip',   1, 1, 'Inner bark, stripped and softened, weaves.'),
  ('root_fibre_bundle', 1, 2, 'Split root is what coiled baskets are sewn with.'),
  ('reed_bundle',       2, 3, 'A bundle is several reeds; reeds are wanted elsewhere, so spent after the common stock.'),
  ('willow_branch',     3, 4, 'Split into withies — the strongest basket stock there is.'),
  ('withy_rope',        2, 8, 'Already twisted; spared nearly as late as cordage.'),
  ('fiber_cordage',     2, 9, 'Twisted cordage counts double and is spared until last, as before.');

DO $$
DECLARE bad text; n int;
BEGIN
  -- The three that were already accepted must weave exactly as they did, or every existing basket test and habit
  -- changes: plant fibre 1, vine 1, cordage 2, spent in that order.
  SELECT count(*) INTO n FROM weaving_stock WHERE (item_key, weave_units) IN (('plant_fiber',1),('vine',1),('fiber_cordage',2));
  IF n <> 3 THEN RAISE EXCEPTION 'V314: an existing weave value moved (% of 3 preserved)', n; END IF;
  IF NOT ((SELECT spare_order FROM weaving_stock WHERE item_key='plant_fiber') < (SELECT spare_order FROM weaving_stock WHERE item_key='vine')
      AND (SELECT spare_order FROM weaving_stock WHERE item_key='vine') < (SELECT spare_order FROM weaving_stock WHERE item_key='fiber_cordage')) THEN
    RAISE EXCEPTION 'V314: plant fibre, then vine, then cordage must stay the order they are spent in';
  END IF;

  -- Cordage is spared last: nothing may be kept back longer than the stock fifty-seven recipes want.
  SELECT string_agg(item_key, ', ') INTO bad FROM weaving_stock
   WHERE spare_order > (SELECT spare_order FROM weaving_stock WHERE item_key='fiber_cordage');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V314: spared even later than cordage: %', bad; END IF;

  -- Stock nobody can obtain would read as a way to weave that never comes.
  SELECT string_agg(ws.item_key, ', ') INTO bad FROM weaving_stock ws
   WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = ws.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V314: weaving stock nobody can obtain: %', bad; END IF;

  -- A basket must be reachable from a single common, gatherable kind of stock — otherwise the table only widens
  -- what a Chronicle who already had cordage could do.
  SELECT count(*) INTO n FROM weaving_stock ws JOIN item_source s ON s.item_key = ws.item_key
   WHERE s.source_kind = 'FLORA_DROP' AND ws.weave_units >= 1;
  IF n < 4 THEN RAISE EXCEPTION 'V314: fewer than four gatherable plant stocks can weave a basket (%)', n; END IF;
END $$;
