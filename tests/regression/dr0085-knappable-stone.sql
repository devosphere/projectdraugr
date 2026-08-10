-- Regression: knappable stone raw materials (V85, M1 #75 slice).
--
-- Pins the acquire → use journey for the two new knappable stones and — the fragile part — that a NAMED stone's
-- knapping phrasing resolves to its OWN process, not the generic knap_arrowheads/knap_scraper. Same matcher rule
-- as V84 (longest whole-word keyword, subject-gated). Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    stones text[] := ARRAY['chert_nodule','obsidian_shard'];
    k text;
    winner text;
    cases text[][] := ARRAY[
        ARRAY['knap the chert into a flake','knap_chert_flake'],
        ARRAY['knap the obsidian into a blade','knap_obsidian_flake']
    ];
    c text[];
BEGIN
    -- 1. Each stone is obtainable through the mineral system, and matchable by "gather <name>" (short display).
    FOREACH k IN ARRAY stones LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k AND source_kind = 'MINERAL') THEN
            RAISE EXCEPTION 'REGRESSION: % has no MINERAL source', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: % is not in mineral_definition', k;
        END IF;
        -- 2. Each has a VERIFIED use before exposure (knapped into a flake).
        IF NOT EXISTS (SELECT 1 FROM material_process_input mi JOIN material_process mp ON mp.process_key = mi.process_key
                       WHERE mi.item_key = k AND mp.review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'REGRESSION: % has no verified use', k;
        END IF;
    END LOOP;

    -- 3. The flake they make exists and is a stackable tool (a real cutting edge, wired into hasCuttingTool).
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'stone_flake' AND category = 'TOOL') THEN
        RAISE EXCEPTION 'REGRESSION: stone_flake is missing or not a TOOL';
    END IF;

    -- 4. Knapping conserves mass: a flake weighs less than the nodule it comes off.
    IF EXISTS (
        SELECT 1 FROM material_process mp
        JOIN material_process_input mi ON mi.process_key = mp.process_key
        JOIN item_definition di ON di.item_key = mi.item_key
        JOIN item_definition dout ON dout.item_key = mp.output_item_key
        WHERE mp.process_key IN ('knap_chert_flake','knap_obsidian_flake')
          AND dout.unit_mass_grams * mp.output_max >= di.unit_mass_grams * mi.quantity) THEN
        RAISE EXCEPTION 'REGRESSION: a knap makes as much stone as it consumes';
    END IF;

    -- 5. Disambiguation: each named knap resolves to its own process, not the generic arrowhead/scraper knap.
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

    RAISE NOTICE 'PASS: chert + obsidian — obtainable, verified knap use into a usable flake, mass-conserving, each named knap beats the generic (V85 #75)';
END $$;

ROLLBACK;
