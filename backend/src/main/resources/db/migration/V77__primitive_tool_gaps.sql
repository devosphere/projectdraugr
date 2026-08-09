-- V77: close primitive wood/stone/bone tool gaps (M1 #58, EPIC #54).
--
-- Ten first-era tools as verified craft/process procedures, each from reachable stock with an equip slot and a
-- real job. Follows the V75/V76 pattern: category agrees with the shaping verb, subjects distinguish it, output
-- mass < input mass, review_state VERIFIED, validated by the routing-reachability probe. (The sling + sling_stone
-- pair is deferred: "sling" collides with the equip verb and wants an ammunition model — a separate change.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('flint_scraper',       'Flint scraper',        'TOOL',   180, 120,  FALSE, TRUE, 0),
('flint_burin',         'Flint burin',          'TOOL',   140, 100,  FALSE, TRUE, 0),
('stone_maul',          'Stone maul',           'TOOL',   2200,1600, FALSE, TRUE, 0),
('stone_mortar',        'Stone mortar',         'TOOL',   3000,2200, FALSE, TRUE, 0),
('stone_pestle',        'Stone pestle',         'TOOL',   700, 500,  FALSE, TRUE, 0),
('wooden_rake',         'Wooden rake',          'TOOL',   900, 3000, FALSE, TRUE, 0),
('wooden_hoe',          'Wooden hoe',           'TOOL',   1000,2600, FALSE, TRUE, 0),
('fire_hardened_spear', 'Fire-hardened spear',  'WEAPON', 1300,2400, FALSE, TRUE, 0),
('wooden_wedge',        'Wooden wedge',         'TOOL',   300, 260,  FALSE, TRUE, 0),
('stone_whetstone',     'Stone whetstone',      'TOOL',   600, 400,  FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Held in hand.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('flint_scraper','HAND_RIGHT','CARRIED'), ('flint_scraper','HAND_LEFT','CARRIED'),
('flint_burin','HAND_RIGHT','CARRIED'), ('flint_burin','HAND_LEFT','CARRIED'),
('stone_maul','HAND_RIGHT','CARRIED'), ('stone_maul','HAND_LEFT','CARRIED'),
('stone_pestle','HAND_RIGHT','CARRIED'), ('stone_pestle','HAND_LEFT','CARRIED'),
('wooden_rake','HAND_RIGHT','CARRIED'), ('wooden_rake','HAND_LEFT','CARRIED'),
('wooden_hoe','HAND_RIGHT','CARRIED'), ('wooden_hoe','HAND_LEFT','CARRIED'),
('fire_hardened_spear','HAND_RIGHT','CARRIED'), ('fire_hardened_spear','HAND_LEFT','CARRIED'),
('wooden_wedge','HAND_RIGHT','CARRIED'), ('wooden_wedge','HAND_LEFT','CARRIED'),
('stone_whetstone','HAND_RIGHT','CARRIED'), ('stone_whetstone','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('knap_scraper',      'Knap a flint scraper', 'flint_scraper',      1,1,'STRIKING',FALSE,FALSE,30,'tools','PROCESS','flint scraper,knap a scraper,scraper,knap a flint scraper', 'You strike a steep working edge onto a flake — a scraper for cleaning hide and shaving wood.', 'VERIFIED', now()),
('knap_burin',        'Knap a flint burin',   'flint_burin',        1,1,'STRIKING',FALSE,FALSE,30,'tools','PROCESS','flint burin,knap a burin,burin,knap a flint burin,graver', 'You knap a strong chisel point onto the flint — a burin for cutting grooves in bone and antler.', 'VERIFIED', now()),
('haft_stone_maul',   'Haft a stone maul',    'stone_maul',         1,1,'CUTTING', FALSE,FALSE,60,'tools','CRAFT',  'stone maul,haft a maul,maul,haft a stone maul,make a maul', 'You seat a heavy stone into a stout handle and bind it fast — a maul for driving wedges and breaking rock.', 'VERIFIED', now()),
('shape_stone_mortar','Shape a stone mortar', 'stone_mortar',       1,1,'STRIKING',FALSE,FALSE,90,'tools','PROCESS','stone mortar,shape a mortar,mortar,hollow a mortar,peck a mortar', 'You peck and grind a deep bowl into a block of stone — a mortar to pound grain and pigment in.', 'VERIFIED', now()),
('shape_stone_pestle','Shape a stone pestle', 'stone_pestle',       1,1,'STRIKING',FALSE,FALSE,40,'tools','PROCESS','stone pestle,shape a pestle,pestle,grind a pestle', 'You shape a smooth, heavy pestle to fit the hand and the mortar''s hollow.', 'VERIFIED', now()),
('carve_rake',        'Carve a wooden rake',  'wooden_rake',        1,1,'CUTTING', FALSE,FALSE,55,'tools','CRAFT',  'wooden rake,carve a rake,rake,carve a wooden rake,make a rake', 'You carve a toothed head and bind it to a shaft — a rake for drawing together litter, hay, and ash.', 'VERIFIED', now()),
('haft_hoe',          'Haft a wooden hoe',    'wooden_hoe',         1,1,'CUTTING', FALSE,FALSE,60,'tools','CRAFT',  'wooden hoe,haft a hoe,hoe,haft a wooden hoe,make a hoe', 'You set a broad blade across a shaft — a hoe for breaking and drawing soil.', 'VERIFIED', now()),
('fire_harden_spear', 'Fire-harden a spear',  'fire_hardened_spear',1,1,NULL,     TRUE, FALSE,25,'tools','PROCESS','fire harden the spear,fire-harden the spear,harden the spear point,char the spear point,fire-hardened spear', 'You turn the spear''s point slowly in the coals until the wood darkens and hardens to a lasting edge.', 'VERIFIED', now()),
('shape_wooden_wedge','Shape a wooden wedge', 'wooden_wedge',       1,2,'CUTTING', FALSE,FALSE,20,'tools','CRAFT',  'wooden wedge,shape a wedge,wedge,carve a wedge,make a wedge', 'You cut and dress a hard wooden wedge for splitting timber along the grain.', 'VERIFIED', now()),
('dress_whetstone',   'Dress a whetstone',    'stone_whetstone',    1,1,'STRIKING',FALSE,FALSE,35,'tools','PROCESS','whetstone,dress a whetstone,sharpening stone,shape a whetstone,hone stone', 'You dress a flat, even face onto a fine-grained stone — a whetstone to put an edge back on a blade.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('knap_scraper','field_stone',1),
('knap_burin','field_stone',1),
('haft_stone_maul','field_stone',1), ('haft_stone_maul','dry_branch',1), ('haft_stone_maul','fiber_cordage',2),
('shape_stone_mortar','field_stone',1),
('shape_stone_pestle','field_stone',1),
('carve_rake','dry_branch',2), ('carve_rake','fiber_cordage',1),
('haft_hoe','dry_branch',1), ('haft_hoe','field_stone',1), ('haft_hoe','fiber_cordage',1),
('fire_harden_spear','primitive_spear',1),
('shape_wooden_wedge','dry_branch',1),
('dress_whetstone','field_stone',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('knap_scraper','scraper'),
('knap_burin','burin'), ('knap_burin','graver'),
('haft_stone_maul','maul'),
('shape_stone_mortar','mortar'),
('shape_stone_pestle','pestle'),
('carve_rake','rake'),
('haft_hoe','hoe'),
('fire_harden_spear','spear'),
('shape_wooden_wedge','wedge'),
('dress_whetstone','whetstone'), ('dress_whetstone','hone')
ON CONFLICT DO NOTHING;
