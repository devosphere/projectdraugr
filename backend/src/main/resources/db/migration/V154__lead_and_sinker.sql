-- V154: lead and the fishing sinker (EPIC #180 heavy industry / #188 gold, silver, lead).
--
-- Not every metal is for an edge. Lead is soft and useless for a tool, but it is dense and melts in a cook-fire,
-- which makes it the sinker metal: a lead weight on a hand-line carries the baited hook down to the deeper fish and
-- holds it steady in the current, where a bare line drifts. This gives lead its own end-to-end chain — mine galena,
-- smelt it, cast small sinkers — and a real, distinctive use: better angling. (The line read is wired in
-- WildlifeEncounterService.fish; ripple-safe — no sinker, no change.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('lead_ore',    'Lead ore (galena)', 'MATERIAL', 1300, 380, TRUE,  FALSE, 0),
('lead_ingot',  'Lead ingot',        'MATERIAL', 900,  90,  TRUE,  FALSE, 0),
('lead_sinker', 'Lead sinker',       'TOOL',     180,  40,  TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('lead_ore',    'MINERAL',   'galena — heavy grey lead ore in mountain and highland rock'),
('lead_ingot',  'TECHNIQUE', 'smelted from galena over a fire'),
('lead_sinker', 'TECHNIQUE', 'cast from molten lead into small weights')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('lead_ore', 'Lead ore (galena)', 'MOUNTAIN,HIGHLAND', 0.25, 'STRIKING', 1, 2,
 'Galena — heavy, soft, blue-grey cubes of lead ore in exposed rock, often beside silver.')
ON CONFLICT (mineral_key) DO NOTHING;

-- 'cast' is decisive CRAFT work (pouring molten metal to shape); 'smelt'->PROCESS is from V144.
INSERT INTO category_term (category_key, term, weight) VALUES
('CRAFT','cast',3)
ON CONFLICT (category_key, term) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('smelt_lead', 'Smelt lead', 'lead_ingot', 1,1, NULL, TRUE, FALSE, 55, 'items', 'PROCESS',
 'smelt lead,smelt the lead,smelt lead ore,smelt the lead ore,smelt the galena,reduce the lead ore',
 'You bank the soft grey ore in among the coals; lead runs early and easy, pooling silver-bright and cooling to a dull heavy ingot.', 'VERIFIED', now()),
('cast_lead_sinker', 'Cast lead sinkers', 'lead_sinker', 2,4, NULL, TRUE, FALSE, 30, 'tools', 'CRAFT',
 'cast a lead sinker,cast lead sinkers,pour lead sinkers,melt lead into sinkers,make lead sinkers,lead sinker',
 'You melt the lead down and pour it into little hollows pressed in the earth, cooling to a handful of smooth heavy sinkers to weight a line.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('smelt_lead', 'lead_ore', 2), ('smelt_lead', 'charcoal', 1),
('cast_lead_sinker', 'lead_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('smelt_lead','lead'), ('smelt_lead','ore'), ('smelt_lead','galena'),
('cast_lead_sinker','lead'), ('cast_lead_sinker','sinker'), ('cast_lead_sinker','sinkers')
ON CONFLICT DO NOTHING;

-- Lead smelts finer at a proper hearth too, like the other metals.
UPDATE material_process SET station_kind = 'bloomery_furnace' WHERE process_key = 'smelt_lead';
