-- Regression: chamomile/pine-needle/wild-rice ingredients (V99, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE k text;
BEGIN
    FOREACH k IN ARRAY ARRAY['chamomile_flower','pine_needle_bundle','wild_rice_grain'] LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k AND source_kind='FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no flora source', k; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: % has no cooking use', k; END IF;
    END LOOP;
    -- chamomile + pine needles steep into an infusion.
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='brew_infusion' AND group_name='herb' AND item_key IN ('chamomile_flower','pine_needle_bundle')) <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: chamomile/pine needle not in the tea herb group'; END IF;
    -- porridge takes wild grain OR wild rice, and did not lose wild grain.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='cook_porridge' AND group_name='grain' AND item_key='wild_rice_grain')
       OR NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='cook_porridge' AND group_name='grain' AND item_key='wild_grain') THEN
        RAISE EXCEPTION 'REGRESSION: porridge grain group missing wild grain or wild rice'; END IF;
    RAISE NOTICE 'PASS: chamomile/pine-needle tea + wild-rice porridge — obtainable and cook (V99 #75)';
END $$;
ROLLBACK;
