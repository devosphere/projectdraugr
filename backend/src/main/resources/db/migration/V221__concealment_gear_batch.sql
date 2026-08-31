-- V221 — story #93 catalogue batch 14: concealment gear. Three items that make a Chronicle harder to catch in a
-- passive ambush, wired into WildlifeEncounterService.passiveEncounter (which already lowers ambush odds for going
-- to ground, a camp alarm, a fence, a fire-brand) — pure data plus a two-line widening:
--   camouflage_cloak   } break the outline: -12 to the ambush chance (stacks with actively hiding)
--   hide_screen        }
--   scent_mask_bundle  -> masks the smell a nose-led predator follows: -8 to the ambush chance
-- All equippable (dead-end-clean). Craft verb 'assemble' dodges CRAFT_GARMENT (whose verbs are sew/stitch/craft/
-- make/weave) even for the cloak, and carries no CONSTRUCT ambiguity; 'hide' here is a material, not the DISENGAGE
-- 'hide from'. Routing verified locally vs matcher + hard-intent + precedence. Ambush odds are a roll, so the test
-- asserts the craft, not the encounter.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('camouflage_cloak',  'Camouflage cloak',   'CLOTHING', 600, 2000, FALSE, TRUE, 1),
('hide_screen',       'Hide screen',        'TOOL',     900, 4000, FALSE, TRUE, 0),
('scent_mask_bundle', 'Scent-mask bundle',  'TOOL',     120, 300,  FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('camouflage_cloak',  'TECHNIQUE', 'a leaf-and-hide cloak that breaks the outline'),
('hide_screen',       'TECHNIQUE', 'a staked hide screen to sit behind unseen'),
('scent_mask_bundle', 'TECHNIQUE', 'a bundle of pungent herbs that masks the scent')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('camouflage_cloak','TORSO','OUTER'),('camouflage_cloak','BACK','CARRIED'),
('hide_screen','BACK','CARRIED'),('hide_screen','HAND_RIGHT','CARRIED'),
('scent_mask_bundle','WAIST','ATTACHED'),('scent_mask_bundle','BACK','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('assemble_camouflage_cloak', 'Assemble a camouflage cloak', 'camouflage_cloak',  1,1,NULL,FALSE,FALSE,50,'tools','CRAFT',
 'assemble a camouflage cloak,rig a camouflage cloak,camouflage cloak',
 'You work leaves, grass, and strips of hide into a ragged cloak that dissolves your shape against the brush.','VERIFIED',now()),
('assemble_hide_screen',      'Assemble a hide screen',      'hide_screen',       1,1,NULL,FALSE,FALSE,45,'tools','PROCESS',
 'assemble a hide screen,rig a hide screen,hide screen,stalking screen',
 'You stretch hide over a light frame of staves — a screen to sit behind, watching a run unseen.','VERIFIED',now()),
('assemble_scent_mask',       'Assemble a scent-mask bundle','scent_mask_bundle', 1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'assemble a scent mask bundle,assemble a scent mask,scent mask bundle,scent mask',
 'You bind a bundle of pungent, earthy herbs to carry — it drowns your own scent so a nose-led animal does not read you on the wind.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('assemble_camouflage_cloak', 'tanned_leather',1),('assemble_camouflage_cloak','plant_fiber',1),
('assemble_hide_screen',      'tanned_leather',1),('assemble_hide_screen','dry_branch',1),
('assemble_scent_mask',       'plant_fiber',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('assemble_camouflage_cloak','camouflage cloak'),('assemble_camouflage_cloak','camouflage'),
('assemble_hide_screen','hide screen'),('assemble_hide_screen','stalking screen'),
('assemble_scent_mask','scent mask bundle'),('assemble_scent_mask','scent mask')
ON CONFLICT DO NOTHING;
