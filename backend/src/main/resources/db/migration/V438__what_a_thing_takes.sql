-- #37 — the card for the making reading, and the question that had been answered by the player's bowels.
--
-- Every bill of materials in this game is data, in TWO tables: material_process_input for what is always
-- wanted, and material_process_input_group for "any one of these will do" (tanning takes two pieces of oak
-- bark AND any one hide — a bill read from one table alone looks like matter from nothing). A Chronicle could
-- not ask about any of it. Measured on a booted world:
--
--   what do I need for a basket  ->  SENSE_BODY: "There is a hard pressure low in you…"
--   what does a basket take      ->  nothing
--   what is clay good for        ->  nothing
--   what can I do with the clay  ->  nothing
--
-- The first is the worst wrong answer of the sweep: a question about materials answered with the state of the
-- player's bowels, in the same even voice as every true answer. "what do i need" sat inside the body block's
-- frame, so the new rule goes above it.
--
-- JUDGE_MAKING reads it both ways round, because a player asks it both ways: name a piece of WORK and it reads
-- the bill from both tables and says which of it is within reach; name a MATERIAL and it says what that
-- material goes into. Both are about a thing the player NAMED. It is deliberately NOT "what can I make here",
-- which would be a menu of the catalogue — this project answers questions and does not offer menus.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one is an
-- hour and a half of CI for nothing. Priced with the other readings: it marks nothing, costs a little
-- attention, and takes the four minutes JUDGE_FOOD takes — going over a bill is of a size with going over what
-- grows within reach.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_MAKING', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 4,
        'Perception alters nothing it touches. Reckoning up what a piece of work would take, or what a '
        || 'material is good for, costs a little attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_MAKING')
    THEN RAISE EXCEPTION 'V438: JUDGE_MAKING has no activity_impact row'; END IF;

    -- 2. A reading marks nothing.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_MAKING' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V438: a reading must not mark the ground'; END IF;

    -- 3. ...and still costs the body something, or it reads as a card somebody forgot to fill in.
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_MAKING';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V438: the reading costs nothing and takes no time'; END IF;

    -- 4. Priced with its siblings rather than invented.
    IF d > (SELECT MAX(duration_minutes) FROM activity_impact
             WHERE intent_key IN ('JUDGE_WATER','JUDGE_CROSSING','JUDGE_FOOD','JUDGE_LIGHT','WHICH_WAY'))
    THEN RAISE EXCEPTION 'V438: the making reading takes longer than every other judgement'; END IF;

    -- 5. AND THERE IS SOMETHING TO READ. The whole reading rests on the two input tables; if either were ever
    --    emptied, this would answer every question with "it takes nothing but the working of it", which is the
    --    matter-from-nothing claim the Auditor exists to refuse.
    SELECT COUNT(*) INTO n FROM material_process_input;
    IF n < 100 THEN RAISE EXCEPTION 'V438: only % direct process inputs exist; the bill reading has nothing to read', n; END IF;
    SELECT COUNT(*) INTO n FROM material_process_input_group;
    IF n < 1 THEN RAISE EXCEPTION 'V438: no grouped process inputs exist, so the "any one of these" half reads nothing'; END IF;
END $$;
