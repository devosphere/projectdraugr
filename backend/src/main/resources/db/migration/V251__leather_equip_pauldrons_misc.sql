-- V251 — story #96, slice 8 (final): shoulder pauldrons and field pieces. leather_sling_carrier is made by "make a
-- leather arm carrier" (not "sling", owned by EQUIP); leather_wound_wrap by "make a leather wound wrap" ('wound' only
-- routes to TREAT_WOUND with a bind/bandage/dress verb, not 'make'). Paired pauldrons to their own SHOULDER slot.
-- All CRAFT, tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('leather_pauldron_left',  'Leather pauldron (left)',  'CLOTHING', 250, 650, FALSE, TRUE, 2, 8),
('leather_pauldron_right', 'Leather pauldron (right)', 'CLOTHING', 250, 650, FALSE, TRUE, 2, 8),
('leather_sling_carrier',  'Leather sling carrier',    'CLOTHING', 200, 600, FALSE, TRUE, 1, 8),
('leather_wound_wrap',     'Leather wound wrap',       'CLOTHING', 60,  180, FALSE, TRUE, 1, 6)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('leather_pauldron_left','SHOULDER_LEFT','PROTECTION'),('leather_pauldron_right','SHOULDER_RIGHT','PROTECTION'),
('leather_sling_carrier','TORSO','CARRIED'),
('leather_wound_wrap','WAIST','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('leather_pauldron_left','TECHNIQUE','a leather plate laced over the left shoulder'),
('leather_pauldron_right','TECHNIQUE','a leather plate laced over the right shoulder'),
('leather_sling_carrier','TECHNIQUE','a leather carrier to cradle an arm or a load across the body'),
('leather_wound_wrap','TECHNIQUE','a strip of soft leather to bind over a dressed wound')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_leather_pauldron_left','Lace a left leather pauldron','leather_pauldron_left',1,1,'CUTTING',FALSE,FALSE,26,'items','CRAFT','make a left leather pauldron,left leather pauldron', 'You lace a leather plate over the left shoulder.', 'VERIFIED', now()),
('make_leather_pauldron_right','Lace a right leather pauldron','leather_pauldron_right',1,1,'CUTTING',FALSE,FALSE,26,'items','CRAFT','make a right leather pauldron,right leather pauldron', 'You lace a leather plate over the right shoulder.', 'VERIFIED', now()),
('make_leather_sling_carrier','Sew a leather arm carrier','leather_sling_carrier',1,1,'CUTTING',FALSE,FALSE,25,'items','CRAFT','leather arm carrier,make a leather arm carrier,arm carrier', 'You cut and sew a leather carrier to cradle an arm or bear a load across the body.', 'VERIFIED', now()),
('make_leather_wound_wrap','Cut a leather wound wrap','leather_wound_wrap',1,1,'CUTTING',FALSE,FALSE,10,'items','CRAFT','leather wound wrap,make a leather wound wrap,wound wrap', 'You cut a strip of soft leather to bind over a dressed wound.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_leather_pauldron_left','tanned_leather',1),('make_leather_pauldron_right','tanned_leather',1),
('make_leather_sling_carrier','tanned_leather',1),
('make_leather_wound_wrap','tanned_leather',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_leather_pauldron_left','pauldron'),('make_leather_pauldron_right','pauldron'),
('make_leather_sling_carrier','arm carrier'),('make_leather_sling_carrier','carrier'),
('make_leather_wound_wrap','wound wrap')
ON CONFLICT DO NOTHING;
