-- Regression: bare-hand cordage materials (V111, M1 #192/#193/#191). Read-only.
BEGIN;
DO $$
DECLARE k text;
BEGIN
    FOREACH k IN ARRAY ARRAY['flexible_root','soft_bast_strip'] LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k AND source_kind='FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no flora source', k; END IF;
    END LOOP;
    -- both twist into cordage BY HAND (tool_class NULL).
    IF EXISTS (SELECT 1 FROM material_process WHERE process_key IN ('twist_flexible_root_cordage','twist_bast_cordage') AND tool_class IS NOT NULL) THEN
        RAISE EXCEPTION 'REGRESSION: a bare-hand cordage process requires a tool'; END IF;
    IF (SELECT count(*) FROM material_process WHERE process_key IN ('twist_flexible_root_cordage','twist_bast_cordage') AND output_item_key='fiber_cordage') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: bast/root do not twist into cordage'; END IF;
    RAISE NOTICE 'PASS: pliable root + soft bast twist into cordage by hand, no tool (V111 #192/#193/#191)';
END $$;
ROLLBACK;
