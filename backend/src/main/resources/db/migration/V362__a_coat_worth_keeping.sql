-- #106 — a coat worth keeping.
--
-- The husbandry catalogue names a curry comb, a grooming brush and a mane comb, and #106 recorded why none of them
-- could be built: there was no coat state for them to act on, so grooming would have been an animation. A kept
-- animal had exactly two conditions — sick, or not sick — and a keeper could do nothing between them.
--
-- A coat is a state a keeper keeps. Left alone it mats; standing in its own filth it mats far faster; matted and
-- verminous it makes the animal ill and its fleece is not worth the shearing. A comb — the bone comb the catalogue
-- already knows how to carve — puts most of it back. Nothing new to craft: what was missing was the state, and the
-- consequence of letting it go.

ALTER TABLE wildlife_bond
    ADD COLUMN coat_condition INTEGER NOT NULL DEFAULT 100 CHECK (coat_condition BETWEEN 0 AND 100);

COMMENT ON COLUMN wildlife_bond.coat_condition IS
  '#106: how clean and unmatted a kept animal''s coat is. Wears each turn (faster on fouled ground), raised by grooming with a comb; under 40 the animal sickens more readily and its fleece is not worth taking.';

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('GROOM_ANIMAL', NULL, 0, FALSE, FALSE,
        'Combing a kept animal over from neck to flank: what comes out of the coat is dirt, shed hair and vermin, dropped where the animal stands and trodden in. The ground is not marked by it.',
        6, 3, 'FINE_MOTOR', 30)
ON CONFLICT (intent_key) DO NOTHING;
