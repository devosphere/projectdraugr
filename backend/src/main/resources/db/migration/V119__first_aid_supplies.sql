-- V119: bare-hand first-aid supplies (M1 #125 Recovery, EPIC #123).
--
-- The survival-viability coverage contract (#123 cat. 9) wants wound handling with splints and bandages, not just a
-- poultice. bindWound already grades a medicinal poultice above a bare plant-fibre binding; V119 adds three more
-- pieces, each with a DISTINCT effect the treatment reads (see ChroniclePhysiologyService.bindWound):
--   fibre_bandage_roll – a clean rolled binding: staunches more blood and eases more pain than raw fibre.
--   bark_splint_set    – rigid bark splints: immobilise a break, the biggest single cut to injury_severity.
--   cordage_arm_sling  – rests an injured limb: eases pain and, above all, stress.
-- All bare-hand (tool_class NULL), from soft/loose material already gatherable. These are supplies consumed in the
-- treatment (like the poultice), not equipment, so they carry no body slot.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('fibre_bandage_roll', 'Fibre bandage roll', 'MATERIAL', 60,  120, TRUE, FALSE, 0),
('bark_splint_set',    'Bark splint set',    'MATERIAL', 300, 700, TRUE, FALSE, 0),
('cordage_arm_sling',  'Cordage arm sling',  'MATERIAL', 100, 260, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fibre_bandage_roll', 'TECHNIQUE', 'rolled from clean plant fibre by hand'),
('bark_splint_set',    'TECHNIQUE', 'bark battens tied with cordage by hand'),
('cordage_arm_sling',  'TECHNIQUE', 'knotted from cordage into a sling by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT. Subject terms kept distinct ("bandage"/"splint"/"arm sling") so they neither collide with the
-- grass/hand "wraps"/"sling" nor trip the TREAT_WOUND classifier (which needs bind/bandage/dress + wound/injury).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('roll_fibre_bandage','Roll a fibre bandage','fibre_bandage_roll',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','fibre bandage,make a bandage,roll a bandage,bandage roll,roll a fibre bandage', 'You comb and roll clean plant fibre into a long, even bandage — something to bind a wound with more kindly than a raw handful.', 'VERIFIED', now()),
('make_bark_splint',  'Make a bark splint',  'bark_splint_set',   1,1,NULL,FALSE,FALSE,15,'items','CRAFT','bark splint,make a splint,splint set,make a bark splint,tie a splint', 'You cut stiff bark to length and bind the battens with cordage into a splint that will hold a broken limb still.', 'VERIFIED', now()),
('knot_arm_sling',    'Knot an arm sling',   'cordage_arm_sling', 1,1,NULL,FALSE,FALSE,10,'items','CRAFT','arm sling,make an arm sling,tie an arm sling,cordage arm sling,knot an arm sling', 'You knot cordage into a broad sling to cradle an injured arm against the body and take its weight.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('roll_fibre_bandage','plant_fiber',2),
('make_bark_splint','bark_sheet',2),('make_bark_splint','fiber_cordage',1),
('knot_arm_sling','fiber_cordage',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('roll_fibre_bandage','bandage'),
('make_bark_splint','splint'),
('knot_arm_sling','arm sling')
ON CONFLICT DO NOTHING;
