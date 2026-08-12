-- Regression: bare-hand clay beads/seals (M1 #197, EPIC #191, V115). Read-only.
--
-- Pins the two new shaping primitives and, critically, the unfired!=durable gate the acceptance demands: a bare
-- hand shapes fragile unfired beads/seals, but only firing (requires_fire, the separate fuel chain) yields a
-- durable trinket, and only a fired trinket can be strung into the wearable cord. Also pins classifier
-- reachability so no shaping keyword is dead.

BEGIN;

DO $$
DECLARE
    items text[] := ARRAY['clay_bead','clay_seal','fired_clay_trinket','clay_trinket_cord'];
    procs text[] := ARRAY['shape_clay_bead','press_clay_seal','fire_clay_trinkets','thread_trinket_cord'];
    it text; pk text;
BEGIN
    -- (A) Items exist with a hand source.
    FOREACH it IN ARRAY items LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = it) THEN
            RAISE EXCEPTION 'CLAY: item % is not defined', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = it AND source_kind = 'TECHNIQUE') THEN
            RAISE EXCEPTION 'CLAY: item % has no TECHNIQUE source', it; END IF;
    END LOOP;

    -- (B) Shaping is bare-hand; firing needs a fire; each process has real inputs.
    IF EXISTS (SELECT 1 FROM material_process WHERE process_key IN ('shape_clay_bead','press_clay_seal','thread_trinket_cord') AND tool_class IS NOT NULL) THEN
        RAISE EXCEPTION 'CLAY: a bare-hand shaping/threading step wrongly requires a tool'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = 'fire_clay_trinkets' AND requires_fire) THEN
        RAISE EXCEPTION 'CLAY: firing must require a fire (the separate fuel chain)'; END IF;
    FOREACH pk IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND review_state = 'VERIFIED') THEN
            RAISE EXCEPTION 'CLAY: process % is not VERIFIED', pk; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = pk)
           AND NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key = pk) THEN
            RAISE EXCEPTION 'CLAY: process % has no inputs', pk; END IF;
    END LOOP;

    -- (C) unfired != durable. Firing consumes an UNFIRED bead/seal; the wearable cord consumes only FIRED trinkets,
    --     never an unfired bead directly — so a bare hand cannot reach a durable ornament without the fire chain.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='fire_clay_trinkets' AND item_key='clay_bead')
       OR NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='fire_clay_trinkets' AND item_key='clay_seal') THEN
        RAISE EXCEPTION 'CLAY: firing must take the unfired bead and seal'; END IF;
    IF EXISTS (SELECT 1 FROM material_process_input WHERE process_key='thread_trinket_cord' AND item_key IN ('clay_bead','clay_seal')) THEN
        RAISE EXCEPTION 'CLAY: the wearable cord must not consume unfired clay directly (firing is required first)'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='thread_trinket_cord' AND item_key='fired_clay_trinket') THEN
        RAISE EXCEPTION 'CLAY: the wearable cord must be strung from fired trinkets'; END IF;
    -- The cord is genuinely wearable (equippable with a body slot), not an orphan.
    IF NOT EXISTS (SELECT 1 FROM item_definition d JOIN item_equipment_compatibility c ON c.item_key=d.item_key
                   WHERE d.item_key='clay_trinket_cord' AND d.equippable) THEN
        RAISE EXCEPTION 'CLAY: clay_trinket_cord must be equippable with a body slot (functional adornment)'; END IF;

    RAISE NOTICE 'PASS: bare-hand clay beads/seals shape fragile+unfired; firing (separate chain) yields durable trinkets; only fired trinkets string into a wearable cord (#197, V115)';
END $$;

-- (D) Classifier reachability for the four processes.
DO $$
DECLARE
    procs text[] := ARRAY['shape_clay_bead','press_clay_seal','fire_clay_trinkets','thread_trinket_cord'];
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
        RAISE EXCEPTION 'CLAY: % of % clay processes are unreachable through the classifier', unreachable, array_length(procs,1);
    END IF;
    RAISE NOTICE 'PASS: all four clay-ornament processes reachable through the real ActivityClassifier rule (V115)';
END $$;

ROLLBACK;
