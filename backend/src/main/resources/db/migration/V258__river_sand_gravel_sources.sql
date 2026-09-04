-- V258 — story #55 (gatherable building stock): river_sand and river_gravel, the two named #55 sources with no
-- obtainable route. Both are aggregates washed/screened from a river bar — modelled as gatherable mineral_definition
-- rows (like knappable stone in V231), obtained by hand (no tool_required) at riverbank and wetland ground. They are
-- the building stock the later chains draw on: sand for ceramic temper, mortar, and sand-filter beds; gravel for
-- paths, drainage, and filter beds. Gathered minerals are not process outputs, so the dead-end guard does not apply.
-- The remaining #55 sources already map to obtainable items (hazel_wand->hazel_rod, tree_vine->vine, bark_strip->
-- bark_sheet, thatch_grass_bundle->dry_grass_bundle, clay_subsoil->clay_lump, green_branch->dry_branch, plus
-- straight_sapling/straw_bundle/limestone_chunk which exist by key).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('river_sand',   'River sand',   'MATERIAL', 500, 350, TRUE, FALSE),
('river_gravel', 'River gravel', 'MATERIAL', 700, 450, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('river_sand',   'River sand',   'RIVER_BANK,WETLAND', 0.9, NULL, 2, 4, 'washed by hand from a river bar for ceramic temper, mortar, and filtration'),
('river_gravel', 'River gravel', 'RIVER_BANK,WETLAND', 0.9, NULL, 2, 4, 'screened by hand from a river bar for paths, drainage, and filter beds')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('river_sand',   'MINERAL', 'washed by hand from a river bar'),
('river_gravel', 'MINERAL', 'screened by hand from a river bar')
ON CONFLICT (item_key, source_kind) DO NOTHING;
