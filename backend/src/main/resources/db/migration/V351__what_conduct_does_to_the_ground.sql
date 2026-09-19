-- #114 — what conduct toward a people does to the ground it happens on.
--
-- The ActivityImpactCard invariant: every intent says what it does to the ground. Conduct toward a people — theft,
-- threat, harm, seizure, apology, restitution, shared work — is not measured by the ground at all but by those who
-- saw it: what it leaves is written in the community's history (V345) and in their standing, and a theft by night
-- leaves nothing anyone saw. Recording it here as a disturbance too would count the same act twice.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('CONDUCT_TOWARD_PEOPLE', NULL, 0, FALSE, FALSE,
        'What is done to a people is kept by the people, not the ground: their history and their standing carry it, and a theft by night leaves nothing anyone saw.',
        6, 1, 'ATTENTION', 20)
ON CONFLICT (intent_key) DO NOTHING;
