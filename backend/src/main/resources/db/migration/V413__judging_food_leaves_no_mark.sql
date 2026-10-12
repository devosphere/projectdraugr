-- V413 — what judging food does to the ground it is done on (#37)
--
-- `ActivityImpactCardIntegrationTest.everyIntentHasACardAndEveryCardHasAnIntent` holds that EVERY performable
-- intent declares its footprint, and its failure message is the rule:
--
--   "these procedures can be performed and nobody has said what they do to the ground they are done on — add a
--    row to activity_impact, and if the answer is 'nothing' the notes must say why: [JUDGE_FOOD]"
--
-- The answer here is nothing, and the notes say why. JUDGE_FOOD is read-only in the strictest sense: it looks at
-- what grows within reach, reads `is_poisonous` and `toxic`, and reports. It picks nothing, moves nothing, and
-- above all CONSUMES nothing — which is the entire point of the intent, since the defect it closes was
-- "is this safe to eat" being answered by EATING it.
--
-- The numbers are taken from its siblings rather than invented. JUDGE_WATER (3 minutes, 1 energy) is the closest
-- analogue — crouch down, look, decide — but this one surveys the growth across a whole piece of ground and
-- goes through what is in the pack, which is nearer TAKE_STOCK_OF_FOOD's 10 minutes and 2 energy. Four minutes
-- and 1 energy: longer than a glance at a stream, shorter than an inventory.
--
-- footprint_kind is NULL and footprint_amount 0, as every perception intent's is: looking at a mushroom leaves
-- nothing on the ground. labor_hygiene is 0 for the same reason — no handling, no mess.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('JUDGE_FOOD', NULL, 0, false, false,
        'Perception alters nothing it touches. Going over what grows within reach, reading which of it is '
        || 'poison and going through what is already in the pack takes the ground exactly as it is and leaves '
        || 'it that way. Nothing is picked and nothing is eaten — not eating it is the whole purpose of the '
        || 'act, since the question "is this safe to eat" was previously answered by eating it.',
        1, 0, 'ATTENTION', 4)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n int;
BEGIN
  -- 1. THE ROW MUST BE THERE. ON CONFLICT DO NOTHING is right for re-running and wrong for silence.
  SELECT count(*) INTO n FROM activity_impact WHERE intent_key = 'JUDGE_FOOD';
  IF n <> 1 THEN
    RAISE EXCEPTION 'V413: JUDGE_FOOD has % activity_impact rows, expected exactly 1', n;
  END IF;

  -- 2. AND IT MUST STILL BE A PERCEPTION. If a later edit gave this intent a footprint, the claim in its own
  --    notes ("alters nothing it touches") would become a lie told in the same even voice as every true line.
  IF EXISTS (SELECT 1 FROM activity_impact WHERE intent_key='JUDGE_FOOD'
              AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire)) THEN
    RAISE EXCEPTION 'V413: JUDGE_FOOD now claims a footprint, and its notes say it leaves none';
  END IF;
END $$;
