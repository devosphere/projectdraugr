-- Regression: plant-fibre → cordage materials (V84, M1 #75 slice).
--
-- Pins the full acquire → use journey for the four new fibres, and — the fragile part — that a NAMED fibre's
-- cordage phrasing resolves to its OWN process rather than the generic twist_cordage. The matcher takes the
-- longest whole-word keyword (ties → lexically first process_key); a short material name like "flax" would lose
-- to the 7-char "cordage" unless the specific process also carries a longer keyword that the phrasing hits. This
-- replicates that rule in SQL over the real rows, so a future keyword edit that reintroduces the collision fails
-- here. Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    fibres text[] := ARRAY['milkweed_fibre','root_fibre_bundle','flax_stalk','hemp_stalk'];
    k text;
    winner text;
    -- phrase → the specific process that must win it
    cases text[][] := ARRAY[
        ARRAY['twist the milkweed fibre into cordage','twist_milkweed_cordage'],
        ARRAY['twist the root fibre into cordage','twist_root_cordage'],
        ARRAY['ret the flax into cordage','ret_flax_cordage'],
        ARRAY['ret the hemp into cordage','ret_hemp_cordage']
    ];
    c text[];
BEGIN
    -- 1. Every fibre is obtainable through an ecological source (flora drop), not injected.
    FOREACH k IN ARRAY fibres LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k AND source_kind = 'FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no FLORA_DROP source', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM flora_drop WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: % is not dropped by any flora', k;
        END IF;
        -- 2. Every fibre has at least one VERIFIED use before it is exposed.
        IF NOT EXISTS (SELECT 1 FROM material_process_input mi JOIN material_process mp ON mp.process_key = mi.process_key
                       WHERE mi.item_key = k AND mp.review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'REGRESSION: % has no verified use', k;
        END IF;
    END LOOP;

    -- 3. Each cordage yields fiber_cordage with output mass below input mass (conservation).
    IF EXISTS (
        SELECT 1 FROM material_process mp
        JOIN material_process_input mi ON mi.process_key = mp.process_key
        JOIN item_definition di ON di.item_key = mi.item_key
        JOIN item_definition dout ON dout.item_key = mp.output_item_key
        WHERE mp.process_key IN ('twist_milkweed_cordage','twist_root_cordage','ret_flax_cordage','ret_hemp_cordage')
          AND dout.unit_mass_grams * mp.output_max >= di.unit_mass_grams * mi.quantity) THEN
        RAISE EXCEPTION 'REGRESSION: a fibre cordage process makes as much mass as it consumes';
    END IF;

    -- 4. The disambiguation: each named phrasing resolves to its OWN process, not the generic twist_cordage.
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

    RAISE NOTICE 'PASS: 4 plant-fibre materials — obtainable, verified cordage use, mass-conserving, and each named phrasing beats the generic twist_cordage (V84 #75)';
END $$;

ROLLBACK;
