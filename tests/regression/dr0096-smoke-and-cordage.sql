-- Regression: waterproof cordage + smoke hood (V96, M1 #75). Read-only.
BEGIN;
DO $$
BEGIN
    -- tarred cordage is an alternative binder wherever it was wired (bucket/bark container/burden/harness).
    IF (SELECT count(DISTINCT process_key) FROM material_process_input_group WHERE item_key='tarred_cordage') < 4 THEN
        RAISE EXCEPTION 'REGRESSION: tarred_cordage is not a binder in enough processes'; END IF;
    -- and it never silently replaced the fibre-cordage requirement (both must remain valid alternatives).
    IF EXISTS (SELECT group_name, process_key FROM material_process_input_group WHERE item_key='tarred_cordage'
               EXCEPT SELECT group_name, process_key FROM material_process_input_group WHERE item_key='fiber_cordage') THEN
        RAISE EXCEPTION 'REGRESSION: a tarred_cordage slot lost its fibre-cordage alternative'; END IF;
    -- the smoke hood completes the smoke rack.
    IF NOT EXISTS (SELECT 1 FROM assembly_stage_requirement WHERE stage_key='smoke_bars' AND item_key='smoke_hood') THEN
        RAISE EXCEPTION 'REGRESSION: smoke_hood does not complete the smoke rack'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='smoke_hood') THEN
        RAISE EXCEPTION 'REGRESSION: smoke_hood not obtainable'; END IF;
    RAISE NOTICE 'PASS: tarred cordage binds where it gets wet + smoke hood completes the smoke rack (V96)';
END $$;
ROLLBACK;
