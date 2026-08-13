-- V125: sling — a thrown-stone force multiplier (M1 #126 defence / #123 cat.7, EPIC #123).
--
-- confront already lets a Chronicle pelt a threat with field stones (a small edge, and the ONLY way to reach an
-- AERIAL attacker). A sling turns that from a desperate lob into a real weapon: cast from cordage with a hide
-- pouch, bare-hand. When one is to hand, thrown stones hit far harder and further (see WildlifeEncounterService:
-- the stones term climbs from up-to-10 to up-to-24). No stones, no shot — an empty sling does nothing.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('sling', 'Sling', 'WEAPON', 150, 300, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('sling','HAND_RIGHT','ATTACHED'),('sling','HAND_LEFT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('sling', 'TECHNIQUE', 'cast from cordage with a hide pouch by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT. Distinct sling phrasings; "grass sling"/"arm sling" makers keep their own longer keywords.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_sling','Make a sling','sling',1,1,NULL,FALSE,FALSE,20,'items','CRAFT','make a sling,leather sling,shot sling,throwing sling,stone sling', 'You braid two cords to a small hide pouch and knot the release — a sling to cast a stone with a hunter''s force.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_sling','fiber_cordage',2),('make_sling','rawhide',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_sling','sling')
ON CONFLICT DO NOTHING;
