-- V114: bare-hand food/water handling (M1 #196, EPIC #191).
--
-- Handling is not cooking and not purification. A Chronicle can shell, crack, peel, and rinse produce with bare
-- hands, but doing so never makes unsafe food safe: a peeled root and a rinsed root are cleaner and cook-ready, not
-- edible-by-fiat — they feed the existing root stew, they are not a meal on their own; acorns still need leaching;
-- and a WALNUT's hard shell cannot be opened by hand at all, so crack_walnut carries tool_class STRIKING and a
-- bare-hand attempt fails grounded, routing the player to a hammerstone. Only the soft-shelled hazelnut yields to
-- the hands. This is the handling/safety distinction #196 asks for, enforced by data rather than narration.

-- Handling verbs the classifier did not yet know. All PROCESS (material transformation), matching crack/wash/rinse
-- which are already PROCESS. No existing keyword contains these words, so adding them cannot re-route anything.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','peel',1),('PROCESS','husk',1),('PROCESS','shell',1),('PROCESS','shuck',1)
ON CONFLICT (category_key, term) DO NOTHING;

-- Handled produce: edible nut kernels (raw-edible) and cook-ready root forms (still require the stew fire).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('hazelnut_kernel', 'Hazelnut kernel', 'FOOD', 4,  5,  TRUE, FALSE, 0),
('walnut_kernel',   'Walnut kernel',   'FOOD', 8,  10, TRUE, FALSE, 0),
('peeled_root',     'Peeled root',     'FOOD', 90, 110, TRUE, FALSE, 0),
('washed_root',     'Washed root',     'FOOD', 110, 130, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('hazelnut_kernel', 'TECHNIQUE', 'shelled from a hazelnut by hand'),
('walnut_kernel',   'TECHNIQUE', 'cracked from a walnut with a striking stone'),
('peeled_root',     'TECHNIQUE', 'peeled from a root by hand'),
('washed_root',     'TECHNIQUE', 'a root rinsed clean in reachable water by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- All PROCESS. shell_hazelnut/peel_root/wash_root are bare-hand (tool_class NULL); crack_walnut needs a STRIKING
-- tool because a walnut's shell will not open to bare hands. wash_root requires reachable water.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('shell_hazelnut', 'Shell a hazelnut',  'hazelnut_kernel', 1,1,NULL,     FALSE,FALSE,5, 'items','PROCESS','shell a hazelnut,shell the hazelnuts,shell hazelnuts,crack a hazelnut,crack open a hazelnut', 'You press the hazelnut until the thin shell gives and pick the sweet kernel out whole.', 'VERIFIED', now()),
('crack_walnut',   'Crack a walnut',    'walnut_kernel',   1,1,'STRIKING',FALSE,FALSE,4, 'items','PROCESS','crack a walnut,crack open a walnut,crack the walnuts,crack walnuts,shell a walnut', 'You set the walnut on a stone and strike it — the hard shell splits and you work the kernel out of its bony chambers.', 'VERIFIED', now()),
('peel_root',      'Peel a root',       'peeled_root',     1,1,NULL,     FALSE,FALSE,4, 'items','PROCESS','peel a root,peel the root,peel a tuber,peel the tuber,peel a rhizome,peel the rhizome', 'You scrape and thumb the tough skin off the root, leaving the pale starchy core ready for the pot.', 'VERIFIED', now()),
('wash_root',      'Rinse a root',      'washed_root',     1,1,NULL,     FALSE,TRUE, 3, 'items','PROCESS','wash the root,wash a root,rinse the root,rinse a root,wash the produce,rinse the produce', 'You swill the root in the water and rub the grit and soil from it — clean of dirt, though the water itself makes nothing safe.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Single-input handling for the nuts.
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('shell_hazelnut','hazelnut',1),
('crack_walnut','walnut',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Root handling takes any one gathered root; wash also accepts an already-peeled root.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('peel_root','root','burdock_root',1),('peel_root','root','bulrush_root',1),('peel_root','root','cattail_rhizome',1),
('wash_root','root','burdock_root',1),('wash_root','root','bulrush_root',1),('wash_root','root','peeled_root',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('shell_hazelnut','hazelnut'),
('crack_walnut','walnut'),
('peel_root','root'),('peel_root','tuber'),('peel_root','rhizome'),
('wash_root','root'),('wash_root','produce')
ON CONFLICT DO NOTHING;

-- Keep the handled roots functional, not orphans: the root stew accepts them as it accepts a raw root.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('cook_root_stew','root','peeled_root',1),('cook_root_stew','root','washed_root',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
