-- Regression: soap from lye and tallow (V89, M1 #75 slice).
--
-- Pins the closing of the ash → lye → soap chain: soap is obtainable, made from already-obtainable lye and
-- tallow, mass-conserving, and "make soap" resolves to make_soap. Its terminal use is washing with soap
-- (wired into ChroniclePhysiologyService.wash). Read-only; rolls back.

BEGIN;

DO $$
DECLARE winner text; m_in int; m_out int;
BEGIN
    -- 1. Soap is obtainable, and both its inputs are obtainable (chain closes, nothing injected).
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = 'soap') THEN
        RAISE EXCEPTION 'REGRESSION: soap is not obtainable';
    END IF;
    IF EXISTS (SELECT 1 FROM material_process_input mi WHERE mi.process_key = 'make_soap'
               AND NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = mi.item_key)) THEN
        RAISE EXCEPTION 'REGRESSION: make_soap has an unobtainable input';
    END IF;

    -- 2. make_soap consumes lye and tallow.
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = 'make_soap' AND item_key = 'lye_solution')
       OR NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = 'make_soap' AND item_key = 'rendered_tallow') THEN
        RAISE EXCEPTION 'REGRESSION: make_soap must consume lye_solution and rendered_tallow';
    END IF;

    -- 3. Mass conservation: the soap weighs less than the lye + tallow it takes.
    SELECT SUM(di.unit_mass_grams * mi.quantity), MAX(dout.unit_mass_grams * mp.output_max)
      INTO m_in, m_out
      FROM material_process mp
      JOIN material_process_input mi ON mi.process_key = mp.process_key
      JOIN item_definition di ON di.item_key = mi.item_key
      JOIN item_definition dout ON dout.item_key = mp.output_item_key
     WHERE mp.process_key = 'make_soap';
    IF m_out >= m_in THEN
        RAISE EXCEPTION 'REGRESSION: soap (% g) is not lighter than its inputs (% g)', m_out, m_in;
    END IF;

    -- 4. "make soap" resolves to make_soap (CRAFT category, subject-gated to soap).
    SELECT mp.process_key INTO winner
    FROM material_process mp
    WHERE mp.review_state = 'VERIFIED' AND mp.category_key = 'CRAFT'
      AND EXISTS (SELECT 1 FROM regexp_split_to_table(mp.keywords, ',') kw
                  WHERE (' make soap ') LIKE ('% ' || trim(kw) || ' %'))
      AND EXISTS (SELECT 1 FROM process_subject ps
                  WHERE ps.process_key = mp.process_key AND (' make soap ') LIKE ('% ' || ps.subject_term || ' %'))
    ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords, ',') kw
              WHERE (' make soap ') LIKE ('% ' || trim(kw) || ' %')) DESC, mp.process_key ASC
    LIMIT 1;
    IF winner IS DISTINCT FROM 'make_soap' THEN
        RAISE EXCEPTION 'REGRESSION: "make soap" resolved to % (expected make_soap)', COALESCE(winner, 'NOTHING');
    END IF;

    RAISE NOTICE 'PASS: soap closes the ash->lye->soap chain — obtainable from obtainable lye+tallow, mass-conserving, "make soap" routes (V89 #75)';
END $$;

ROLLBACK;
