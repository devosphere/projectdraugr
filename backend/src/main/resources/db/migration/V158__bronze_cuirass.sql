-- V158: bronze cuirass — metal armour (EPIC #180 heavy industry / #184 copper objects; the defence side of metal).
--
-- The metal ladder tiered the EDGE — a keener, harder weapon at every step — but defence was stuck in the stone
-- age: boiled leather, knapped scale, a shell helm. A forged bronze cuirass turns a blow far better than any of
-- them, which is the whole reason armies went to bronze and then iron. This is the defensive counterpart to the
-- metal weapons: the metals a Chronicle smelts now protect as well as they kill. (The blunting read is in
-- WildlifeEncounterService.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bronze_cuirass', 'Bronze cuirass', 'CLOTHING', 2400, 6000, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bronze_cuirass', 'TECHNIQUE', 'a bronze breastplate cast, beaten to shape, and fitted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Worn over the torso as protection.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bronze_cuirass','TORSO','PROTECTION')
ON CONFLICT DO NOTHING;

-- Forged from three ingots ('forge'->CRAFT, V144); the cuirass/breastplate subject keeps it distinct.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_bronze_cuirass', 'Forge a bronze cuirass', 'bronze_cuirass', 1,1, 'STRIKING', TRUE, FALSE, 150, 'tools', 'CRAFT',
 'forge a bronze cuirass,forge bronze cuirass,beat out a bronze cuirass,hammer out a bronze breastplate,make a bronze cuirass,bronze cuirass,bronze breastplate',
 'You cast bronze in broad sheets and beat them over a form into a breastplate and backplate, fitting and riveting them to sit close over the chest — armour that turns a blow no leather or shell could.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_bronze_cuirass', 'bronze_ingot', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_bronze_cuirass','bronze'), ('forge_bronze_cuirass','cuirass'), ('forge_bronze_cuirass','breastplate')
ON CONFLICT DO NOTHING;

-- A worn-out cuirass is heavy bronze too — it recycles back to ingots like the axe, spear, and pickaxe.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('melt_down_bronze','bronze_object','bronze_cuirass',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
