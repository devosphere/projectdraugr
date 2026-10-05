-- #37 — "What time is it?" / "What am I carrying?"
--
-- Act fifteen swept the two domains the earlier acts never did: what a person says about their own gear, and
-- what they say about the world over their head. 22 OF 35 PHRASINGS REACHED NOTHING, and in both cases the
-- answer was sitting in a column the simulation maintains.
--
-- THE SKY. "what is the weather doing" and "what season is it" answered well. Nothing else did:
--
--   what time is it           how long until dark      is it getting dark
--   how high is the sun       what month is it         is it near winter
--   what is the wind doing    is the wind getting up   is it going to rain
--   is there frost            will it freeze tonight
--
-- world_weather.wind_speed_kph is the sharpest of them: a column the weather simulation keeps, carried all the
-- way into BiomeClimate.Local as a wind FELT at this elevation and aspect, read by the body when it decides how
-- fast you lose heat — and no sentence in the game could reach it. The hour and the month were the same story
-- in simulation_clock. READ_THE_SKY says all of it, on the same 06:00-20:00 daylight the fine-work check uses,
-- so the answer about the light can never disagree with whether close work is possible.
--
-- YOUR OWN GEAR. item_instance has carried condition_state (SOUND / WORN / BROKEN), use_count and
-- quality_grade since the table existed, and the LOAD is computed on every single action — mass, bulk, the
-- single-lift limit, carry aids, a draft team's haul. None of it could be asked for:
--
--   what am I carrying        check my tools           what tools do I have
--   is anything broken        how worn is the knife     am I carrying too much
--
-- And "how heavy is my pack" DID reach MEASURE, which answered "You have nothing by that name in hand to
-- weigh" — the load computed on every action, answered as though a pack were an object to put on scales.
-- TAKE_STOCK_OF_GEAR names what is broken first, then what is worn, then what is sound, and says how near the
-- limit the load sits.
--
-- Both read-only. Neither adds a capability; both say what the world already knew.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('READ_THE_SKY', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking up at the weather, reckoning the hour by the light and judging '
  'whether the wind is getting up all take the world exactly as it is and put nothing back.',
  1, 0, 'ATTENTION', 5),
 ('TAKE_STOCK_OF_GEAR', NULL, 0, FALSE, FALSE,
  'Going through what you carry and looking over its condition moves nothing and wears nothing. The load is '
  'carried either way, which the carrying itself already accounts for.',
  2, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key IN ('READ_THE_SKY','TAKE_STOCK_OF_GEAR');
    IF n <> 2 THEN RAISE EXCEPTION 'V400: both readings need exactly one impact card each, found %', n; END IF;

    -- Looking at the sky and looking in your pack each mark nothing. A footprint on either would be a claim
    -- that noticing the weather fouls the ground, and it should be argued for.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key IN ('READ_THE_SKY','TAKE_STOCK_OF_GEAR')
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V400: neither reading may leave a mark on the world'; END IF;

    -- What the sky reading reads must exist, or it is a sentence about nothing. wind_speed_kph is the column
    -- this slice exists for: it was maintained and unaskable.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='world_weather' AND column_name='wind_speed_kph') THEN
        RAISE EXCEPTION 'V400: wind_speed_kph is the wind the sky reading names';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='world_weather' AND column_name='ambient_temperature_c') THEN
        RAISE EXCEPTION 'V400: ambient_temperature_c is how the frost is known';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM simulation_clock WHERE id=1) THEN
        RAISE EXCEPTION 'V400: the clock is where the hour and the month come from';
    END IF;

    -- And what the gear reading reads. condition_state is the whole point of naming the broken thing first.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='item_instance' AND column_name='condition_state') THEN
        RAISE EXCEPTION 'V400: condition_state is what tells a broken tool from a sound one';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle_carry_capacity' AND column_name='sustained_mass_grams') THEN
        RAISE EXCEPTION 'V400: the carry capacity is what "too much" is measured against';
    END IF;

    -- The weather must actually carry a wind, or the sharpest line in the reading describes nothing. A world
    -- with a row of zero wind is fine; a world with no row at all means the simulation has not run.
    IF NOT EXISTS (SELECT 1 FROM world_weather) THEN
        RAISE EXCEPTION 'V400: no weather row exists, so there is no wind to report';
    END IF;
END $$;
