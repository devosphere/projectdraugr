-- V176 — rust: iron and steel corrode when left on wet ground (EPIC #215 / story #220 maintenance, neglect, decay,
-- ... RUST ...). Tool wear (V139) is USE-based: a tool degrades only as it is worked. But metal also corrodes with
-- exposure — an iron axe or a steel striker dropped in a bog or left out in the wet rusts whether or not it is used,
-- while it lies there unmaintained. Nothing modelled that: a metal tool abandoned on wetland ground stayed pristine
-- forever. This adds the timestamp the weathering pass needs to account elapsed corrosion per item; the pass itself
-- (PhysicalItemService.weatherExposedMetal, run in the world tick) steps an exposed iron/steel item down the same
-- SOUND→WORN→BROKEN condition ladder use-wear climbs. Carried metal is kept and maintained, so only unowned ground
-- stock at a wet biome corrodes; bronze, copper, stone, and bone do not rust.

ALTER TABLE item_instance ADD COLUMN weathered_at TIMESTAMPTZ;

COMMENT ON COLUMN item_instance.weathered_at IS
    'When corrosion (#220 rust) was last accounted for this item — set on first exposure to wet ground, advanced each weathering pass. NULL = not yet weathering (never exposed, or not a rusting metal).';
