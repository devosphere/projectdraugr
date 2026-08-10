-- V86: first-era nut foods (M1 #75 slice, EPIC #45/#54).
--
-- The food-plant family of #75, through the flora system. Four tree/shrub nuts are edible as gathered and
-- nourish through the existing EAT path; the acorn is deliberately NOT — raw acorns are bitter with tannin, so
-- it is modelled as a MATERIAL whose only use is leaching and grinding into acorn flour (an edible FOOD). That
-- keeps the honest line #75 asks for: an inedible-raw plant is not silently converted into nourishment; the
-- work of making it safe is a real, verified step. Mirrors V84/V85: obtainable source, obtainable use output,
-- mass < input on the process, keyword carrying the material name, promoted VERIFIED, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('acorn',       'Acorn',        'MATERIAL', 8,  10, TRUE, FALSE, 0),
('acorn_flour', 'Acorn flour',  'FOOD',     15, 20, TRUE, FALSE, 0),
('hazelnut',    'Hazelnut',     'FOOD',     6,  8,  TRUE, FALSE, 0),
('walnut',      'Walnut',       'FOOD',     12, 16, TRUE, FALSE, 0),
('chestnut',    'Chestnut',     'FOOD',     14, 18, TRUE, FALSE, 0),
('pine_nut',    'Pine nut',     'FOOD',     3,  4,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Sources: oak drops acorns, hazel hazelnuts, pine pine-nuts (all already present); walnut and chestnut trees
-- are new flora. Gathered by hand (no tool); nut-bearing seasons left open for the first pass.
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('walnut_tree',   'TREE', 'TEMPERATE_FOREST',          NULL, 90, FALSE),
('chestnut_tree', 'TREE', 'TEMPERATE_FOREST,HIGHLAND', NULL, 90, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('oak',           'acorn',    3, 8, 'AUTUMN'),
('hazel',         'hazelnut', 2, 6, 'AUTUMN'),
('pine',          'pine_nut', 2, 5, 'AUTUMN'),
('walnut_tree',   'walnut',   2, 6, 'AUTUMN'),
('chestnut_tree', 'chestnut', 3, 7, 'AUTUMN')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('acorn',       'FLORA_DROP', 'gather acorns under oaks in autumn'),
('hazelnut',    'FLORA_DROP', 'gather hazelnuts from hazel in autumn'),
('pine_nut',    'FLORA_DROP', 'shake pine nuts from cones in autumn'),
('walnut',      'FLORA_DROP', 'gather walnuts under walnut trees in autumn'),
('chestnut',    'FLORA_DROP', 'gather chestnuts under chestnut trees in autumn'),
('acorn_flour', 'TECHNIQUE',  'leached and ground from acorns')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- The acorn's verified use: leach out the tannin and grind to flour. Needs water (the leaching), consumes a
-- handful of acorns, yields less mass of flour. Subject "acorn"; keywords carry it so nothing generic competes.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('leach_acorn_flour','Leach and grind acorn flour','acorn_flour',1,2,NULL,FALSE,TRUE,70,'items','PROCESS','acorn flour,acorn meal,leach the acorns,leach acorns,grind the acorns,grind acorns,acorns,acorn','You shell the acorns and leach the bitterness out with water, changed until it runs clear, then grind the sweetened meats to a coarse flour.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('leach_acorn_flour','acorn',6)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('leach_acorn_flour','acorn'), ('leach_acorn_flour','acorns')
ON CONFLICT DO NOTHING;
