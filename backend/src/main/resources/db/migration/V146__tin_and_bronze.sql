-- V146: tin and bronze — the alloy that gives copper its purpose (EPIC #180 / #185 bronze alloy composition).
--
-- Copper (V144/V145) is soft; bronze — copper hardened with a tenth part of tin — takes and holds a far keener,
-- harder edge, which is why the Bronze Age is a whole era and not a footnote. This is why a Chronicle goes looking
-- for the rarer tin. Adds:
--   tin_ore      — a scarce mineable ore (cassiterite) in mountain and highland rock;
--   smelt_tin    — reduce it with charcoal over a fire into a tin_ingot;
--   alloy_bronze — melt copper and tin together into bronze_ingot (the ~9:1 alloy);
--   forge a bronze spear — the harder metal makes the deadliest hand weapon of the era.
-- The bronze spear's edge is read in WildlifeEncounterService: it bites deeper than copper or stone, so the combat
-- ordering runs stone < copper < bronze. Casting, more bronze objects, and iron follow later.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('tin_ore',      'Tin ore',      'MATERIAL', 1100, 480, TRUE,  FALSE, 0),
('tin_ingot',    'Tin ingot',    'MATERIAL', 850,  180, TRUE,  FALSE, 0),
('bronze_ingot', 'Bronze ingot', 'MATERIAL', 900,  200, TRUE,  FALSE, 0),
('bronze_spear', 'Bronze spear', 'WEAPON',   1400, 2500, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('tin_ore',      'MINERAL',   'cassiterite, a scarce dark ore in mountain and highland rock'),
('tin_ingot',    'TECHNIQUE', 'smelted from tin ore with charcoal over a hot fire'),
('bronze_ingot', 'TECHNIQUE', 'copper and tin melted together into the harder alloy'),
('bronze_spear', 'TECHNIQUE', 'a bronze spearhead cast, hammered, and hafted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('tin_ore', 'Tin ore', 'MOUNTAIN,HIGHLAND', 0.20, 'STRIKING', 1, 2,
 'Cassiterite — heavy, dark, and scarce, in a few veins of mountain and highland rock. The rarer half of bronze.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bronze_spear','HAND_RIGHT','CARRIED'), ('bronze_spear','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 'alloy' is decisive PROCESS work (melting two metals into one); 'smelt'→PROCESS and 'forge'→CRAFT are from V144.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','alloy',3)
ON CONFLICT (category_key, term) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('smelt_tin', 'Smelt tin', 'tin_ingot', 1,1, NULL, TRUE, FALSE, 70, 'items', 'PROCESS',
 'smelt tin,smelt the tin,smelt tin ore,smelt the tin ore,reduce the tin ore',
 'You roast and reduce the dark ore among the charcoal until soft grey tin sweats out and pools, cooling to a light ingot.', 'VERIFIED', now()),
('alloy_bronze', 'Alloy bronze', 'bronze_ingot', 2,2, NULL, TRUE, FALSE, 65, 'items', 'PROCESS',
 'alloy bronze,alloy the bronze,alloy copper and tin,make bronze,melt copper and tin together',
 'You melt the copper down and stir in about a tenth part of tin, and the pour comes out harder and brighter than either metal alone — bronze.', 'VERIFIED', now()),
('forge_bronze_spear', 'Forge a bronze spear', 'bronze_spear', 1,1, 'STRIKING', TRUE, FALSE, 70, 'tools', 'CRAFT',
 'forge a bronze spear,forge bronze spear,cast a bronze spear,hammer out a bronze spearhead,work the bronze into a spear,bronze spear',
 'You cast a bronze spearhead, hammer and grind it to a long keen point, and rivet it to a shaft — an edge that bites deeper and lasts far longer than copper or stone.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('smelt_tin', 'tin_ore', 2), ('smelt_tin', 'charcoal', 1),
('alloy_bronze', 'copper_ingot', 2), ('alloy_bronze', 'tin_ingot', 1), ('alloy_bronze', 'charcoal', 1),
('forge_bronze_spear', 'bronze_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('smelt_tin','tin'), ('smelt_tin','ore'),
('alloy_bronze','bronze'), ('alloy_bronze','copper'), ('alloy_bronze','tin'),
('forge_bronze_spear','bronze'), ('forge_bronze_spear','spear')
ON CONFLICT DO NOTHING;
