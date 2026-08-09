-- V84: plant-fibre raw materials → cordage (M1 #75 slice, EPIC #45/#54).
--
-- #75 is a 100-entry raw-material catalogue; this lands a coherent, acceptance-complete vertical of it: four
-- new plant-fibre materials, each with a real ecological source (the flora system, gated on biome), a carried
-- object representation, and at least one VERIFIED use before it is exposed — retting/twisting into cordage,
-- the fiber_cordage that binds tools, shelters, and carrying gear. The bark-derived fibres from the same family
-- (cedar/willow/linden) are deferred: their natural phrasing ("strip the willow bark") is owned by STRIP_BARK,
-- and giving them a specific route needs that intent generalised first — a separate, tracked step.
--
-- Pattern mirrors V79/V82: obtainable inputs, subjects derived from the material, keywords carrying the material
-- name so a named fibre beats the generic twist_cordage (the matcher takes the longest matching keyword), output
-- mass < input, promoted VERIFIED, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('milkweed_fibre',     'Milkweed fibre bundle',    'MATERIAL', 120, 600,  TRUE, FALSE, 0),
('root_fibre_bundle',  'Root fibre bundle',        'MATERIAL', 250, 900,  TRUE, FALSE, 0),
('flax_stalk',         'Flax stalks',              'MATERIAL', 300, 1200, TRUE, FALSE, 0),
('hemp_stalk',         'Hemp stalks',              'MATERIAL', 340, 1300, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Ecological sources, each in a credible biome. Milkweed, flax, and hemp are gathered whole (no tool); the
-- fibrous roots are dug from wet ground. None poisonous to gather.
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('milkweed',      'HERB', 'GRASSLAND',                 NULL, 25, FALSE),
('flax_plant',    'HERB', 'GRASSLAND',                 NULL, 30, FALSE),
('hemp_plant',    'HERB', 'GRASSLAND',                 NULL, 30, FALSE),
('fibrous_roots', 'HERB', 'WETLAND,TEMPERATE_FOREST',  NULL, 20, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('milkweed',      'milkweed_fibre',    1, 3, NULL),
('flax_plant',    'flax_stalk',        2, 4, NULL),
('hemp_plant',    'hemp_stalk',        2, 4, NULL),
('fibrous_roots', 'root_fibre_bundle', 1, 2, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('milkweed_fibre',    'FLORA_DROP', 'strip fibre from milkweed stalks in grassland'),
('flax_stalk',        'FLORA_DROP', 'gather flax in grassland'),
('hemp_stalk',        'FLORA_DROP', 'gather hemp in grassland'),
('root_fibre_bundle', 'FLORA_DROP', 'dig fibrous roots from wet or forest ground')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- The verified use: each fibre worked into cordage. Keywords carry the material name (and material+cordage
-- phrase) so a named fibre outranks the generic twist_cordage; subjects are the material itself. flax and hemp
-- ret (soak and break down) first; milkweed and root are twisted straight.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('twist_milkweed_cordage','Twist milkweed cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','milkweed into cordage,milkweed cordage,twist milkweed,ret milkweed,milkweed fibre,milkweed fiber,milkweed rope,milkweed twine,milkweed','You roll the milkweed fibre against your thigh and ply it back on itself into a fine, strong cord.','VERIFIED',now()),
('twist_root_cordage',   'Twist root-fibre cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','root fibre into cordage,root cordage,root fibre cord,root fiber cord,twist the roots,ret the roots,root fibre,root fiber','You clean and split the fibrous roots and twist them down into a rough, serviceable cord.','VERIFIED',now()),
('ret_flax_cordage',     'Ret flax into cordage','fiber_cordage',1,2,NULL,FALSE,TRUE,50,'textiles','PROCESS','flax into cordage,flax cordage,ret the flax,ret flax,soak flax,flax fibre,flax fiber,flax rope,flax twine,flax line,flax','You ret the flax until the woody stem breaks away, comb out the long line fibre, and twist it into a smooth, strong cord.','VERIFIED',now()),
('ret_hemp_cordage',     'Ret hemp into cordage','fiber_cordage',1,3,NULL,FALSE,TRUE,50,'textiles','PROCESS','hemp into cordage,hemp cordage,ret the hemp,ret hemp,soak hemp,hemp fibre,hemp fiber,hemp rope,hemp twine,hemp line,hemp','You ret the hemp, break out the coarse fibre, and lay it up into a heavy, hard-wearing cord.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('twist_milkweed_cordage','milkweed_fibre',4),
('twist_root_cordage','root_fibre_bundle',3),
('ret_flax_cordage','flax_stalk',3),
('ret_hemp_cordage','hemp_stalk',3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('twist_milkweed_cordage','milkweed'),
('twist_root_cordage','root fibre'), ('twist_root_cordage','root fiber'), ('twist_root_cordage','roots'),
('ret_flax_cordage','flax'),
('ret_hemp_cordage','hemp')
ON CONFLICT DO NOTHING;
