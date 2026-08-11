-- Regression: ochre/pigment/dyed cloth (V104, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    -- ochres obtainable (mineral) and grind into pigment; pigment dyes cloth into dyed_cloth.
    IF (SELECT count(*) FROM item_source WHERE item_key IN ('ochre_red','ochre_yellow') AND source_kind='MINERAL') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: ochres not mineral-sourced'; END IF;
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='grind_pigment' AND group_name='stock') < 3 THEN
        RAISE EXCEPTION 'REGRESSION: grind_pigment should take ochre/charcoal'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='dye_cloth' AND item_key='pigment') THEN
        RAISE EXCEPTION 'REGRESSION: dye_cloth must consume pigment'; END IF;
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='dye_cloth' AND group_name='cloth') < 3 THEN
        RAISE EXCEPTION 'REGRESSION: dye_cloth should take woven cloth'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='dyed_cloth') THEN
        RAISE EXCEPTION 'REGRESSION: dyed_cloth not obtainable'; END IF;
    RAISE NOTICE 'PASS: ochre -> pigment -> dyed cloth -> garment stock (V104 #75)';
END $$;
ROLLBACK;
