-- V466: the card for TAKE_UP_AGAIN (#876).
--
-- "carry on" is how a player takes up the steps a stopped procedure set aside (V311). Written as a STEP of a
-- procedure, though, it reached nothing that knew what it meant: the plan it belongs to has just replaced
-- whatever was set aside, so there was nothing to resume, and the phrase fell through the intent chain to the
-- material matcher — which answered it with a recipe. Live, Chronicle 17:
--
--   urinate; gather plant fiber; prepare a tinder nest; carry on
--
-- ...and the last step wound the player's FIRE SPINDLE into a "fibre-wound spindle". The bow-drill ignition
-- they had spent the morning assembling then failed for want of the spindle their own procedure had eaten.
--
-- The intent now exists so the phrase has somewhere of its own to land. It is reached only when there is
-- nothing to take up, because resolvePlan resumes a real remainder before classification is ever consulted.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs
-- an hour and a half of CI.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('TAKE_UP_AGAIN', NULL, 0, FALSE, FALSE, 0, 0, 'ATTENTION', 1,
        'Looking for work you set aside alters nothing. When there is a remainder the plan resumes it and '
        || 'the steps themselves mark the ground; when there is none — which is the only way this intent is '
        || 'ever reached — nothing happens at all but a moment of looking.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'TAKE_UP_AGAIN')
    THEN RAISE EXCEPTION 'V466: TAKE_UP_AGAIN has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'TAKE_UP_AGAIN' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V466: looking for set-aside work must mark no ground'; END IF;
END $$;
