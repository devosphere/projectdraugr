-- V179 — tillage: breaking and turning the ground before sowing (EPIC #162 / story #165 land preparation). Sown
-- grain came up the same whether the ground was worked or not. In truth a broken, turned seedbed — loosened so the
-- roots take and the weeds are set back — yields markedly more than seed cast on unbroken sod. This adds the prepared
-- ground a Chronicle makes with a digging tool: a tilled patch that a sowing draws on for a fuller stand. Tillage is
-- optional (seed still comes up on unbroken ground, just thinner), so nothing already sown changes; the reward is for
-- the labour of preparing the ground first.
CREATE TABLE tilled_ground (
    chunk_id  UUID        PRIMARY KEY REFERENCES world_chunk(id),
    tilled_at TIMESTAMPTZ NOT NULL
);

-- Whether the stand grew from a tilled seedbed — set at sowing from the tilled ground it consumed, and read at
-- harvest for the fuller yield tilled ground gives.
ALTER TABLE crop_stand ADD COLUMN tilled BOOLEAN NOT NULL DEFAULT FALSE;
