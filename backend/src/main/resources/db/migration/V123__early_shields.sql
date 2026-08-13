-- V123: early improvised shields (M1 #126 defence / #123 cat.7, EPIC #123).
--
-- A war shield already exists and turns a mauling aside (7 in confront). But it is a late, heavy build; a
-- vulnerable Chronicle needs cover on day one. These three improvised shields are graded below it — a bark or
-- woven-reed shield is light and turns the least (4 in confront), a rawhide-over-frame shield rather more (6) —
-- so early defence is real "cover/deflection" (#126) without making a body untouchable. All are held in a hand
-- and bare-hand to assemble (the rawhide's wooden frame is a pre-made component). See WildlifeEncounterService.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bark_shield',       'Bark shield',        'TOOL', 700,  2000, FALSE, TRUE, 0),
('woven_reed_shield', 'Woven reed shield',  'TOOL', 900,  2200, FALSE, TRUE, 0),
('rawhide_shield',    'Rawhide shield',     'TOOL', 1300, 2600, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Held in either hand, strapped on (matching war_shield's ATTACHED layer).
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bark_shield','HAND_LEFT','ATTACHED'),('bark_shield','HAND_RIGHT','ATTACHED'),
('woven_reed_shield','HAND_LEFT','ATTACHED'),('woven_reed_shield','HAND_RIGHT','ATTACHED'),
('rawhide_shield','HAND_LEFT','ATTACHED'),('rawhide_shield','HAND_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bark_shield',       'TECHNIQUE', 'folded and lashed from bark by hand'),
('woven_reed_shield', 'TECHNIQUE', 'woven from reed and lashed by hand'),
('rawhide_shield',    'TECHNIQUE', 'rawhide stretched over a wooden frame by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT. Distinct "<material> shield" subjects so they don't collide with the war shield.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_bark_shield',   'Make a bark shield',   'bark_shield',       1,1,NULL,FALSE,FALSE,30,'items','CRAFT','bark shield,make a bark shield,fold a bark shield', 'You layer and lash sheets of bark into a broad guard — light and rough, but it will turn a claw or a thrown stone.', 'VERIFIED', now()),
('make_reed_shield',   'Make a reed shield',   'woven_reed_shield', 1,1,NULL,FALSE,FALSE,40,'items','CRAFT','reed shield,woven reed shield,make a reed shield,make a woven reed shield', 'You weave reed tightly over a hooped frame and lash it fast — a light shield to keep between you and the teeth.', 'VERIFIED', now()),
('make_rawhide_shield','Make a rawhide shield','rawhide_shield',    1,1,NULL,FALSE,FALSE,45,'items','CRAFT','rawhide shield,make a rawhide shield,hide shield,make a hide shield', 'You stretch rawhide over a bent wooden frame and let it draw tight — heavier, but it takes a blow far better than bark or reed.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_bark_shield','bark_sheet',5),('make_bark_shield','fiber_cordage',1),
('make_reed_shield','reed_bundle',3),('make_reed_shield','fiber_cordage',1),
('make_rawhide_shield','rawhide',1),('make_rawhide_shield','wooden_component',1),('make_rawhide_shield','fiber_cordage',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_bark_shield','bark shield'),
('make_reed_shield','reed shield'),('make_reed_shield','woven reed shield'),
('make_rawhide_shield','rawhide shield'),('make_rawhide_shield','hide shield')
ON CONFLICT DO NOTHING;
