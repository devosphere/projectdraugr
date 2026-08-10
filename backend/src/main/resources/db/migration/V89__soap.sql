-- V89: soap from lye and tallow (M1 #75 slice, EPIC #45/#54).
--
-- The utilitarian/hygiene end of #75. Wood ash already leaches to lye (leach_lye → lye_solution) and animal fat
-- already renders to tallow (→ rendered_tallow); both were used elsewhere but the chain never closed on soap.
-- This adds it: lye + tallow saponify into soap, and washing with soap lifts far more dirt than water alone
-- (wired into wash). Inputs already obtainable; make_soap is CRAFT (its natural verb is "make"); output mass
-- below input; VERIFIED, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('soap', 'Bar of soap', 'MATERIAL', 200, 180, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('soap', 'TECHNIQUE', 'saponified from lye and tallow')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_soap','Make a bar of soap','soap',1,1,NULL,TRUE,FALSE,80,'items','CRAFT','soap,make soap,make a bar of soap,tallow soap,soap from lye,craft soap','You boil the lye into the tallow until it thickens and takes, then set it to harden into soap that will actually lift grease.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_soap','lye_solution',1),
('make_soap','rendered_tallow',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_soap','soap')
ON CONFLICT DO NOTHING;
