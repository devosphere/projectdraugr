-- V210 — story #93: the fire-hardened digging stick, completing the group-1-25 tools. A plain branch charred and
-- hardened in the coals into a point that turns earth without splitting — a real digging tool. Mirrors the proven
-- fire_harden_spear recipe (PROCESS, requires_fire, 'harden'/'char' are PROCESS terms). The functional payoff is a
-- one-line widening of PhysicalItemService.tillGround to accept it as a digging tool (alongside digging_stick and
-- wooden_shovel), so a Chronicle who has hardened one can break ground with it. Equippable (dead-end-clean);
-- matter-safe (300 g out of a 350 g branch). Keywords hyphen-free; routing verified locally.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('fire_hardened_digging_stick', 'Fire-hardened digging stick', 'TOOL', 300, 500, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fire_hardened_digging_stick', 'TECHNIQUE', 'a branch pointed and hardened in the fire')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fire_hardened_digging_stick','HAND_RIGHT','CARRIED'),('fire_hardened_digging_stick','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('fire_harden_digging_stick', 'Fire-harden a digging stick', 'fire_hardened_digging_stick', 1,1, NULL, TRUE, FALSE, 25, 'tools', 'PROCESS',
 'fire harden a digging stick,harden a digging stick,char a digging stick,fire hardened digging stick',
 'You point a stout branch and turn it slowly in the coals until the tip darkens and hardens — a digging stick that will bite earth without splitting.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('fire_harden_digging_stick', 'dry_branch', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('fire_harden_digging_stick','digging stick'),('fire_harden_digging_stick','fire hardened digging stick')
ON CONFLICT DO NOTHING;
