-- Regression: felled logs become timber (V91, M1 #75/#77).
--
-- Pins that the six species logs are now consumed and that the three milling processes no longer produce from
-- nothing — each requires a log (any species) through an input group, so a plank always comes off a log.
-- Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    logs text[] := ARRAY['oak_log','ash_log','pine_log','spruce_log','maple_log','birch_log'];
    mills text[] := ARRAY['split_planks','timber_from_log','notch_log'];
    k text; p text; n int;
BEGIN
    -- 1. Every species log is now a valid input to milling (no longer an orphan) and is obtainable.
    FOREACH k IN ARRAY logs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: log % is consumed by nothing', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: log % is required but not obtainable', k;
        END IF;
    END LOOP;

    -- 2. Each milling process now has a 'log' input group with all six species as alternatives — so it can no
    --    longer produce timber from nothing, and any one log satisfies it.
    FOREACH p IN ARRAY mills LOOP
        SELECT count(*) INTO n FROM material_process_input_group WHERE process_key = p AND group_name = 'log';
        IF n <> 6 THEN RAISE EXCEPTION 'REGRESSION: % has % log alternatives, expected 6', p, n; END IF;
    END LOOP;

    -- 3. Milling stays mass-honest: the planks/timber a log yields weigh less than the lightest log (birch).
    IF EXISTS (
        SELECT 1 FROM material_process mp JOIN item_definition dout ON dout.item_key = mp.output_item_key
        WHERE mp.process_key = ANY(mills)
          AND dout.unit_mass_grams * mp.output_max >= (SELECT MIN(unit_mass_grams) FROM item_definition WHERE item_key = ANY(
              ARRAY['oak_log','ash_log','pine_log','spruce_log','maple_log','birch_log']))) THEN
        RAISE EXCEPTION 'REGRESSION: a milling process makes more mass than the lightest log it takes';
    END IF;

    RAISE NOTICE 'PASS: 6 species logs now mill into planks/timber/notched logs (input-group, mass-honest) — no more timber from nothing (V91 #75)';
END $$;

ROLLBACK;
