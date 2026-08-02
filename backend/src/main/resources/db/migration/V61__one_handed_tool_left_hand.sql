-- V61: One-handed tools can be held in either hand.
--
-- Small hand tools were only ever compatible with the right hand, so a Chronicle
-- could not, for example, keep a spear in the right hand and take a knife in the
-- left. These add the left-hand slot for the clearly one-handed tools. Genuinely
-- two-handed things (spear, the felling axe, the pickaxe, the bow, the shovel) are
-- left right-hand-only on purpose.

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('stone_knife','HAND_LEFT','ATTACHED'),
('stone_hammer','HAND_LEFT','ATTACHED'),
('stone_hatchet','HAND_LEFT','ATTACHED'),
('stone_adze','HAND_LEFT','ATTACHED'),
('stone_chisel','HAND_LEFT','ATTACHED'),
('wooden_mallet','HAND_LEFT','ATTACHED')
ON CONFLICT DO NOTHING;
