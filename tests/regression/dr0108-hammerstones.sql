-- Regression: hammerstones (V108, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    IF (SELECT count(*) FROM item_source WHERE item_key IN ('granite_cobble','basalt_cobble') AND source_kind='MINERAL') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: cobbles not mineral-sourced'; END IF;
    IF (SELECT count(*) FROM mineral_definition WHERE mineral_key IN ('granite_cobble','basalt_cobble')) <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: cobbles not in mineral_definition'; END IF;
    RAISE NOTICE 'PASS: granite/basalt cobbles gather as hammerstones (STRIKING wired in code) (V108 #75)';
END $$;
ROLLBACK;
