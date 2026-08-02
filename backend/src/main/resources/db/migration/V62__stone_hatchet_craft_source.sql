-- V62: The stone hatchet is now a player craft.
--
-- stone_hatchet has existed since V47 and fellTree() has always accepted it, but the
-- only way to obtain one was a technique the player cannot invoke. The two-handed
-- stone axe needs prepared components (planks -> wooden_component), so a fresh
-- chronicle had no realistic way to fell a tree at all. A hatchet is now crafted like
-- the other primitive hafted tools (stone + fibre + branch), so record that source.

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('stone_hatchet','CODE','PhysicalItemService.craftPrimitiveTool via CRAFT_HATCHET')
ON CONFLICT DO NOTHING;
