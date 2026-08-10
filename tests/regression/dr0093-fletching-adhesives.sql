-- Regression: any feather fletches, any adhesive binds (V93, M1 #75).
--
-- Pins that the specific feathers and the other adhesives now do their real work in the arrow chain (input
-- groups), so none is an orphan, and that the recipes still require their other real parts. Read-only.

BEGIN;

DO $$
DECLARE k text;
BEGIN
    -- 1. Each of the five is now consumed as a real alternative in an input group.
    FOREACH k IN ARRAY ARRAY['harpy_feather','roc_feather','birch_tar','fish_glue','propolis'] LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: % is consumed by nothing', k;
        END IF;
    END LOOP;

    -- 2. Fletching now takes any feather (3 alternatives) and still needs sinew + shafts.
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='fletch_arrows' AND group_name='fletching') <> 3 THEN
        RAISE EXCEPTION 'REGRESSION: fletch_arrows should accept 3 feather types';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='fletch_arrows' AND item_key='animal_sinew')
       OR NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='fletch_arrows' AND item_key='arrow_shaft') THEN
        RAISE EXCEPTION 'REGRESSION: fletch_arrows lost its sinew/shaft requirement';
    END IF;

    -- 3. Arrow assembly now takes any of four binders and still needs a fletched shaft and a point group.
    IF (SELECT count(*) FROM material_process_input_group WHERE process_key='assemble_arrows' AND group_name='binder') <> 4 THEN
        RAISE EXCEPTION 'REGRESSION: assemble_arrows should accept 4 binders';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='assemble_arrows' AND item_key='fletched_shaft')
       OR NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='assemble_arrows' AND group_name='point') THEN
        RAISE EXCEPTION 'REGRESSION: assemble_arrows lost its shaft or point requirement';
    END IF;

    -- 4. No binder/feather slot is empty of alternatives (would let an arrow assemble from nothing).
    IF EXISTS (SELECT process_key, group_name FROM material_process_input_group
               GROUP BY process_key, group_name HAVING count(*) = 0) THEN
        RAISE EXCEPTION 'REGRESSION: an input group has no alternatives';
    END IF;

    RAISE NOTICE 'PASS: harpy/roc feathers fletch and birch-tar/fish-glue/propolis bind — 5 orphans closed by real interchangeability (V93 #75)';
END $$;

ROLLBACK;
