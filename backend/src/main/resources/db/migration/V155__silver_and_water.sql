-- V155: silver and clean water (EPIC #180 heavy industry / #188 gold, silver, lead).
--
-- The second soft metal, and again its worth is not an edge. Silver is too soft for a tool, but it keeps water
-- sweet: something in the bright metal holds off the rot, so water drunk from a silver cup sits far easier in the
-- gut than water drunk raw from a skin. This gives silver its own end-to-end chain — mine the ore, smelt it, cast a
-- cup — and a real, distinctive use: the safest way to drink untreated water short of boiling it. (The drink read is
-- wired in ChronicleActionService; ripple-safe — no silver cup, no change.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('silver_ore',   'Silver ore',  'MATERIAL', 1200, 340, TRUE,  FALSE, 0),
('silver_ingot', 'Silver ingot','MATERIAL', 850,  85,  TRUE,  FALSE, 0),
('silver_cup',   'Silver cup',  'TOOL',     220,  260, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('silver_ore',   'MINERAL',   'grey-black silver ore in mountain and highland rock, often beside lead'),
('silver_ingot', 'TECHNIQUE', 'smelted and cupelled from silver ore over a hot fire'),
('silver_cup',   'TECHNIQUE', 'cast and beaten from a silver ingot into a drinking cup')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('silver_ore', 'Silver ore', 'MOUNTAIN,HIGHLAND', 0.15, 'STRIKING', 1, 1,
 'Scarce grey-black silver ore in exposed rock, often in the same veins as galena.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('silver_cup','HAND_RIGHT','CARRIED'), ('silver_cup','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 'smelt'->PROCESS (V144), 'cast'->CRAFT (V154), so both route by an ordinary sentence.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('smelt_silver', 'Smelt silver', 'silver_ingot', 1,1, NULL, TRUE, FALSE, 90, 'items', 'PROCESS',
 'smelt silver,smelt the silver,smelt silver ore,smelt the silver ore,reduce the silver ore,cupel the silver',
 'You roast and reduce the grey ore, then drive the fire hot to cupel it, until a bright bead of silver gathers and cools to a small heavy ingot.', 'VERIFIED', now()),
('cast_silver_cup', 'Cast a silver cup', 'silver_cup', 1,1, NULL, TRUE, FALSE, 60, 'tools', 'CRAFT',
 'cast a silver cup,cast a silver vessel,make a silver cup,pour a silver cup,beat out a silver cup,silver cup',
 'You melt the silver and cast it, then beat and smooth it into a shallow drinking cup — the bright metal that keeps water sweeter than any clay.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('smelt_silver', 'silver_ore', 2), ('smelt_silver', 'charcoal', 2),
('cast_silver_cup', 'silver_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('smelt_silver','silver'), ('smelt_silver','ore'),
('cast_silver_cup','silver'), ('cast_silver_cup','cup'), ('cast_silver_cup','vessel')
ON CONFLICT DO NOTHING;

UPDATE material_process SET station_kind = 'bloomery_furnace' WHERE process_key = 'smelt_silver';
