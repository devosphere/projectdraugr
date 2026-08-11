-- Regression: whetstone & grit sharpening (V107, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    IF (SELECT count(*) FROM item_source WHERE item_key IN ('sandstone_piece','pumice_piece') AND source_kind='MINERAL') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: sandstone/pumice not mineral-sourced'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='dress_whetstone' AND item_key='sandstone_piece') THEN
        RAISE EXCEPTION 'REGRESSION: whetstone not dressed from sandstone'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='whetstone' AND category='TOOL') THEN
        RAISE EXCEPTION 'REGRESSION: whetstone missing'; END IF;
    RAISE NOTICE 'PASS: sandstone/pumice grit + whetstone; sharpening draws an edge against stone (V107 #75)';
END $$;
ROLLBACK;
