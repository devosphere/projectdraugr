-- V79: field cooking + provisioning objects (M1 #60, EPIC #54).
--
-- Preservation PROCEDURES are already rich (dry_meat/fish/mushrooms/herbs, smoke_meat/fish/fowl, salt_meat/fish,
-- brine_fish, render_tallow, make_pemmican, preserve_berries) and a smoke_hood structure exists. This adds the
-- missing #60 OBJECTS: field-cooking gear, a drying mat, a food-carrying pouch, and one new preservation route
-- (fermentation). Same pattern: VERIFIED, obtainable inputs, subjects, mass < input, probe-clean.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('cooking_tripod',       'Cooking tripod',       'TOOL',      1400, 5000, FALSE, TRUE,  0),
('stone_griddle',        'Stone griddle',        'TOOL',      2600, 1800, FALSE, TRUE,  0),
('drying_mat',           'Drying mat',           'TOOL',      600,  4000, FALSE, TRUE,  0),
('travel_ration_pouch',  'Travel ration pouch',  'CONTAINER', 250,  1500, FALSE, TRUE,  0),
('fermented_vegetables', 'Fermented vegetables', 'FOOD',      220,  200,  TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('travel_ration_pouch', 3000, 3500)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('cooking_tripod','HAND_RIGHT','CARRIED'), ('cooking_tripod','HAND_LEFT','CARRIED'),
('stone_griddle','HAND_RIGHT','CARRIED'), ('stone_griddle','HAND_LEFT','CARRIED'),
('drying_mat','BACK','CARRIED'),
('travel_ration_pouch','WAIST','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_cooking_tripod','Make a cooking tripod', 'cooking_tripod',      1,1,'CUTTING',FALSE,FALSE,50,'items','CRAFT',  'cooking tripod,pot tripod,make a cooking tripod,lash a cooking tripod,set up a tripod', 'You lash three stout poles into a tripod that stands over the fire to hang a pot or a hook from.', 'VERIFIED', now()),
('dress_stone_griddle','Dress a stone griddle', 'stone_griddle',       1,1,'STRIKING',FALSE,FALSE,70,'items','PROCESS','stone griddle,cooking stone,dress a stone griddle,make a griddle,shape a griddle', 'You dress a broad, flat stone to sit level in the coals — a griddle to bake and sear food on.', 'VERIFIED', now()),
('weave_drying_mat',  'Weave a drying mat',    'drying_mat',          1,1,'CUTTING',FALSE,FALSE,60,'items','CRAFT',  'drying mat,make a drying mat,weave a drying mat,drying rack mat', 'You weave a broad open mat to spread food on so air passes above and below and dries it evenly.', 'VERIFIED', now()),
('sew_ration_pouch',  'Sew a ration pouch',    'travel_ration_pouch', 1,1,'CUTTING',FALSE,FALSE,45,'items','CRAFT',  'travel ration pouch,ration pouch,sew a ration pouch,make a ration pouch', 'You sew a small, close pouch to carry a day''s dried food dry and to hand on the trail.', 'VERIFIED', now()),
('ferment_vegetables','Ferment vegetables',    'fermented_vegetables',1,2,NULL,     FALSE,FALSE,90,'items','PROCESS','ferment vegetables,ferment the greens,fermented vegetables,make sauerkraut,pickle vegetables,ferment', 'You pack the greens down under salt and their own brine and leave them to sour and keep far longer than fresh.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_cooking_tripod','dry_branch',3), ('make_cooking_tripod','fiber_cordage',2),
('dress_stone_griddle','stone_slab',1),
('weave_drying_mat','plant_fiber',4),
('sew_ration_pouch','tanned_leather',1), ('sew_ration_pouch','leather_cord',1),
('ferment_vegetables','wild_berries',3), ('ferment_vegetables','ground_salt',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_cooking_tripod','tripod'),
('dress_stone_griddle','griddle'),
('weave_drying_mat','mat'),
('sew_ration_pouch','pouch'), ('sew_ration_pouch','ration'),
('ferment_vegetables','vegetables'), ('ferment_vegetables','greens'), ('ferment_vegetables','sauerkraut')
ON CONFLICT DO NOTHING;
