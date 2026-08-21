-- V144: copper metallurgy — the first metal (EPIC #180 heavy industry / #184 copper & tin extraction).
--
-- The first complete ore -> metal -> tool vertical. The world already places copper in mountain and highland rock
-- (backdrops/ecology name copper seams), but there was no way to WIN it, SMELT it, or USE it. This adds:
--   copper_ore   — a mineable mineral (green malachite / native copper), broken free with a hammer or pick;
--   smelt_copper — reduce the ore with charcoal over a hot fire into a rough copper_ingot;
--   forge a copper chisel — hammer and anneal the ingot into a chisel that holds a finer edge than any stone.
-- The chisel is terminally BETTER than a stone edge (read in PhysicalItemService.executeProcess): it lifts the
-- workmanship of a fine carve one grade — the same bounded assist a workstation or seasoned stock gives — so the
-- whole chain is functional end to end, not a catalogue of tokens. Bronze (copper+tin), iron, and a proper
-- furnace/bellows follow in later slices; this is the copper foundation.

-- 1. Items: the ore, the smelted metal, and the first copper tool.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('copper_ore',    'Copper ore',    'MATERIAL', 1200, 500, TRUE,  FALSE, 0),
('copper_ingot',  'Copper ingot',  'MATERIAL', 900,  200, TRUE,  FALSE, 0),
('copper_chisel', 'Copper chisel', 'TOOL',     360,  180, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- 2. Reachability (V51 item_source) — every item must declare how it enters the world, or the Auditor flags it.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
('copper_ore',    'MINERAL',   'green malachite and native copper in mountain and highland rock'),
('copper_ingot',  'TECHNIQUE', 'smelted from copper ore with charcoal over a hot fire'),
('copper_chisel', 'TECHNIQUE', 'forged and annealed from a copper ingot')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- 3. The ore as a gatherable mineral — mountain and highland rock, broken free with a striking tool (hammer/pick).
INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('copper_ore', 'Copper ore', 'MOUNTAIN,HIGHLAND', 0.30, 'STRIKING', 1, 2,
 'Green-stained malachite and beads of native copper in exposed rock — the first metal, worked cold or smelted from its ore.')
ON CONFLICT (mineral_key) DO NOTHING;

-- 4. The copper chisel is held in the hand like any hafted tool.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('copper_chisel','HAND_RIGHT','CARRIED'), ('copper_chisel','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 5. Classifier vocabulary: 'smelt' is decisively PROCESS work (raw ore -> metal), 'forge' decisively CRAFT
--    (metal -> finished tool). High weight so the ore/copper nouns in the sentence do not pull the category away.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','smelt',3),
('CRAFT','forge',3)
ON CONFLICT (category_key, term) DO NOTHING;

-- 6. The two processes. smelt_copper reduces ore to ingot over a fire; forge_copper_chisel hammers an ingot to a
--    chisel (needs a striking tool and heat). Verified so they route by an ordinary sentence.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('smelt_copper', 'Smelt copper', 'copper_ingot', 1,1, NULL, TRUE, FALSE, 75, 'items', 'PROCESS',
 'smelt copper,smelt the copper,smelt copper ore,smelt the copper ore,smelt the ore,reduce the copper ore,smelt ore',
 'You bank the ore in among glowing charcoal and force the fire hot with a steady draught, until beads of molten copper run and gather and cool to a rough ingot.', 'VERIFIED', now()),
('forge_copper_chisel', 'Forge a copper chisel', 'copper_chisel', 1,1, 'STRIKING', TRUE, FALSE, 55, 'tools', 'CRAFT',
 'forge a copper chisel,forge copper chisel,forge the copper chisel,hammer out a copper chisel,work the copper into a chisel,copper chisel',
 'You heat the copper ingot and hammer it out, turning and annealing it as it work-hardens, into a chisel that takes and holds an edge no stone can.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('smelt_copper', 'copper_ore', 2), ('smelt_copper', 'charcoal', 2),
('forge_copper_chisel', 'copper_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('smelt_copper','copper'), ('smelt_copper','ore'), ('smelt_copper','ingot'),
('forge_copper_chisel','copper'), ('forge_copper_chisel','chisel')
ON CONFLICT DO NOTHING;
