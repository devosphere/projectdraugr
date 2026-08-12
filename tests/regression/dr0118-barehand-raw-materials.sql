-- Regression: bare-hand raw materials (M1 #192, EPIC #191, V116). Read-only.
--
-- Pins that each of the ten new materials is BOTH obtainable bare-hand (a flora_drop off a NULL-tool flora, or a
-- NULL-tool mineral row) AND consumed (a feeder process input, or — for the three tinder materials — craftTinder,
-- which is code and documented here, mirroring the orphan-audit's code-path exclusions). Also pins that the seven
-- feeder processes are VERIFIED bare-hand with real inputs and are reachable through the real classifier. A break
-- here means a material became an orphan (obtainable but useless) or unobtainable (useful but uncollectable), or a
-- feeder keyword went dead.

BEGIN;

DO $$
DECLARE
    mats  text[] := ARRAY['green_grass_bundle','straight_reed','loose_bark_strip','birch_bark_shed','fallen_leaf_litter','dry_twig','shed_feather','shed_fur_tuft','clay_lump_surface','silt_bundle'];
    procs text[] := ARRAY['dry_grass','ready_reed_shaft','ret_bark_strip','flatten_bark','dress_feathers','clean_surface_clay','temper_clay_with_silt'];
    -- materials whose only consumer is craftTinder (code), so no material_process_input is expected
    code_tinder text[] := ARRAY['fallen_leaf_litter','dry_twig','shed_fur_tuft'];
    it text; pk text;
BEGIN
    FOREACH it IN ARRAY mats LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it) THEN
            RAISE EXCEPTION 'RAW: material % is not defined', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it) THEN
            RAISE EXCEPTION 'RAW: material % has no item_source (undocumented collection)', it; END IF;
        -- obtainable: a bare-hand flora_drop OR a NULL-tool mineral row
        IF NOT (EXISTS (SELECT 1 FROM flora_drop fd JOIN flora_definition f ON f.flora_key = fd.flora_key
                        WHERE fd.item_key = it AND f.tool_required IS NULL)
             OR EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key = it AND tool_required IS NULL)) THEN
            RAISE EXCEPTION 'RAW: material % is not obtainable bare-hand (no NULL-tool flora_drop or mineral)', it; END IF;
        -- consumed: a material_process_input, OR one of the documented code-tinder materials
        IF NOT (EXISTS (SELECT 1 FROM material_process_input WHERE item_key = it)
             OR EXISTS (SELECT 1 FROM material_process_input_group WHERE item_key = it)
             OR it = ANY(code_tinder)) THEN
            RAISE EXCEPTION 'RAW: material % is an orphan (obtainable but consumed by nothing)', it; END IF;
    END LOOP;

    FOREACH pk IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND tool_class IS NULL AND review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'RAW: feeder process % is not a VERIFIED bare-hand process', pk; END IF;
        IF EXISTS (SELECT 1 FROM material_process_input mpi WHERE mpi.process_key = pk
                     AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = mpi.item_key)) THEN
            RAISE EXCEPTION 'RAW: feeder process % consumes an undefined item', pk; END IF;
        -- each feeder's output is a real item the wider catalogue already uses
        IF NOT EXISTS (SELECT 1 FROM material_process mp JOIN item_definition d ON d.item_key = mp.output_item_key WHERE mp.process_key = pk) THEN
            RAISE EXCEPTION 'RAW: feeder process % has an undefined output', pk; END IF;
    END LOOP;

    RAISE NOTICE 'PASS: ten bare-hand materials all obtainable + consumed; seven feeder processes verified bare-hand (#192, V116)';
END $$;

-- Classifier reachability for the seven feeders.
DO $$
DECLARE
    procs text[] := ARRAY['dry_grass','ready_reed_shaft','ret_bark_strip','flatten_bark','dress_feathers','clean_surface_clay','temper_clay_with_silt'];
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
        RAISE EXCEPTION 'RAW: % of % feeder processes are unreachable through the classifier', unreachable, array_length(procs,1);
    END IF;
    RAISE NOTICE 'PASS: all seven feeder processes reachable through the real ActivityClassifier rule (V116)';
END $$;

ROLLBACK;
