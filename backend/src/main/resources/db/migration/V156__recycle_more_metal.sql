-- V156: recycle more metal (EPIC #180 heavy industry — completing the recycling loop).
--
-- V148 let a worn bronze AXE be melted back to an ingot, but a worn bronze spear or pickaxe, or a worn copper axe,
-- was a dead end — which contradicts the whole point of metal over stone: it can be won back and made anew. This
-- broadens bronze recycling to any of the heavy bronze tools and adds copper recycling, so no smelted metal object
-- is ever truly spent.

-- Broaden melt_down_bronze from the fixed axe input to a group of the heavy bronze tools (all heavier than the
-- ingot they yield, so mass still conserves). The small bronze knife is left out — too little metal for a full ingot.
DELETE FROM material_process_input WHERE process_key='melt_down_bronze';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('melt_down_bronze','bronze_object','bronze_axe',1),
('melt_down_bronze','bronze_object','bronze_spear',1),
('melt_down_bronze','bronze_object','bronze_pickaxe',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

UPDATE material_process SET keywords = 'melt down the bronze,melt down the bronze axe,melt the bronze,remelt the bronze,recycle the bronze,recover the bronze'
 WHERE process_key='melt_down_bronze';
INSERT INTO process_subject (process_key, subject_term) VALUES
('melt_down_bronze','spear'), ('melt_down_bronze','pickaxe')
ON CONFLICT DO NOTHING;

-- Copper recycles too: a worn copper axe melts back to a copper ingot (copper was the first metal ever recycled).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('melt_down_copper', 'Melt down copper', 'copper_ingot', 1,1, NULL, TRUE, FALSE, 45, 'items', 'PROCESS',
 'melt down the copper,melt down the copper axe,melt the copper,remelt the copper,recycle the copper,recover the copper',
 'You set the worn copper axe-head in the coals until the soft metal slumps and runs, and pour it off into an ingot to forge again — the oldest trick of the metalworker.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('melt_down_copper', 'copper_axe', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('melt_down_copper','copper'), ('melt_down_copper','axe')
ON CONFLICT DO NOTHING;
