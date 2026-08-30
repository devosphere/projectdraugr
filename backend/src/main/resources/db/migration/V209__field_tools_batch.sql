-- V209 — story #93 catalogue batch 3: field tools. Three tools that complete the working (non-weapon) end of the
-- edged/impact group, each functional through the V206 tool_profile registry — pure data, no Java:
--   antler_hammer      — a soft antler billet for knapping and driving (STRIKING)
--   hide_scraper       — a steep-edged flake for cleaning hides and shaving wood (CUTTING)
--   field_butchery_kit — a roll of cutting blades for dressing a carcass in the field (CUTTING)
-- Routing verified locally (classify+match): antler hammer and butchery kit are CRAFT ('carve'/'assemble'); the
-- hide scraper is PROCESS ('knap'), winning its category by longest keyword. Keywords are hyphen-free. Equippable,
-- so dead-end-clean; matter-safe (each output below its input mass).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('antler_hammer',      'Antler hammer',       'TOOL', 400, 300, FALSE, TRUE, 0),
('hide_scraper',       'Hide scraper',        'TOOL', 120, 60,  FALSE, TRUE, 0),
('field_butchery_kit', 'Field butchery kit',  'TOOL', 300, 260, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('antler_hammer',      'TECHNIQUE', 'a length of antler hafted as a soft-hammer billet'),
('hide_scraper',       'TECHNIQUE', 'a steep working edge knapped onto a flake'),
('field_butchery_kit', 'TECHNIQUE', 'a roll of knapped blades bound for dressing game')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('antler_hammer','HAND_RIGHT','CARRIED'),('antler_hammer','HAND_LEFT','CARRIED'),
('hide_scraper','HAND_RIGHT','CARRIED'),('hide_scraper','HAND_LEFT','CARRIED'),
('field_butchery_kit','HAND_RIGHT','CARRIED'),('field_butchery_kit','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_antler_hammer',    'Carve an antler hammer',    'antler_hammer', 1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'carve an antler hammer,haft an antler hammer,antler hammer,antler billet',
 'You cut a length of antler and bind it to a short grip — a soft hammer that drives a flake without shattering the edge.','VERIFIED',now()),
('knap_hide_scraper',      'Knap a hide scraper',       'hide_scraper',  1,1,NULL,FALSE,FALSE,25,'tools','PROCESS',
 'knap a hide scraper,strike a hide scraper,hide scraper',
 'You strike a steep, blunt working edge onto a broad flake — a scraper for cleaning a hide down and shaving wood.','VERIFIED',now()),
('assemble_butchery_kit',  'Assemble a field butchery kit','field_butchery_kit',1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'assemble a field butchery kit,make up a butchery kit,field butchery kit,butchery kit',
 'You knap a few keen blades and bind them into a roll with a bone point — everything the hand needs to open and dress a carcass.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_antler_hammer',   'deer_antler',1),('carve_antler_hammer','dry_branch',1),
('knap_hide_scraper',     'flint_stone',1),
('assemble_butchery_kit', 'flint_stone',1),('assemble_butchery_kit','animal_bone',1),('assemble_butchery_kit','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_antler_hammer','antler hammer'),('carve_antler_hammer','antler'),
('knap_hide_scraper','hide scraper'),('knap_hide_scraper','scraper'),
('assemble_butchery_kit','butchery kit'),('assemble_butchery_kit','butchery')
ON CONFLICT DO NOTHING;

-- Tool functions (V206): the antler hammer strikes; the scraper and butchery kit cut.
INSERT INTO tool_profile (item_key, tool_class) VALUES
('antler_hammer','STRIKING'),
('hide_scraper','CUTTING'),
('field_butchery_kit','CUTTING')
ON CONFLICT (item_key, tool_class) DO NOTHING;
