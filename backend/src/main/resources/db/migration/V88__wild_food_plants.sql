-- V88: wild food plants (M1 #75 slice, EPIC #45/#54).
--
-- More of the food-plant family of #75. Four are edible as gathered (bulbs, cloves, leaves, rhizomes) and
-- nourish through the existing EAT path; wild grain is NOT eaten as a seed head — it must be threshed first,
-- keeping the honest processing line. cattail, dandelion, and wild_garlic flora already exist and gain a food
-- drop; wild_onion and wild_grass are new flora. Mirrors V86: obtainable source, edible FOOD or a verified
-- processing route, mass < input on the process, keyword carrying the material name, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('wild_onion_bulb',   'Wild onion bulb',   'FOOD',     30, 35, TRUE, FALSE, 0),
('garlic_clove_wild', 'Wild garlic clove', 'FOOD',     6,  8,  TRUE, FALSE, 0),
('dandelion_leaf',    'Dandelion leaf',    'FOOD',     10, 20, TRUE, FALSE, 0),
('cattail_rhizome',   'Cattail rhizome',   'FOOD',     60, 55, TRUE, FALSE, 0),
('wild_grain_head',   'Wild grain head',   'MATERIAL', 40, 60, TRUE, FALSE, 0),
('wild_grain',        'Wild grain',        'FOOD',     12, 12, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- wild_onion and wild_grass are new flora; the other three flora already exist and gain a food drop.
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('wild_onion', 'HERB', 'GRASSLAND,TEMPERATE_FOREST', NULL, 20, FALSE),
('wild_grass', 'HERB', 'GRASSLAND',                  NULL, 20, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('wild_onion',  'wild_onion_bulb',   1, 3, NULL),
('wild_garlic', 'garlic_clove_wild', 2, 5, NULL),
('dandelion',   'dandelion_leaf',    2, 5, NULL),
('cattail',     'cattail_rhizome',   1, 3, NULL),
('wild_grass',  'wild_grain_head',   2, 5, 'AUTUMN')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('wild_onion_bulb',   'FLORA_DROP', 'dig wild onions in grassland or open forest'),
('garlic_clove_wild', 'FLORA_DROP', 'gather wild garlic in forest'),
('dandelion_leaf',    'FLORA_DROP', 'pick dandelion leaves in grassland/forest/highland'),
('cattail_rhizome',   'FLORA_DROP', 'pull cattail rhizomes from wetland margins'),
('wild_grain_head',   'FLORA_DROP', 'strip wild grain heads from grass in autumn'),
('wild_grain',        'TECHNIQUE',  'threshed from wild grain heads')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- The grain's verified use: thresh the seed heads free of chaff into edible grain. Thresh/winnow are not code
-- intents, so this resolves through the material-process matcher; subject "grain", keywords carry it.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('thresh_wild_grain','Thresh wild grain','wild_grain',1,3,NULL,FALSE,FALSE,35,'items','PROCESS','wild grain,thresh grain,thresh the grain,thresh the heads,winnow grain,winnow the grain,grain head,grain','You beat the seed heads out and winnow the chaff away on the wind, and a small heap of clean grain is left.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('thresh_wild_grain','wild_grain_head',3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('thresh_wild_grain','grain'), ('thresh_wild_grain','heads')
ON CONFLICT DO NOTHING;
