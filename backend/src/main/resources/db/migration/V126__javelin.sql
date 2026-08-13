-- V126: fire-hardened javelin — a thrown spear (M1 #132 ranged / #123 cat.7, EPIC #123).
--
-- The ranged-piercing option #132 wants, distinct from the sling (thrown stones, blunt) and the spear (melee). A
-- light shaft with a fire-hardened point, thrown at a threat: it strikes before melee range and — like a thrown
-- stone — can reach an AERIAL attacker a hand never could. Bare-hand to make (the point is hardened in a fire, no
-- blade). In confront it adds a flat ranged-piercing edge (+25, below a melee spear's +35) and opens the air.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('javelin', 'Fire-hardened javelin', 'WEAPON', 600, 1400, TRUE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('javelin','HAND_RIGHT','ATTACHED'),('javelin','HAND_LEFT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('javelin', 'TECHNIQUE', 'a straight shaft with a fire-hardened point, worked by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT; the point is hardened in a fire (requires_fire), so no blade is needed. "javelin"/"throwing
-- spear" subjects keep it clear of the melee spear crafts.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_javelin','Make a javelin','javelin',1,1,NULL,TRUE,FALSE,25,'items','CRAFT','javelin,make a javelin,throwing spear,light throwing spear,cast spear', 'You true a straight shaft and char its point hard in the fire, working it to a hardened tip — a spear light enough to throw.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_javelin','dry_branch',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_javelin','javelin'),('make_javelin','throwing spear')
ON CONFLICT DO NOTHING;
