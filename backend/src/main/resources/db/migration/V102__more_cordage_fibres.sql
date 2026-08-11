-- V102: bramble, cattail, and bulrush fibres → cordage (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (non-food): three more real cordage plants. Bramble canes, cattail leaves, and bulrush all
-- give up a serviceable fibre that twists into cordage — the fiber_cordage that binds tools, shelters, and gear.
-- Mirrors V84: obtainable flora source, a twist process whose keywords carry the plant name so it beats the
-- generic twist_cordage, output mass below input.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bramble_cane',    'Bramble cane',    'MATERIAL', 150, 700, TRUE, FALSE, 0),
('cattail_leaf',    'Cattail leaves',  'MATERIAL', 60,  500, TRUE, FALSE, 0),
('bulrush_bundle',  'Bulrush bundle',  'MATERIAL', 200, 900, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('blackberry', 'bramble_cane',   1, 3, NULL),
('cattail',    'cattail_leaf',   2, 5, NULL),
('bulrush',    'bulrush_bundle', 2, 4, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bramble_cane',   'FLORA_DROP', 'cut bramble canes from the thicket'),
('cattail_leaf',   'FLORA_DROP', 'strip cattail leaves in wetland'),
('bulrush_bundle', 'FLORA_DROP', 'cut bulrush in wetland')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('twist_bramble_cordage','Twist bramble cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','bramble into cordage,bramble cordage,strip the bramble,twist bramble,bramble fibre,bramble fiber,bramble', 'You strip the thorns and outer rind from the bramble canes and twist the tough inner fibre into a strong cord.', 'VERIFIED', now()),
('twist_cattail_cordage','Twist cattail cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','cattail into cordage,cattail cordage,twist cattail,cattail leaf cord,cattail leaves,cattail', 'You split and twist the cattail leaves down into a light, serviceable cord.', 'VERIFIED', now()),
('twist_bulrush_cordage','Twist bulrush cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','bulrush into cordage,bulrush cordage,twist bulrush,bulrush cord,bulrush', 'You twist the bulrush down against your thigh into a rough, buoyant cord.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('twist_bramble_cordage','bramble_cane',3),
('twist_cattail_cordage','cattail_leaf',4),
('twist_bulrush_cordage','bulrush_bundle',3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('twist_bramble_cordage','bramble'),
('twist_cattail_cordage','cattail'),
('twist_bulrush_cordage','bulrush')
ON CONFLICT DO NOTHING;
