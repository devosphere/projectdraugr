-- V107: whetstone and grit-stone for sharpening (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (stone/abrasive family) with its consumer: sandstone and pumice are the grit a Chronicle puts
-- an edge on a blade with. A whetstone is dressed from sandstone; sharpening (repairNamedItem) now draws a
-- dulled edge back against a whetstone or raw grit-stone (reusable, not consumed) instead of the old, unreal
-- "bind it with cordage". Gathered as minerals.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('sandstone_piece', 'Sandstone',  'MATERIAL', 1500, 800, TRUE, FALSE, 0),
('pumice_piece',    'Pumice',     'MATERIAL', 200,  350, TRUE, FALSE, 0),
('whetstone',       'Whetstone',  'TOOL',     800,  400, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('sandstone_piece', 'Sandstone', 'HIGHLAND,RIVER_BANK,GRASSLAND', 0.60, 'STRIKING', 1, 2, 'A gritty stone that hones an edge.'),
('pumice_piece',    'Pumice',    'MOUNTAIN',                      0.30, NULL,       1, 3, 'Light volcanic grit for smoothing and honing.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('sandstone_piece', 'MINERAL',   'break sandstone from highland/river rock'),
('pumice_piece',    'MINERAL',   'gather pumice on volcanic ground'),
('whetstone',       'TECHNIQUE', 'dressed flat from a piece of sandstone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('dress_whetstone','Dress a whetstone','whetstone',1,1,'STRIKING',FALSE,FALSE,40,'items','CRAFT','whetstone,dress a whetstone,shape a whetstone,make a whetstone,sharpening stone,hone stone', 'You dress a piece of sandstone flat and true against a harder stone until it is a whetstone fit to hone a blade.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dress_whetstone','sandstone_piece',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('dress_whetstone','whetstone'),('dress_whetstone','sharpening stone')
ON CONFLICT DO NOTHING;
