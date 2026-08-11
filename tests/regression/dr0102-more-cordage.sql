-- Regression: bramble/cattail/bulrush cordage (V102, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE cases text[][] := ARRAY[ARRAY['twist the bramble into cordage','twist_bramble_cordage'],ARRAY['twist cattail into cordage','twist_cattail_cordage'],ARRAY['twist the bulrush into cordage','twist_bulrush_cordage']]; c text[]; k text; winner text;
BEGIN
    FOREACH k IN ARRAY ARRAY['bramble_cane','cattail_leaf','bulrush_bundle'] LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=k AND source_kind='FLORA_DROP') THEN RAISE EXCEPTION 'REGRESSION: % no source', k; END IF;
        IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE item_key=k) THEN RAISE EXCEPTION 'REGRESSION: % unused', k; END IF;
    END LOOP;
    FOREACH c SLICE 1 IN ARRAY cases LOOP
        SELECT mp.process_key INTO winner FROM material_process mp WHERE mp.review_state='VERIFIED' AND mp.category_key='PROCESS'
          AND EXISTS(SELECT 1 FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' '||c[1]||' ') LIKE ('% '||trim(kw)||' %'))
          AND EXISTS(SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key AND (' '||c[1]||' ') LIKE ('% '||ps.subject_term||' %'))
          ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' '||c[1]||' ') LIKE ('% '||trim(kw)||' %')) DESC, mp.process_key LIMIT 1;
        IF winner IS DISTINCT FROM c[2] THEN RAISE EXCEPTION 'REGRESSION: "%" -> % (expected %)', c[1], COALESCE(winner,'NOTHING'), c[2]; END IF;
    END LOOP;
    RAISE NOTICE 'PASS: bramble/cattail/bulrush twist into cordage, each beating the generic (V102 #75)';
END $$;
ROLLBACK;
