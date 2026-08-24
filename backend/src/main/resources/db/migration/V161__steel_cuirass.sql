-- V161: steel cuirass — the top defensive rung, to match the steel edge (EPIC #180 heavy industry / #187 steel).
--
-- The edge ladder tops out at steel (a carburised axe out-cuts iron); the defence ladder stopped at iron. Steel
-- armour turns a blow better than iron for the same reason a steel edge cuts better — case-hardening works carbon
-- into the surface, giving a harder, tougher skin. Early steel was won from finished iron, not smelted apart, so a
-- steel cuirass is a forged iron cuirass carburised in charcoal at heat — exactly as carburise_iron_axe (V151)
-- makes a steel axe from an iron one. This completes the symmetry: leather < scale < bronze < iron < steel, on the
-- defence side as on the offence side, so the metals a Chronicle wins protect as well as they kill at every rung.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('steel_cuirass', 'Steel cuirass', 'CLOTHING', 2600, 6200, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('steel_cuirass', 'TECHNIQUE', 'an iron cuirass case-hardened by carburising in charcoal at heat')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Worn over the torso as protection, like the bronze and iron cuirasses.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('steel_cuirass','TORSO','PROTECTION')
ON CONFLICT DO NOTHING;

-- 'carburise' is a PROCESS term (V151); the cuirass/breastplate subject keeps it distinct from carburise_iron_axe.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carburise_iron_cuirass', 'Case-harden an iron cuirass', 'steel_cuirass', 1,1, NULL, TRUE, FALSE, 180, 'items', 'PROCESS',
 'carburise the iron cuirass,carburize the iron cuirass,case-harden the iron cuirass,case harden the cuirass,case-harden the breastplate,steel the iron cuirass,pack the cuirass in charcoal',
 'You pack the whole iron cuirass in a bed of charcoal and hold it at a steady red heat for the best part of a day; carbon creeps into the plate and, quenched, its skin comes up steel — harder and tougher than iron, turning a blow that iron would take.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carburise_iron_cuirass', 'iron_cuirass', 1), ('carburise_iron_cuirass', 'charcoal', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carburise_iron_cuirass','steel'), ('carburise_iron_cuirass','cuirass'), ('carburise_iron_cuirass','breastplate')
ON CONFLICT DO NOTHING;
