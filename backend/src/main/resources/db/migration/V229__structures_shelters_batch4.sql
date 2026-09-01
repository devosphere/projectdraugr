-- V229 — story #77 slice 4: dwelling types. Six more one-stage STRUCTURE assemblies, each a construction_kind marked
-- is_shelter (a completed dwelling shelters the body through the existing ConstructionService/physiology shelter
-- effect). Same pure-data pattern as V226-V228. Distinct multiword keywords: 'pit house' is not BUILD_FIRE_PIT's
-- 'fire pit'; 'windward screen' is not PLACE_WINDBREAK's 'windbreak' nor rain_screen; 'hide lodge' is not hide_tent;
-- 'bark cabin' is not bark_shelter/bark_door; 'log shelter' is not bark_shelter. None collides with a Java BUILD_.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('PIT_HOUSE',       'Pit house',       'construction', TRUE, FALSE, TRUE, 'V229'),
('LOG_SHELTER',     'Log shelter',     'construction', TRUE, FALSE, TRUE, 'V229'),
('BRUSH_DOME',      'Brush dome',      'construction', TRUE, FALSE, TRUE, 'V229'),
('HIDE_LODGE',      'Hide lodge',      'construction', TRUE, FALSE, TRUE, 'V229'),
('WINDWARD_SCREEN', 'Windward screen', 'construction', TRUE, FALSE, TRUE, 'V229'),
('BARK_CABIN',      'Bark cabin',      'construction', TRUE, FALSE, TRUE, 'V229')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('pit_house','STRUCTURE','Pit house',FALSE,NULL,'PIT_HOUSE','construction',
 'build a pit house,dig a pit house,sink a pit house,pit house',
 'pit house',
 'A floor sunk a little into the earth and roofed over with a low frame of poles and bark — the ground''s own warmth banked around a body against the deep cold.','VERIFIED',now()),
('log_shelter','STRUCTURE','Log shelter',FALSE,NULL,'LOG_SHELTER','construction',
 'build a log shelter,raise a log shelter,notch up a log shelter,log shelter',
 'log shelter',
 'Split timber notched and stacked into low walls under a pitched cover — a solid log shelter that stands against wind and weather far longer than boughs.','VERIFIED',now()),
('brush_dome','STRUCTURE','Brush dome',FALSE,NULL,'BRUSH_DOME','construction',
 'build a brush dome,bend up a brush dome,weave a brush dome,brush dome',
 'brush dome',
 'Supple rods bent into a dome and lashed, then thatched over with brush — a rounded shell that holds still, warm air within against the night.','VERIFIED',now()),
('hide_lodge','STRUCTURE','Hide lodge',FALSE,NULL,'HIDE_LODGE','construction',
 'build a hide lodge,raise a hide lodge,pitch a hide lodge,hide lodge',
 'hide lodge',
 'A tall cone of poles wrapped and pegged with dressed hides, a smoke-hole left at the crown — a hide lodge roomy enough to hold a fire and its warmth through the longest night.','VERIFIED',now()),
('windward_screen','STRUCTURE','Windward screen',FALSE,NULL,'WINDWARD_SCREEN','construction',
 'build a windward screen,set a windward screen,windward screen',
 'windward screen',
 'A low wall of thatched reed set square to the prevailing wind — it breaks the cold blast off the sitting-place and the fire behind it.','VERIFIED',now()),
('bark_cabin','STRUCTURE','Bark cabin',FALSE,NULL,'BARK_CABIN','construction',
 'build a bark cabin,raise a bark cabin,shingle a bark cabin,bark cabin',
 'bark cabin',
 'Walls and a pitched roof clad wholly in lapped slabs of bark on a pole frame — a dry, walled bark cabin to winter in, not merely sit out a squall under.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('pit_house_build','pit_house',1,'Sink the floor and roof it',NULL,0,NULL,FALSE,'You sink the floor a little into the earth and roof it over with poles and bark until the ground''s warmth banks around you.'),
('log_shelter_build','log_shelter',1,'Notch and stack the walls',NULL,0,NULL,FALSE,'You notch split timber and stack it into low walls under a pitched cover until the shelter stands solid.'),
('brush_dome_build','brush_dome',1,'Bend the dome and thatch it',NULL,0,NULL,FALSE,'You bend supple rods into a dome, lash them, and thatch it over with brush until still, warm air holds within.'),
('hide_lodge_build','hide_lodge',1,'Raise the cone and wrap the hides',NULL,0,NULL,FALSE,'You stand a tall cone of poles and wrap and peg the dressed hides around it, a smoke-hole left at the crown.'),
('windward_screen_build','windward_screen',1,'Set the screen to windward',NULL,0,NULL,FALSE,'You set a low wall of thatched reed square to the wind until it breaks the cold blast off the ground behind.'),
('bark_cabin_build','bark_cabin',1,'Frame and clad in bark',NULL,0,NULL,FALSE,'You raise a pole frame and clad walls and roof wholly in lapped slabs of bark until it stands dry and walled.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('pit_house_build','dry_branch',3),('pit_house_build','bark_sheet',2),
('log_shelter_build','timber_plank',3),
('brush_dome_build','hazel_rod',3),('brush_dome_build','plant_fiber',2),
('hide_lodge_build','tanned_leather',2),('hide_lodge_build','dry_branch',2),
('windward_screen_build','reed_bundle',2),
('bark_cabin_build','bark_sheet',4)
ON CONFLICT (stage_key, item_key) DO NOTHING;
