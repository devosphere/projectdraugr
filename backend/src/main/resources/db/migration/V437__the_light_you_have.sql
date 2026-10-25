-- #37 — the card for the light reading, and the reason it exists.
--
-- JUDGE_LIGHT answers a question four finished rules could already have answered and nobody could ask:
--
--   tooDarkForFineWork    night is dark, and inside the rock is dark at noon
--   consumePortableLight  a burning brand, then a resin torch, a rushlight, a tallow candle, and last a lamp
--                         burning fish oil or rendered tallow — spent in that order
--   guttering             weather and a wind over 30 kph take a BARE flame, outside the rock
--   stone_lantern_cover   "a stone cover to hood a flame and control its light" — the one thing that stops it
--
-- Measured on a booted world before building it: `is there enough light to work`, `it is too dark to see`,
-- `what have I got to burn` and `put out the torch` reached nothing or spent an act on nothing at all, while a
-- Chronicle could learn the whole of the above only by failing at close work in the dark and being told why
-- one piece of it had gone wrong.
--
-- EVERY NEW INTENT NEEDS A CARD. activity_impact holds one for all 160 intents, and
-- ActivityImpactCardIntegrationTest fails the build for an intent without one — that test skips locally (no
-- Testcontainers here), so a missing card is a wasted CI round of an hour and a half. JUDGE_FOOD cost exactly
-- that once.
--
-- The numbers are the other readings' numbers, deliberately: this is perception, which alters nothing it
-- touches, costs a little attention, and takes about as long as looking at water. JUDGE_WATER is 3 minutes,
-- JUDGE_CROSSING and JUDGE_FOOD are 4, WHICH_WAY is 5. Looking over what you are carrying and what the sky is
-- doing to it is a JUDGE_WATER-sized act, so it takes its 3.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_LIGHT', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 3,
        'Perception alters nothing it touches. Looking over what you carry that will burn, whether the place '
        || 'and the hour are dark, and whether the weather would have a bare flame out, takes a little '
        || 'attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists, which is the whole point of the file.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_LIGHT')
    THEN RAISE EXCEPTION 'V437: JUDGE_LIGHT has no activity_impact row'; END IF;

    -- 2. It marks nothing, as a reading must: a footprint on a question would have the act of asking about the
    --    light wear the ground it is asked on.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_LIGHT' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V437: a reading must not mark the ground'; END IF;

    -- 3. And it still costs the body something, because nothingMarksTheLandWhileCostingTheBodyNothing is
    --    asserted from the other side: an act that marks nothing AND tires nobody reads exactly like an act
    --    somebody forgot to fill in.
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_LIGHT';
    IF e < 1 THEN RAISE EXCEPTION 'V437: the reading costs no attention at all'; END IF;
    IF d < 1 THEN RAISE EXCEPTION 'V437: the reading takes no time at all'; END IF;

    -- 4. It is priced like its siblings rather than invented: no reading may cost more than the longest of them.
    IF d > (SELECT MAX(duration_minutes) FROM activity_impact
             WHERE intent_key IN ('JUDGE_WATER','JUDGE_CROSSING','JUDGE_FOOD','WHICH_WAY'))
    THEN RAISE EXCEPTION 'V437: the light reading takes longer than every other judgement'; END IF;

    -- 5. And the lights it recites are all real catalogue items, since a reading that named one the player can
    --    never hold would be the token-listing this project refuses.
    SELECT COUNT(*) INTO n FROM (VALUES ('firebrand'),('resin_torch'),('rush_light'),('tallow_candle'),
                                        ('oil_lamp'),('fish_oil'),('rendered_tallow'),('stone_lantern_cover')) AS w(k)
     WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = w.k);
    IF n <> 0 THEN RAISE EXCEPTION 'V437: % of the lights the reading names do not exist', n; END IF;
END $$;
