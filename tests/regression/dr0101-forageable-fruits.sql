-- Regression: forageable fruits (V101, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE k text;
BEGIN
    FOREACH k IN ARRAY ARRAY['crab_apple','sloe','bilberry'] LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k AND source_kind='FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no flora source', k; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='stew_compote' AND item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: % does not stew into a compote', k; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key=k AND category='FOOD') THEN
            RAISE EXCEPTION 'REGRESSION: % is not FOOD', k; END IF;
    END LOOP;
    RAISE NOTICE 'PASS: crab apple, sloe, bilberry — obtainable, edible, and stew into compote (V101 #75)';
END $$;
ROLLBACK;
