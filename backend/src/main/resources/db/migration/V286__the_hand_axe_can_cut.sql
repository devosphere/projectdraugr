-- The hand axe can cut (#93/#134).
--
-- stone_hand_axe is in the catalogue, is knapped by knap_stone_hand_axe, is a weapon in weapon_profile and is
-- equippable — and has no row in tool_profile at all. Tool class is how a process asks whether the Chronicle is
-- holding something that will do the work, so the archetypal knapped biface, the tool the Palaeolithic is named
-- for, could not perform a single one of the 156 processes that want a CUTTING edge. It could be swung at an
-- animal and used for nothing else.
--
-- CUTTING and not AXE, deliberately. A hand axe cuts, scrapes, butchers and works wood; what it cannot do is fell
-- a forest, which wants a hafted axe swung with two hands. clearLand and the axe-wear check both read tool_class
-- 'AXE', and a stone held in the fist has no business satisfying them.
INSERT INTO tool_profile (item_key, tool_class)
SELECT 'stone_hand_axe', 'CUTTING'
 WHERE EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'stone_hand_axe')
ON CONFLICT (item_key, tool_class) DO NOTHING;
