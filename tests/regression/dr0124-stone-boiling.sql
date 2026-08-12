-- Regression: stone-boiling water treatment (M1 #125 / #123 cat.2, V122). Read-only.
--
-- Pins the boiling_stone_set as a real, bare-hand-craftable implement and the data the water-boil gate depends on:
-- a set of hot stones from any hard cobble, and a non-empty set of fireproof vessels for the direct-boil path.
-- The BOIL_WATER gate itself (fireproof vessel OR stones, else grounded) is code, exercised by the backend suite.

BEGIN;

DO $$
DECLARE bad text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='boiling_stone_set') THEN
        RAISE EXCEPTION 'BOIL: boiling_stone_set is not defined'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='boiling_stone_set' AND source_kind='TECHNIQUE') THEN
        RAISE EXCEPTION 'BOIL: boiling_stone_set has no TECHNIQUE source'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_boiling_stones' AND tool_class IS NULL AND review_state='VERIFIED' AND output_item_key='boiling_stone_set') THEN
        RAISE EXCEPTION 'BOIL: make_boiling_stones is not a VERIFIED bare-hand maker'; END IF;
    IF EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key='make_boiling_stones'
                 AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=g.item_key)) THEN
        RAISE EXCEPTION 'BOIL: make_boiling_stones consumes an undefined cobble'; END IF;
    -- no mass gain (output <= heaviest group option)
    SELECT CASE WHEN od.unit_mass_grams*mp.output_max >
             (SELECT MAX(gm) FROM (SELECT SUM(g.quantity*d.unit_mass_grams) gm FROM material_process_input_group g JOIN item_definition d ON d.item_key=g.item_key WHERE g.process_key='make_boiling_stones' GROUP BY g.group_name, g.item_key) t)
           THEN 'make_boiling_stones' END INTO bad
      FROM material_process mp JOIN item_definition od ON od.item_key=mp.output_item_key WHERE mp.process_key='make_boiling_stones';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'BOIL: boiling stone set outweighs its cobbles (mass gain)'; END IF;
    -- the direct-boil path needs at least one fireproof vessel to exist in the catalogue
    IF (SELECT count(*) FROM item_definition WHERE item_key IN ('clay_pot','clay_jar','fired_bowl','fired_cup','clay_water_filter','soapstone_bowl')) = 0 THEN
        RAISE EXCEPTION 'BOIL: no fireproof vessel exists for the direct-boil path'; END IF;
    RAISE NOTICE 'PASS: stone-boiling set craftable bare-hand (no mass gain); fireproof vessels exist for direct boil (#125, V122)';
END $$;

-- Classifier reachability for make_boiling_stones.
DO $$
DECLARE unreachable int;
BEGIN
    WITH kws AS (SELECT mp.category_key pc, lower(trim(k)) kw FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key='make_boiling_stones'),
    win AS (SELECT k.pc, k.kw, (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key=ct.category_key WHERE k.kw ~ ('\y'||lower(ct.term)||'\y') GROUP BY ct.category_key, ac.precedence ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) win_cat FROM kws k),
    live AS (SELECT 1 FROM win w WHERE (w.win_cat IS NULL OR w.win_cat=w.pc) AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key='make_boiling_stones' AND w.kw ~ ('\y'||lower(ps.subject_term)||'\y')))
    SELECT CASE WHEN EXISTS (SELECT 1 FROM live) THEN 0 ELSE 1 END INTO unreachable;
    IF unreachable <> 0 THEN RAISE EXCEPTION 'BOIL: make_boiling_stones is unreachable through the classifier'; END IF;
    RAISE NOTICE 'PASS: make_boiling_stones reachable through the real ActivityClassifier rule (V122)';
END $$;

ROLLBACK;
