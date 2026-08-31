-- V215 — story #93 catalogue batch 8: weapon maintenance gear. Two carried kits that let a Chronicle put an edge
-- back on a dulled tool or weapon in the field, wired into PhysicalItemService.repairItem's sharpen path (which read
-- only found/dressed whetstones and grit-stones until now) — a one-line widening plus pure data:
--   sharpening_kit         — a hone and strop in a small roll
--   weapon_maintenance_roll — a fuller kit of hones, grit, and binding for edge and haft
-- Both equippable (dead-end-clean). Honing OUTCOME is deterministic (a worn edge comes back to SOUND), so the test
-- proves it end to end. Craft phrases avoid 'sharpen' (which is the REPAIR_ITEM verb) and win their category
-- outright under the precedence tie-break; verified locally against the material matcher, the hard-intent
-- classifier, and precedence.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('sharpening_kit',          'Sharpening kit',          'TOOL', 300, 400, FALSE, TRUE, 0),
('weapon_maintenance_roll', 'Weapon maintenance roll', 'TOOL', 450, 900, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('sharpening_kit',          'TECHNIQUE', 'a hone and strop bound in a small roll'),
('weapon_maintenance_roll', 'TECHNIQUE', 'a roll of hones, grit, and binding for tool and weapon care')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('sharpening_kit','WAIST','ATTACHED'),('sharpening_kit','BACK','CARRIED'),
('weapon_maintenance_roll','WAIST','ATTACHED'),('weapon_maintenance_roll','BACK','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('assemble_sharpening_kit', 'Assemble a honing kit',       'sharpening_kit',          1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'assemble a honing kit,make up a honing kit,honing kit,sharpening kit',
 'You bind a grit-stone hone and a leather strop into a small roll — everything the hand needs to draw an edge back keen.','VERIFIED',now()),
('sew_maintenance_roll',    'Sew a maintenance roll',      'weapon_maintenance_roll', 1,1,NULL,FALSE,FALSE,45,'tools','CRAFT',
 'sew a maintenance roll,stitch a maintenance roll,maintenance roll,tool roll',
 'You sew a leather roll with pockets for hones, grit, and cordage — a full kit for keeping an edge and a haft sound in the field.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('assemble_sharpening_kit', 'sandstone_piece',1),('assemble_sharpening_kit','tanned_leather',1),
('sew_maintenance_roll',    'tanned_leather',1),('sew_maintenance_roll','sandstone_piece',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('assemble_sharpening_kit','honing kit'),('assemble_sharpening_kit','sharpening kit'),
('sew_maintenance_roll','maintenance roll'),('sew_maintenance_roll','tool roll')
ON CONFLICT DO NOTHING;
