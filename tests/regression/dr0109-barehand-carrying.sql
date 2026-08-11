-- Regression: bare-hand carrying objects (V109, M1 #194/#191). Read-only.
BEGIN;
DO $$
DECLARE p text;
BEGIN
    -- Every carrying process is bare-hand (no tool), and its output is a functional container.
    FOREACH p IN ARRAY ARRAY['make_leaf_wrap','fold_bark_cup','fold_bark_container','make_grass_sling','weave_reed_pouch'] LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key=p AND tool_class IS NULL) THEN
            RAISE EXCEPTION 'REGRESSION: % must be bare-hand (tool_class NULL)', p; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process mp JOIN container_capacity_default c ON c.item_key=mp.output_item_key WHERE mp.process_key=p) THEN
            RAISE EXCEPTION 'REGRESSION: % output is not a functional container', p; END IF;
    END LOOP;
    -- Improvised = low capacity: none exceeds a modest cap (< the 2500ml fired bowl).
    IF EXISTS (SELECT 1 FROM container_capacity_default WHERE item_key IN ('leaf_wrap','folded_bark_cup','bark_fold_container','grass_bundle_sling','reed_pouch') AND max_mass_grams > 1500) THEN
        RAISE EXCEPTION 'REGRESSION: a bare-hand container is not low-capacity'; END IF;
    -- Hand-gathered materials are obtainable.
    IF (SELECT count(*) FROM item_source WHERE item_key IN ('big_leaf','dry_grass_bundle') AND source_kind='FLORA_DROP') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: big_leaf/dry_grass not hand-gatherable'; END IF;
    RAISE NOTICE 'PASS: 5 bare-hand carrying objects — no tool, functional, low-capacity (V109 #194/#191)';
END $$;
ROLLBACK;
