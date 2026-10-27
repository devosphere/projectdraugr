-- #37 — the card for the clothing reading, and the model it finally makes askable.
--
-- The warmth model is finished and careful, and every bit of it was invisible:
--
--   insulation_value   per garment, summed, plus whatever THIS one has been lined with (V391's lining_bonus,
--                      a property of the object: these boots are packed with dry grass and those are not)
--   capped at 80%      so no stack of rags repeals the cold — clothing buys time against it
--   water_resistance   summed and capped at 60%: no outfit stays dry in a storm, it slows the soaking
--   wet clothing       insulates 1.6x WORSE, which is how people actually die of cold in weather that is not
--                      itself lethal
--   the wind           cut to a quarter under cover, to a half behind a windbreak
--
-- 153 items carry insulation and 131 shed water, and a Chronicle could not ask about any of it. Measured on a
-- booted world:
--
--   will these clothes keep me warm   ->  nothing at all
--   is my cloak keeping the rain off  ->  nothing at all
--   am I dressed for this weather     ->  EQUIP: "You have nothing unequipped that can be worn or wielded"
--
-- The last is a question answered by an act that found nothing to do, which is this ticket's standing shape.
--
-- JUDGE_CLOTHING reads those same numbers rather than describing them again, and says the two CAPS out loud,
-- because they are the part a player cannot otherwise learn: that more layers stop helping, that nothing stays
-- dry in a storm, and that the cloak they are proud of is worth a fraction of itself while it is soaked.
--
-- EVERY NEW INTENT NEEDS A CARD. ActivityImpactCardIntegrationTest fails the build for an intent without one
-- and skips locally, so a missing card is an hour and a half of CI for nothing — JUDGE_FOOD cost exactly that.
-- Priced with the other readings: it marks nothing, costs a little attention, and takes the two minutes that
-- looking yourself over takes, which is less than going over a bill or reading the sky.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_CLOTHING', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 2,
        'Perception alters nothing it touches. Looking over what you have on, what it keeps out of the cold '
        || 'and the wet, and whether that suits the sky, costs a little attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_CLOTHING')
    THEN RAISE EXCEPTION 'V440: JUDGE_CLOTHING has no activity_impact row'; END IF;

    -- 2. A reading marks nothing, and 3. still costs the body something — the pair
    --    nothingMarksTheLandWhileCostingTheBodyNothing is asserted from both sides.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_CLOTHING' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V440: a reading must not mark the ground'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_CLOTHING';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V440: the reading costs nothing and takes no time'; END IF;

    -- 4. Looking yourself over is quicker than reading the sky or reckoning a bill, and must stay so: a reading
    --    priced above its siblings would make asking about your coat the most expensive question in the game.
    IF d > (SELECT MIN(duration_minutes) FROM activity_impact
             WHERE intent_key IN ('JUDGE_WATER','JUDGE_LIGHT','JUDGE_FOOD','JUDGE_MAKING','WHICH_WAY'))
    THEN RAISE EXCEPTION 'V440: the clothing reading takes longer than the quickest of the other judgements'; END IF;

    -- 5. AND THERE IS SOMETHING TO READ. The whole reading rests on two columns and one per-object property; if
    --    any of them emptied, it would tell every Chronicle their clothes keep out none of the cold while the
    --    body was being charged as though they did.
    SELECT COUNT(*) INTO n FROM item_definition WHERE insulation_value > 0;
    IF n < 20 THEN RAISE EXCEPTION 'V440: only % items carry insulation; the warmth half reads nothing', n; END IF;
    SELECT COUNT(*) INTO n FROM item_definition WHERE water_resistance > 0;
    IF n < 20 THEN RAISE EXCEPTION 'V440: only % items shed water; the rain half reads nothing', n; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='item_instance' AND column_name='lining_bonus')
    THEN RAISE EXCEPTION 'V440: lining_bonus is gone, so a lined garment reads the same as an unlined one'; END IF;
END $$;
