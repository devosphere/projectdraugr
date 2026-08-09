-- Regression: first-era staged shelters (V83, M1 #61) — the twelve settlement-core structures are canon.
--
-- Each is a staged assembly (V58): an ordered list of stages, every stage drawing on stock that already has
-- an acquisition path, promoted to VERIFIED only if the review gate finds no blocking fault. This pins the
-- data contract the AssemblyService match()/advance() path depends on — a broken definition here would silently
-- drop a structure out of reach at the action boundary rather than fail loudly. Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    shelters text[] := ARRAY['wattle_and_daub_hut','earth_sheltered_hut','raised_sleeping_platform','reed_screen',
        'clay_lined_hearth','wood_store','rainwater_catchment','split_rail_fence','wattle_fence','simple_gate',
        'footbridge','fishing_landing'];
    k text;
    n int;
    bad text;
BEGIN
    -- 1. All twelve exist and are VERIFIED (matchable from the action boundary).
    SELECT count(*) INTO n FROM assembly_definition WHERE assembly_key = ANY(shelters) AND review_state = 'VERIFIED';
    IF n <> 12 THEN RAISE EXCEPTION 'REGRESSION: expected 12 VERIFIED shelters, got %', n; END IF;

    -- 2. No open blocking review finding stands against any of them.
    SELECT string_agg(DISTINCT assembly_key, ', ') INTO bad
      FROM assembly_review
     WHERE assembly_key = ANY(shelters) AND severity = 'BLOCKING' AND resolved_at IS NULL;
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'REGRESSION: blocking review findings on %', bad; END IF;

    -- 3. Every stage requirement is obtainable — no stage can demand what nobody can make or gather.
    SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
      FROM assembly_stage_requirement r
      JOIN assembly_stage s ON s.stage_key = r.stage_key
     WHERE s.assembly_key = ANY(shelters)
       AND NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key = r.item_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'REGRESSION: unobtainable stage requirement(s): %', bad; END IF;

    -- 4. Every assembly has a clean stage chain: stage_order starts at 1, the first stage has no prerequisite,
    --    and each later stage depends on a strictly earlier stage of the same assembly (no cycles, no leaks).
    FOREACH k IN ARRAY shelters LOOP
        IF NOT EXISTS (SELECT 1 FROM assembly_stage WHERE assembly_key = k AND stage_order = 1 AND prerequisite_stage_key IS NULL) THEN
            RAISE EXCEPTION 'REGRESSION: % has no order-1 stage with a null prerequisite', k;
        END IF;
        SELECT count(*) INTO n
          FROM assembly_stage s JOIN assembly_stage p ON p.stage_key = s.prerequisite_stage_key
         WHERE s.assembly_key = k AND (p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order);
        IF n > 0 THEN RAISE EXCEPTION 'REGRESSION: % has a forward or cross-assembly prerequisite', k; END IF;
    END LOOP;

    -- 5. The two enclosing, roofed forms carry the construction_kind the exposure model keys shelter benefit on.
    SELECT count(*) INTO n FROM assembly_definition
     WHERE assembly_key IN ('wattle_and_daub_hut','earth_sheltered_hut')
       AND construction_kind IN ('WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT');
    IF n <> 2 THEN RAISE EXCEPTION 'REGRESSION: enclosing huts missing their shelter construction_kind (got %)', n; END IF;

    RAISE NOTICE 'PASS: 12 first-era shelters VERIFIED, obtainable, well-ordered, with enclosing huts wired for shelter (V83 #61)';
END $$;

ROLLBACK;
