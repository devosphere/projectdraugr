-- V145: copper axe — a metal tool that fells and fights (EPIC #180 heavy industry / #184 copper-object chains).
--
-- The second copper object, building on V144's ore -> smelt -> ingot chain. A copper axe is forged from two
-- ingots; it is a working axe (fells trees, serves the axe-gated crafts) AND a real weapon that keeps a keener
-- edge than any knapped stone — so in a fight it bites deeper than a stone axe. This gives the smelted metal a
-- terminal use in the world beyond the carving chisel: felling and self-defence, the two things a first-era
-- Chronicle most needs an axe for. (Reachability, felling, and combat readers are wired in Java.)

-- 1. The item and how it enters the world (item_source keeps the Auditor's reachability invariant satisfied).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('copper_axe', 'Copper axe', 'TOOL', 950, 700, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('copper_axe', 'TECHNIQUE', 'forged and hafted from copper ingots')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- 2. Held in the hand like any hafted tool or weapon.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('copper_axe','HAND_RIGHT','CARRIED'), ('copper_axe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 3. Forge it from two ingots over a fire with a striking tool. 'forge' already classifies to CRAFT (V144), so it
--    routes by an ordinary sentence; the 'axe' subject keeps it distinct from the copper chisel forge.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_copper_axe', 'Forge a copper axe', 'copper_axe', 1,1, 'STRIKING', TRUE, FALSE, 80, 'tools', 'CRAFT',
 'forge a copper axe,forge copper axe,hammer out a copper axe,work the copper into an axe,cast a copper axe,copper axe',
 'You cast and hammer the copper into a broad axe-head, annealing it as it work-hardens, and haft it tight — an edge that bites where stone would only bruise, and holds it far longer.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_copper_axe', 'copper_ingot', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_copper_axe','copper'), ('forge_copper_axe','axe')
ON CONFLICT DO NOTHING;
