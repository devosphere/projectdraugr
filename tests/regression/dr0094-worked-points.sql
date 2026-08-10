-- Regression: animal hard parts carved into points (V94, M1 #75).
-- Pins that horn/tusk/fang/claw/talon/bone/stinger/thorn all carve into a worked_point, that "carve a point"
-- routes to carve_point, and that a worked point tips an arrow. Read-only; rolls back.
BEGIN;
DO $$
DECLARE parts text[] := ARRAY['aurochs_horn','boar_tusk','dire_wolf_fang','predator_fang','predator_claw','raptor_talon','harpy_talon','roc_talon','wyvern_fang','troll_bone','giant_stinger','wild_rose_thorn','hawthorn_thorn']; k text; winner text;
BEGIN
    FOREACH k IN ARRAY parts LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='carve_point' AND item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: hard part % does not carve into a point', k; END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='worked_point') THEN RAISE EXCEPTION 'REGRESSION: worked_point not obtainable'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='assemble_arrows' AND group_name='point' AND item_key='worked_point') THEN
        RAISE EXCEPTION 'REGRESSION: a worked point cannot tip an arrow'; END IF;
    SELECT mp.process_key INTO winner FROM material_process mp WHERE mp.review_state='VERIFIED' AND mp.category_key='CRAFT'
      AND EXISTS(SELECT 1 FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' carve a point ') LIKE ('% '||trim(kw)||' %'))
      AND EXISTS(SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key AND (' carve a point ') LIKE ('% '||ps.subject_term||' %'))
      ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' carve a point ') LIKE ('% '||trim(kw)||' %')) DESC, mp.process_key LIMIT 1;
    IF winner IS DISTINCT FROM 'carve_point' THEN RAISE EXCEPTION 'REGRESSION: "carve a point" resolved to % (expected carve_point)', COALESCE(winner,'NOTHING'); END IF;
    RAISE NOTICE 'PASS: 13 animal hard parts carve into a worked_point that tips arrows (V94 #75)';
END $$;
ROLLBACK;
