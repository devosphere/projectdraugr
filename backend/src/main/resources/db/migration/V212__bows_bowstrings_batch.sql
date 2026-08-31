-- V212 — story #93 catalogue batch 5: self bows and their strings. A bow is only a weapon with a string and an
-- arrow (the story's rule), so this batch builds both: three bowstrings (the string is a real material consumed in
-- assembling a bow) and three bows that carry a weapon_profile BOW role, functional in confront's archery with the
-- arrows from V211. Pure data on V205. Bows are shaped with a cutting tool (tool_class CUTTING, as the existing
-- assemble_bow); distinct longer keywords beat the bare 'assemble'/'string the bow' incumbent. Routing verified
-- locally. Keywords hyphen-free; bows equippable (dead-end-clean); each string is consumed by one bow; matter-safe.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bowstring_sinew', 'Sinew bowstring', 'MATERIAL', 15, 10, TRUE,  FALSE, 0),
('bowstring_bast',  'Bast bowstring',  'MATERIAL', 15, 12, TRUE,  FALSE, 0),
('bowstring_hemp',  'Hemp bowstring',  'MATERIAL', 15, 12, TRUE,  FALSE, 0),
('short_self_bow',  'Short self bow',  'WEAPON',   300, 900, FALSE, TRUE, 0),
('long_self_bow',   'Long self bow',   'WEAPON',   500, 1300,FALSE, TRUE, 0),
('recurve_wood_bow','Recurve wood bow','WEAPON',   600, 1100,FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bowstring_sinew', 'TECHNIQUE', 'a string twisted from animal sinew'),
('bowstring_bast',  'TECHNIQUE', 'a string twisted from bast fibre'),
('bowstring_hemp',  'TECHNIQUE', 'a string twisted from hemp fibre'),
('short_self_bow',  'TECHNIQUE', 'a short stave tillered and strung'),
('long_self_bow',   'TECHNIQUE', 'a long stave tillered and strung for reach'),
('recurve_wood_bow','TECHNIQUE', 'a worked stave recurved at the tips and strung')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('short_self_bow','HAND_RIGHT','CARRIED'),('short_self_bow','HAND_LEFT','CARRIED'),
('long_self_bow','HAND_RIGHT','CARRIED'),('long_self_bow','HAND_LEFT','CARRIED'),
('recurve_wood_bow','HAND_RIGHT','CARRIED'),('recurve_wood_bow','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_bowstring_sinew', 'Twist a sinew bowstring', 'bowstring_sinew', 1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'make a sinew bowstring,twist a sinew bowstring,sinew bowstring',
 'You twist and wax lengths of sinew into a strong, low-stretch string — the best a bow can be strung with.','VERIFIED',now()),
('make_bowstring_bast',  'Twist a bast bowstring',  'bowstring_bast',  1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'make a bast bowstring,twist a bast bowstring,bast bowstring',
 'You twist bast fibre into a serviceable bowstring — it stretches more than sinew but holds.','VERIFIED',now()),
('make_bowstring_hemp',  'Twist a hemp bowstring',  'bowstring_hemp',  1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'make a hemp bowstring,twist a hemp bowstring,hemp bowstring',
 'You lay up hemp fibre into a strong, even bowstring.','VERIFIED',now()),
('assemble_short_self_bow','Assemble a short self bow','short_self_bow',1,1,'CUTTING',FALSE,FALSE,80,'tools','CRAFT',
 'assemble a short self bow,tiller a short self bow,short self bow,short bow',
 'You tiller a short stave to an even bend and string it — a quick, handy bow for close cover.','VERIFIED',now()),
('assemble_long_self_bow', 'Assemble a long self bow', 'long_self_bow', 1,1,'CUTTING',FALSE,FALSE,90,'tools','CRAFT',
 'assemble a long self bow,tiller a long self bow,long self bow,long bow',
 'You tiller a long stave to a smooth full draw and string it — cast and reach beyond the short bow.','VERIFIED',now()),
('assemble_recurve_wood_bow','Assemble a recurve wood bow','recurve_wood_bow',1,1,'CUTTING',FALSE,FALSE,120,'tools','CRAFT',
 'assemble a recurve wood bow,tiller a recurve wood bow,recurve wood bow,recurve bow',
 'You work a stave with recurved tips, tiller it true, and string it — more cast from a shorter bow, but slow to make.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_bowstring_sinew', 'animal_sinew',1),
('make_bowstring_bast',  'plant_fiber',2),
('make_bowstring_hemp',  'plant_fiber',2),
('assemble_short_self_bow',   'dry_branch',1),('assemble_short_self_bow','bowstring_bast',1),
('assemble_long_self_bow',    'dry_branch',2),('assemble_long_self_bow','bowstring_hemp',1),
('assemble_recurve_wood_bow', 'timber_plank',1),('assemble_recurve_wood_bow','bowstring_sinew',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_bowstring_sinew','sinew bowstring'),('make_bowstring_sinew','bowstring'),
('make_bowstring_bast','bast bowstring'),
('make_bowstring_hemp','hemp bowstring'),
('assemble_short_self_bow','short self bow'),('assemble_short_self_bow','short bow'),
('assemble_long_self_bow','long self bow'),('assemble_long_self_bow','long bow'),
('assemble_recurve_wood_bow','recurve wood bow'),('assemble_recurve_wood_bow','recurve bow')
ON CONFLICT DO NOTHING;

-- Combat function (V205): each bow is a BOW — a weapon only with a string (built in) and an arrow to loose.
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
('short_self_bow',  'BOW', 'PLAIN', FALSE),
('long_self_bow',   'BOW', 'PLAIN', FALSE),
('recurve_wood_bow','BOW', 'PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;
