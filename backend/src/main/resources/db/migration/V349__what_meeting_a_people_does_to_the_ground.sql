-- #112 — what meeting a people does to the ground it happens on.
--
-- Every intent has a card here saying what it does to the ground (the ActivityImpactCard invariant), and contact is
-- a new intent. It leaves nothing behind: watching, calling across the water, waiting, gesturing and speaking are
-- done by a Chronicle standing still in the open, which is the whole of their etiquette. It is slow: a quarter of an
-- hour at the edge of the reeds is the least that waiting to be answered takes.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('CONTACT_PEOPLE', NULL, 0, FALSE, FALSE,
        'Meeting a people is done standing still in the open, watching, calling across the water and waiting to be answered; it takes the ground as it is and leaves nothing on it.',
        3, 0, 'ATTENTION', 15)
ON CONFLICT (intent_key) DO NOTHING;
