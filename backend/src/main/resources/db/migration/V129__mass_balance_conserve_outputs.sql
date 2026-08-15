-- V129 — mass-balance the remaining #245 part C processes: a made thing weighs at most its materials.
--
-- 14 VERIFIED, non-exempt processes declared an output heavier than the least their recipe can consume
-- (process_mass_balance.max_output_grams > min_input_grams) — i.e. matter created from nothing. The
-- physically-correct, non-guessed fix is conservation: set each violating output's unit mass so the
-- most it can yield never exceeds the least its inputs weigh. This is realistic for BOTH classes:
--   * reductive work (cook/peel/grind/harden/bake/stew/shape) removes mass, so output <= input;
--   * combination work (haft/craft/carve/weave/assemble) yields a thing that weighs what went into it.
-- It is NOT a claim that these tools should be light — a heavier maul or mortar is a RECIPE change
-- (require more/heavier input), tracked separately; here we only stop matter appearing from nowhere.
--
-- Applied as a fixpoint because four outputs feed downstream recipes (peeled_root -> wash_root ->
-- cook_root_stew; grind_pigment -> dye_cloth; carve_point -> assemble_arrows): lowering an upstream
-- output lowers a downstream min-input, which the next pass then reconciles. Masses only decrease and
-- the dependency graph is acyclic, so it converges; we assert convergence and fail loud otherwise.
DO $$
DECLARE changed int;
BEGIN
    FOR i IN 1..12 LOOP
        UPDATE item_definition d
        SET unit_mass_grams = t.newmass
        FROM (
            SELECT b.output_item_key AS k,
                   min(GREATEST(1, floor(b.min_input_grams::numeric / NULLIF(mp.output_max, 0))::int)) AS newmass
            FROM process_mass_balance b
            JOIN material_process mp ON mp.process_key = b.process_key
            WHERE mp.review_state = 'VERIFIED' AND NOT mp.conservation_exempt
              AND b.max_output_grams > b.min_input_grams
            GROUP BY b.output_item_key
        ) t
        WHERE d.item_key = t.k AND d.unit_mass_grams <> t.newmass;
        GET DIAGNOSTICS changed = ROW_COUNT;
        EXIT WHEN changed = 0;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM process_mass_balance b
        JOIN material_process mp ON mp.process_key = b.process_key
        WHERE mp.review_state = 'VERIFIED' AND NOT mp.conservation_exempt
          AND b.max_output_grams > b.min_input_grams
    ) THEN
        RAISE EXCEPTION 'V129: mass-balance did not converge — a cyclic or unsatisfiable recipe remains';
    END IF;
END $$;
