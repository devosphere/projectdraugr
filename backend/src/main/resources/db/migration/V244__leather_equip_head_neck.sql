-- V244 — story #96 (75 rawhide/leather/fur/footwear entries), slice 1: head, neck and shoulders. Nine equippable
-- pieces from rawhide/tanned_leather/animal_hide/fur, cut and sewn from the hides the existing field-dressing→scrape
-- →tan chain already produces, on the V110/V230/V237 garment pattern. Hide type sets the trade-off: fur insulates
-- (high insulation, lower water resistance), leather resists water and abrasion, rawhide is stiff and light-cover.
-- Phrases lead with 'make' (CRAFT) and carry no belt/tunic/boot/cloak/legging/sling word, so each routes to its own
-- recipe (two-axis sim + hard-intent audit checked).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('rawhide_headband',      'Rawhide headband',       'CLOTHING', 80,  180,  FALSE, TRUE, 2, 3),
('fur_cap',               'Fur cap',                'CLOTHING', 150, 500,  FALSE, TRUE, 12, 4),
('leather_cap',           'Leather cap',            'CLOTHING', 120, 450,  FALSE, TRUE, 5, 8),
('leather_hood',          'Leather hood',           'CLOTHING', 250, 700,  FALSE, TRUE, 7, 9),
('fur_hood',              'Fur hood',               'CLOTHING', 280, 800,  FALSE, TRUE, 15, 4),
('leather_neck_guard',    'Leather neck guard',     'CLOTHING', 150, 400,  FALSE, TRUE, 4, 8),
('fur_neck_wrap',         'Fur neck wrap',          'CLOTHING', 120, 400,  FALSE, TRUE, 10, 3),
('leather_shoulder_cape', 'Leather shoulder cape',  'CLOTHING', 500, 1400, FALSE, TRUE, 6, 10),
('hide_shoulder_cape',    'Hide shoulder cape',     'CLOTHING', 450, 1300, FALSE, TRUE, 5, 7)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('rawhide_headband','HEAD','CLOTHING'),
('fur_cap','HEAD','OUTER'),
('leather_cap','HEAD','OUTER'),
('leather_hood','HEAD','OUTER'),
('fur_hood','HEAD','OUTER'),
('leather_neck_guard','NECK','PROTECTION'),
('fur_neck_wrap','NECK','CLOTHING'),
('leather_shoulder_cape','TORSO','OUTER'),
('hide_shoulder_cape','TORSO','OUTER')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('rawhide_headband','TECHNIQUE','cut and bound from a strip of rawhide by hand'),
('fur_cap','TECHNIQUE','sewn from fur by hand'),
('leather_cap','TECHNIQUE','cut and sewn from tanned leather by hand'),
('leather_hood','TECHNIQUE','cut and sewn from tanned leather by hand'),
('fur_hood','TECHNIQUE','sewn from fur by hand'),
('leather_neck_guard','TECHNIQUE','a stiff leather guard laced round the neck by hand'),
('fur_neck_wrap','TECHNIQUE','sewn from fur by hand'),
('leather_shoulder_cape','TECHNIQUE','cut and sewn from tanned leather by hand'),
('hide_shoulder_cape','TECHNIQUE','cut from a whole hide and laced by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_rawhide_headband','Bind a rawhide headband','rawhide_headband',1,1,'CUTTING',FALSE,FALSE,12,'items','PROCESS','rawhide headband,make a rawhide headband', 'You cut and bind a strip of rawhide round your brow as a headband.', 'VERIFIED', now()),
('make_fur_cap','Sew a fur cap','fur_cap',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','fur cap,make a fur cap,sew a fur cap', 'You sew fur into a close cap, warm about the head and ears.', 'VERIFIED', now()),
('make_leather_cap','Sew a leather cap','leather_cap',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','leather cap,make a leather cap,sew a leather cap', 'You cut and sew tanned leather into a snug cap.', 'VERIFIED', now()),
('make_leather_hood','Sew a leather hood','leather_hood',1,1,'CUTTING',FALSE,FALSE,30,'items','CRAFT','leather hood,make a leather hood,sew a leather hood', 'You cut and sew tanned leather into a hood that turns rain off the head and neck.', 'VERIFIED', now()),
('make_fur_hood','Sew a fur hood','fur_hood',1,1,'CUTTING',FALSE,FALSE,32,'items','CRAFT','fur hood,make a fur hood,sew a fur hood', 'You sew fur into a deep hood, warm against the hardest cold.', 'VERIFIED', now()),
('make_leather_neck_guard','Lace a leather neck guard','leather_neck_guard',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','leather neck guard,make a leather neck guard', 'You cut a stiff leather guard and lace it round the neck.', 'VERIFIED', now()),
('make_fur_neck_wrap','Sew a fur neck wrap','fur_neck_wrap',1,1,'CUTTING',FALSE,FALSE,18,'items','CRAFT','fur neck wrap,make a fur neck wrap,sew a fur neck wrap', 'You sew fur into a wrap for the neck against the cold.', 'VERIFIED', now()),
('make_leather_shoulder_cape','Sew a leather shoulder cape','leather_shoulder_cape',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','leather shoulder cape,make a leather shoulder cape', 'You cut and sew tanned leather into a cape over the shoulders that turns wind and rain.', 'VERIFIED', now()),
('make_hide_shoulder_cape','Lace a hide shoulder cape','hide_shoulder_cape',1,1,'CUTTING',FALSE,FALSE,38,'items','CRAFT','hide shoulder cape,make a hide shoulder cape', 'You cut a whole hide and lace it into a rough cape over the shoulders.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_rawhide_headband','rawhide',1),
('make_fur_cap','fur_lining',1),
('make_leather_cap','tanned_leather',1),
('make_leather_hood','tanned_leather',1),
('make_fur_hood','fur_lining',1),
('make_leather_neck_guard','tanned_leather',1),
('make_fur_neck_wrap','fur_lining',1),
('make_leather_shoulder_cape','tanned_leather',2),
('make_hide_shoulder_cape','animal_hide',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_rawhide_headband','headband'),
('make_fur_cap','cap'),('make_fur_cap','fur cap'),
('make_leather_cap','cap'),('make_leather_cap','leather cap'),
('make_leather_hood','hood'),('make_leather_hood','leather hood'),
('make_fur_hood','hood'),('make_fur_hood','fur hood'),
('make_leather_neck_guard','neck guard'),
('make_fur_neck_wrap','neck wrap'),
('make_leather_shoulder_cape','shoulder cape'),
('make_hide_shoulder_cape','shoulder cape')
ON CONFLICT DO NOTHING;
