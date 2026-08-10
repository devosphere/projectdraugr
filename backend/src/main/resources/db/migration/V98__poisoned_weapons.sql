-- V98: venom-tipped weapons (M1 #75, EPIC #45/#54).
--
-- Real-world-logic ([[feedback_real_world_simulation]]), the poison system. snake_venom, hornet_venom, and
-- formic_acid were orphans — the toxins a Chronicle renders from serpents, hornets, and ant nests, with nothing
-- to do with them. Their true first-era use is to tip a hunting weapon: a poisoned spear envenoms its quarry so
-- the animal fails faster and a kill lands where a plain thrust would only wound. Coat a spear with any of the
-- three venoms to make a poisoned_spear; confront gives it an edge over a plain spear (see
-- WildlifeEncounterService: it counts as a weapon AND adds a poison bonus to the odds of a clean kill).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('poisoned_spear', 'Poisoned spear', 'TOOL', 900, 2400, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('poisoned_spear','HAND_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('poisoned_spear','TECHNIQUE','a spear tipped with rendered venom')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('coat_spear','Poison a spear','poisoned_spear',1,1,NULL,FALSE,FALSE,15,'items','PROCESS','poison the spear,poison a spear,poisoned spear,coat the spear with venom,envenom the spear,venom-tipped spear,tip the spear with venom', 'You work the venom carefully into the barbs and let it dry there — a spear that carries more than an edge now.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('coat_spear','primitive_spear',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Any rendered toxin serves.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('coat_spear','venom','snake_venom',1),('coat_spear','venom','hornet_venom',1),('coat_spear','venom','formic_acid',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('coat_spear','spear'),('coat_spear','poison'),('coat_spear','venom')
ON CONFLICT DO NOTHING;
