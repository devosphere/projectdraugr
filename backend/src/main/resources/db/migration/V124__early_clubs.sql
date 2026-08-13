-- V124: early blunt weapons — clubs (M1 #126 defence / #123 cat.7, EPIC #123).
--
-- The offence companion to V123's shields. A vulnerable Chronicle needs a weapon before a hafted stone spear is
-- possible; a club is the simplest — a heavy stick, or a cobble bound to a handle. Both are bare-hand to make.
-- In confront they add a graded "blunt" capability (+22), below a piercing spear/axe (+35) — a real but lesser
-- edge. The same blunt tier also finally lets the EXISTING stone_maul and stone_hammer (heavy striking tools) be
-- swung as weapons, not just crafting implements. See WildlifeEncounterService.confront.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('wooden_club', 'Wooden club', 'WEAPON', 600,  1200, FALSE, TRUE, 0),
('stone_club',  'Stone club',  'WEAPON', 1400, 1800, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('wooden_club','HAND_RIGHT','ATTACHED'),('wooden_club','HAND_LEFT','ATTACHED'),
('stone_club','HAND_RIGHT','ATTACHED'),('stone_club','HAND_LEFT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('wooden_club', 'TECHNIQUE', 'a heavy branch broken and worked to a club by hand'),
('stone_club',  'TECHNIQUE', 'a cobble hafted to a handle with cordage by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT. "club"/"cudgel" subjects; stone club also carries the piercing-free "stone club".
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_wooden_club','Make a wooden club','wooden_club',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','wooden club,make a wooden club,shape a wooden club,cudgel,heavy club', 'You break a heavy branch to length and work one end to a fat, blunt head — a club that will crack bone.', 'VERIFIED', now()),
('make_stone_club', 'Haft a stone club', 'stone_club', 1,1,NULL,FALSE,FALSE,25,'items','CRAFT','stone club,make a stone club,haft a stone club,stone-headed club', 'You seat a heavy cobble in a split handle and bind it fast with cordage — a stone club that hits far harder than wood alone.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_wooden_club','dry_branch',2),
('make_stone_club','granite_cobble',1),('make_stone_club','dry_branch',1),('make_stone_club','fiber_cordage',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_wooden_club','club'),('make_wooden_club','cudgel'),
('make_stone_club','club'),('make_stone_club','stone club')
ON CONFLICT DO NOTHING;
