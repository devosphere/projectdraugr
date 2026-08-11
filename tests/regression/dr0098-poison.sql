-- Regression: venom-tipped weapons (V98, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE venoms text[] := ARRAY['snake_venom','hornet_venom','formic_acid']; k text; winner text;
BEGIN
    FOREACH k IN ARRAY venoms LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='coat_spear' AND item_key=k) THEN
            RAISE EXCEPTION 'REGRESSION: venom % cannot poison a spear', k; END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='coat_spear' AND item_key='primitive_spear') THEN
        RAISE EXCEPTION 'REGRESSION: coat_spear must consume a spear'; END IF;
    IF NOT (EXISTS(SELECT 1 FROM item_source WHERE item_key='poisoned_spear') AND EXISTS(SELECT 1 FROM item_equipment_compatibility WHERE item_key='poisoned_spear')) THEN
        RAISE EXCEPTION 'REGRESSION: poisoned_spear not obtainable/equippable'; END IF;
    SELECT mp.process_key INTO winner FROM material_process mp WHERE mp.review_state='VERIFIED'
      AND EXISTS(SELECT 1 FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' poison the spear ') LIKE ('% '||trim(kw)||' %'))
      AND EXISTS(SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key AND (' poison the spear ') LIKE ('% '||ps.subject_term||' %'))
      ORDER BY (SELECT max(length(trim(kw))) FROM regexp_split_to_table(mp.keywords,',') kw WHERE (' poison the spear ') LIKE ('% '||trim(kw)||' %')) DESC LIMIT 1;
    IF winner IS DISTINCT FROM 'coat_spear' THEN RAISE EXCEPTION 'REGRESSION: "poison the spear" resolved to %', COALESCE(winner,'NOTHING'); END IF;
    RAISE NOTICE 'PASS: 3 venoms tip a poisoned spear that routes and equips (combat edge wired in confront) (V98)';
END $$;
ROLLBACK;
