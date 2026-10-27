-- #37 — the card for the reading of unfinished work, and the records it is held to.
--
-- assembly_instance keeps every piece a Chronicle has begun, assembly_stage_completion keeps which stages are
-- set into it, assembly_stage keeps what the next one wants — its tool, its fire, the span it must cure, the
-- weather it cannot be worked in — and construction_project keeps a raising at its percentage. None of it
-- could be asked. Swept on a booted world:
--
--     what am I working on / what is unfinished / what jobs are half done  ->  nothing at all
--     is the shelter finished / what is left to do on the shelter          ->  nothing at all
--     how far along is the lean-to   ->  TAKE_STOCK_OF_CAMP, a list of what is already raised
--     how far along is the hide      ->  MEASURE, "you pace it out and reckon by eye"
--     how is my shelter holding up   ->  TAKE_STOCK_OF_GEAR, the clothes on your back
--
-- The last three are the worse half: a question about a work in progress answered in the same even voice with
-- something else entirely. JUDGE_WORK_IN_HAND answers it from the stage records, in the same terms
-- AssemblyService.advance refuses on, so what a keeper is told is waiting is what would actually stop them.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Priced with the other readings: going round what you have begun is attention and
-- nothing else, and takes a little longer than the readings that answer about one thing, because it is a walk
-- round the work.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_WORK_IN_HAND', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 5,
        'Perception alters nothing it touches. Going round what you have begun and not finished — the stages '
        || 'set into a piece, what the next one wants of you, a raising at its percentage — costs a little '
        || 'attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_WORK_IN_HAND')
    THEN RAISE EXCEPTION 'V442: JUDGE_WORK_IN_HAND has no activity_impact row'; END IF;

    -- 2. A reading marks nothing, and 3. still costs the body a little — both sides, as
    --    nothingMarksTheLandWhileCostingTheBodyNothing requires.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_WORK_IN_HAND' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V442: a reading must not mark the ground'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_WORK_IN_HAND';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V442: the reading costs nothing and takes no time'; END IF;

    -- 4. AND THE RECORDS IT READS ARE STILL THERE. The whole reading rests on the stage tables and the
    --    project's percentage. If any of them went, the reading would tell every Chronicle that nothing they
    --    had begun was waiting on them — which is a lie a keeper would act on.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='assembly_stage' AND column_name='cure_minutes')
    THEN RAISE EXCEPTION 'V442: assembly_stage.cure_minutes is gone; the reading cannot say what is still curing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='assembly_stage' AND column_name='weather_limit')
    THEN RAISE EXCEPTION 'V442: assembly_stage.weather_limit is gone; the reading cannot say what the sky forbids'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='assembly_stage_completion')
    THEN RAISE EXCEPTION 'V442: assembly_stage_completion is gone; the reading cannot count what is set in'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_project' AND column_name='progress_percent')
    THEN RAISE EXCEPTION 'V442: construction_project.progress_percent is gone; the reading cannot place a raising'; END IF;
END $$;
