-- V476: the willow gives its wood (#879).
--
-- The willow is catalogued as a TREE (V39) whose felling wants an AXE_CLASS tool, and a wetland survey reports
-- "a full stand of timber" — but its only drop was willow_branch with no tool condition, and fellTree takes the
-- AXE_CLASS drop. So "fell a willow" with a sound hatchet answered "The tree stands but offers nothing your axe
-- can shape", while the same hatchet felled an oak two tiles east. Every other felling tree (oak, birch, pine,
-- ash, maple, spruce) has a log; the willow is given one, and it joins the same milling groups as the other six
-- logs (V52, V57, V91), so a felled willow becomes planks, timber, a notched log, a stave or a trough.
--
-- Mass: willow is lighter, softer wood than birch (~450 vs ~650 kg/m3), so a log of the same mass is bulkier.
-- It is held at birch's 6000 g — the lightest log already in every one of these groups — so each milling step
-- stays mass-honest exactly as it does for birch (largest output 5600 g, split_planks / notch_log).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable)
VALUES ('willow_log', 'Willow Log', 'MATERIAL', 6000, 50000, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season, tool_condition)
SELECT 'willow', 'willow_log', 1, 3, NULL, 'AXE_CLASS'
WHERE NOT EXISTS (SELECT 1 FROM flora_drop WHERE flora_key = 'willow' AND item_key = 'willow_log');

INSERT INTO item_source (item_key, source_kind, detail)
SELECT 'willow_log', source_kind, detail FROM item_source WHERE item_key = 'birch_log'
ON CONFLICT DO NOTHING;

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity)
SELECT g.process_key, g.group_name, 'willow_log', g.quantity
FROM material_process_input_group g
WHERE g.item_key = 'birch_log'
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
