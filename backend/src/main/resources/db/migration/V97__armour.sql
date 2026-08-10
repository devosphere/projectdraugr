-- V97: armour from scale, shell, and chitin (M1 #75, EPIC #45/#54).
--
-- Real-world-logic ([[feedback_real_world_simulation]]), the defence system. wyvern_scale, wyvern_wing_membrane,
-- turtle_shell, and chitin_fragment were orphans — the hardest, most protective things a Chronicle can take off
-- a monster, and no way to wear any of it. This gives them their true use: armour. Worn armour turns part of a
-- wildlife mauling aside (wired into WildlifeEncounterService.confront — each piece blunts the wound, never
-- erases it). Three pieces cover the body: a scale cuirass, a chitin helm, and a war shield (rigid turtle shell
-- or membrane stretched over a frame). Crafted with a cutting edge and cordage.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('scale_armour', 'Scale cuirass', 'CLOTHING', 1200, 900,  FALSE, TRUE, 0),
('chitin_helm',  'Chitin helm',   'CLOTHING', 420,  600,  FALSE, TRUE, 0),
('war_shield',   'War shield',    'TOOL',     1600, 3000, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('scale_armour','TORSO','OUTER'),
('chitin_helm','HEAD','CLOTHING'),
('war_shield','HAND_LEFT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('scale_armour','TECHNIQUE','sewn from wyvern scales'),
('chitin_helm','TECHNIQUE','shaped from chitin plate'),
('war_shield','TECHNIQUE','built from turtle shell or wing membrane over a frame')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('craft_scale_armour','Sew a scale cuirass','scale_armour',1,1,'CUTTING',FALSE,FALSE,90,'items','CRAFT','scale armour,scale armor,scale cuirass,make scale armour,sew scale armour,craft scale armour', 'You overlap the scales row on row and sew them down to a backing until they turn a point — a cuirass that will take a blow.', 'VERIFIED', now()),
('craft_chitin_helm','Shape a chitin helm','chitin_helm',1,1,'CUTTING',FALSE,FALSE,60,'items','CRAFT','chitin helm,chitin helmet,make a chitin helm,shape a chitin helm,craft a helm', 'You fit and lash the chitin plates into a hard shell for the head, curved to shed a strike.', 'VERIFIED', now()),
('craft_war_shield','Build a war shield','war_shield',1,1,'CUTTING',FALSE,FALSE,70,'items','CRAFT','war shield,make a shield,build a shield,craft a shield,shield', 'You brace the shell — or stretch the tough membrane over a bent frame — and grip it up into a shield that stands between you and what comes.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('craft_scale_armour','wyvern_scale',6),('craft_scale_armour','fiber_cordage',2),
('craft_chitin_helm','chitin_fragment',8),('craft_chitin_helm','fiber_cordage',1),
('craft_war_shield','dry_branch',2),('craft_war_shield','fiber_cordage',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- The shield face is rigid shell or tough membrane — either does.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('craft_war_shield','face','turtle_shell',1),('craft_war_shield','face','wyvern_wing_membrane',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('craft_scale_armour','armour'),('craft_scale_armour','armor'),('craft_scale_armour','cuirass'),
('craft_chitin_helm','helm'),('craft_chitin_helm','helmet'),
('craft_war_shield','shield')
ON CONFLICT DO NOTHING;
