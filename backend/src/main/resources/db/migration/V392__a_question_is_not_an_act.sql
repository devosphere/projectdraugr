-- #37 — "is the water safe to drink" was answered by drinking it.
--
--   is the water safe to drink  ->  DRINK / SUCCEEDED
--   "You drink from the marsh water. It eases the dryness, but it is not clean, and the gut will know it."
--
-- The Chronicle asked WHETHER, and the world answered by taking the risk. That is the worst shape a wrong answer
-- can take: the question is precisely an attempt to avoid the thing, and asking it incurs the thing.
--
-- And the world already knew. safeWaterSource() reads whether the water moves, whether the camp above it is
-- foul, whether a spring head is walled, and whether the ground was designated for waste; drawTreatment() reads
-- a standing structure that clears a raw draw; waterNamed() says what the water is called. Every part of the
-- answer existed and none of it could be asked for without swallowing a mouthful first.
--
-- JUDGE_WATER is that question, answered as a JUDGEMENT of what can be seen -- moving or standing, clean camp or
-- fouled, a filter to hand or not -- and never as a verdict on what cannot. A person looking at a stream cannot
-- see what is in it, and the prose says so: the honest answer ends in what it would cost to be sure, which is
-- boiling it.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('JUDGE_WATER', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking at water, smelling it and reading the ground above it take the world exactly as it is and put nothing back; nothing is drunk, drawn, carried or disturbed by the asking.',
  1, 0, 'ATTENTION', 3)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='JUDGE_WATER';
    IF n <> 1 THEN RAISE EXCEPTION 'V392: JUDGE_WATER must have exactly one impact card, found %', n; END IF;

    -- Looking at water must cost the ground nothing. If a later edit gives this a footprint, that is a claim
    -- that inspecting a stream disturbs it, and it should be argued for.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='JUDGE_WATER' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V392: judging water must leave it exactly as it was';
    END IF;

    -- It must cost less than drinking does, because it is the cheaper thing a careful person does first.
    SELECT COUNT(*) INTO n FROM activity_impact j JOIN activity_impact d ON d.intent_key='DRINK'
     WHERE j.intent_key='JUDGE_WATER' AND j.duration_minutes < d.duration_minutes;
    IF n <> 1 THEN RAISE EXCEPTION 'V392: looking before drinking must be quicker than drinking'; END IF;
END $$;
