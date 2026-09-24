-- #37 — BREEDING_PROSPECTS must say what it does to the ground it is done on.
--
-- Every intent owes the same account (ActivityImpactCardIntegrationTest): somebody has said what performing it
-- does to the place it is performed in, and where the answer is "nothing" the notes have to say why.
--
-- Looking over your own stock and reckoning whether they will settle to breeding is perception. You walk among
-- animals you already keep and read their condition; nothing is handled, nothing is moved, nothing is left
-- behind. It takes a little longer than a glance because you are counting a herd and looking at each of them,
-- and it costs a little more effort for the same reason — but the ground is exactly as it was.
--
-- It cannot dirty you: reading an animal's condition is looking, not mucking out.
INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('BREEDING_PROSPECTS', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking over stock you already keep, counting them and reading their condition, takes the herd exactly as it is and puts nothing back.',
  3, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'BREEDING_PROSPECTS';
    IF n <> 1 THEN RAISE EXCEPTION 'V380: BREEDING_PROSPECTS must have exactly one impact card, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'BREEDING_PROSPECTS'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V380: looking over the stock must leave the ground exactly as it was'; END IF;

    -- The guard's own companion rules, asserted here so a bad card cannot reach a 65-minute CI round: a card
    -- that marks nothing must argue why (40 characters of notes), and nothing may cost cleanliness without
    -- costing effort.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'BREEDING_PROSPECTS' AND (length(btrim(notes)) < 40 OR (labor_hygiene > 0 AND labor_energy = 0));
    IF n > 0 THEN RAISE EXCEPTION 'V380: the card does not meet the rules every card meets'; END IF;
END $$;
