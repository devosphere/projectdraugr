-- Regression: sling — thrown-stone multiplier (M1 #126 / #123 cat.7, V125). Read-only.
--
-- Pins the sling as a real, bare-hand-craftable WEAPON with a hand slot, a TECHNIQUE source, a VERIFIED
-- tool_class NULL maker with real inputs and no mass gain, classifier-reachable. Its confront effect (amplifying
-- the thrown-stone term, and doing nothing without stones) is code, exercised by the backend suite.

BEGIN;

DO $$
DECLARE outm int; inm int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='sling' AND category='WEAPON' AND equippable) THEN
        RAISE EXCEPTION 'SLING: sling is not an equippable WEAPON'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_equipment_compatibility WHERE item_key='sling' AND body_position IN ('HAND_LEFT','HAND_RIGHT')) THEN
        RAISE EXCEPTION 'SLING: sling has no hand slot'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='sling' AND source_kind='TECHNIQUE') THEN
        RAISE EXCEPTION 'SLING: sling has no TECHNIQUE source'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_sling' AND tool_class IS NULL AND review_state='VERIFIED' AND output_item_key='sling') THEN
        RAISE EXCEPTION 'SLING: make_sling is not a VERIFIED bare-hand maker'; END IF;
    IF EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key='make_sling' AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=i.item_key)) THEN
        RAISE EXCEPTION 'SLING: make_sling consumes an undefined item'; END IF;
    SELECT (SELECT unit_mass_grams FROM item_definition WHERE item_key='sling'),
           (SELECT COALESCE(SUM(i.quantity*d.unit_mass_grams),0) FROM material_process_input i JOIN item_definition d ON d.item_key=i.item_key WHERE i.process_key='make_sling')
      INTO outm, inm;
    IF outm > inm THEN RAISE EXCEPTION 'SLING: sling outweighs its materials (mass gain)'; END IF;
    RAISE NOTICE 'PASS: sling is a bare-hand-craftable hand weapon, no mass gain (#126, V125)';
END $$;

DO $$
DECLARE unreachable int;
BEGIN
    WITH kws AS (SELECT mp.category_key pc, lower(trim(k)) kw FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key='make_sling'),
    win AS (SELECT k.pc, k.kw, (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key=ct.category_key WHERE k.kw ~ ('\y'||lower(ct.term)||'\y') GROUP BY ct.category_key, ac.precedence ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) win_cat FROM kws k),
    live AS (SELECT 1 FROM win w WHERE (w.win_cat IS NULL OR w.win_cat=w.pc) AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key='make_sling' AND w.kw ~ ('\y'||lower(ps.subject_term)||'\y')))
    SELECT CASE WHEN EXISTS (SELECT 1 FROM live) THEN 0 ELSE 1 END INTO unreachable;
    IF unreachable <> 0 THEN RAISE EXCEPTION 'SLING: make_sling is unreachable through the classifier'; END IF;
    RAISE NOTICE 'PASS: make_sling reachable through the real ActivityClassifier rule (V125)';
END $$;

ROLLBACK;
