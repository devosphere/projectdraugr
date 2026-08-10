-- V95: more herbs steep into tea (M1 #75, EPIC #45/#54).
--
-- Real-world-logic (misc audit). elder_flower and dried_herb_bundle were orphans, yet elderflower and dried
-- herbs are exactly what you steep for a hot drink. Adds them to the brew_infusion herb group, so a herbal
-- infusion (V92) can be made from them as well as from mint/nettle/dandelion. Pure data.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('brew_infusion','herb','elder_flower',2),
('brew_infusion','herb','dried_herb_bundle',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
