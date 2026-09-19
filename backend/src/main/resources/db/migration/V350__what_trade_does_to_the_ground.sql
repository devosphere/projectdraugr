-- #113 — what trade with a people does to the ground it happens on.
--
-- The ActivityImpactCard invariant: every intent says what it does to the ground. An exchange at the landing moves
-- goods between hands and changes nothing underfoot; laying out, looking over and handing across what is traded
-- takes the better part of half an hour.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('TRADE_WITH_PEOPLE', NULL, 0, FALSE, FALSE,
        'An exchange at the landing moves goods from hand to hand and changes nothing underfoot: what is traded is laid out, looked over and handed across.',
        2, 0, 'ATTENTION', 25)
ON CONFLICT (intent_key) DO NOTHING;
