-- V100: greens, roots, and morels into the cooking system (M1 #75, EPIC #45/#54).
--
-- Net-new breadth + the food-should-cook principle ([[feedback_real_world_simulation]]): burdock and bulrush
-- roots, nettle and watercress greens were edible raw but did not cook, and the morel — a prized edible — was
-- missing. This gives them real culinary uses through the V92 system: the root-vegetable stew's root slot now
-- takes cattail, burdock, OR bulrush; a new cooked-greens dish wilts nettle/watercress/dandelion; and the
-- morel joins the pan of cooked mushrooms.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('cooked_greens', 'Cooked greens', 'FOOD', 150, 140, TRUE, FALSE, 0),
('morel',         'Morel',         'FOOD', 20,  30,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('morel', 'FUNGI', 'TEMPERATE_FOREST', NULL, 14, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('morel', 'morel', 1, 3, 'SPRING')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('cooked_greens', 'TECHNIQUE',  'wilted from foraged greens'),
('morel',         'FLORA_DROP', 'forage morels in spring forest')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- The stew's root can be any of three roots (was a fixed cattail rhizome).
DELETE FROM material_process_input WHERE process_key='cook_root_stew' AND item_key='cattail_rhizome';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('cook_root_stew','root','cattail_rhizome',1),('cook_root_stew','root','burdock_root',1),('cook_root_stew','root','bulrush_root',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

-- A new cooked-greens dish.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('cook_greens','Wilt a pan of greens','cooked_greens',1,1,NULL,TRUE,FALSE,15,'items','PROCESS','cooked greens,cook the greens,boil the greens,wilt the greens,cook greens,steam the greens,pot of greens', 'You wilt the greens down in a little water over the fire until they soften and lose their bite.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('cook_greens','greens','nettle_leaf',2),('cook_greens','greens','watercress_bundle',2),('cook_greens','greens','dandelion_leaf',2),
('cook_greens','water','clean_water',1),('cook_greens','water','filtered_water',1),
-- morel joins the pan of cooked mushrooms.
('cook_mushrooms','mushroom','morel',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('cook_greens','greens')
ON CONFLICT DO NOTHING;
