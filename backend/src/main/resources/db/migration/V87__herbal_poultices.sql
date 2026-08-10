-- V87: medicinal herbs → herbal poultice (M1 #75 slice, EPIC #45/#54).
--
-- The medicine family of #75. yarrow_bundle, comfrey_leaf, and plantain_leaf already gather from the world but
-- had NO use — an #75 acceptance gap (a material must have a verified use before it is exposed). This closes it:
-- each herb is pounded into an herbal_poultice, and a poultice makes wound-dressing markedly more effective than
-- a bare fibre binding (wired into bindWound). Processing verbs (pound/crush/mash/prepare) are not code intents,
-- so these resolve through the material-process matcher; PROCESS category, keywords carry the herb name, output
-- mass below input, promoted VERIFIED, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('herbal_poultice', 'Herbal poultice', 'MATERIAL', 24, 30, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('herbal_poultice', 'TECHNIQUE', 'pounded from a medicinal herb')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- One process per herb, each pinning that herb's use. yarrow staunches bleeding, comfrey knits, plantain draws.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('poultice_yarrow',  'Pound a yarrow poultice',  'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','yarrow poultice,pound yarrow,crush yarrow,mash yarrow,prepare a yarrow poultice,yarrow dressing,yarrow','You bruise and pound the yarrow to a green pulp — the old field dressing that slows a wound''s bleeding.','VERIFIED',now()),
('poultice_comfrey', 'Pound a comfrey poultice', 'herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','comfrey poultice,pound comfrey,crush comfrey,mash comfrey,prepare a comfrey poultice,comfrey dressing,comfrey','You mash the comfrey leaf to a cool paste — knitbone, laid over a hurt to help it close.','VERIFIED',now()),
('poultice_plantain','Pound a plantain poultice','herbal_poultice',1,1,NULL,FALSE,FALSE,20,'items','PROCESS','plantain poultice,pound plantain,crush plantain,mash plantain,prepare a plantain poultice,plantain dressing,plantain','You chew and crush the plantain leaf into a drawing poultice for a wound or a sting.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('poultice_yarrow','yarrow_bundle',2),
('poultice_comfrey','comfrey_leaf',2),
('poultice_plantain','plantain_leaf',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('poultice_yarrow','yarrow'),
('poultice_comfrey','comfrey'),
('poultice_plantain','plantain')
ON CONFLICT DO NOTHING;
