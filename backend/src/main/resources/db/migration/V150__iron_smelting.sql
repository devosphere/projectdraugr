-- V150: iron — the hardest metal of the age (EPIC #180 heavy industry / #186 iron ore & smelting).
--
-- The capstone of the metal ladder: iron is more common than tin yet far harder to win — it does not pour like
-- copper but forms a spongy bloom that must be hammered free of its slag. Worked, it holds an edge harder than any
-- bronze. The chain: mine common iron ore, smelt it with a heavy charge of charcoal into an iron bloom, and forge
-- the bloom into an iron axe that out-cuts and out-fights bronze. (Combat and felling readers wired in Java.)
--
-- Heat note: like the copper and bronze smelts (V144/V146) this gates on a hot fire (requires_fire); a dedicated
-- bloomery furnace with forced draught is a later refinement to layer across all the metal smelts at once.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('iron_ore',   'Iron ore',   'MATERIAL', 1300, 520, TRUE,  FALSE, 0),
('iron_bloom', 'Iron bloom', 'MATERIAL', 1400, 400, TRUE,  FALSE, 0),
('iron_axe',   'Iron axe',   'TOOL',     1050, 800, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('iron_ore',   'MINERAL',   'hematite and bog iron, common in mountain, highland, and wetland ground'),
('iron_bloom', 'TECHNIQUE', 'smelted from iron ore under a heavy charge of charcoal'),
('iron_axe',   'TECHNIQUE', 'an iron bloom hammered free of slag, forged, and hafted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Iron ore is common — hematite in the hills, bog iron in the wetlands — so its affinity spans more ground.
INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('iron_ore', 'Iron ore', 'MOUNTAIN,HIGHLAND,WETLAND', 0.40, 'STRIKING', 1, 2,
 'Rusty hematite in the hills and heavy bog iron in the wetlands — common, but it yields its metal only to a hot, hungry fire.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('iron_axe','HAND_RIGHT','CARRIED'), ('iron_axe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 'smelt'->PROCESS and 'forge'->CRAFT are from V144, so both route by an ordinary sentence.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('smelt_iron', 'Smelt iron', 'iron_bloom', 1,1, NULL, TRUE, FALSE, 110, 'items', 'PROCESS',
 'smelt iron,smelt the iron,smelt iron ore,smelt the iron ore,reduce the iron ore,bloom the iron',
 'You pack the ore in with a heavy charge of charcoal and drive the fire hot for hours; the iron never runs, but gathers low in a spongy, slag-riddled bloom you rake out glowing.', 'VERIFIED', now()),
('forge_iron_axe', 'Forge an iron axe', 'iron_axe', 1,1, 'STRIKING', TRUE, FALSE, 120, 'tools', 'CRAFT',
 'forge an iron axe,forge a iron axe,forge iron axe,hammer out an iron axe,work the iron into an axe,iron axe',
 'You hammer the glowing bloom again and again to drive out the slag, then draw and fold the iron into an axe-head and haft it — an edge harder than any bronze, that bites deep and dulls slow.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('smelt_iron', 'iron_ore', 2), ('smelt_iron', 'charcoal', 3),
('forge_iron_axe', 'iron_bloom', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('smelt_iron','iron'), ('smelt_iron','ore'), ('smelt_iron','bloom'),
('forge_iron_axe','iron'), ('forge_iron_axe','axe')
ON CONFLICT DO NOTHING;
