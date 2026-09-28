-- #37 — the water sounds its own depth and will not say whether you can cross it.
--
-- Act thirteen, standing on a river bank:
--
--   how deep is it   ->  "You sound the river with a stick — it shelves off gradually, past a safe wade
--                         before long."
--   can I cross here ->  UNKNOWN
--   wade across      ->  UNKNOWN
--   swim across      ->  UNKNOWN
--
-- The depth answer is one of the better lines in the game, and it describes a wade the player then cannot ask
-- about. Crossing EXISTS: waterCrossing() refuses to let a Chronicle carry more than a quarter of their capacity
-- into the sea or three quarters into a fen, a ford lifts the refusal entirely, and a laid timber way (V333)
-- lifts it over peat. All of it is reachable only by walking into the water and being told no.
--
-- JUDGE_CROSSING is the question, answered from those same readers and answered as a JUDGEMENT: what the ground
-- ahead is, whether anything crosses it, and whether what is on your back is the thing stopping you. It never
-- moves the Chronicle -- the same rule as V392, where asking whether water was safe had been answered by
-- drinking it.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('JUDGE_CROSSING', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking at water, reading the bank and weighing what is on your own back take the world exactly as it is and put nothing back; nothing is waded, swum, carried across or disturbed by the asking.',
  1, 0, 'ATTENTION', 4)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='JUDGE_CROSSING';
    IF n <> 1 THEN RAISE EXCEPTION 'V394: JUDGE_CROSSING must have exactly one impact card, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='JUDGE_CROSSING' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V394: looking at a crossing must leave it exactly as it was'; END IF;

    -- Looking before wading must be quicker and cheaper than the wading. MOVE is the act it stands in front of.
    SELECT COUNT(*) INTO n FROM activity_impact j JOIN activity_impact m ON m.intent_key='MOVE'
     WHERE j.intent_key='JUDGE_CROSSING' AND j.duration_minutes < m.duration_minutes AND j.labor_energy < m.labor_energy;
    IF n <> 1 THEN RAISE EXCEPTION 'V394: judging a crossing must cost less than making one'; END IF;

    -- And the thing it reads must still be there.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE crosses_soft_ground) THEN
        RAISE EXCEPTION 'V394: nothing crosses soft ground any more, so half this answer would be a lie';
    END IF;
END $$;
