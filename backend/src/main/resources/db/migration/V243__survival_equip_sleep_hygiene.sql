-- V243 — story #95, slice 6: sleep and hygiene gear. Five genuinely-new items (the other three #95 sleep/hygiene
-- entries already exist and are mapped at close, not duplicated: reed_sleeping_mat = reed_mat via weave_reed_mat
-- (its keywords already include 'sleeping mat'); grass_sleeping_mat = temporary_grass_carry_mat via weave_grass_mat;
-- moss_absorbent_pad = moss_pad). All five are equippable carried gear (a groundsheet/blanket wrapped for warmth off
-- the ground; a washcloth/comb/toothpick as personal kit), so each is reachable, made bare-handed, and dead-end-exempt.
-- Phrases avoid MAKE_BED's 'sleeping mat'/'bed'/'pallet' words and lead with 'make' (CRAFT). Two-axis sim checked;
-- 'make a bark comb' (17) beats the existing carve_bone_comb's bare 'comb' keyword on length within CRAFT.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('bark_groundsheet', 'Bark groundsheet', 'CLOTHING', 300, 1100, FALSE, TRUE, 4, 8),
('fibre_blanket',    'Fibre blanket',    'CLOTHING', 280, 1200, FALSE, TRUE, 10, 1),
('reed_washcloth',   'Reed washcloth',   'CLOTHING', 50,  150,  FALSE, TRUE, 0, 0),
('bark_comb',        'Bark comb',        'CLOTHING', 30,  60,   FALSE, TRUE, 0, 0),
('wooden_toothpick', 'Wooden toothpick', 'CLOTHING', 5,   10,   FALSE, TRUE, 0, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bark_groundsheet','BACK','CARRIED'),
('fibre_blanket','TORSO','CARRIED'),
('reed_washcloth','WAIST','ATTACHED'),
('bark_comb','WAIST','ATTACHED'),
('wooden_toothpick','WAIST','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bark_groundsheet','TECHNIQUE','bark sheets bound into a groundsheet to lie on off the wet'),
('fibre_blanket','TECHNIQUE','plant fibre knotted into a blanket for warmth'),
('reed_washcloth','TECHNIQUE','a soft reed cloth for washing down'),
('bark_comb','TECHNIQUE','a comb cut and notched from stiff bark'),
('wooden_toothpick','TECHNIQUE','a sliver of wood pointed for a toothpick')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_bark_groundsheet','Bind a bark groundsheet','bark_groundsheet',1,1,NULL,FALSE,FALSE,25,'items','CRAFT','bark groundsheet,make a bark groundsheet,groundsheet', 'You bind bark sheets into a groundsheet to lie on, off the cold wet ground.', 'VERIFIED', now()),
('make_fibre_blanket','Knot a fibre blanket','fibre_blanket',1,1,NULL,FALSE,FALSE,35,'items','CRAFT','fibre blanket,make a fibre blanket,knot a fibre blanket', 'You knot plant fibre into a coarse blanket for warmth.', 'VERIFIED', now()),
('make_reed_washcloth','Make a reed washcloth','reed_washcloth',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','reed washcloth,make a reed washcloth', 'You work reed into a soft cloth for washing down.', 'VERIFIED', now()),
('make_bark_comb','Cut a bark comb','bark_comb',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','bark comb,make a bark comb,cut a bark comb', 'You cut and notch a comb from a piece of stiff bark.', 'VERIFIED', now()),
('make_wooden_toothpick','Point a wooden toothpick','wooden_toothpick',1,1,NULL,FALSE,FALSE,4,'items','CRAFT','wooden toothpick,make a wooden toothpick,point a toothpick', 'You whittle a sliver of wood to a point for a toothpick.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_bark_groundsheet','bark_sheet',3),
('make_fibre_blanket','plant_fiber',4),
('make_reed_washcloth','reed_bundle',1),
('make_bark_comb','bark_sheet',1),
('make_wooden_toothpick','dry_branch',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_bark_groundsheet','groundsheet'),
('make_fibre_blanket','blanket'),
('make_reed_washcloth','washcloth'),
('make_bark_comb','bark comb'),('make_bark_comb','comb'),
('make_wooden_toothpick','toothpick')
ON CONFLICT DO NOTHING;
