-- #37/#99 — the card for the reading of what this ground would take, and the guards on what it reads.
--
-- assembly_definition holds 110 verified builds; assembly_stage holds the first stage of each with exactly
-- what it wants; eight of the builds are gated to particular ground by site_biomes. Every bit of that is read
-- when a keeper NAMES a thing to build. Asked the other way round it reached nothing — measured live:
--
--     what can I build here / what could I build here / what can be built here  ->  nothing at all
--
-- so the only way to learn what this ground would take was to guess a name and be refused, one guess at a
-- time. On open grassland with empty hands the answer is now: 7 could be begun, 95 want materials not yet to
-- hand, 8 want different ground.
--
-- The reading is ONE query: what is within reach is counted once and every first stage's bill is compared
-- against it in the same statement, so a hundred and ten builds cost one round trip rather than three hundred.
-- A first stage that wants nothing counts as ready, which is what V427 settled for the pits and the frames.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Walking the ground over and reckoning what it would take is attention and a few
-- minutes of it — the same price as the other site readings.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_RAISING', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 5,
        'Perception alters nothing it touches. Walking this ground over and reckoning what could be raised on '
        || 'it — what could be begun with what is within reach, what wants materials you have not got, and '
        || 'what wants different ground — costs a little attention and marks nothing. Nothing is built by '
        || 'asking.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_RAISING')
    THEN RAISE EXCEPTION 'V446: JUDGE_RAISING has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_RAISING' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V446: a reading must not mark the ground — nothing is built by asking'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_RAISING';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V446: the reading costs nothing and takes no time'; END IF;

    -- WHAT IT READS. The stage order picks the FIRST stage, the requirement rows are the bill it compares
    -- against what is in reach, and site_biomes is what separates "you have not got the materials" from "this
    -- is the wrong ground" — which are different answers and the keeper is owed the difference.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='assembly_stage' AND column_name='stage_order')
    THEN RAISE EXCEPTION 'V446: assembly_stage.stage_order is gone; the first stage cannot be found'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='assembly_stage_requirement')
    THEN RAISE EXCEPTION 'V446: assembly_stage_requirement is gone; the reading cannot weigh a bill'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='assembly_definition' AND column_name='site_biomes')
    THEN RAISE EXCEPTION 'V446: assembly_definition.site_biomes is gone; the reading cannot tell wrong ground '
                         'from a missing material'; END IF;

    -- And there are builds for it to read, each with a first stage. A verified build with no first stage would
    -- be invisible to this reading AND unbuildable, so it is worth saying out loud here.
    SELECT COUNT(*) INTO n FROM assembly_definition d
     WHERE d.review_state='VERIFIED'
       AND NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key=d.assembly_key AND s.stage_order=1);
    IF n > 0 THEN RAISE EXCEPTION 'V446: % verified build(s) have no first stage', n; END IF;
END $$;
