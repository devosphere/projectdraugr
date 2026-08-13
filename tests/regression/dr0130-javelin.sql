-- Regression: fire-hardened javelin (M1 #132 / #123 cat.7, V126). Read-only.
--
-- Pins the javelin as a real, bare-hand-craftable, hand-held ranged WEAPON: a hand slot, a TECHNIQUE source, a
-- VERIFIED tool_class NULL maker (point hardened in a fire) with real inputs and no mass gain, classifier-reachable.
-- Its confront effect (+25 ranged piercing, and opening the AERIAL gate) is code, exercised by the backend suite.

BEGIN;

DO $$
DECLARE outm int; inm int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='javelin' AND category='WEAPON' AND equippable) THEN
        RAISE EXCEPTION 'JAVELIN: javelin is not an equippable WEAPON'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_equipment_compatibility WHERE item_key='javelin' AND body_position IN ('HAND_LEFT','HAND_RIGHT')) THEN
        RAISE EXCEPTION 'JAVELIN: javelin has no hand slot'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='javelin' AND source_kind='TECHNIQUE') THEN
        RAISE EXCEPTION 'JAVELIN: javelin has no TECHNIQUE source'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_javelin' AND tool_class IS NULL AND requires_fire AND review_state='VERIFIED' AND output_item_key='javelin') THEN
        RAISE EXCEPTION 'JAVELIN: make_javelin is not a VERIFIED bare-hand fire-hardened maker'; END IF;
    IF EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key='make_javelin' AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=i.item_key)) THEN
        RAISE EXCEPTION 'JAVELIN: make_javelin consumes an undefined item'; END IF;
    SELECT (SELECT unit_mass_grams FROM item_definition WHERE item_key='javelin'),
           (SELECT COALESCE(SUM(i.quantity*d.unit_mass_grams),0) FROM material_process_input i JOIN item_definition d ON d.item_key=i.item_key WHERE i.process_key='make_javelin')
      INTO outm, inm;
    IF outm > inm THEN RAISE EXCEPTION 'JAVELIN: javelin outweighs its materials (mass gain)'; END IF;
    RAISE NOTICE 'PASS: javelin is a bare-hand fire-hardened ranged weapon, no mass gain (#132, V126)';
END $$;

DO $$
DECLARE unreachable int;
BEGIN
    WITH kws AS (SELECT mp.category_key pc, lower(trim(k)) kw FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key='make_javelin'),
    win AS (SELECT k.pc, k.kw, (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key=ct.category_key WHERE k.kw ~ ('\y'||lower(ct.term)||'\y') GROUP BY ct.category_key, ac.precedence ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) win_cat FROM kws k),
    live AS (SELECT 1 FROM win w WHERE (w.win_cat IS NULL OR w.win_cat=w.pc) AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key='make_javelin' AND w.kw ~ ('\y'||lower(ps.subject_term)||'\y')))
    SELECT CASE WHEN EXISTS (SELECT 1 FROM live) THEN 0 ELSE 1 END INTO unreachable;
    IF unreachable <> 0 THEN RAISE EXCEPTION 'JAVELIN: make_javelin is unreachable through the classifier'; END IF;
    RAISE NOTICE 'PASS: make_javelin reachable through the real ActivityClassifier rule (V126)';
END $$;

ROLLBACK;
