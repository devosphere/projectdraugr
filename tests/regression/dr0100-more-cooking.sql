-- Regression: greens/roots/morel cooking (V100, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE k text;
BEGIN
    FOREACH k IN ARRAY ARRAY['burdock_root','bulrush_root','nettle_leaf','watercress_bundle','morel'] LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: % has no cooking use', k; END IF;
    END LOOP;
    -- stew root slot takes three roots and did not lose cattail.
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='cook_root_stew' AND group_name='root') <> 3 THEN
        RAISE EXCEPTION 'REGRESSION: stew root group should have 3 roots'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='cook_root_stew' AND group_name='root' AND item_key='cattail_rhizome') THEN
        RAISE EXCEPTION 'REGRESSION: stew lost cattail rhizome'; END IF;
    -- morel joins the mushroom pan; cooked greens is obtainable FOOD.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='cook_mushrooms' AND item_key='morel') THEN
        RAISE EXCEPTION 'REGRESSION: morel not in cook_mushrooms'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='cooked_greens' AND category='FOOD') THEN
        RAISE EXCEPTION 'REGRESSION: cooked_greens missing'; END IF;
    RAISE NOTICE 'PASS: burdock/bulrush roots + nettle/watercress greens + morel all cook (V100 #75)';
END $$;
ROLLBACK;
