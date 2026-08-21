-- V147: bronze axe — a metal working axe of the harder alloy (EPIC #180 / #185 bronze objects).
--
-- Bronze gave the era its weapons (the spear, V146); it also gave it the best working tools. A bronze axe forged
-- from two ingots fells and fights better than copper: it keeps the same keen bronze edge, so it out-cuts stone and
-- out-fights copper. Completes bronze's tool coverage beside the spear. (Felling and combat readers wired in Java.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bronze_axe', 'Bronze axe', 'TOOL', 1050, 800, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bronze_axe', 'TECHNIQUE', 'a bronze axe-head cast, hammered, and hafted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bronze_axe','HAND_RIGHT','CARRIED'), ('bronze_axe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- Forge it from two ingots over a fire with a striking tool. 'forge'->CRAFT (V144); the 'axe' + 'bronze' subjects
-- keep it distinct from the copper axe (copper, not bronze) and the bronze spear (spear, not axe).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_bronze_axe', 'Forge a bronze axe', 'bronze_axe', 1,1, 'STRIKING', TRUE, FALSE, 85, 'tools', 'CRAFT',
 'forge a bronze axe,forge bronze axe,cast a bronze axe,hammer out a bronze axe,work the bronze into an axe,bronze axe',
 'You cast a broad bronze axe-head, hammer and grind its edge keen, and haft it tight — it bites deeper than copper and holds that edge through far more work than stone ever could.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_bronze_axe', 'bronze_ingot', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_bronze_axe','bronze'), ('forge_bronze_axe','axe')
ON CONFLICT DO NOTHING;
