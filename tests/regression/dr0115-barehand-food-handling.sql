-- Regression: bare-hand food/water handling (M1 #196, EPIC #191, V114). Read-only.
--
-- Pins that handling exists AND stays distinct from cooking/purification: shell/peel/rinse are bare-hand PROCESS
-- steps producing edible kernels or cook-ready roots, but a walnut's hard shell is tool-gated (STRIKING) and the
-- handled roots are stew inputs, not standalone safe meals — a bare hand never makes food safe by fiat. Also pins
-- classifier reachability so no handling keyword is dead on arrival.

BEGIN;

DO $$
DECLARE
    foods  text[] := ARRAY['hazelnut_kernel','walnut_kernel','peeled_root','washed_root'];
    procs  text[] := ARRAY['shell_hazelnut','crack_walnut','peel_root','wash_root'];
    it text; pk text;
BEGIN
    -- (A) Handled produce is real FOOD with a hand source.
    FOREACH it IN ARRAY foods LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it AND category = 'FOOD') THEN
            RAISE EXCEPTION 'HANDLING: % is not a defined FOOD item', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it AND source_kind = 'TECHNIQUE') THEN
            RAISE EXCEPTION 'HANDLING: % has no TECHNIQUE source', it; END IF;
    END LOOP;

    -- (B) Each handling process is a VERIFIED PROCESS producing one handled food, with real inputs.
    FOREACH pk IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND category_key = 'PROCESS' AND review_state = 'VERIFIED' AND output_item_key = ANY(foods)) THEN
            RAISE EXCEPTION 'HANDLING: process % is not a VERIFIED PROCESS producing a handled food', pk; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = pk)
           AND NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key = pk) THEN
            RAISE EXCEPTION 'HANDLING: process % has no inputs', pk; END IF;
        IF EXISTS (SELECT 1 FROM material_process_input mpi WHERE mpi.process_key = pk
                     AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = mpi.item_key))
           OR EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key = pk
                     AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = g.item_key)) THEN
            RAISE EXCEPTION 'HANDLING: process % consumes an undefined input item', pk; END IF;
    END LOOP;

    -- (C) Bare-hand where plausible; the hard shell is tool-gated; the rinse needs water.
    IF EXISTS (SELECT 1 FROM material_process WHERE process_key IN ('shell_hazelnut','peel_root','wash_root') AND tool_class IS NOT NULL) THEN
        RAISE EXCEPTION 'HANDLING: a soft-handling process wrongly requires a tool'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = 'crack_walnut' AND tool_class = 'STRIKING') THEN
        RAISE EXCEPTION 'HANDLING: crack_walnut must be STRIKING-gated (a walnut shell will not open to bare hands)'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = 'wash_root' AND requires_water) THEN
        RAISE EXCEPTION 'HANDLING: wash_root must require reachable water'; END IF;

    -- (D) Handling is not safety. The handled roots are stew inputs, not standalone safe meals; and the acorn
    --     safety chain (leaching) remains — no bare-hand step short-circuits it.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key = 'cook_root_stew' AND group_name = 'root' AND item_key = 'peeled_root')
       OR NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key = 'cook_root_stew' AND group_name = 'root' AND item_key = 'washed_root') THEN
        RAISE EXCEPTION 'HANDLING: handled roots must feed the root stew (cook-ready, not fiat-safe)'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = 'leach_acorn_flour' AND review_state = 'VERIFIED') THEN
        RAISE EXCEPTION 'HANDLING: acorn leaching (safety chain) must remain intact'; END IF;

    RAISE NOTICE 'PASS: bare-hand handling present and distinct from safety — shell/peel/rinse bare-hand, walnut tool-gated, handled roots are stew inputs, leaching intact (#196, V114)';
END $$;

-- (E) Classifier reachability: every handling process has a keyword that resolves through the real classify() rule.
DO $$
DECLARE
    procs text[] := ARRAY['shell_hazelnut','crack_walnut','peel_root','wash_root'];
    unreachable int;
BEGIN
    WITH kws AS (
        SELECT mp.process_key, mp.category_key AS pc, lower(trim(k)) AS kw
        FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
        WHERE mp.process_key = ANY(procs)
    ),
    win AS (
        SELECT k.process_key, k.pc, k.kw,
            (SELECT ct.category_key FROM category_term ct
              JOIN activity_category ac ON ac.category_key = ct.category_key
             WHERE k.kw ~ ('\y' || lower(ct.term) || '\y')
             GROUP BY ct.category_key, ac.precedence
             ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) AS win_cat
        FROM kws k
    ),
    live AS (
        SELECT DISTINCT w.process_key FROM win w
        WHERE (w.win_cat IS NULL OR w.win_cat = w.pc)
          AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key = w.process_key
                       AND w.kw ~ ('\y' || lower(ps.subject_term) || '\y'))
    )
    SELECT array_length(procs,1) - (SELECT count(*) FROM live WHERE process_key = ANY(procs)) INTO unreachable;
    IF unreachable <> 0 THEN
        RAISE EXCEPTION 'HANDLING: % of % handling processes are unreachable through the classifier', unreachable, array_length(procs,1);
    END IF;
    RAISE NOTICE 'PASS: all four handling processes reachable through the real ActivityClassifier rule (V114)';
END $$;

ROLLBACK;
