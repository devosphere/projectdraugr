-- V199 — wild flora, batch 3 (story #85), the variety batch that closes the 100-flora catalogue.
-- Two survival uses, both functional end-to-end:
--   * 6 MEDICINAL herbs — each gathers from the world and pounds into an herbal_poultice, the same wound-dressing
--     material V87 wired into bindWound. So a fresh herb → poultice → a better-healed wound (real terminal use),
--     not a token. Pattern mirrors V87 exactly (process + input + subject; no input_group).
--   * 12 FOOD plants — each grows in a plausible biome, is gathered, and nourishes through EAT (terminal), as in
--     V197/V198.
-- Probe-clean (every item has a source and a use), dead-end-clean (herbs feed a poultice, foods are eaten),
-- matter-safe (a poultice is 24 g out of 2 × 16 g herb in; foods are gathered, not crafted). Seasons kept to the
-- AUTUMN/SUMMER/NULL set proven in V198.

-- ── Items ───────────────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
-- medicinal herbs (MATERIAL — pounded into a poultice, not eaten)
('self_heal_leaf',    'Self-heal leaf',      'MATERIAL', 16, 24, TRUE, FALSE, 0),
('woundwort_leaf',    'Woundwort leaf',      'MATERIAL', 16, 24, TRUE, FALSE, 0),
('meadowsweet_herb',  'Meadowsweet',         'MATERIAL', 16, 26, TRUE, FALSE, 0),
('sphagnum_moss',     'Sphagnum moss',       'MATERIAL', 16, 40, TRUE, FALSE, 0),
('agrimony_herb',     'Agrimony',            'MATERIAL', 16, 26, TRUE, FALSE, 0),
('sanicle_leaf',      'Sanicle leaf',        'MATERIAL', 16, 24, TRUE, FALSE, 0),
-- food plants (FOOD — eaten)
('hazelnut',          'Hazelnut',            'FOOD', 10, 12, TRUE, FALSE, 0),
('crab_apple',        'Crab apple',          'FOOD', 40, 60, TRUE, FALSE, 0),
('wild_pear',         'Wild pear',           'FOOD', 80,110, TRUE, FALSE, 0),
('medlar',            'Medlar',              'FOOD', 45, 60, TRUE, FALSE, 0),
('cloudberry',        'Cloudberry',          'FOOD', 5,  6,  TRUE, FALSE, 0),
('lingonberry',       'Lingonberry',         'FOOD', 3,  4,  TRUE, FALSE, 0),
('cranberry',         'Cranberry',           'FOOD', 4,  5,  TRUE, FALSE, 0),
('sea_buckthorn_berry','Sea buckthorn berry','FOOD', 4,  5,  TRUE, FALSE, 0),
('good_king_henry_greens','Good King Henry greens','FOOD',12,26,TRUE,FALSE,0),
('sow_thistle_leaf',  'Sow thistle leaf',    'FOOD', 12, 26, TRUE, FALSE, 0),
('wild_garlic_leaf',  'Wild garlic leaf',    'FOOD', 8,  18, TRUE, FALSE, 0),
('sweet_chestnut',    'Sweet chestnut',      'FOOD', 14, 18, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- ── Flora ───────────────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('self_heal_plant',   'HERB',  'GRASSLAND,TEMPERATE_FOREST', NULL, 18, FALSE),
('woundwort_plant',   'HERB',  'WETLAND,GRASSLAND',          NULL, 18, FALSE),
('meadowsweet_plant', 'HERB',  'WETLAND,GRASSLAND',          NULL, 20, FALSE),
('sphagnum_bed',      'HERB',  'WETLAND',                    NULL, 25, FALSE),
('agrimony_plant',    'HERB',  'GRASSLAND,TEMPERATE_FOREST', NULL, 20, FALSE),
('sanicle_plant',     'HERB',  'TEMPERATE_FOREST',           NULL, 20, FALSE),
('hazel_shrub',       'SHRUB', 'TEMPERATE_FOREST',           NULL, 40, FALSE),
('crab_apple_shrub',  'SHRUB', 'TEMPERATE_FOREST,GRASSLAND', NULL, 45, FALSE),
('wild_pear_shrub',   'SHRUB', 'TEMPERATE_FOREST',           NULL, 45, FALSE),
('medlar_shrub',      'SHRUB', 'TEMPERATE_FOREST',           NULL, 45, FALSE),
('cloudberry_plant',  'HERB',  'WETLAND',                    NULL, 22, FALSE),
('lingonberry_shrub', 'SHRUB', 'HIGHLAND,TEMPERATE_FOREST',  NULL, 30, FALSE),
('cranberry_plant',   'HERB',  'WETLAND',                    NULL, 22, FALSE),
('sea_buckthorn_shrub','SHRUB','COAST',                      NULL, 35, FALSE),
('good_king_henry',   'HERB',  'GRASSLAND,TEMPERATE_FOREST', NULL, 15, FALSE),
('sow_thistle',       'HERB',  'GRASSLAND',                  NULL, 15, FALSE),
('ramsons',           'HERB',  'TEMPERATE_FOREST,GRASSLAND', NULL, 18, FALSE),
('sweet_chestnut_shrub','SHRUB','TEMPERATE_FOREST',          NULL, 45, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('self_heal_plant',    'self_heal_leaf',    2, 4, NULL),
('woundwort_plant',    'woundwort_leaf',    2, 4, NULL),
('meadowsweet_plant',  'meadowsweet_herb',  2, 4, NULL),
('sphagnum_bed',       'sphagnum_moss',     3, 6, NULL),
('agrimony_plant',     'agrimony_herb',     2, 4, NULL),
('sanicle_plant',      'sanicle_leaf',      2, 4, NULL),
('hazel_shrub',        'hazelnut',          4, 10,'AUTUMN'),
('crab_apple_shrub',   'crab_apple',        3, 8, 'AUTUMN'),
('wild_pear_shrub',    'wild_pear',         2, 6, 'AUTUMN'),
('medlar_shrub',       'medlar',            2, 6, 'AUTUMN'),
('cloudberry_plant',   'cloudberry',        4, 10,'SUMMER'),
('lingonberry_shrub',  'lingonberry',       5, 12,'AUTUMN'),
('cranberry_plant',    'cranberry',         5, 12,'AUTUMN'),
('sea_buckthorn_shrub','sea_buckthorn_berry',5,12,'AUTUMN'),
('good_king_henry',    'good_king_henry_greens',2,5,NULL),
('sow_thistle',        'sow_thistle_leaf',  2, 5, NULL),
('ramsons',            'wild_garlic_leaf',  2, 5, NULL),
('sweet_chestnut_shrub','sweet_chestnut',   3, 8, 'AUTUMN')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('self_heal_leaf',    'FLORA_DROP', 'gather self-heal in grassland and open wood'),
('woundwort_leaf',    'FLORA_DROP', 'gather woundwort in damp grassland'),
('meadowsweet_herb',  'FLORA_DROP', 'gather meadowsweet by wet ground'),
('sphagnum_moss',     'FLORA_DROP', 'lift sphagnum moss from a bog'),
('agrimony_herb',     'FLORA_DROP', 'gather agrimony on grassy edges'),
('sanicle_leaf',      'FLORA_DROP', 'gather sanicle on the forest floor'),
('hazelnut',          'FLORA_DROP', 'gather hazelnuts in autumn woods'),
('crab_apple',        'FLORA_DROP', 'gather crab apples in autumn'),
('wild_pear',         'FLORA_DROP', 'gather wild pears in autumn'),
('medlar',            'FLORA_DROP', 'gather medlars in late autumn'),
('cloudberry',        'FLORA_DROP', 'gather cloudberries on the bog in summer'),
('lingonberry',       'FLORA_DROP', 'gather lingonberries on highland in autumn'),
('cranberry',         'FLORA_DROP', 'gather cranberries in autumn wetland'),
('sea_buckthorn_berry','FLORA_DROP','gather sea buckthorn berries on the coast in autumn'),
('good_king_henry_greens','FLORA_DROP','pick Good King Henry greens in grassland'),
('sow_thistle_leaf',  'FLORA_DROP', 'pick sow thistle leaves in grassland'),
('wild_garlic_leaf',  'FLORA_DROP', 'gather wild garlic in spring woods'),
('sweet_chestnut',    'FLORA_DROP', 'gather sweet chestnuts in autumn')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- ── Medicinal use: pound each herb into a poultice (mirrors V87) ─────────────────────────────────────────────
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('poultice_self_heal',  'Pound a self-heal poultice',  'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','self heal poultice,pound self heal,crush self heal,mash self heal,prepare a self heal poultice,self heal dressing,self heal','You bruise the self-heal to a green pulp — the woundwort of the old fields, laid to a hurt to close it.','VERIFIED',now()),
('poultice_woundwort',  'Pound a woundwort poultice',  'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','woundwort poultice,pound woundwort,crush woundwort,mash woundwort,prepare a woundwort poultice,woundwort dressing,woundwort','You pound the woundwort leaf to a paste and press it over the wound to stop the bleeding.','VERIFIED',now()),
('poultice_meadowsweet','Pound a meadowsweet poultice','herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','meadowsweet poultice,pound meadowsweet,crush meadowsweet,mash meadowsweet,prepare a meadowsweet poultice,meadowsweet dressing,meadowsweet','You crush the meadowsweet to a cooling pulp that eases the ache of a hurt.','VERIFIED',now()),
('poultice_sphagnum',   'Pack a sphagnum dressing',    'herbal_poultice',1,1,NULL,FALSE,FALSE,15,'items','PROCESS','sphagnum poultice,pound sphagnum,crush sphagnum,mash sphagnum,pack sphagnum,prepare a sphagnum dressing,sphagnum dressing,sphagnum moss,sphagnum','You wring and pack the sphagnum moss — a clean, drinking dressing that soaks a wound and holds it.','VERIFIED',now()),
('poultice_agrimony',   'Pound an agrimony poultice',  'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','agrimony poultice,pound agrimony,crush agrimony,mash agrimony,prepare an agrimony poultice,agrimony dressing,agrimony','You pound the agrimony to a poultice, the old herb for staunching and drawing a wound clean.','VERIFIED',now()),
('poultice_sanicle',    'Pound a sanicle poultice',    'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','sanicle poultice,pound sanicle,crush sanicle,mash sanicle,prepare a sanicle poultice,sanicle dressing,sanicle','You mash the sanicle leaf — the wound-herb of the deep woods — to a healing paste.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('poultice_self_heal',  'self_heal_leaf',   2),
('poultice_woundwort',  'woundwort_leaf',   2),
('poultice_meadowsweet','meadowsweet_herb', 2),
('poultice_sphagnum',   'sphagnum_moss',    2),
('poultice_agrimony',   'agrimony_herb',    2),
('poultice_sanicle',    'sanicle_leaf',     2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('poultice_self_heal',  'self heal'),
('poultice_woundwort',  'woundwort'),
('poultice_meadowsweet','meadowsweet'),
('poultice_sphagnum',   'sphagnum'),
('poultice_agrimony',   'agrimony'),
('poultice_sanicle',    'sanicle')
ON CONFLICT DO NOTHING;
