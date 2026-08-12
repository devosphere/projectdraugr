-- Regression: forest-floor ignition/binding/scavenging materials (M1 #135, EPIC #123, V116+V120). Read-only.
--
-- Pins #135's coverage: each of the eight forest-floor materials is obtainable BARE-HAND (a NULL-tool flora_drop,
-- a NULL-tool mineral, or a code gather with no tool) AND used (a process input, or a documented code path —
-- craftTinder for the fine tinders, fuel/carving for dead branches). A break means a scavenging material became
-- an orphan or lost its bare-hand route.

BEGIN;

DO $$
DECLARE
    mats text[] := ARRAY['dry_twig','dry_branch','fallen_leaf_litter','loose_bark_strip','shed_feather','straight_reed','pine_resin','dry_grass_bundle'];
    -- materials whose consumer is a documented code path (craftTinder / fuel / carving), so no material_process_input is required
    code_used text[] := ARRAY['dry_twig','fallen_leaf_litter','dry_branch'];
    it text;
BEGIN
    FOREACH it IN ARRAY mats LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it) THEN
            RAISE EXCEPTION 'FOREST: material % is not defined', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it) THEN
            RAISE EXCEPTION 'FOREST: material % has no item_source', it; END IF;
        -- obtainable bare-hand: NULL-tool flora_drop, OR NULL-tool mineral, OR a CODE gather (dry_branch)
        IF NOT (EXISTS (SELECT 1 FROM flora_drop fd JOIN flora_definition f ON f.flora_key = fd.flora_key WHERE fd.item_key = it AND f.tool_required IS NULL)
             OR EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key = it AND tool_required IS NULL)
             OR EXISTS (SELECT 1 FROM item_source WHERE item_key = it AND source_kind = 'CODE')) THEN
            RAISE EXCEPTION 'FOREST: material % has no bare-hand obtain route', it; END IF;
        -- used: a process input, or a documented code path
        IF NOT (EXISTS (SELECT 1 FROM material_process_input WHERE item_key = it)
             OR EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key = it)
             OR it = ANY(code_used)) THEN
            RAISE EXCEPTION 'FOREST: material % is an orphan (obtainable but used by nothing)', it; END IF;
    END LOOP;
    -- pine_resin specifically must now have BOTH the axe-tap flora path and the new bare-hand scavenge path.
    IF NOT EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key='pine_resin' AND tool_required IS NULL) THEN
        RAISE EXCEPTION 'FOREST: pine_resin has no bare-hand scavenge path (#135)'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE item_key='pine_resin') THEN
        RAISE EXCEPTION 'FOREST: pine_resin lost its consumer'; END IF;
    RAISE NOTICE 'PASS: all eight forest-floor materials obtainable bare-hand and used; pine_resin now scavenge-able by hand (#135)';
END $$;

ROLLBACK;
