-- V108: granite and basalt cobbles as hammerstones (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (stone family): a hard rounded cobble is the original hammerstone — what a knapper strikes
-- flakes with and a builder dresses stone with. Granite and basalt cobbles are gathered as minerals and count
-- as a STRIKING tool wherever one is needed (knapping, dressing stone, breaking minerals), alongside the field
-- stone and stone hammer.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('granite_cobble', 'Granite cobble', 'MATERIAL', 1200, 600, TRUE, FALSE, 0),
('basalt_cobble',  'Basalt cobble',  'MATERIAL', 1300, 620, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('granite_cobble', 'Granite cobble', 'HIGHLAND,MOUNTAIN,RIVER_BANK', 0.70, NULL, 1, 2, 'A hard rounded cobble — a ready hammerstone.'),
('basalt_cobble',  'Basalt cobble',  'MOUNTAIN,HIGHLAND',            0.55, NULL, 1, 2, 'A dense dark cobble — a ready hammerstone.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('granite_cobble', 'MINERAL', 'pick a granite cobble from highland/river ground'),
('basalt_cobble',  'MINERAL', 'pick a basalt cobble from volcanic ground')
ON CONFLICT (item_key, source_kind) DO NOTHING;
