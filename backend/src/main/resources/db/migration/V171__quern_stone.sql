-- V171: the quern-stone — dressed stone that grinds a finer flour (EPIC #180 / #183 stone shaping; and #45 food).
--
-- Grain is ground to flour between two stones (grind_flour, V105) — by hand, coarse and slow. A quern is dressed
-- stone made for the work: a bedstone and a runner, cut true and dished so grain feeds even between them and comes
-- out fine. Grinding at a quern gives a finer flour than pounding grain on a rough stone, and a finer flour bakes a
-- better, more nourishing bread (food quality carries into nourishment, #271). This gives dressed stone a use beyond
-- the wall and gives the grain chain a real tool to earn.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('quern_stone', 'Quern-stone', 'TOOL', 2200, 1400, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('quern_stone', 'TECHNIQUE', 'a broad stone slab dressed into a bedstone and runner for grinding grain')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Dressed from a stone slab. 'dress'/'shape' are PROCESS terms (V57); STRIKING work with hammer and chisel.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('dress_quern', 'Dress a quern-stone', 'quern_stone', 1,1, 'STRIKING', FALSE, FALSE, 120, 'stoneworking', 'PROCESS',
 'dress a quern,dress a quern stone,shape a quern,shape a quern stone,peck a quern,cut a quern stone',
 'You dress a broad stone slab down into a quern — a bedstone and a runner, both cut true and the faces pecked so grain feeds even between them and grinds fine.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dress_quern', 'stone_slab', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('dress_quern','quern'), ('dress_quern','stone')
ON CONFLICT DO NOTHING;

-- The quern is the workstation of grinding: at one, the flour comes out finer (a grade lift, not more mass), and a
-- finer flour nourishes better. Grinding still works without one — just coarser. Station only eases, never gates.
UPDATE material_process SET station_kind = 'quern_stone' WHERE process_key = 'grind_flour';
