-- V82: gatherable building stock (M1 #55, EPIC #54).
--
-- Most of #55's stock already exists (thatch_bundle, hazel_rod, willow_branch, reed_bundle, withy_rope, vine,
-- bark_sheet, clay_lump). This adds the missing PLANT building stock — straight saplings (structural poles for
-- wattle frames) and straw (thatching) — through the flora system, seeded only in credible biomes. The
-- geological stock (river_sand / river_gravel / limestone_chunk) is deferred with the lime chain, since it needs
-- the mineral-deposit system rather than flora.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('straight_sapling', 'Straight sapling', 'MATERIAL', 1500, 6000, FALSE, FALSE, 0),
('straw_bundle',     'Straw bundle',     'MATERIAL', 400,  8000, TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Flora sources, in credible biomes (young growth in forest/highland/wetland; meadow grass on grassland).
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('sapling',      'SHRUB', 'TEMPERATE_FOREST,HIGHLAND,WETLAND', 'KNIFE_CLASS', 40, FALSE),
('meadow_grass', 'HERB',  'GRASSLAND',                         NULL,          14, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('sapling',      'straight_sapling', 1, 2, NULL),
('meadow_grass', 'straw_bundle',     2, 4, NULL),
('meadow_grass', 'thatch_bundle',    2, 4, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('straight_sapling', 'FLORA_DROP', 'cut young saplings in forest/highland/wetland'),
('straw_bundle',     'FLORA_DROP', 'cut and bundle meadow grass on grassland')
ON CONFLICT (item_key, source_kind) DO NOTHING;
