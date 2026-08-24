-- V165: finite mineral deposits — a seam is not bottomless (EPIC #180 heavy industry / #181 finite site depletion).
--
-- Mineral gathering (V50) rolled against a mineral's rarity with no memory of the ground: a Chronicle could take iron,
-- copper, limestone, or flint from the same chunk forever. That made the whole extraction economy — the metal ladder,
-- the lime chain — free of any resource constraint, the way it was free of a fuel constraint until the charcoal clamp
-- (V163). Real deposits run out. This records how much a worked seam still holds, per chunk and per mineral, drawn
-- down as it is quarried; when it is spent, that mineral is worked out there and must be sought on fresh ground.
--
-- The deposit is recorded lazily — the first time a chunk is worked for a given mineral it is entered at full — so no
-- world-generation change is needed and existing ground simply fills in as it is first quarried. A seam is generous
-- (dozens of units) so ordinary play is not pestered; it is the sustained, industrial draw that finally exhausts it.

CREATE TABLE mineral_deposit (
    chunk_id        UUID         NOT NULL REFERENCES world_chunk(id),
    mineral_key     VARCHAR(100) NOT NULL REFERENCES mineral_definition(mineral_key),
    remaining_units INTEGER      NOT NULL CHECK (remaining_units >= 0),
    first_worked_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (chunk_id, mineral_key)
);

COMMENT ON TABLE mineral_deposit IS
    'Per-chunk remaining stock of each mineral (#181). Seeded lazily at full on first gather by PhysicalItemService.gatherMineral and drawn down as the seam is quarried; zero means worked out here.';
