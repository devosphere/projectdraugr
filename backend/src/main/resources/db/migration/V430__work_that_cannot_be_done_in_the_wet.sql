-- #77 — weather limits during construction, which did not exist.
--
-- #77's acceptance criteria ask that a build declare "site constraints, footprint, components, stages, tools,
-- labour, WEATHER, integrity/decay, salvage". Auditing AssemblyService.advance() for the lifecycle work showed
-- it reads a stage's tool_class, its requires_fire and its materials — AND NOTHING CONSULTS THE SKY. So a roof
-- could be thatched in a storm and daub applied in driving rain, work that would be ruined before it set.
--
-- This adds the limit as data and the reader in the same change. That order matters: V429 was a stage declaring
-- materials on a branch that never consumed them, and the lesson is that a property the data states and the
-- code ignores is worse than one that was never declared. There is a standing guard in
-- ConstructionRegistryCompleteIntegrationTest holding every limit to a value the reader actually honours.
--
-- TWO LIMITS, AND A DELIBERATE THIRD CATEGORY OF NONE:
--
--   DRY  — refused in RAIN, STORM or SNOW. Work that must go on dry or be spoiled before it sets: daub applied
--          as a wet plaster, and thatch, which beds wet and then rots.
--   CALM — refused in STORM only. Raising a frame or rafters: long timbers overhead in a gale is how people are
--          hurt, and the wind takes whatever you lift.
--   none — and this is the part worth reading, because blanket-applying DRY to every stage that touches clay or
--          thatch would have been wrong:
--            * CLAY LINING A WATER FEATURE IS NOT WET-SENSITIVE WORK. Puddling clay into a settling basin, a
--              sand filter bed, a waterfowl pool, a rainwater catchment or a spring head is work whose whole
--              purpose is to hold water. Rain does not spoil it; if anything it helps. Five stages, left open.
--            * A SNOW SHELTER NEEDS SNOW. Digging back a drift in clear weather is the impossible one, and
--              refusing it in snow would be exactly backwards.
--            * Bark and branch roofing, below-ground work and stacked stores are left open rather than guessed
--              at. A limit nobody can justify is a limit that will be argued with.
--
-- The hearth lining is also left open: it already has its own "Let the lining cure" stage, which is where
-- drying actually matters, and a hearth is built under cover.

ALTER TABLE assembly_stage ADD COLUMN IF NOT EXISTS weather_limit VARCHAR(10);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'assembly_stage_weather_limit_known') THEN
        ALTER TABLE assembly_stage ADD CONSTRAINT assembly_stage_weather_limit_known
            CHECK (weather_limit IS NULL OR weather_limit IN ('DRY','CALM'));
    END IF;
END $$;

-- A CURING STAGE MAY NOT CARRY ONE EITHER, for the same reason it may not carry a tool or materials: advance()
-- returns from the cure branch before any of it is read. V429 established the rule; this keeps the new column
-- inside it from the start rather than leaving the next person to discover it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'assembly_stage_cure_declares_no_work') THEN
        ALTER TABLE assembly_stage ADD CONSTRAINT assembly_stage_cure_declares_no_work
            CHECK (cure_minutes = 0 OR (tool_class IS NULL AND NOT requires_fire AND weather_limit IS NULL));
    END IF;
END $$;

-- ------------------------------------------------------------------------------- DRY: daub and thatch
UPDATE assembly_stage SET weather_limit = 'DRY' WHERE weather_limit IS NULL AND cure_minutes = 0 AND stage_key IN (
    -- daub applied as a wet plaster
    'hut_apply_daub', 'daub_wall_build', 'tack_room_walls',
    -- thatch laid, combed, or bent and thatched
    'cartshed_roof', 'cattle_byre_walls', 'earth_turf_roof', 'gatehouse_roof', 'horse_stable_roof',
    'lean_thatch', 'ox_shed_walls', 'shade_shelter_top', 'thatch_roof_build', 'hut_thatch_roof',
    'store_roof', 'yakshed_roof', 'brush_dome_build', 'reed_hut_build'
);

-- ------------------------------------------------------------------------- CALM: frames and rafters raised
UPDATE assembly_stage SET weather_limit = 'CALM' WHERE weather_limit IS NULL AND cure_minutes = 0 AND stage_key IN (
    'cabin_roof', 'roofing_frame_build', 'earth_frame_roof', 'lean_rafters'
);

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Both limits are actually used. A limit the catalogue never sets is a reader nothing exercises, and
    --    would pass every test while meaning nothing.
    SELECT COUNT(*) INTO n FROM assembly_stage WHERE weather_limit = 'DRY';
    IF n < 15 THEN RAISE EXCEPTION 'V430: only % stages are DRY; the thatch and daub work was not matched', n; END IF;
    SELECT COUNT(*) INTO n FROM assembly_stage WHERE weather_limit = 'CALM';
    IF n < 4 THEN RAISE EXCEPTION 'V430: only % stages are CALM; the roof frames were not matched', n; END IF;

    -- 2. THE FIVE WATER FEATURES STAY OPEN, asserted so a later cycle cannot blanket-apply DRY to anything
    --    touching clay without first reading why these are different. Lining a basin to hold water is not work
    --    the rain can spoil.
    SELECT string_agg(stage_key, ', ') INTO bad FROM assembly_stage
     WHERE weather_limit IS NOT NULL
       AND stage_key IN ('catch_line_basin','settling_basin_dig','sand_filter_bed_dig','waterfowl_pool_line',
                         'spring_head_wall','spring_head_cover','hearth_line_clay');
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V430: % lines a water feature — rain does not spoil work meant to hold water. Say why first.', bad; END IF;

    -- 3. A SNOW SHELTER NEEDS SNOW. Refusing it in snow would be exactly backwards.
    IF EXISTS (SELECT 1 FROM assembly_stage WHERE stage_key='snow_shelter_build' AND weather_limit IS NOT NULL)
    THEN RAISE EXCEPTION 'V430: a snow shelter cannot be refused for snow'; END IF;

    -- 4. Nothing curing carries a limit (the CHECK above enforces it going forward; this proves it holds now).
    SELECT string_agg(stage_key, ', ') INTO bad FROM assembly_stage
     WHERE cure_minutes > 0 AND weather_limit IS NOT NULL;
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V430: % carries a weather limit on a curing stage, which advance() never reads', bad; END IF;

    -- 5. Every limit is a value the reader knows. AssemblyService honours exactly DRY and CALM, so a third
    --    spelling would be declared and ignored — the V429 defect in a new column.
    SELECT string_agg(DISTINCT weather_limit, ', ') INTO bad FROM assembly_stage
     WHERE weather_limit IS NOT NULL AND weather_limit NOT IN ('DRY','CALM');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V430: % is not a limit the reader honours', bad; END IF;

    -- 6. Every stage carrying a limit still demands its materials, so the limit is an extra gate rather than a
    --    replacement for one.
    SELECT string_agg(s.stage_key, ', ') INTO bad FROM assembly_stage s
     WHERE s.weather_limit IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V430: % has a weather limit but needs no materials', bad; END IF;
END $$;
