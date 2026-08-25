-- V167: young growth vs mature timber — the cost of clear-cutting (EPIC #200 forestry / #201 tree condition).
--
-- Felling drew a finite stand down (#200) and a clear-cut stand recolonised from a neighbour as a single sapling,
-- but that young regrowth felled for exactly the same timber as a century-old wood — so clear-cutting carried no real
-- penalty beyond the wait. In truth a young stand is thin poles, not sawlogs. This records when the current cohort of
-- trees took root, so a wood cut to nothing and grown back gives poor timber until it matures — the standing reason
-- to harvest a wood selectively and leave it standing rather than strip it bare.
--
-- NULL means an old natural stand, long mature (the lazy-seeded natural woodland and anything already recorded), so
-- nothing that stands today changes. established_at is set only when a new cohort takes root: a planted sapling, or a
-- clear-cut recolonised from a neighbour. A cohort is young until it has stood for its species' regrowth span.

ALTER TABLE chunk_flora ADD COLUMN established_at TIMESTAMPTZ;

COMMENT ON COLUMN chunk_flora.established_at IS
    'When the current tree cohort took root (#201): set on planting and on recolonisation after a clear-cut; NULL is an old, mature natural stand. A cohort younger than its species regrowth_days yields only thin poles when felled.';
