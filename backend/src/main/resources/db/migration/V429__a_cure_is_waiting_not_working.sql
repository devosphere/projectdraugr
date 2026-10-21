-- #77/#108 — two stages that were built out of nothing, and the guard so it cannot happen again.
--
-- AssemblyService.advance() treats a curing stage as PURE WAITING. The branch is:
--
--     if (cure > 0) { ...check elapsed time, record the stage, return... }
--     else          { ...check tool_class, requires_fire, and consume assembly_stage_requirement... }
--
-- So a stage with cure_minutes > 0 never checks a tool, never needs a fire, and NEVER CONSUMES ITS MATERIALS.
-- That is a sound design — a cure is time passing, not work — and the whole catalogue follows it. Every
-- pre-existing cure stage is even NAMED for waiting: "Let the backing cure", "Let the lining cure", "Let the
-- daub dry", "Let it fill", "Bank the earth".
--
-- TWO STAGES BROKE IT, AND BOTH ARE MINE:
--
--   lime_burning_pit / limepit_line   cure 90, STRIKING, 8 field stone   named "Line it with stone"
--   yak_shelter      / yakshed_roof   cure 60, CUTTING,  4 thatch bundle named "Roof it low"
--
-- Both are named for WORK, which is the tell. The consequence: a lime burning pit was raised from the 2 stones
-- of its first stage instead of 10, and a yak shelter's roof was thatched out of nothing — matter from nothing,
-- which is precisely what the Auditor's conservation rule exists to forbid, slipped in through a branch that
-- never consults it.
--
-- THE FIX IS TO SEPARATE THE WORK FROM THE WAITING, which is what the catalogue was already doing:
--
--   * limepit_line keeps its stone and its hammer and stops curing, so both are actually required. A new
--     limepit_set stage does the waiting, declaring nothing — a lime pit lining genuinely has to set before the
--     first burn, so the wait is real and is now the last stage, which is also when the pit comes into being.
--   * yakshed_roof keeps its thatch and its blade and simply stops curing. THATCH DOES NOT CURE. The 60 minutes
--     were decoration I had added for atmosphere, and the honest version of "let it settle" is no stage at all.

-- Work, not waiting: the stone and the hammer are now required, because the stage no longer cures.
UPDATE assembly_stage SET cure_minutes = 0 WHERE stage_key = 'limepit_line' AND cure_minutes > 0;

-- And the waiting becomes its own stage, declaring nothing, on the catalogue's own naming convention.
INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
 ('limepit_set', 'lime_burning_pit', 3, 'Let the lining set', 'limepit_line', 90, NULL, false,
  'You leave the lining to set before you put the first fire in it, because stone laid up wet and then heated hard will spall and take the wall with it.')
ON CONFLICT (stage_key) DO NOTHING;

-- Thatch does not cure. The thatch and the blade are now required instead.
UPDATE assembly_stage SET cure_minutes = 0 WHERE stage_key = 'yakshed_roof' AND cure_minutes > 0;

DO $$
DECLARE bad TEXT;
BEGIN
    -- 1. THE STANDING RULE, as a property of the data: no stage may declare a tool, a fire or materials
    --    alongside a cure, because advance() ignores all three on the curing branch. A stage that does is a
    --    stage built from nothing, and nothing at runtime complains.
    SELECT string_agg(s.assembly_key || '/' || s.stage_key, ', ') INTO bad
      FROM assembly_stage s
     WHERE s.cure_minutes > 0
       AND (s.tool_class IS NOT NULL OR s.requires_fire
            OR EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key));
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V429: % declares a tool, a fire or materials on a CURING stage — advance() ignores all three there, so '
        'the stage would be built out of nothing. Split the work from the waiting.', bad; END IF;

    -- 2. The two fixed stages still demand what they are made of.
    SELECT string_agg(v.k, ', ') INTO bad FROM (VALUES ('limepit_line'),('yakshed_roof')) v(k)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_stage s
                        WHERE s.stage_key = v.k AND s.cure_minutes = 0 AND s.tool_class IS NOT NULL
                          AND EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V429: % should now be work, with a tool and materials that count', bad; END IF;

    -- 3. The lime pit's wait is real, is last, and declares nothing.
    IF NOT EXISTS (SELECT 1 FROM assembly_stage WHERE stage_key='limepit_set' AND cure_minutes = 90
                     AND tool_class IS NULL AND NOT requires_fire
                     AND prerequisite_stage_key = 'limepit_line')
    THEN RAISE EXCEPTION 'V429: the lime pit must have a bare waiting stage after the lining'; END IF;

    IF EXISTS (SELECT 1 FROM assembly_stage_requirement WHERE stage_key='limepit_set')
    THEN RAISE EXCEPTION 'V429: limepit_set is a wait, so it must declare no materials'; END IF;

    -- 4. Every stage order is still unique within its assembly, and the prerequisite chain still reaches every
    --    stage — inserting a stage is the easiest way to orphan one.
    SELECT string_agg(assembly_key || ' order ' || stage_order, ', ') INTO bad
      FROM (SELECT assembly_key, stage_order FROM assembly_stage
             GROUP BY assembly_key, stage_order HAVING count(*) > 1) d;
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V429: two stages share an order: %', bad; END IF;

    SELECT string_agg(s.assembly_key || '/' || s.stage_key, ', ') INTO bad
      FROM assembly_stage s
     WHERE s.prerequisite_stage_key IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM assembly_stage p WHERE p.stage_key = s.prerequisite_stage_key
                                                        AND p.assembly_key = s.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V429: % waits on a stage that is not in its own assembly', bad; END IF;
END $$;
