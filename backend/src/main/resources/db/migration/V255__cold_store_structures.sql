-- V255 — story #77 (groups 2, keys 35-36): cold-storage food structures that add a GENUINELY NEW effect, not a
-- re-keying of an existing mechanic. root_cellar and cool_food_pit are buildable one-stage STRUCTURE assemblies
-- (construction_kind, NOT is_shelter / NOT is_workstation, decaying field structures). Their effect is wired in Java
-- (FoodPreservationService.advanceTo): while the keeper stands on ground holding a COMPLETED cold store, carried food
-- ages far slower — the cool, stable air holds a larder near-suspended, the counterpart to the #218 pest penalty that
-- ages food FASTER on fouled ground. Phrases lead with build/dig (not a gather verb), so 'root cellar' dodges the
-- GATHER_PLANT 'root' trigger, and neither collides with BUILD_STORAGE_AREA (needs 'storage area'/'storehouse'/…) or
-- BUILD_LATRINE (needs latrine/refuse/midden). Keys 35 (root_cellar) and 36 (cool_food_pit) of #77's group 2.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('ROOT_CELLAR',   'Root cellar',   'construction', FALSE, FALSE, TRUE, 'V255'),
('COOL_FOOD_PIT', 'Cool food pit', 'construction', FALSE, FALSE, TRUE, 'V255')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('root_cellar','STRUCTURE','Root cellar',FALSE,NULL,'ROOT_CELLAR','construction',
 'build a root cellar,dig a root cellar,dig out a root cellar,root cellar',
 'root cellar',
 'A chamber dug down into the earth, stone-lined and roofed over with poles and bark and a cap of soil — the deep ground holds it cool and even the year round, so what is stored here keeps far longer than anything left in the open air.','VERIFIED',now()),
('cool_food_pit','STRUCTURE','Cool food pit',FALSE,NULL,'COOL_FOOD_PIT','construction',
 'build a cool food pit,dig a cool food pit,dig a cold food pit,cool food pit,cold food pit',
 'cool food pit,cold food pit',
 'A shallow pit lined with stone and cool grass and covered against the sun — humbler than a cellar, but the shaded, earth-cooled hollow still slows the spoiling of a day''s food well past what the open ground allows.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('root_cellar_build','root_cellar',1,'Dig, line, and roof the cellar',NULL,0,NULL,FALSE,'You dig a chamber into the earth, line it with stone, and roof it over with poles, bark, and a cap of soil until it holds cool and even.'),
('cool_food_pit_build','cool_food_pit',1,'Dig, line, and cover the pit',NULL,0,NULL,FALSE,'You dig a shallow pit, line it with stone and cool grass, and cover it against the sun until the hollow holds the day''s food cool.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('root_cellar_build','field_stone',3),('root_cellar_build','dry_branch',2),('root_cellar_build','bark_sheet',2),
('cool_food_pit_build','field_stone',2),('cool_food_pit_build','dry_grass_bundle',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;
