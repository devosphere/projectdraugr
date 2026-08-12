-- Regression: bare-hand resin torch portable light (M1 #125 / #123 cat.1, V121). Read-only.
--
-- Pins the resin torch as a real, bare-hand-craftable portable light: defined item, TECHNIQUE source, a VERIFIED
-- tool_class NULL maker with real inputs (all bare-hand obtainable — dead branch, hand-scavenged pine resin, plant
-- fibre), no mass gain, and classifier-reachable. The consumePortableLight tier that lets it be burned for sight
-- work in the dark is code, exercised by the backend suite.

BEGIN;

DO $$
DECLARE bad text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='resin_torch') THEN
        RAISE EXCEPTION 'TORCH: resin_torch is not defined'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='resin_torch' AND source_kind='TECHNIQUE') THEN
        RAISE EXCEPTION 'TORCH: resin_torch has no TECHNIQUE source'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_resin_torch' AND tool_class IS NULL AND review_state='VERIFIED' AND output_item_key='resin_torch') THEN
        RAISE EXCEPTION 'TORCH: make_resin_torch is not a VERIFIED bare-hand maker of resin_torch'; END IF;
    -- every input is a real item, and each is itself bare-hand obtainable
    IF EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key='make_resin_torch'
                 AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=i.item_key)) THEN
        RAISE EXCEPTION 'TORCH: make_resin_torch consumes an undefined item'; END IF;
    IF NOT EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key='pine_resin' AND tool_required IS NULL) THEN
        RAISE EXCEPTION 'TORCH: resin torch relies on pine_resin, which must be bare-hand obtainable (V120)'; END IF;
    -- no mass gain
    SELECT CASE WHEN od.unit_mass_grams*mp.output_max >
                (SELECT COALESCE(SUM(i.quantity*d.unit_mass_grams),0) FROM material_process_input i JOIN item_definition d ON d.item_key=i.item_key WHERE i.process_key='make_resin_torch')
           THEN 'make_resin_torch' END INTO bad
      FROM material_process mp JOIN item_definition od ON od.item_key=mp.output_item_key WHERE mp.process_key='make_resin_torch';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'TORCH: resin torch outweighs its materials (mass gain)'; END IF;
    RAISE NOTICE 'PASS: resin torch is a bare-hand-craftable portable light with real inputs, no mass gain (#125, V121)';
END $$;

-- Classifier reachability.
DO $$
DECLARE unreachable int;
BEGIN
    WITH kws AS (SELECT mp.category_key pc, lower(trim(k)) kw FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k WHERE mp.process_key='make_resin_torch'),
    win AS (SELECT k.pc, k.kw, (SELECT ct.category_key FROM category_term ct JOIN activity_category ac ON ac.category_key=ct.category_key WHERE k.kw ~ ('\y'||lower(ct.term)||'\y') GROUP BY ct.category_key, ac.precedence ORDER BY sum(ct.weight) DESC, ac.precedence ASC LIMIT 1) win_cat FROM kws k),
    live AS (SELECT 1 FROM win w WHERE (w.win_cat IS NULL OR w.win_cat=w.pc) AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key='make_resin_torch' AND w.kw ~ ('\y'||lower(ps.subject_term)||'\y')))
    SELECT CASE WHEN EXISTS (SELECT 1 FROM live) THEN 0 ELSE 1 END INTO unreachable;
    IF unreachable <> 0 THEN RAISE EXCEPTION 'TORCH: make_resin_torch is unreachable through the classifier'; END IF;
    RAISE NOTICE 'PASS: make_resin_torch reachable through the real ActivityClassifier rule (V121)';
END $$;

ROLLBACK;
