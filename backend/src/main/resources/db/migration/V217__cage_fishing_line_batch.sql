-- V217 — story #93 catalogue batch 10: a cage frame and hand-line tackle. Four items wired into existing mechanics:
--   cage_trap_frame     — a pre-made frame; setTrap builds a CAGE from it (alt to weaving hazel rods on the spot)
--   fishing_line_sinew  } fish() selects the LINE method when one is carried (previously only a hook did)
--   fishing_line_bast   }
--   stone_fishing_weight — weights a hand-line so it carries deep, +15 to a LINE cast (as a lead sinker does)
-- Craft verbs ('carve'/'twist'/'shape') dodge SET_TRAP and are safe from FISH ('fishing' is not the whole word
-- 'fish'); 'frame' is CONSTRUCT but ties lose to CRAFT on precedence. Read only by setTrap/fish() (code consumers),
-- so all four are registered in DeadEndOutputInvariantTest.CODE_TERMINAL. Routing/matter/probe verified locally.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('cage_trap_frame',      'Cage trap frame',      'MATERIAL', 700, 3000, FALSE, FALSE, 0),
('fishing_line_sinew',   'Sinew fishing line',   'MATERIAL', 20,  30,   TRUE,  FALSE, 0),
('fishing_line_bast',    'Bast fishing line',    'MATERIAL', 20,  30,   TRUE,  FALSE, 0),
('stone_fishing_weight', 'Stone fishing weight', 'MATERIAL', 60,  30,   TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('cage_trap_frame',      'TECHNIQUE', 'a woven withy frame for a cage trap'),
('fishing_line_sinew',   'TECHNIQUE', 'a strong twisted-sinew hand-line'),
('fishing_line_bast',    'TECHNIQUE', 'a twisted bast-fibre hand-line'),
('stone_fishing_weight', 'TECHNIQUE', 'a grooved stone weight for a hand-line')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_cage_frame',      'Carve a cage frame',       'cage_trap_frame',      1,1,NULL,FALSE,FALSE,45,'tools','CRAFT',
 'carve a cage frame,build a cage frame from rods,cage frame',
 'You bend and lash withies into a stiff box frame — the skeleton of a cage trap, ready to set anywhere.','VERIFIED',now()),
('twist_line_sinew',      'Twist a sinew fishing line','fishing_line_sinew',  1,1,NULL,FALSE,FALSE,20,'tools','PROCESS',
 'twist a sinew fishing line,twist a sinew line,sinew fishing line',
 'You twist lengths of sinew into a strong, thin hand-line that will hold a fighting fish.','VERIFIED',now()),
('twist_line_bast',       'Twist a bast fishing line', 'fishing_line_bast',   1,1,NULL,FALSE,FALSE,20,'tools','PROCESS',
 'twist a bast fishing line,twist a bast line,bast fishing line',
 'You lay up bast fibre into a serviceable hand-line.','VERIFIED',now()),
('shape_fishing_weight',  'Shape a fishing weight',    'stone_fishing_weight',1,2,NULL,FALSE,FALSE,15,'tools','PROCESS',
 'shape a fishing weight,shape a line weight,fishing weight,line weight',
 'You groove a small stone to sit on a hand-line and carry the baited hook down to the deeper fish.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_cage_frame',     'hazel_rod',2),
('twist_line_sinew',     'animal_sinew',1),
('twist_line_bast',      'plant_fiber',2),
('shape_fishing_weight', 'field_stone',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_cage_frame','cage frame'),
('twist_line_sinew','sinew fishing line'),('twist_line_sinew','sinew line'),
('twist_line_bast','bast fishing line'),('twist_line_bast','bast line'),
('shape_fishing_weight','fishing weight'),('shape_fishing_weight','line weight')
ON CONFLICT DO NOTHING;
