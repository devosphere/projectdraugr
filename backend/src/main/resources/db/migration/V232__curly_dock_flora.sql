-- V232 — story #138 (temperate-forest survival flora). Adds the one named entry missing after #85 delivered the
-- other seven (stinging nettle, broadleaf plantain=plantain_herb, blackberry bramble, hazel, birch, willow, cattail):
-- curly dock. A common broad-leaved dock of grassland and damp forest edges; its young leaves are a foraged green,
-- eaten like the existing nettle/sorrel/dandelion greens. Pure data on the #85 edible-green pattern: flora_definition
-- + flora_drop + a FOOD item_definition + FLORA_DROP source. Gatherable in its biome and terminally useful (eaten).
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('curly_dock', 'HERB', 'GRASSLAND,TEMPERATE_FOREST,WETLAND', NULL, 14, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('curly_dock_leaf', 'Curly dock leaf', 'FOOD', 10, 20, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('curly_dock_leaf', 'FLORA_DROP', 'gathered as a young leaf from curly dock')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season, tool_condition) VALUES
('curly_dock', 'curly_dock_leaf', 2, 5, NULL, NULL)
ON CONFLICT DO NOTHING;
