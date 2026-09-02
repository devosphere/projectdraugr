-- V245 — story #96, slice 2: torso wear and body armour. Seven equippable pieces cut and sewn from rawhide/leather/
-- hide/fur. reed/bark tunics used 'jerkin' to dodge CRAFT_GARMENT; leather_tunic/hide_tunic do the same. rawhide
-- pieces are category PROCESS (the word 'rawhide' is a PROCESS category term, weight 2 — see V244). leather/fur/hide
-- pieces are CRAFT. leather_cuirass keyword is distinct from the metal forge_*_cuirass recipes. tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('rawhide_vest',        'Rawhide vest',        'CLOTHING', 600,  1600, FALSE, TRUE, 4, 4),
('leather_vest',        'Leather vest',        'CLOTHING', 700,  1800, FALSE, TRUE, 7, 9),
('fur_vest',            'Fur vest',            'CLOTHING', 700,  2000, FALSE, TRUE, 16, 4),
('leather_tunic',       'Leather tunic',       'CLOTHING', 800,  2000, FALSE, TRUE, 8, 8),
('hide_tunic',          'Hide tunic',          'CLOTHING', 800,  2000, FALSE, TRUE, 7, 6),
('leather_cuirass',     'Leather cuirass',     'CLOTHING', 1500, 3200, FALSE, TRUE, 6, 10),
('rawhide_chest_panel', 'Rawhide chest panel', 'CLOTHING', 700,  1700, FALSE, TRUE, 3, 4)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('rawhide_vest','TORSO','PROTECTION'),
('leather_vest','TORSO','CLOTHING'),
('fur_vest','TORSO','CLOTHING'),
('leather_tunic','TORSO','INNER'),
('hide_tunic','TORSO','INNER'),
('leather_cuirass','TORSO','PROTECTION'),
('rawhide_chest_panel','TORSO','PROTECTION')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('rawhide_vest','TECHNIQUE','cut and laced from stiff rawhide by hand'),
('leather_vest','TECHNIQUE','cut and sewn from tanned leather by hand'),
('fur_vest','TECHNIQUE','sewn from fur by hand'),
('leather_tunic','TECHNIQUE','cut and sewn from tanned leather over the body by hand'),
('hide_tunic','TECHNIQUE','cut from hide and laced over the body by hand'),
('leather_cuirass','TECHNIQUE','moulded and laced from thick tanned leather by hand'),
('rawhide_chest_panel','TECHNIQUE','a stiff rawhide panel laced over the chest by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_rawhide_vest','Lace a rawhide vest','rawhide_vest',1,1,'CUTTING',FALSE,FALSE,35,'items','PROCESS','rawhide vest,make a rawhide vest', 'You cut and lace stiff rawhide into a rigid vest over the chest and back.', 'VERIFIED', now()),
('make_leather_vest','Sew a leather vest','leather_vest',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','leather vest,make a leather vest,sew a leather vest', 'You cut and sew tanned leather into a close vest.', 'VERIFIED', now()),
('make_fur_vest','Sew a fur vest','fur_vest',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','fur vest,make a fur vest,sew a fur vest', 'You sew fur into a warm vest for the body.', 'VERIFIED', now()),
('make_leather_tunic','Sew a leather jerkin','leather_tunic',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','leather jerkin,make a leather jerkin,sew a leather jerkin', 'You cut and sew tanned leather into a sleeveless body jerkin — a leather tunic.', 'VERIFIED', now()),
('make_hide_tunic','Lace a hide jerkin','hide_tunic',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','hide jerkin,make a hide jerkin,lace a hide jerkin', 'You cut hide and lace it into a sleeveless body jerkin — a hide tunic.', 'VERIFIED', now()),
('make_leather_cuirass','Mould a leather cuirass','leather_cuirass',1,1,'CUTTING',FALSE,FALSE,70,'items','CRAFT','leather cuirass,make a leather cuirass,mould a leather cuirass', 'You mould and lace thick tanned leather into a cuirass over the chest and back.', 'VERIFIED', now()),
('make_rawhide_chest_panel','Lace a rawhide chest panel','rawhide_chest_panel',1,1,'CUTTING',FALSE,FALSE,30,'items','PROCESS','rawhide chest panel,make a rawhide chest panel', 'You lace a stiff rawhide panel over the chest.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_rawhide_vest','rawhide',2),
('make_leather_vest','tanned_leather',2),
('make_fur_vest','fur_lining',2),
('make_leather_tunic','tanned_leather',2),
('make_hide_tunic','animal_hide',1),
('make_leather_cuirass','tanned_leather',3),
('make_rawhide_chest_panel','rawhide',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_rawhide_vest','vest'),('make_rawhide_vest','rawhide vest'),
('make_leather_vest','vest'),('make_leather_vest','leather vest'),
('make_fur_vest','vest'),('make_fur_vest','fur vest'),
('make_leather_tunic','jerkin'),
('make_hide_tunic','jerkin'),
('make_leather_cuirass','cuirass'),
('make_rawhide_chest_panel','chest panel')
ON CONFLICT DO NOTHING;
