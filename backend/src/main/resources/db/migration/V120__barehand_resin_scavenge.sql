-- V120: bare-hand pine resin scavenging (M1 #135, EPIC #123).
--
-- pine_resin already exists, but its only source is a flora_drop off `pine`, an AXE_CLASS tree — so tapping a live
-- pine needs a blade. #135 lists "pine resin lump" as a forest-floor SCAVENGING material: hardened lumps of resin
-- that ooze from the bark and drop to the ground, picked up by hand. This adds that bare-hand path as a NULL-tool
-- mineral (uncommon, forest biomes), alongside — not replacing — the axe-tap flora path. The existing consumer
-- (render_pitch → pine_pitch) is untouched, so the lump is obtainable bare-hand AND still used.
INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('pine_resin', 'Pine resin lump', 'TEMPERATE_FOREST,HIGHLAND,MOUNTAIN', 0.30, NULL, 1, 1, 'Hardened resin lumps shed from pine bark, scavenged by hand from the forest floor.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('pine_resin', 'MINERAL', 'pick up hardened resin lumps shed under a pine by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;
