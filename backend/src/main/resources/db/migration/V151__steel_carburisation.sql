-- V151: steel — case-hardened iron, the keenest edge of the age (EPIC #180 heavy industry / #187 steel carburisation).
--
-- The top of the metal ladder. Early steel was not smelted apart but WON from finished iron: pack an iron tool in
-- charcoal and hold it at a red heat for hours, and carbon works into the surface, hardening the edge to a steel
-- that takes and keeps a bite no plain iron can. This carburises a forged iron axe into a steel axe. No furnace of
-- its own yet — like the smelts it gates on a hot fire; the dedicated bloomery is a later refinement.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('steel_axe', 'Steel axe', 'TOOL', 1050, 800, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('steel_axe', 'TECHNIQUE', 'an iron axe case-hardened by carburising in charcoal at heat')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('steel_axe','HAND_RIGHT','CARRIED'), ('steel_axe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 'carburise' is decisive PROCESS work (working carbon into iron to make steel).
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','carburise',3), ('PROCESS','carburize',3)
ON CONFLICT (category_key, term) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carburise_iron_axe', 'Case-harden an iron axe', 'steel_axe', 1,1, NULL, TRUE, FALSE, 130, 'items', 'PROCESS',
 'carburise the iron axe,carburize the iron axe,case-harden the iron axe,case harden the axe,steel the iron axe,pack the axe in charcoal',
 'You pack the iron axe-head in a bed of charcoal and hold it at a steady red heat for hours; carbon creeps into the metal and, quenched, the edge comes up steel — harder and keener than any iron, and slow to dull.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carburise_iron_axe', 'iron_axe', 1), ('carburise_iron_axe', 'charcoal', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carburise_iron_axe','steel'), ('carburise_iron_axe','iron'), ('carburise_iron_axe','axe')
ON CONFLICT DO NOTHING;
