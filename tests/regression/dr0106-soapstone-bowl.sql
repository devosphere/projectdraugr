-- Regression: soapstone bowl (V106, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='soapstone_piece' AND source_kind='MINERAL') THEN
        RAISE EXCEPTION 'REGRESSION: soapstone not mineral-sourced'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='carve_soapstone_bowl' AND item_key='soapstone_piece') THEN
        RAISE EXCEPTION 'REGRESSION: bowl not carved from soapstone'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='soapstone_bowl' AND category='CONTAINER')
       OR NOT EXISTS (SELECT 1 FROM container_capacity_default WHERE item_key='soapstone_bowl') THEN
        RAISE EXCEPTION 'REGRESSION: soapstone_bowl is not a functional container'; END IF;
    RAISE NOTICE 'PASS: soapstone quarried and carved into a working bowl-container (V106 #75)';
END $$;
ROLLBACK;
