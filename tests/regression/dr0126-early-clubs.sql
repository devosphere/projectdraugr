-- Regression: early blunt weapons — clubs (M1 #126 / #123 cat.7, V124). Read-only.
--
-- Pins the two clubs as real, bare-hand-craftable, hand-held WEAPON items with a hand slot, a TECHNIQUE source,
-- VERIFIED tool_class NULL makers with real inputs and no mass gain, classifier-reachable. Their +22 blunt
-- capability in confront (and that the existing stone_maul/stone_hammer join the same tier) is code, exercised by
-- the backend suite.

BEGIN;

DO $$
DECLARE
    clubs text[] := ARRAY['wooden_club','stone_club'];
    procs text[] := ARRAY['make_wooden_club','make_stone_club'];
    it text; pk text; outm int; inm int;
BEGIN
    FOREACH it IN ARRAY clubs LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key=it AND category='WEAPON' AND equippable) THEN
            RAISE EXCEPTION 'CLUB: % is not an equippable WEAPON', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_equipment_compatibility WHERE item_key=it AND body_position IN ('HAND_LEFT','HAND_RIGHT')) THEN
            RAISE EXCEPTION 'CLUB: % has no hand slot', it; END IF;
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key=it AND source_kind='TECHNIQUE') THEN
            RAISE EXCEPTION 'CLUB: % has no TECHNIQUE source', it; END IF;
    END LOOP;
    FOREACH pk IN ARRAY procs LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key=pk AND tool_class IS NULL AND review_state='VERIFIED') THEN
            RAISE EXCEPTION 'CLUB: maker % is not VERIFIED bare-hand', pk; END IF;
        IF EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key=pk AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=i.item_key)) THEN
            RAISE EXCEPTION 'CLUB: maker % consumes an undefined item', pk; END IF;
        SELECT od.unit_mass_grams*mp.output_max,
               (SELECT COALESCE(SUM(i.quantity*d.unit_mass_grams),0) FROM material_process_input i JOIN item_definition d ON d.item_key=i.item_key WHERE i.process_key=pk)
          INTO outm, inm FROM material_process mp JOIN item_definition od ON od.item_key=mp.output_item_key WHERE mp.process_key=pk;
        IF outm > inm THEN RAISE EXCEPTION 'CLUB: maker % outweighs its materials (mass gain)', pk; END IF;
    END LOOP;
    -- the existing heavy striking tools that the blunt tier also enlists must still exist
    IF (SELECT count(*) FROM item_definition WHERE item_key IN ('stone_maul','stone_hammer')) < 2 THEN
        RAISE EXCEPTION 'CLUB: stone_maul/stone_hammer (blunt-tier weapons) missing'; END IF;
    RAISE NOTICE 'PASS: two clubs bare-hand-craftable hand weapons, no mass gain; maul/hammer present for the blunt tier (#126, V124)';
END $$;

DO $$
DECLARE procs text[] := ARRAY['make_wooden_club','make_stone_club']; unreachable int;
BEGIN
    WITH kws AS (SELECT mp.process_key, mp.category_key pc, lower(trim(k)) kw FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key = ANY(procs)),
    win AS (SELECT k.process_key, k.pc, k.kw, (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key=ct.category_key WHERE k.kw ~ ('\y'||lower(ct.term)||'\y') GROUP BY ct.category_key, ac.precedence ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) win_cat FROM kws k),
    live AS (SELECT DISTINCT w.process_key FROM win w WHERE (w.win_cat IS NULL OR w.win_cat=w.pc) AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key=w.process_key AND w.kw ~ ('\y'||lower(ps.subject_term)||'\y')))
    SELECT array_length(procs,1) - (SELECT count(*) FROM live WHERE process_key = ANY(procs)) INTO unreachable;
    IF unreachable <> 0 THEN RAISE EXCEPTION 'CLUB: % of % club makers unreachable through the classifier', unreachable, array_length(procs,1); END IF;
    RAISE NOTICE 'PASS: both club makers reachable through the real ActivityClassifier rule (V124)';
END $$;

ROLLBACK;
