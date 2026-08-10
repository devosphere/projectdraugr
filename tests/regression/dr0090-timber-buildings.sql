-- Regression: timber buildings close the carpentry chain (V90, M1 #75).
--
-- The carpentry/masonry chain produced 25 building components that nothing consumed — every one dead-ended in
-- inventory, breaking the real-world line (you could rive a shake but never roof anything). This pins that the
-- log cabin and timber barn are VERIFIED staged assemblies and that between them they consume EVERY one of those
-- 25 components, so none is an orphan any longer. Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    components text[] := ARRAY['foundation_stone','stone_course','notched_log','moss_chinking','joined_frame',
        'scarfed_beam','sill_plate','ridge_beam','rafter_pole','roof_shake','bark_roofing','floorboard',
        'wooden_wedge','seasoned_timber','door_blank','shutter_panel','charred_post','packed_floor',
        'pitched_timber','timber_log','dovetailed_corner','board_panel','lapped_plank','daubed_panel','mortared_course'];
    k text; n int;
BEGIN
    -- 1. Both buildings are VERIFIED (reachable from the action boundary).
    SELECT count(*) INTO n FROM assembly_definition WHERE assembly_key IN ('log_cabin','timber_barn') AND review_state='VERIFIED';
    IF n <> 2 THEN RAISE EXCEPTION 'REGRESSION: expected 2 VERIFIED timber buildings, got %', n; END IF;

    -- 2. No open blocking review finding against either.
    IF EXISTS (SELECT 1 FROM assembly_review WHERE assembly_key IN ('log_cabin','timber_barn') AND severity='BLOCKING' AND resolved_at IS NULL) THEN
        RAISE EXCEPTION 'REGRESSION: a timber building has an open blocking finding';
    END IF;

    -- 3. Every one of the 25 finished components is now consumed by one of these assemblies (no dead ends).
    FOREACH k IN ARRAY components LOOP
        IF NOT EXISTS (SELECT 1 FROM assembly_stage_requirement ar JOIN assembly_stage s ON s.stage_key=ar.stage_key
                       WHERE ar.item_key=k AND s.assembly_key IN ('log_cabin','timber_barn')) THEN
            RAISE EXCEPTION 'REGRESSION: component % is still not consumed by a timber building', k;
        END IF;
        -- and each such component must itself be obtainable (the chain that makes it still closes).
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: component % is required by a building but is not obtainable', k;
        END IF;
    END LOOP;

    -- 4. The log cabin carries the LOG_CABIN construction_kind the exposure model keys shelter on.
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key='log_cabin' AND construction_kind='LOG_CABIN') THEN
        RAISE EXCEPTION 'REGRESSION: log_cabin missing its LOG_CABIN construction_kind';
    END IF;

    RAISE NOTICE 'PASS: log cabin + timber barn VERIFIED and consume all 25 carpentry/masonry components — the chain closes (V90 #75)';
END $$;

ROLLBACK;
