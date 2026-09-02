-- V254 — story #77 slice 5: the remaining group-1 (shelter/sleep/weather) structures that are cleanly buildable as
-- pure-data one-stage STRUCTURE assemblies, each a construction_kind marked is_shelter (a completed build shelters the
-- body through the existing ConstructionService/physiology shelter effect). Same pattern as V226-V229. Phrasing dodges
-- the Java hard intents and existing assembly keywords: 'leaning branch shelter' is not START_LEAN_TO's 'lean-to';
-- 'cave niche' carries no 'sleep' word (SLEEP steals any 'sleeping'); 'low stone wall' is not V172 dry_stone_wall;
-- 'daub wall' is not V228 wattle_wall; 'earth berm' collides with no Java BUILD_. The three #77 beds (brush/reed/hide)
-- and brush_windbreak are NOT built here — they are already the MAKE_BED and PLACE_WINDBREAK mechanics, mapped at close.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('LEANING_BRANCH_SHELTER', 'Leaning branch shelter', 'construction', TRUE, FALSE, TRUE, 'V254'),
('SNOW_SHELTER',           'Snow shelter',           'construction', TRUE, FALSE, TRUE, 'V254'),
('CAVE_NICHE',             'Cave niche',             'construction', TRUE, FALSE, TRUE, 'V254'),
('DAUB_WALL',              'Daub wall',              'construction', TRUE, FALSE, TRUE, 'V254'),
('STONE_WALL_LOW',         'Low stone wall',         'construction', TRUE, FALSE, TRUE, 'V254'),
('EARTH_BERM_WALL',        'Earth berm wall',        'construction', TRUE, FALSE, TRUE, 'V254')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('leaning_branch_shelter','STRUCTURE','Leaning branch shelter',FALSE,NULL,'LEANING_BRANCH_SHELTER','construction',
 'build a leaning branch shelter,lean up a branch shelter,leaning branch shelter,branch shelter',
 'leaning branch shelter,branch shelter',
 'A row of long branches leant against a ridge pole and a fallen trunk, closed over with brush — the simplest roof a body can throw up before dark to turn the worst of the wind and wet.','VERIFIED',now()),
('snow_shelter','STRUCTURE','Snow shelter',FALSE,NULL,'SNOW_SHELTER','construction',
 'build a snow shelter,dig a snow shelter,hollow out a snow shelter,snow shelter',
 'snow shelter',
 'A hollow dug back into a packed drift and roofed with branches and more snow — the still, dead air inside holds a body''s own warmth far better than the open, where the wind would kill.','VERIFIED',now()),
('cave_sleeping_niche','STRUCTURE','Cave niche',FALSE,NULL,'CAVE_NICHE','construction',
 'prepare a cave niche,line a cave niche,fit out a cave niche,cave niche,rock niche',
 'cave niche,rock niche',
 'A dry alcove in the rock cleared of loose stone and lined thick with grass and brush — the stone at your back breaks the wind and holds the day''s warmth long into the night.','VERIFIED',now()),
('daub_wall','STRUCTURE','Daub wall',FALSE,NULL,'DAUB_WALL','construction',
 'build a daub wall,daub a wall,raise a daub wall,daub wall',
 'daub wall',
 'A panel of woven rods packed both sides with wet clay daub and left to firm — a solid wall that turns wind and rain where a screen of brush would only slow them.','VERIFIED',now()),
('stone_wall_low','STRUCTURE','Low stone wall',FALSE,NULL,'STONE_WALL_LOW','construction',
 'build a low stone wall,course a low stone wall,lay up a low stone wall,low stone wall',
 'low stone wall',
 'Field stone coursed dry into a low wall, hip high — enough to break the wind off a sitting-place or fire and mark the edge of a camp, and it will stand untended for years.','VERIFIED',now()),
('earth_berm_wall','STRUCTURE','Earth berm wall',FALSE,NULL,'EARTH_BERM_WALL','construction',
 'build an earth berm,raise an earth berm,heap up an earth berm,earth berm wall,earth berm',
 'earth berm wall,earth berm',
 'Earth heaped high against a staked face of brush and packed firm — a bank that stops the wind dead and, set to the weather side, keeps a camp behind it dry and still.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('leaning_branch_shelter_build','leaning_branch_shelter',1,'Lean the branches and close them with brush',NULL,0,NULL,FALSE,'You lean long branches against a ridge and close them over with brush until they turn the wind and wet.'),
('snow_shelter_build','snow_shelter',1,'Dig back the drift and roof the hollow',NULL,0,NULL,FALSE,'You hollow a chamber back into the packed drift and roof it over until the still air inside holds your warmth.'),
('cave_sleeping_niche_build','cave_sleeping_niche',1,'Clear the alcove and line it',NULL,0,NULL,FALSE,'You clear the loose stone from a dry alcove and line it thick with grass and brush until the rock keeps you warm.'),
('daub_wall_build','daub_wall',1,'Wattle the panel and daub it',NULL,0,NULL,FALSE,'You weave rods into a panel and pack it both sides with wet clay daub, leaving it to firm into a solid wall.'),
('stone_wall_low_build','stone_wall_low',1,'Course the field stone',NULL,0,NULL,FALSE,'You course field stone dry into a low, hip-high wall that breaks the wind and will stand untended.'),
('earth_berm_wall_build','earth_berm_wall',1,'Stake the face and heap the earth',NULL,0,NULL,FALSE,'You stake a face of brush and heap earth against it, packing it firm into a bank that stops the wind dead.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('leaning_branch_shelter_build','dry_branch',3),('leaning_branch_shelter_build','plant_fiber',1),
('snow_shelter_build','dry_branch',2),
('cave_sleeping_niche_build','dry_grass_bundle',2),('cave_sleeping_niche_build','plant_fiber',1),
('daub_wall_build','hazel_rod',2),('daub_wall_build','clay_lump',3),
('stone_wall_low_build','field_stone',4),
('earth_berm_wall_build','dry_branch',2),('earth_berm_wall_build','plant_fiber',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;
