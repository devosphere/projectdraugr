-- V266 — husbandry yields: the catalogue promised them and nothing delivered.
-- fowl_egg, goat_milk and wool_tuft each declare item_source kind TAMED_YIELD, but no code anywhere referenced those
-- items and TAMED_YIELD was handled nowhere at all. Keeping animals produced nothing renewable: a Chronicle could
-- tame a goat and never take a drop of milk from it, tame fowl and never gather an egg. This is the whole point of
-- keeping stock rather than hunting it.
--
-- A yield needs a clock, or one animal could be milked continuously. wildlife_bond tracks draft fatigue, hunger and
-- conditioning but had nothing for produce, so last_yield_at records when this bond last gave, and the service
-- enforces a rest between yields — daily for milk and eggs, far longer for a fleece, which is not a daily thing.
ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS last_yield_at timestamptz;

COMMENT ON COLUMN wildlife_bond.last_yield_at IS
    'When this tamed animal last gave produce (milk, eggs, fleece). Enforces rest between yields so stock cannot be milked continuously.';
