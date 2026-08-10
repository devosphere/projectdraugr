-- Regression: medicinal herbs → herbal poultice (V87, M1 #75 slice).
--
-- Closes an #75 acceptance gap: yarrow/comfrey/plantain gathered from the world but had NO use. Pins that each
-- now has a verified poultice use, that the poultice is obtainable and mass-conserving, and that a named
-- "pound the <herb>" resolves to that herb's own process (subject-gated). Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    herbs text[] := ARRAY['yarrow_bundle','comfrey_leaf','plantain_leaf'];
    k text;
    winner text;
    cases text[][] := ARRAY[
        ARRAY['pound the yarrow into a poultice','poultice_yarrow'],
        ARRAY['crush the comfrey into a poultice','poultice_comfrey'],
        ARRAY['mash the plantain into a poultice','poultice_plantain']
    ];
    c text[];
BEGIN
    -- 1. Each herb is obtainable AND now has at least one verified use (it had none before V87).
    FOREACH k IN ARRAY herbs LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: % is not obtainable', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input mi JOIN material_process mp ON mp.process_key = mi.process_key
                       WHERE mi.item_key = k AND mp.output_item_key = 'herbal_poultice' AND mp.review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'REGRESSION: % has no verified poultice use', k;
        END IF;
    END LOOP;

    -- 2. The poultice exists, is obtainable, and weighs less than the herbs any of its processes take.
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = 'herbal_poultice') THEN
        RAISE EXCEPTION 'REGRESSION: herbal_poultice is not obtainable';
    END IF;
    IF EXISTS (
        SELECT 1 FROM material_process mp
        JOIN material_process_input mi ON mi.process_key = mp.process_key
        JOIN item_definition di ON di.item_key = mi.item_key
        JOIN item_definition dout ON dout.item_key = mp.output_item_key
        WHERE mp.process_key IN ('poultice_yarrow','poultice_comfrey','poultice_plantain')
          AND dout.unit_mass_grams * mp.output_max >= di.unit_mass_grams * mi.quantity) THEN
        RAISE EXCEPTION 'REGRESSION: a poultice weighs as much as the herbs it takes';
    END IF;

    -- 3. Disambiguation: each named pound/crush/mash resolves to that herb's own process.
    FOREACH c SLICE 1 IN ARRAY cases LOOP
        SELECT mp.process_key INTO winner
        FROM material_process mp
        WHERE mp.review_state = 'VERIFIED' AND mp.category_key = 'PROCESS'
          AND EXISTS (SELECT 1 FROM regexp_split_to_table(mp.keywords, ',') kw
                      WHERE (' ' || c[1] || ' ') LIKE ('% ' || trim(kw) || ' %'))
          AND EXISTS (SELECT 1 FROM process_subject ps
                      WHERE ps.process_key = mp.process_key AND (' ' || c[1] || ' ') LIKE ('% ' || ps.subject_term || ' %'))
        ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords, ',') kw
                  WHERE (' ' || c[1] || ' ') LIKE ('% ' || trim(kw) || ' %')) DESC,
                 mp.process_key ASC
        LIMIT 1;
        IF winner IS DISTINCT FROM c[2] THEN
            RAISE EXCEPTION 'REGRESSION: "%" resolved to % (expected %)', c[1], COALESCE(winner, 'NOTHING'), c[2];
        END IF;
    END LOOP;

    RAISE NOTICE 'PASS: yarrow/comfrey/plantain now have a verified poultice use, mass-conserving, each named pound routes to its own herb (V87 #75)';
END $$;

ROLLBACK;
