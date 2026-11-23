-- V481: A FULL STOMACH IS NOT A FED BODY (GitHub #959, under EPIC #956).
--
-- One number has been doing four jobs. `hours_without_food` rises with time and effort, falls when you eat,
-- drives the Hunger label, and decides starvation death. Measured against the live code, that produces the
-- defect #959 reports, and worse than it says:
--
--     eat()           subtracts  8 hours
--     eatCookedMeal() subtracts 16 hours
--     "Starving"      begins at 336 hours
--
-- So a Chronicle who has gone a fortnight without food must eat FORTY-TWO ordinary meals, or twenty-one
-- cooked ones, to stop being Starving -- and each of those meals relieves exactly as much whether it is a
-- single blackberry or a whole roast fish, because the counter knows nothing about portions. A body that
-- has eaten its fill still reads Starving, and a body that has eaten forty-two berries reads Satisfied.
--
-- FOUR THINGS, BECAUSE THERE ARE FOUR THINGS:
--
--   stomach_fullness_ml   what is in there NOW. Bounded by a stomach, emptied by digestion. This is what
--                         Hunger means, and the only thing a meal relieves directly.
--   energy_reserves       the store the body actually runs on, filled by digestion over hours and spent by
--                         living, working, shivering and being ill. This is what Energy draws on.
--   nutrition             longer-term nutritional condition. A month of nothing but lean rabbit is not the
--                         same as a month of fish, fat and greens, and neither is a full stomach of either.
--   starvation_severity   the damage done once the reserves are gone, which a meal does not undo. #959:
--                         "A small snack must not erase accumulated starvation."
--
-- `hours_without_food` IS KEPT, and keeps its name, as #959 asks: "Keep actual time since eating as history
-- rather than an accumulated debt meals must erase." Nothing stops writing it, so every existing reader --
-- including the death rule and howWellKept -- behaves exactly as it did. #960 is where the death rule moves
-- onto starvation_severity; this migration changes no outcome on its own.

ALTER TABLE chronicle_physiology
    ADD COLUMN stomach_fullness_ml INTEGER  NOT NULL DEFAULT 0,
    ADD COLUMN energy_reserves     SMALLINT NOT NULL DEFAULT 70,
    ADD COLUMN nutrition           SMALLINT NOT NULL DEFAULT 70,
    ADD COLUMN starvation_severity SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE chronicle_physiology
    ADD CONSTRAINT fullness_fits_a_stomach  CHECK (stomach_fullness_ml BETWEEN 0 AND 2400),
    ADD CONSTRAINT reserves_are_a_fraction  CHECK (energy_reserves     BETWEEN 0 AND 100),
    ADD CONSTRAINT nutrition_is_a_fraction  CHECK (nutrition           BETWEEN 0 AND 100),
    ADD CONSTRAINT starvation_is_a_fraction CHECK (starvation_severity BETWEEN 0 AND 100);

-- ---------------------------------------------------------------------------------------------------
-- EXISTING CHRONICLES, which #959 asks for "a documented compatibility approach" for.
--
-- The approach is: believe the one number we have. `hours_without_food` is the only record of how fed a
-- living Chronicle is, so the new four are derived FROM it rather than defaulted — a Chronicle who was
-- starving when this migration ran is still starving afterwards, and one who had just eaten is not
-- suddenly destitute. Nothing is invented and nothing is forgiven.
--
--   under 24h   a fed body: reserves and nutrition high, no starvation
--   to 168h     the edge of hunger: reserves falling, nutrition slipping
--   to 336h     real deprivation: reserves low, starvation begun
--   beyond      the state the old counter called Starving, carried over honestly
--
-- The stomach starts empty for everyone, which is the one thing that cannot be wrong: whatever they last
-- ate, the simulation has no record of it, and an empty stomach is relieved by the next meal.
-- ---------------------------------------------------------------------------------------------------
UPDATE chronicle_physiology SET
    stomach_fullness_ml = 0,
    energy_reserves = CASE
        WHEN hours_without_food <  24 THEN 85
        WHEN hours_without_food <  72 THEN 70
        WHEN hours_without_food < 168 THEN 45
        WHEN hours_without_food < 336 THEN 20
        ELSE 4 END,
    nutrition = CASE
        WHEN hours_without_food <  24 THEN 80
        WHEN hours_without_food <  72 THEN 70
        WHEN hours_without_food < 168 THEN 55
        WHEN hours_without_food < 336 THEN 35
        ELSE 18 END,
    starvation_severity = CASE
        WHEN hours_without_food < 168 THEN 0
        WHEN hours_without_food < 336 THEN 12
        WHEN hours_without_food < 504 THEN 35
        WHEN hours_without_food < 672 THEN 60
        ELSE 78 END;

-- ---------------------------------------------------------------------------------------------------
-- AND THE FOUR JOIN #953's LEDGER, so every movement in them is recorded with its cause.
--
-- slips_per_idle_week is 0 for all four: these are not faculties that fade from lack of practice, they are
-- a body's running state with its own arithmetic. letTheUnpractisedSlip only touches metrics with a
-- positive slip rate, so it leaves these alone, and #960 records their rise and fall itself with the causes
-- it actually knows -- NOURISHMENT when a meal digests, ILLNESS when a sickness burns through the store.
--
-- The felt steps are set where a person would notice: reserves and nutrition move slowly and by small
-- amounts, so 8 points is a real change; starvation at 6 is sooner, because it should be.
-- ---------------------------------------------------------------------------------------------------
-- FIRST, A CONSTRAINT OF MINE THAT ENCODED AN ASSUMPTION THAT HAS STOPPED BEING TRUE. V474 wrote
--     CHECK (felt_step > 0 AND felt_step <= 1)
-- because every metric it catalogued was a 0..1 familiarity, so a step of 0.05 was the whole scale. These
-- three run 0..100, and a felt step of 8 points is the same kind of statement -- "this much movement is
-- worth telling someone about" -- in the metric's own units. The constraint was right about the intent and
-- wrong about the range; it reads in the metric's units now, which is what felt_step always meant.
ALTER TABLE metric_subject DROP CONSTRAINT metric_felt_step_is_a_real_step;
ALTER TABLE metric_subject ADD CONSTRAINT metric_felt_step_is_a_real_step
    CHECK (felt_step > 0 AND felt_step <= 100);

INSERT INTO metric_subject (metric_key, display_name, source_table, source_column, hidden, felt_step, slips_per_idle_week, notes) VALUES
 ('ENERGY_RESERVES', 'what your body has left to run on', 'chronicle_physiology', 'energy_reserves', TRUE, 8, 0,
  'The store the body actually spends: filled by digestion over hours, drawn down by living, working, shivering and being ill. Energy is what you can do right now; this is what there is to do it with.'),
 ('NUTRITION', 'how well you have been fed, over weeks', 'chronicle_physiology', 'nutrition', TRUE, 8, 0,
  'Longer-term nutritional condition. A month of nothing but lean meat is not a month of fish and fat and greens, and a full stomach of either says nothing about which it was.'),
 ('STARVATION_SEVERITY', 'what going without has done to you', 'chronicle_physiology', 'starvation_severity', TRUE, 6, 0,
  'The damage left once the reserves are gone, which a meal does not undo. It is the thing a snack cannot erase and the thing that eventually kills.')
ON CONFLICT (metric_key) DO NOTHING;
