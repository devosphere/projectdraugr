-- V231 — story #136 (knappable stone, hammerstone, and abrasive source chains). Fills the three named entries missing
-- after prior work already delivered flint_stone (flint nodule), chert_nodule, sandstone_piece (sandstone abrader),
-- and basalt_cobble: a quartzite cobble and a river hammerstone (both hard STRIKING percussors for knapping), and a
-- slate shard (an expedient CUTTING edge). All three are gatherable by name through gatherMineral (mineral_definition
-- + biome affinity) and functional as tools through tool_profile (soundestToolOfClass reads it for STRIKING/CUTTING
-- process gates and the assembly STRIKING check), exactly like the existing cobbles. Pure data; the only code change
-- is adding 'slate'/'hammerstone' to the GATHER_MINERAL trigger so those names route to prospecting.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('quartzite_cobble',  'Quartzite cobble',  'MATERIAL', 1250, 600, TRUE, FALSE),
('river_hammerstone', 'River hammerstone', 'MATERIAL', 1000, 480, TRUE, FALSE),
('slate_shard',       'Slate shard',       'MATERIAL', 400,  250, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- Gatherable by name in the ground that holds them. Quartzite and river cobbles are loose surface stone (no tool to
-- break them free); a slate shard is split off bedrock, so it wants a hammerstone/striking edge like other seams.
INSERT INTO mineral_definition (mineral_key, display_name, rarity, tool_required, yield_min, yield_max, biome_affinity, notes) VALUES
('quartzite_cobble',  'Quartzite cobble',  0.55, NULL,       1, 2, 'MOUNTAIN,HIGHLAND,RIVER_BANK', 'A hard, tough cobble that takes and keeps a striking face — a good hammerstone for knapping.'),
('river_hammerstone', 'River hammerstone', 0.65, NULL,       1, 1, 'RIVER_BANK',                   'A water-rounded cobble off a river bar, sized to the hand for a percussor.'),
('slate_shard',       'Slate shard',       0.50, 'STRIKING', 1, 2, 'HIGHLAND,MOUNTAIN,RIVER_BANK',  'Fissile slate splits into flat shards with a straight, if soft, cutting edge.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('quartzite_cobble','MINERAL','gathered as a loose cobble from mountain, highland, or river ground'),
('river_hammerstone','MINERAL','picked up water-rounded from a river bar'),
('slate_shard','MINERAL','split from slate bedrock with a hammerstone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Tool roles: the two cobbles are STRIKING percussors (knapping, and the assembly STRIKING gate); the slate shard is
-- an expedient CUTTING edge. soundestToolOfClass reads tool_profile, so these satisfy the same process/tool gates as
-- the existing stones with no further wiring.
INSERT INTO tool_profile (item_key, tool_class) VALUES
('quartzite_cobble','STRIKING'),
('river_hammerstone','STRIKING'),
('slate_shard','CUTTING')
ON CONFLICT (item_key, tool_class) DO NOTHING;
