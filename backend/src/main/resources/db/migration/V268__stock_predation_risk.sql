-- V268 — kept stock can be taken. Once an animal was tamed it was safe forever: nothing anywhere reduced a bonded
-- animal's population, so a goat left on open ground beside a wolf range was in no danger at all. That left every
-- secure structure in #108 — the predator-resistant coop, the night pen, the boar isolation pen — with nothing to be
-- resistant to, and husbandry with no downside worth building against.
--
-- Deliberately conservative. A loss needs ALL of: a real carnivore population on the keeper's ground, stock left
-- unprotected there, darkness, and no loss from that herd within the rest window. A completed animal pen, or any
-- build the catalogue counts a shelter, prevents it outright — which is the whole point of penning stock at night.
-- One animal at a time, never below none, and the herd is only ever thinned, never wiped.
ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS last_raid_at timestamptz;

COMMENT ON COLUMN wildlife_bond.last_raid_at IS
    'When this herd was last raided by a predator. Enforces a rest window so unprotected stock are thinned occasionally, not stripped.';
