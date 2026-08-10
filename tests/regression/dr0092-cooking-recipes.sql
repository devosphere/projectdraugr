-- Regression: cooking recipes (V92, M1 #75).
--
-- Real-world-logic: food ingredients must be cookable into specific dishes, not only eaten raw. Pins that seven
-- dishes are obtainable, each needs a fire, each consumes real ingredients (fixed and/or grouped), and each
-- named phrasing resolves to its recipe — and that the "cook" vocabulary fix (INHABIT→PROCESS) is in place so
-- "cook a stew" routes. Read-only; rolls back.

BEGIN;

DO $$
DECLARE
    dishes text[] := ARRAY['root_vegetable_stew','grain_porridge','acorn_flatbread','herbal_infusion','cooked_mushrooms','trail_cake','berry_compote'];
    procs text[] := ARRAY['cook_root_stew','cook_porridge','bake_flatbread','brew_infusion','cook_mushrooms','bake_trail_cake','stew_compote'];
    k text; p text; n int; winner text;
    cases text[][] := ARRAY[
        ARRAY['cook a vegetable stew','cook_root_stew'],
        ARRAY['boil grain porridge','cook_porridge'],
        ARRAY['bake acorn flatbread','bake_flatbread'],
        ARRAY['brew a herbal tea','brew_infusion'],
        ARRAY['cook the mushrooms','cook_mushrooms'],
        ARRAY['bake a nut cake','bake_trail_cake'],
        ARRAY['stew the berries into a compote','stew_compote']
    ];
    c text[];
BEGIN
    -- 0. The vocabulary fix: cook is PROCESS work now, not INHABIT.
    IF NOT EXISTS (SELECT 1 FROM category_term WHERE term='cook' AND category_key='PROCESS') THEN
        RAISE EXCEPTION 'REGRESSION: cook must be categorised PROCESS';
    END IF;

    -- 1. Every dish is an obtainable FOOD (edible via EAT once cooked).
    FOREACH k IN ARRAY dishes LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key=k AND category='FOOD') THEN
            RAISE EXCEPTION 'REGRESSION: dish % is not FOOD', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: dish % is not obtainable', k;
        END IF;
    END LOOP;

    -- 2. Each recipe needs a fire and consumes at least one real ingredient (fixed or grouped), all obtainable.
    FOREACH p IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key=p AND requires_fire) THEN
            RAISE EXCEPTION 'REGRESSION: recipe % does not require a fire', p;
        END IF;
        SELECT count(*) INTO n FROM (
            SELECT item_key FROM material_process_input WHERE process_key=p
            UNION ALL SELECT item_key FROM material_process_input_group WHERE process_key=p) x;
        IF n = 0 THEN RAISE EXCEPTION 'REGRESSION: recipe % consumes no ingredients', p; END IF;
        IF EXISTS (SELECT item_key FROM material_process_input WHERE process_key=p
                   UNION SELECT item_key FROM material_process_input_group WHERE process_key=p
                   EXCEPT SELECT item_key FROM item_source) THEN
            RAISE EXCEPTION 'REGRESSION: recipe % has an unobtainable ingredient', p;
        END IF;
    END LOOP;

    -- 3. Each named phrasing resolves to its recipe (PROCESS category, subject-gated).
    FOREACH c SLICE 1 IN ARRAY cases LOOP
        SELECT mp.process_key INTO winner
        FROM material_process mp
        WHERE mp.review_state='VERIFIED' AND mp.category_key='PROCESS'
          AND EXISTS (SELECT 1 FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' '||c[1]||' ') LIKE ('% '||trim(kw)||' %'))
          AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key AND (' '||c[1]||' ') LIKE ('% '||ps.subject_term||' %'))
        ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' '||c[1]||' ') LIKE ('% '||trim(kw)||' %')) DESC, mp.process_key ASC
        LIMIT 1;
        IF winner IS DISTINCT FROM c[2] THEN
            RAISE EXCEPTION 'REGRESSION: "%" resolved to % (expected %)', c[1], COALESCE(winner,'NOTHING'), c[2];
        END IF;
    END LOOP;

    RAISE NOTICE 'PASS: 7 cooking recipes — obtainable dishes, fire + real ingredients, each named dish routes, cook fixed to PROCESS (V92 #75)';
END $$;

ROLLBACK;
