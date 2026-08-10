-- V92: cooking recipes — ingredients become specific dishes (M1 #75, EPIC #45/#54).
--
-- Real-world-logic ([[feedback_real_world_simulation]]): a food that is a real cooking ingredient must be
-- cookable into a specific dish, not only eaten raw. The catalogue already holds the ingredients — roots, grain,
-- acorn flour, herbs, mushrooms, nuts, berries, honey, water — but the only heat-cooking path was COOK_MEAT.
-- This adds seven first-era dishes as material processes: each needs a fire, most need water, and each consumes
-- real ingredients into an edible dish (nourished through EAT). Input GROUPS let a slot take any of several
-- ingredients (any aromatic, any mushroom, any nut, any berry, any potable water), so the recipes are flexible
-- as real cooking is. All PROCESS category so "cook/boil/bake/simmer/stew/roast/brew the X" resolves.
--
-- First, a vocabulary correction: "cook" was miscategorised as INHABIT (which has zero processes), so "cook a
-- stew" resolved to nothing. Cooking is PROCESS work; this fixes it (no process relied on the old mapping).

UPDATE category_term SET category_key='PROCESS' WHERE term='cook';

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('root_vegetable_stew','Root vegetable stew','FOOD', 450, 400, TRUE, FALSE, 0),
('grain_porridge',     'Grain porridge',     'FOOD', 400, 350, TRUE, FALSE, 0),
('acorn_flatbread',    'Acorn flatbread',    'FOOD', 180, 160, TRUE, FALSE, 0),
('herbal_infusion',    'Herbal infusion',    'FOOD', 300, 300, TRUE, FALSE, 0),
('cooked_mushrooms',   'Cooked mushrooms',   'FOOD', 160, 150, TRUE, FALSE, 0),
('trail_cake',         'Nut-and-honey cake', 'FOOD', 200, 160, TRUE, FALSE, 0),
('berry_compote',      'Berry compote',      'FOOD', 260, 220, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('root_vegetable_stew','TECHNIQUE','simmered from roots and aromatics'),
('grain_porridge',     'TECHNIQUE','boiled from wild grain'),
('acorn_flatbread',    'TECHNIQUE','baked from acorn flour'),
('herbal_infusion',    'TECHNIQUE','steeped from a herb'),
('cooked_mushrooms',   'TECHNIQUE','cooked from foraged mushrooms'),
('trail_cake',         'TECHNIQUE','baked from nuts and honey'),
('berry_compote',      'TECHNIQUE','stewed from berries and honey')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('cook_root_stew',   'Simmer a root vegetable stew','root_vegetable_stew',1,1,NULL,TRUE,FALSE,45,'items','PROCESS','vegetable stew,root stew,pottage,simmer a stew,stew the vegetables,cook a stew,boil a stew,make a stew', 'You cut the roots down into a pot with the aromatics and water and let it simmer over the fire into a thick, savoury stew.', 'VERIFIED', now()),
('cook_porridge',    'Boil grain porridge',        'grain_porridge',     1,1,NULL,TRUE,FALSE,35,'items','PROCESS','porridge,grain porridge,gruel,boil porridge,cook porridge,simmer porridge,make porridge', 'You boil the grain in water, stirring, until it softens and thickens into a plain, filling porridge.', 'VERIFIED', now()),
('bake_flatbread',   'Bake acorn flatbread',       'acorn_flatbread',    1,2,NULL,TRUE,FALSE,30,'items','PROCESS','flatbread,acorn flatbread,acorn bread,bake flatbread,bake bread,cook flatbread,make flatbread', 'You work the acorn flour and water into a stiff dough and bake it flat on a hot stone into a dense bread.', 'VERIFIED', now()),
('brew_infusion',    'Steep a herbal infusion',    'herbal_infusion',    1,1,NULL,TRUE,FALSE,15,'items','PROCESS','herbal tea,herb tea,herbal infusion,brew tea,steep tea,cook tea,make tea,brew an infusion', 'You steep the herb in water just off the boil until it colours and gives up its scent into a warming infusion.', 'VERIFIED', now()),
('cook_mushrooms',   'Cook foraged mushrooms',     'cooked_mushrooms',   1,1,NULL,TRUE,FALSE,20,'items','PROCESS','cooked mushrooms,fried mushrooms,saute mushrooms,fry mushrooms,roast mushrooms,cook mushrooms,cook the mushrooms', 'You cook the mushrooms through over the fire until they darken and soften and lose their raw edge.', 'VERIFIED', now()),
('bake_trail_cake',  'Bake a nut-and-honey cake',  'trail_cake',         1,1,NULL,TRUE,FALSE,40,'items','PROCESS','trail cake,nut cake,honey cake,bake a nut cake,cook a nut cake,make a trail cake', 'You bind the pounded nuts with honey and bake them into a dense, keeping cake for the trail.', 'VERIFIED', now()),
('stew_compote',     'Stew a berry compote',       'berry_compote',      1,1,NULL,TRUE,FALSE,25,'items','PROCESS','compote,berry compote,stewed berries,stew the berries,cook the berries,simmer the berries,make compote', 'You stew the berries down with a little honey until they collapse into a dark, sweet-sharp compote.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Fixed inputs (the defining ingredient of each dish).
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('cook_root_stew','cattail_rhizome',1),
('cook_porridge','wild_grain',2),
('bake_flatbread','acorn_flour',2),
('bake_trail_cake','raw_honey',1),
('stew_compote','raw_honey',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Input groups (a slot that any of several ingredients can fill — real cooking flexibility).
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
-- water for the boiled/simmered/steeped dishes
('cook_root_stew','water','clean_water',1),('cook_root_stew','water','filtered_water',1),
('cook_porridge','water','clean_water',1),('cook_porridge','water','filtered_water',1),
('bake_flatbread','water','clean_water',1),('bake_flatbread','water','filtered_water',1),
('brew_infusion','water','clean_water',1),('brew_infusion','water','filtered_water',1),
-- aromatics for the stew
('cook_root_stew','aromatic','wild_onion_bulb',1),('cook_root_stew','aromatic','wild_garlic_bulb',1),('cook_root_stew','aromatic','garlic_clove_wild',1),
-- the herb for the infusion
('brew_infusion','herb','mint_sprig',1),('brew_infusion','herb','nettle_leaf',1),('brew_infusion','herb','dandelion_leaf',1),
-- any foraged mushroom
('cook_mushrooms','mushroom','chanterelle',2),('cook_mushrooms','mushroom','porcini',2),('cook_mushrooms','mushroom','oyster_mushroom',2),('cook_mushrooms','mushroom','lions_mane',2),
-- any nut for the cake
('bake_trail_cake','nut','hazelnut',2),('bake_trail_cake','nut','walnut',2),('bake_trail_cake','nut','chestnut',2),('bake_trail_cake','nut','pine_nut',2),
-- any berry for the compote
('stew_compote','berry','blackberry',3),('stew_compote','berry','elderberry',3),('stew_compote','berry','wild_berries',3),('stew_compote','berry','hawthorn_berry',3),('stew_compote','berry','wild_rose_hip',3)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('cook_root_stew','stew'),('cook_root_stew','pottage'),
('cook_porridge','porridge'),('cook_porridge','gruel'),
('bake_flatbread','flatbread'),('bake_flatbread','bread'),
('brew_infusion','tea'),('brew_infusion','infusion'),
('cook_mushrooms','mushroom'),('cook_mushrooms','mushrooms'),
('bake_trail_cake','cake'),
('stew_compote','compote'),('stew_compote','berries')
ON CONFLICT DO NOTHING;
