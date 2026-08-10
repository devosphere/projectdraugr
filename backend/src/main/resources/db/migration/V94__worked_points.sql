-- V94: animal hard parts carved into points (M1 #75, EPIC #45/#54).
--
-- Real-world-logic ([[feedback_real_world_simulation]]), animal-parts audit (Vertical D). Horn, tusk, fang,
-- claw, talon, bone, a giant stinger, and stout thorns are exactly what a first-era hunter carves into points —
-- for arrows, and for barbs and awls. Every one of them was an orphan: gathered off a kill, then useless.
--
-- This closes them through their true use: a single `carve_point` process takes any one of them (input GROUP)
-- and works it down to a `worked_point`, and a worked point joins the arrow head slot alongside knapped stone
-- and bone — so a boar's tusk or a wolf's fang can tip an arrow, as it should. Needs a cutting edge.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('worked_point', 'Worked point', 'MATERIAL', 40, 30, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('worked_point', 'TECHNIQUE', 'carved and ground from horn, tusk, tooth, bone, or thorn')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_point','Carve a point','worked_point',1,2,'CUTTING',FALSE,FALSE,30,'items','CRAFT','carve a point,carve a bone point,carve an arrow point,worked point,bone point,carve a barb,make a point,shape a point,grind a point', 'You work the hard stock down against stone, grinding and carving until it comes to a keen, barbed point.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Any one hard part yields a point (the big ones yield two).
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('carve_point','stock','aurochs_horn',1),('carve_point','stock','boar_tusk',1),('carve_point','stock','dire_wolf_fang',1),
('carve_point','stock','predator_fang',1),('carve_point','stock','predator_claw',1),('carve_point','stock','raptor_talon',1),
('carve_point','stock','harpy_talon',1),('carve_point','stock','roc_talon',1),('carve_point','stock','wyvern_fang',1),
('carve_point','stock','troll_bone',1),('carve_point','stock','giant_stinger',1),
('carve_point','stock','wild_rose_thorn',2),('carve_point','stock','hawthorn_thorn',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_point','point'),('carve_point','barb')
ON CONFLICT DO NOTHING;

-- A worked point tips an arrow, alongside knapped stone and bone.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('assemble_arrows','point','worked_point',3)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
