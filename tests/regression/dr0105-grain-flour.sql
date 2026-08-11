-- Regression: grain -> flour -> bread (V105, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='grind_flour' AND item_key='wild_grain') THEN
        RAISE EXCEPTION 'REGRESSION: grind_flour must grind wild_grain'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='grind_flour' AND output_item_key='grain_flour' AND tool_class='STRIKING') THEN
        RAISE EXCEPTION 'REGRESSION: grind_flour must need a stone and yield flour'; END IF;
    -- flatbread now bakes from acorn OR grain flour.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='bake_flatbread' AND item_key='grain_flour')
       OR NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='bake_flatbread' AND item_key='acorn_flour') THEN
        RAISE EXCEPTION 'REGRESSION: flatbread flour group missing acorn or grain flour'; END IF;
    RAISE NOTICE 'PASS: wild grain grinds to flour and bakes into flatbread (V105 #75)';
END $$;
ROLLBACK;
