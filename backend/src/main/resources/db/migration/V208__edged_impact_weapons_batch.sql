-- V208 — story #93 catalogue batch 2: edged and impact close/reach weapons. Eight real first-era weapons, each a
-- physical equippable object made through the material-process matcher and made functional in the hunt by a
-- weapon_profile row (V205) — no Java. Hand weapons (cleaver, hand axe, the tipped spears) count in confront when
-- held; the two javelins count as thrown reach when carried; the root club is a blunt weapon.
--
-- Masses are kept below the input sum (dry_branch 350 g + a stone/bone point + binding), so the matter-from-nothing
-- gate holds — unlike the Java-made primitive_spear, a material-process output is checked against its inputs.
-- Keyword design: 'knap'/'haft'/'fashion' dodge the Java CRAFT_SPEAR intent (craft|make + spear); no axe/cleaver/
-- club/javelin intent exists, and all verbs are CRAFT category terms so the processes self-classify.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('stone_cleaver',        'Stone cleaver',        'WEAPON', 450, 500, FALSE, TRUE, 0),
('stone_hand_axe',       'Stone hand axe',       'WEAPON', 180, 120, FALSE, TRUE, 0),
('root_club',            'Root club',            'WEAPON', 300, 700, FALSE, TRUE, 0),
('flint_tipped_spear',   'Flint-tipped spear',   'WEAPON', 500,1400, FALSE, TRUE, 0),
('bone_tipped_spear',    'Bone-tipped spear',    'WEAPON', 500,1400, FALSE, TRUE, 0),
('barbed_bone_spear',    'Barbed bone spear',    'WEAPON', 500,1400, FALSE, TRUE, 0),
('stone_tipped_javelin', 'Stone-tipped javelin', 'WEAPON', 480,1300, TRUE,  TRUE, 0),
('bone_tipped_javelin',  'Bone-tipped javelin',  'WEAPON', 480,1300, TRUE,  TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('stone_cleaver',        'TECHNIQUE', 'a broad knapped blade hafted for heavy chopping'),
('stone_hand_axe',       'TECHNIQUE', 'a knapped biface worked to fit the hand'),
('root_club',            'TECHNIQUE', 'a hard root or bough shaped into a striking club'),
('flint_tipped_spear',   'TECHNIQUE', 'a flint point hafted to a straight shaft'),
('bone_tipped_spear',    'TECHNIQUE', 'a bone point hafted to a straight shaft'),
('barbed_bone_spear',    'TECHNIQUE', 'a barbed bone point hafted to a shaft — it holds in the wound'),
('stone_tipped_javelin', 'TECHNIQUE', 'a light throwing shaft with a stone point'),
('bone_tipped_javelin',  'TECHNIQUE', 'a light throwing shaft with a bone point')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('stone_cleaver','HAND_RIGHT','CARRIED'),('stone_cleaver','HAND_LEFT','CARRIED'),
('stone_hand_axe','HAND_RIGHT','CARRIED'),('stone_hand_axe','HAND_LEFT','CARRIED'),
('root_club','HAND_RIGHT','CARRIED'),('root_club','HAND_LEFT','CARRIED'),
('flint_tipped_spear','HAND_RIGHT','CARRIED'),('flint_tipped_spear','HAND_LEFT','CARRIED'),
('bone_tipped_spear','HAND_RIGHT','CARRIED'),('bone_tipped_spear','HAND_LEFT','CARRIED'),
('barbed_bone_spear','HAND_RIGHT','CARRIED'),('barbed_bone_spear','HAND_LEFT','CARRIED'),
('stone_tipped_javelin','HAND_RIGHT','CARRIED'),('stone_tipped_javelin','HAND_LEFT','CARRIED'),
('bone_tipped_javelin','HAND_RIGHT','CARRIED'),('bone_tipped_javelin','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('knap_stone_cleaver',   'Knap a stone cleaver',   'stone_cleaver', 1,1,NULL,FALSE,FALSE,40,'tools','PROCESS',
 'knap a stone cleaver,stone cleaver,cleaver',
 'You knap a broad, heavy edge and lash it to a stout handle — a cleaver that chops where a knife only cuts.','VERIFIED',now()),
('knap_stone_hand_axe',  'Knap a stone hand axe',  'stone_hand_axe',1,1,NULL,FALSE,FALSE,35,'tools','PROCESS',
 'knap a stone hand axe,knap a hand axe,knap a handaxe,stone hand axe,hand axe,handaxe',
 'You work a stone down to a teardrop biface with an edge all round — the oldest tool, gripped bare in the fist.','VERIFIED',now()),
('fashion_root_club',    'Fashion a root club',    'root_club',     1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'fashion a root club,carve a root club,root club',
 'You cut and trim a hard root-bole into a heavy-headed club, balanced to swing.','VERIFIED',now()),
('haft_flint_spear',     'Haft a flint tipped spear',   'flint_tipped_spear',1,1,NULL,FALSE,FALSE,40,'tools','CRAFT',
 'haft a flint tipped spear,bind a flint tipped spear,flint tipped spear,flint spear',
 'You knap a leaf-shaped flint point and bind it hard into the split of a straight shaft — a spear with a true cutting head.','VERIFIED',now()),
('haft_bone_spear',      'Haft a bone tipped spear',    'bone_tipped_spear',1,1,NULL,FALSE,FALSE,40,'tools','CRAFT',
 'haft a bone tipped spear,bind a bone spear,bone tipped spear,bone spear',
 'You grind a bone point and lash it to a shaft — not as keen as flint, but it drives deep and does not shatter.','VERIFIED',now()),
('haft_barbed_bone_spear','Haft a barbed bone spear',   'barbed_bone_spear',1,1,NULL,FALSE,FALSE,45,'tools','CRAFT',
 'haft a barbed bone spear,carve a barbed bone spear,barbed bone spear,barbed spear',
 'You carve backward barbs into a bone point before hafting it — once it goes in, it holds, and the quarry cannot shake it free.','VERIFIED',now()),
('haft_stone_javelin',   'Haft a stone tipped javelin', 'stone_tipped_javelin',1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'haft a stone tipped javelin,fashion a stone tipped javelin,stone tipped javelin,stone javelin',
 'You fit a small stone point to a light, balanced shaft — made to be thrown, not held.','VERIFIED',now()),
('haft_bone_javelin',    'Haft a bone tipped javelin',  'bone_tipped_javelin',1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'haft a bone tipped javelin,fashion a bone tipped javelin,bone tipped javelin,bone javelin',
 'You fit a bone point to a light throwing shaft, weighted to fly true.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('knap_stone_cleaver',   'flint_stone',1),('knap_stone_cleaver','dry_branch',1),('knap_stone_cleaver','plant_fiber',1),
('knap_stone_hand_axe',  'flint_stone',1),
('fashion_root_club',    'dry_branch',1),
('haft_flint_spear',     'flint_stone',1),('haft_flint_spear','dry_branch',1),('haft_flint_spear','plant_fiber',1),
('haft_bone_spear',      'animal_bone',1),('haft_bone_spear','dry_branch',1),('haft_bone_spear','plant_fiber',1),
('haft_barbed_bone_spear','animal_bone',1),('haft_barbed_bone_spear','dry_branch',1),('haft_barbed_bone_spear','plant_fiber',1),
('haft_stone_javelin',   'flint_stone',1),('haft_stone_javelin','dry_branch',1),('haft_stone_javelin','plant_fiber',1),
('haft_bone_javelin',    'animal_bone',1),('haft_bone_javelin','dry_branch',1),('haft_bone_javelin','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('knap_stone_cleaver','cleaver'),('knap_stone_cleaver','stone cleaver'),
('knap_stone_hand_axe','hand axe'),('knap_stone_hand_axe','handaxe'),('knap_stone_hand_axe','hand axe'),
('fashion_root_club','root club'),('fashion_root_club','club'),
('haft_flint_spear','flint spear'),('haft_flint_spear','flint tipped spear'),
('haft_bone_spear','bone spear'),('haft_bone_spear','bone tipped spear'),
('haft_barbed_bone_spear','barbed spear'),('haft_barbed_bone_spear','barbed bone spear'),
('haft_stone_javelin','stone javelin'),('haft_stone_javelin','stone tipped javelin'),
('haft_bone_javelin','bone javelin'),('haft_bone_javelin','bone tipped javelin')
ON CONFLICT DO NOTHING;

-- Combat function (V205 weapon_profile): hand weapons bite when held, javelins are thrown reach, the club is blunt.
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
('stone_cleaver',       'HAND',   'PLAIN', FALSE),
('stone_hand_axe',      'HAND',   'PLAIN', FALSE),
('root_club',           'BLUNT',  'PLAIN', FALSE),
('flint_tipped_spear',  'HAND',   'PLAIN', FALSE),
('bone_tipped_spear',   'HAND',   'PLAIN', FALSE),
('barbed_bone_spear',   'HAND',   'PLAIN', FALSE),
('stone_tipped_javelin','JAVELIN','PLAIN', FALSE),
('bone_tipped_javelin', 'JAVELIN','PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;
