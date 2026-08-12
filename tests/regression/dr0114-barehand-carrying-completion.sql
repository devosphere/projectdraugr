-- Regression: bare-hand carrying completion (M1 #194, EPIC #191, V113). Read-only.
--
-- V109 shipped five hand-made carriers; V113 completes #194's named set with five more (bark scoop, tied reed
-- sheaf, cordage carry loop, grass carry-mat, tied forage bundle). This pins that each is a fully functional,
-- reachable object: a CONTAINER item with real capacity, a TECHNIQUE source, and a bare-hand (tool_class NULL)
-- VERIFIED process whose inputs all exist. It also pins the matcher self-consistency invariant — every keyword of
-- these processes shares a whole-word subject term, so no phrasing is dead on arrival (a keyword with no co-present
-- subject term can never resolve under ProcessMatcher's rule).

BEGIN;

DO $$
DECLARE
    carriers text[] := ARRAY['bark_scoop','reed_bundle_tie','simple_cordage_loop','temporary_grass_carry_mat','foraged_material_bundle'];
    procs    text[] := ARRAY['fold_bark_scoop','tie_reed_sheaf','knot_cordage_loop','weave_grass_mat','tie_forage_bundle'];
    it   text;
    pk   text;
    kw   text;
    bad  int;
BEGIN
    -- (A) Each carrier is a real, capacity-bearing, hand-sourced CONTAINER.
    FOREACH it IN ARRAY carriers LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it AND category = 'CONTAINER') THEN
            RAISE EXCEPTION 'CARRY: % is not a defined CONTAINER item', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM container_capacity_default WHERE item_key = it AND max_mass_grams > 0 AND max_volume_ml > 0) THEN
            RAISE EXCEPTION 'CARRY: % has no positive default capacity', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it AND source_kind = 'TECHNIQUE') THEN
            RAISE EXCEPTION 'CARRY: % has no TECHNIQUE source', it; END IF;
    END LOOP;

    -- (B) Each process is bare-hand, VERIFIED, produces its carrier, and every input is a real item.
    FOREACH pk IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND tool_class IS NULL AND review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'CARRY: process % is not a VERIFIED bare-hand process', pk; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = pk) THEN
            RAISE EXCEPTION 'CARRY: process % has no inputs', pk; END IF;
        IF EXISTS (SELECT 1 FROM material_process_input mpi
                   WHERE mpi.process_key = pk
                     AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = mpi.item_key)) THEN
            RAISE EXCEPTION 'CARRY: process % consumes an undefined input item', pk; END IF;
    END LOOP;

    -- Each process's output is one of the five carriers.
    IF (SELECT count(*) FROM material_process WHERE process_key = ANY(procs) AND output_item_key = ANY(carriers)) <> array_length(procs,1) THEN
        RAISE EXCEPTION 'CARRY: a completion process does not output one of the five carriers'; END IF;

    -- (C) Matcher self-consistency: every keyword shares a whole-word subject term with its own process.
    FOR pk IN SELECT unnest(procs) LOOP
        FOR kw IN SELECT trim(k) FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key = pk LOOP
            SELECT count(*) INTO bad FROM process_subject s
             WHERE s.process_key = pk AND lower(kw) ~ ('\y' || lower(s.subject_term) || '\y');
            IF bad = 0 THEN
                RAISE EXCEPTION 'CARRY: keyword "%" of process % has no co-present subject term — it can never resolve', kw, pk;
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'PASS: five #194 carriers complete — CONTAINER+capacity+source+bare-hand process+real inputs; all keywords carry a subject term (V113)';
END $$;

-- (D) Classifier reachability: replicate ActivityClassifier.classify() over each keyword and assert every process
--     has at least one keyword that (i) classifies to its own category or to no category (null → gate dropped) AND
--     (ii) carries a subject term. A keyword whose dominant token is an ACQUIRE/PROCESS verb (scoop, forage, weave)
--     classifies away from CRAFT and can never resolve to these CRAFT processes — this is exactly the trap V113's
--     keyword design avoids, and this check keeps future carriers out of it.
DO $$
DECLARE
    procs text[] := ARRAY['fold_bark_scoop','tie_reed_sheaf','knot_cordage_loop','weave_grass_mat','tie_forage_bundle'];
    unreachable int;
BEGIN
    WITH kws AS (
        SELECT mp.process_key, mp.category_key AS pc, lower(trim(k)) AS kw
        FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
        WHERE mp.process_key = ANY(procs)
    ),
    win AS (  -- the winning category per keyword, or NULL when no vocabulary term is present
        SELECT k.process_key, k.pc, k.kw,
            (SELECT ct.category_key
               FROM category_term ct
               JOIN activity_category ac ON ac.category_key = ct.category_key
              WHERE k.kw ~ ('\y' || lower(ct.term) || '\y')
              GROUP BY ct.category_key, ac.precedence
              ORDER BY sum(ct.weight) DESC, ac.precedence ASC
              LIMIT 1) AS win_cat
        FROM kws k
    ),
    live AS (  -- keywords that would actually resolve: right (or dropped) category AND a subject present
        SELECT DISTINCT w.process_key
        FROM win w
        WHERE (w.win_cat IS NULL OR w.win_cat = w.pc)
          AND EXISTS (SELECT 1 FROM process_subject ps
                       WHERE ps.process_key = w.process_key
                         AND w.kw ~ ('\y' || lower(ps.subject_term) || '\y'))
    )
    SELECT array_length(procs,1) - (SELECT count(*) FROM live WHERE process_key = ANY(procs)) INTO unreachable;

    IF unreachable <> 0 THEN
        RAISE EXCEPTION 'CARRY: % of % completion processes are unreachable through the classifier (no live keyword)', unreachable, array_length(procs,1);
    END IF;
    RAISE NOTICE 'PASS: all five carriers reachable through the real ActivityClassifier rule (V113)';
END $$;

ROLLBACK;
