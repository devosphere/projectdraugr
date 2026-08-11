-- V104: ochre, pigment, and dyed cloth (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (dye/pigment family) with its consumer: ochre and charred wood are ground into a pigment, and
-- pigment dyes woven cloth into coloured cloth — a real product that is then garment stock (craftGarment).
-- Ochres are gathered like other minerals; the grind is PROCESS work; dyeing takes any woven cloth.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('ochre_red',    'Red ochre',    'MATERIAL', 200, 120, TRUE, FALSE, 0),
('ochre_yellow', 'Yellow ochre', 'MATERIAL', 200, 120, TRUE, FALSE, 0),
('pigment',      'Ground pigment','MATERIAL', 60,  50,  TRUE, FALSE, 0),
('dyed_cloth',   'Dyed cloth',   'MATERIAL', 220, 700, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('ochre_red',    'Red ochre',    'RIVER_BANK,WETLAND,HIGHLAND', 0.55, NULL, 1, 3, 'Iron-rich earth that grinds to a red pigment.'),
('ochre_yellow', 'Yellow ochre', 'RIVER_BANK,WETLAND,GRASSLAND',0.55, NULL, 1, 3, 'Iron-rich earth that grinds to a yellow pigment.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('ochre_red',    'MINERAL',   'dig red ochre from iron-rich ground'),
('ochre_yellow', 'MINERAL',   'dig yellow ochre from iron-rich ground'),
('pigment',      'TECHNIQUE', 'ground from ochre or charred wood'),
('dyed_cloth',   'TECHNIQUE', 'cloth dyed with pigment')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Grind a pigment from any of red/yellow ochre or charcoal (black).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('grind_pigment','Grind a pigment','pigment',1,2,NULL,FALSE,FALSE,20,'items','PROCESS','grind pigment,grind ochre,grind the ochre,make pigment,ochre pigment,pigment', 'You grind the ochre to a fine, even powder on a flat stone — a pigment ready to take to cloth or skin.', 'VERIFIED', now()),
('dye_cloth','Dye cloth with pigment','dyed_cloth',1,1,NULL,FALSE,TRUE,40,'items','PROCESS','dye the cloth,dye cloth,dyed cloth,colour the cloth,color the cloth,dye the wool,dye', 'You work the pigment into the wetted cloth and let the colour strike and set into the fibres.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dye_cloth','pigment',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('grind_pigment','stock','ochre_red',1),('grind_pigment','stock','ochre_yellow',1),('grind_pigment','stock','charcoal',1),
('dye_cloth','cloth','wool_cloth',1),('dye_cloth','cloth','felt_sheet',1),('dye_cloth','cloth','textile_material',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('grind_pigment','pigment'),('grind_pigment','ochre'),
('dye_cloth','cloth'),('dye_cloth','wool')
ON CONFLICT DO NOTHING;
