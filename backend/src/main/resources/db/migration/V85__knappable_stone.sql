-- V85: knappable stone raw materials → a usable stone flake (M1 #75 slice, EPIC #45/#54).
--
-- The stone/earth/mineral family of #75, continued through the mineral system (mineral_definition, which
-- gatherMineral reads by biome affinity and rarity). Flint already exists (flint_stone); this adds the two
-- genuinely-new knappable stones — chert (an embedded nodule, freed with a striking tool) and obsidian (loose
-- volcanic-glass shards, picked up by hand in the mountains). Each has at least one VERIFIED use before it is
-- exposed: knapped into a sharp stone flake, which is itself a real cutting edge (wired into hasCuttingTool),
-- the most primitive blade there is. Mirrors V84: obtainable source, obtainable use output, mass < input,
-- keywords carrying the material name so a named stone beats the generic knap, promoted VERIFIED, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('chert_nodule',   'Chert nodule',   'MATERIAL', 700, 400, TRUE,  FALSE, 0),
('obsidian_shard', 'Obsidian shard', 'MATERIAL', 300, 180, TRUE,  FALSE, 0),
('stone_flake',    'Sharp stone flake','TOOL',    90,  60,  TRUE,  TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- Ecological sources. gatherMineral matches the spoken text against the mineral's display_name, so a short
-- display ("Chert", "Obsidian") lets "gather chert" / "collect obsidian" resolve to these and not the first
-- stone in the ground. Chert is locked in rock (a striking tool frees it); obsidian lies loose as glass shards.
INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('chert_nodule',   'Chert',    'HIGHLAND,GRASSLAND,RIVER_BANK,TEMPERATE_FOREST', 0.50, 'STRIKING', 1, 2, 'A fine-grained flintlike stone that knaps to a keen edge.'),
('obsidian_shard', 'Obsidian', 'MOUNTAIN',                                       0.28, NULL,       1, 2, 'Volcanic glass; the sharpest edge the first era can strike.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('chert_nodule',   'MINERAL', 'free chert nodules from rock in highland/grassland/river ground'),
('obsidian_shard', 'MINERAL', 'pick up loose obsidian shards in the mountains'),
('stone_flake',    'TECHNIQUE', 'struck from a knappable stone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- The verified use: knap each stone into a sharp flake. STRIKING tool, PROCESS category (as the existing knap
-- processes), keywords carry the stone name so a named stone outranks the generic knap; subject is the stone.
-- The subject gate keeps these off knap_arrowheads/knap_scraper, whose subjects (arrowhead/scraper) are absent.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('knap_chert_flake',   'Knap a chert flake',   'stone_flake',1,2,'STRIKING',FALSE,FALSE,25,'tools','PROCESS','chert flake,knap chert,knap the chert,strike a flake from the chert,chert blade,chert','You rest the chert nodule and strike it at the right angle, and a long, keen flake springs free.','VERIFIED',now()),
('knap_obsidian_flake','Knap an obsidian flake','stone_flake',1,2,'STRIKING',FALSE,FALSE,25,'tools','PROCESS','obsidian flake,knap obsidian,knap the obsidian,strike a flake from the obsidian,obsidian blade,obsidian','You strike the obsidian and it parts like nothing else in the world, leaving an edge finer than any stone.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('knap_chert_flake','chert_nodule',1),
('knap_obsidian_flake','obsidian_shard',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('knap_chert_flake','chert'),
('knap_obsidian_flake','obsidian')
ON CONFLICT DO NOTHING;
