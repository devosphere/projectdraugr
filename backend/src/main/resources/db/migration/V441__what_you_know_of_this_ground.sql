-- #37 — the card for the place reading, and the travel rule it finally makes askable.
--
-- planTravel decides whether a place can be found again, and the rule is careful:
--
--     locatable = on a map you carry
--              OR (a marker standing there AND the name memorised)
--              OR walked often enough to be routine (five visits)
--              OR memorised and visited recently
--
-- chronicle_chunk_visit has counted every arrival since the table existed, and chronicle_named_location knows
-- which names were committed to memory. None of it could be asked. Measured on a booted world:
--
--     what do I know about this place  ->  nothing at all
--
-- So the only way to learn that a name had faded was to try to travel to it and be told "you cannot call the
-- way to mind clearly enough to set out" — by which time the Chronicle is standing somewhere else with a day
-- spent on it. That is the fifth face this ticket keeps naming: the world knew and would not say.
--
-- JUDGE_PLACE answers it from the same clauses travel uses, and the five-visit threshold is now ONE constant
-- read by both rather than the same number written twice, so the reading cannot drift from the act.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Priced with the other readings: casting back over what you know of a place is
-- attention and nothing else, and takes the three minutes JUDGE_WATER takes.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_PLACE', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 3,
        'Perception alters nothing it touches. Casting back over what you know of this ground — whether you '
        || 'have stood here, what you called it, and whether you could find it again — costs a little '
        || 'attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_PLACE')
    THEN RAISE EXCEPTION 'V441: JUDGE_PLACE has no activity_impact row'; END IF;

    -- 2. A reading marks nothing, and 3. still costs the body a little — asserted from both sides, as
    --    nothingMarksTheLandWhileCostingTheBodyNothing requires.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_PLACE' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V441: a reading must not mark the ground'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_PLACE';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V441: the reading costs nothing and takes no time'; END IF;

    -- 4. Priced among its siblings rather than invented.
    IF d > (SELECT MAX(duration_minutes) FROM activity_impact
             WHERE intent_key IN ('JUDGE_WATER','JUDGE_LIGHT','JUDGE_FOOD','JUDGE_MAKING','JUDGE_CLOTHING','WHICH_WAY'))
    THEN RAISE EXCEPTION 'V441: the place reading takes longer than every other judgement'; END IF;

    -- 5. AND THE RECORDS IT READS ARE STILL THERE. The whole reading rests on three places where the world
    --    remembers ground: the visit count, the named location with its memorised flag, and the standing
    --    marker. If any of them went, the reading would tell every Chronicle they had never been anywhere.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle_chunk_visit' AND column_name='visit_count')
    THEN RAISE EXCEPTION 'V441: chronicle_chunk_visit.visit_count is gone; the reading cannot count arrivals'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle_named_location' AND column_name='memorized')
    THEN RAISE EXCEPTION 'V441: chronicle_named_location.memorized is gone; the reading cannot tell a fixed name from a faded one'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='location_marker')
    THEN RAISE EXCEPTION 'V441: location_marker is gone; the reading cannot see a marker standing'; END IF;
END $$;
