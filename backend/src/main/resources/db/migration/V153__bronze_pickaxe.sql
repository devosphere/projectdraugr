-- V153: bronze pickaxe — a metal pick that wins finer ore (EPIC #180 heavy industry / #183-#184 extraction).
--
-- Closes the extraction loop: the metal a Chronicle smelts lets them forge a proper pickaxe, and a metal pick
-- breaks a nodule out clean where a cobble or a stone hammer shatters it — so a careful worker with one selects a
-- FINER mineral than bare stone tools could, and finer ore smelts to finer metal and finer tools. A bronze pick
-- also breaks ore free where the bare-hand gate demands a striking tool. (Both reads wired in PhysicalItemService
-- .gatherMineral; ripple-safe — no pick, no change.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bronze_pickaxe', 'Bronze pickaxe', 'TOOL', 1100, 800, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bronze_pickaxe', 'TECHNIQUE', 'a bronze pick-head cast, hammered, and hafted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bronze_pickaxe','HAND_RIGHT','CARRIED'), ('bronze_pickaxe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- Forge it from an ingot and a haft over a fire with a striking tool. 'forge'->CRAFT (V144); the pick subject and
-- the 'forge' verb keep it clear of the CRAFT_PICKAXE intent (which needs 'make'/'craft' + 'pickaxe').
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_bronze_pickaxe', 'Forge a bronze pickaxe', 'bronze_pickaxe', 1,1, 'STRIKING', TRUE, FALSE, 75, 'tools', 'CRAFT',
 'forge a bronze pickaxe,forge bronze pickaxe,forge a bronze pick,hammer out a bronze pickaxe,work the bronze into a pick,bronze pickaxe',
 'You cast and hammer a stout bronze pick-head to a hard point, and haft it tight — it bites into rock and breaks a nodule out whole where a cobble would only crush it.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_bronze_pickaxe', 'bronze_ingot', 1), ('forge_bronze_pickaxe', 'dry_branch', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_bronze_pickaxe','bronze'), ('forge_bronze_pickaxe','pickaxe'), ('forge_bronze_pickaxe','pick')
ON CONFLICT DO NOTHING;
