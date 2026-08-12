-- Regression: bare-hand first-aid supplies (M1 #125 Recovery, EPIC #123, V119). Read-only.
--
-- Pins the three first-aid supplies as real, bare-hand-craftable stock the wound treatment reads: a fibre bandage,
-- a bark splint, and a cordage arm sling. Each has a defined source and a VERIFIED bare-hand process with real
-- inputs, none creates mass, and each process resolves through the real classifier. The tiered effect in
-- bindWound (poultice > bandage > fibre binding, plus splint/sling additions) is code, exercised by the backend
-- suite; here the contract is that the supplies exist, are makeable by hand, and are reachable.

BEGIN;

DO $$
DECLARE
    items text[] := ARRAY['fibre_bandage_roll','bark_splint_set','cordage_arm_sling'];
    procs text[] := ARRAY['roll_fibre_bandage','make_bark_splint','knot_arm_sling'];
    it text; pk text; bad text;
BEGIN
    FOREACH it IN ARRAY items LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it) THEN
            RAISE EXCEPTION 'AID: supply % is not defined', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it AND source_kind = 'TECHNIQUE') THEN
            RAISE EXCEPTION 'AID: supply % has no TECHNIQUE source', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE output_item_key = it AND tool_class IS NULL AND review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'AID: supply % has no VERIFIED bare-hand maker', it; END IF;
    END LOOP;

    FOREACH pk IN ARRAY procs LOOP
        IF EXISTS (SELECT 1 FROM material_process_input mpi WHERE mpi.process_key = pk
                     AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = mpi.item_key)) THEN
            RAISE EXCEPTION 'AID: process % consumes an undefined item', pk; END IF;
    END LOOP;

    -- No mass gain (consistent with the #199 handwork limit).
    SELECT string_agg(mp.process_key, ', ') INTO bad FROM material_process mp
      JOIN item_definition od ON od.item_key = mp.output_item_key
      LEFT JOIN (SELECT process_key, SUM(quantity*d.unit_mass_grams) m FROM material_process_input i JOIN item_definition d ON d.item_key=i.item_key GROUP BY 1) s ON s.process_key = mp.process_key
     WHERE mp.process_key = ANY(procs) AND od.unit_mass_grams*mp.output_max > COALESCE(s.m,0);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'AID: first-aid result exceeds its material (mass gain) in: %', bad; END IF;

    RAISE NOTICE 'PASS: three first-aid supplies craftable bare-hand with real inputs, no mass gain (#125, V119)';
END $$;

-- Classifier reachability for the three makers.
DO $$
DECLARE
    procs text[] := ARRAY['roll_fibre_bandage','make_bark_splint','knot_arm_sling'];
    unreachable int;
BEGIN
    WITH kws AS (
        SELECT mp.process_key, mp.category_key AS pc, lower(trim(k)) AS kw
        FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
        WHERE mp.process_key = ANY(procs)
    ),
    win AS (
        SELECT k.process_key, k.pc, k.kw,
            (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key = ct.category_key
             WHERE k.kw ~ ('\y' || lower(ct.term) || '\y') GROUP BY ct.category_key, ac.precedence
             ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) AS win_cat
        FROM kws k
    ),
    live AS (
        SELECT DISTINCT w.process_key FROM win w
        WHERE (w.win_cat IS NULL OR w.win_cat = w.pc)
          AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key = w.process_key AND w.kw ~ ('\y' || lower(ps.subject_term) || '\y'))
    )
    SELECT array_length(procs,1) - (SELECT count(*) FROM live WHERE process_key = ANY(procs)) INTO unreachable;
    IF unreachable <> 0 THEN
        RAISE EXCEPTION 'AID: % of % first-aid makers are unreachable through the classifier', unreachable, array_length(procs,1); END IF;
    RAISE NOTICE 'PASS: all three first-aid makers reachable through the real ActivityClassifier rule (V119)';
END $$;

ROLLBACK;
