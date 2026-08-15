-- V127 — make the catalogue Auditor-clean, part 1 of #245: attainability + routability.
--
-- The Persistent State Auditor flagged (and FullTickPlaythroughIntegrationTest asserts against):
--   * 34 item_definitions with no item_source  -> "no way to be obtained"
--   * 5 material_processes whose keywords don't classify to their category -> "unroutable"
-- Both are pinned invariants; canon must satisfy them exactly as RuntimeProcessGate makes AI-authored
-- processes satisfy them. (The 15 mass-balance violations and the craftPrimitiveSpear equip regression
-- are addressed separately — see #245 parts C/D — because each needs per-process judgement.)

-- ── Part A: every catalogued item declares a truthful acquisition source ────────────────────────────
-- A1. Items a material_process already produces are obtained by running that process. Data-driven so it
--     stays correct as the catalogue grows; the item genuinely IS obtained this way (not a token row).
INSERT INTO item_source (item_key, source_kind, detail)
SELECT mp.output_item_key, 'TECHNIQUE', min(mp.process_key)
FROM material_process mp
WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = mp.output_item_key)
GROUP BY mp.output_item_key
ON CONFLICT DO NOTHING;

-- A2. Multi-output byproducts likewise.
INSERT INTO item_source (item_key, source_kind, detail)
SELECT mo.item_key, 'TECHNIQUE', min(mo.process_key)
FROM material_process_output mo
WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = mo.item_key)
GROUP BY mo.item_key
ON CONFLICT DO NOTHING;

-- A3. Items obtained through a CODE craft path (CRAFT_NET / CRAFT_WORKSTATION) register no material_process
--     output, so declare their code-path source explicitly.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
    ('fishing_net',        'CODE', 'CRAFT_NET'),
    ('landing_net',        'CODE', 'CRAFT_NET'),
    ('woodworking_bench',  'CODE', 'CRAFT_WORKSTATION'),
    ('stoneworking_bench', 'CODE', 'CRAFT_WORKSTATION'),
    ('loom',               'CODE', 'CRAFT_WORKSTATION')
ON CONFLICT DO NOTHING;

-- ── Part B: make 5 processes routable through the two-axis rule ──────────────────────────────────────
-- Each already reads naturally but none of its keywords contained a term that classifies to its own
-- category. Add one keyword per process built on an existing category_term (soak/bind/sift/brine — the
-- exact verbs the classifier routes by), so a natural phrasing reaches the process without colliding.
UPDATE material_process SET keywords = keywords || ',soak the spear point in venom'  WHERE process_key='coat_spear'         AND keywords NOT LIKE '%soak the spear point in venom%';
UPDATE material_process SET keywords = keywords || ',bind the reeds into a sheaf'     WHERE process_key='tie_reed_sheaf'     AND keywords NOT LIKE '%bind the reeds into a sheaf%';
UPDATE material_process SET keywords = keywords || ',sift the grain from the chaff'   WHERE process_key='thresh_wild_grain'  AND keywords NOT LIKE '%sift the grain from the chaff%';
UPDATE material_process SET keywords = keywords || ',soak the cloth in the dye'       WHERE process_key='dye_cloth'          AND keywords NOT LIKE '%soak the cloth in the dye%';
UPDATE material_process SET keywords = keywords || ',brine the vegetables'            WHERE process_key='ferment_vegetables' AND keywords NOT LIKE '%brine the vegetables%';
