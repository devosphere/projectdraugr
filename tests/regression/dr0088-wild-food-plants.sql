-- Regression: wild food plants (V88, M1 #75 slice).
--
-- Pins the acquire → use journey for the wild-food family: four are directly-edible FOOD, while a wild grain
-- HEAD is a MATERIAL that must be threshed into edible grain (an inedible-as-gathered seed head is not silently
-- eaten). Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    edible text[] := ARRAY['wild_onion_bulb','garlic_clove_wild','dandelion_leaf','cattail_rhizome'];
    k text; winner text; m_in int; m_out int;
BEGIN
    -- 1. Every gathered plant is obtainable through a flora drop.
    FOREACH k IN ARRAY (edible || ARRAY['wild_grain_head']) LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k AND source_kind = 'FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no FLORA_DROP source', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM flora_drop WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: % is dropped by no flora', k;
        END IF;
    END LOOP;

    -- 2. The four are edible FOOD; their use is EAT.
    FOREACH k IN ARRAY edible LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = k AND category = 'FOOD') THEN
            RAISE EXCEPTION 'REGRESSION: % is not FOOD', k;
        END IF;
    END LOOP;

    -- 3. The seed head is NOT directly-edible FOOD (a MATERIAL), and HAS a verified thresh use to edible grain.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'wild_grain_head' AND category = 'MATERIAL') THEN
        RAISE EXCEPTION 'REGRESSION: raw wild_grain_head must be a MATERIAL, not directly edible';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process mp JOIN material_process_input mi ON mi.process_key = mp.process_key
                   WHERE mi.item_key = 'wild_grain_head' AND mp.output_item_key = 'wild_grain' AND mp.review_state = 'VERIFIED') THEN
        RAISE EXCEPTION 'REGRESSION: wild_grain_head has no verified thresh use';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'wild_grain' AND category = 'FOOD') THEN
        RAISE EXCEPTION 'REGRESSION: threshed wild_grain must be edible FOOD';
    END IF;

    -- 4. Threshing conserves mass.
    SELECT di.unit_mass_grams * mi.quantity, dout.unit_mass_grams * mp.output_max INTO m_in, m_out
      FROM material_process mp
      JOIN material_process_input mi ON mi.process_key = mp.process_key
      JOIN item_definition di ON di.item_key = mi.item_key
      JOIN item_definition dout ON dout.item_key = mp.output_item_key
     WHERE mp.process_key = 'thresh_wild_grain';
    IF m_out >= m_in THEN
        RAISE EXCEPTION 'REGRESSION: threshed grain (% g) is not lighter than the heads it takes (% g)', m_out, m_in;
    END IF;

    -- 5. "thresh the grain" resolves to the thresh process.
    SELECT mp.process_key INTO winner
    FROM material_process mp
    WHERE mp.review_state = 'VERIFIED' AND mp.category_key = 'PROCESS'
      AND EXISTS (SELECT 1 FROM regexp_split_to_table(mp.keywords, ',') kw
                  WHERE (' thresh the grain ') LIKE ('% ' || trim(kw) || ' %'))
      AND EXISTS (SELECT 1 FROM process_subject ps
                  WHERE ps.process_key = mp.process_key AND (' thresh the grain ') LIKE ('% ' || ps.subject_term || ' %'))
    ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords, ',') kw
              WHERE (' thresh the grain ') LIKE ('% ' || trim(kw) || ' %')) DESC, mp.process_key ASC
    LIMIT 1;
    IF winner IS DISTINCT FROM 'thresh_wild_grain' THEN
        RAISE EXCEPTION 'REGRESSION: "thresh the grain" resolved to % (expected thresh_wild_grain)', COALESCE(winner, 'NOTHING');
    END IF;

    RAISE NOTICE 'PASS: 4 edible wild foods + wild grain — obtainable, edible via EAT, grain head inedible until threshed, mass-conserving (V88 #75)';
END $$;

ROLLBACK;
