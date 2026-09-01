-- V227 — story #77 slice 2: sleeping and weather-cutting structures. Six more one-stage STRUCTURE assemblies, each
-- a construction_kind marked is_shelter so a completed one improves rest / cuts wind, rain, and smoke through the
-- existing shelter effect. Same data-driven pattern as V226. 'build a raised bed platform' keeps the word 'platform'
-- so it is NOT swallowed by MAKE_BED (which owns 'build a bed'); 'bench'/'hood'/'screen'/'hanging' collide with no
-- Java build intent.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('RAISED_BED_PLATFORM', 'Raised bed platform',  'construction', TRUE, FALSE, TRUE, 'V227'),
('SLEEPING_BENCH',      'Sleeping bench',        'construction', TRUE, FALSE, TRUE, 'V227'),
('SMOKE_HOOD',          'Smoke hood',            'construction', TRUE, FALSE, TRUE, 'V227'),
('RAIN_SCREEN',         'Rain screen',           'construction', TRUE, FALSE, TRUE, 'V227'),
('ROCK_OVERHANG_SCREEN','Rock overhang screen',  'construction', TRUE, FALSE, TRUE, 'V227'),
('DOOR_HANGING',        'Door hanging',          'construction', TRUE, FALSE, TRUE, 'V227')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('raised_bed_platform','STRUCTURE','Raised bed platform',FALSE,NULL,'RAISED_BED_PLATFORM','construction',
 'build a raised bed platform,raise a bed platform,raised bed platform,bed platform',
 'raised bed platform,bed platform',
 'A low frame of poles lashed off the cold, damp ground and decked with springy boughs — a raised platform that sleeps warm and dry.','VERIFIED',now()),
('sleeping_bench','STRUCTURE','Sleeping bench',FALSE,NULL,'SLEEPING_BENCH','construction',
 'build a sleeping bench,raise a sleeping bench,sleeping bench',
 'sleeping bench',
 'A plank bench built along a wall, off the floor draught — somewhere dry and level to lie the night through.','VERIFIED',now()),
('smoke_hood','STRUCTURE','Smoke hood',FALSE,NULL,'SMOKE_HOOD','construction',
 'build a smoke hood,rig a smoke hood,smoke hood',
 'smoke hood',
 'A hood of bark set over the hearth to gather the smoke and lead it up and out, so the air within stays clear to breathe.','VERIFIED',now()),
('rain_screen','STRUCTURE','Rain screen',FALSE,NULL,'RAIN_SCREEN','construction',
 'build a rain screen,rig a rain screen,rain screen',
 'rain screen',
 'A leaning screen of thatched reed pitched to windward — it turns the driven rain off the sitting-place behind it.','VERIFIED',now()),
('rock_overhang_screen','STRUCTURE','Rock overhang screen',FALSE,NULL,'ROCK_OVERHANG_SCREEN','construction',
 'build a rock overhang screen,screen a rock overhang,rock overhang screen,overhang screen',
 'rock overhang screen,overhang screen',
 'A screen of poles and brush closed across the open mouth of a rock overhang, turning a bare shelf of stone into a walled, wind-tight den.','VERIFIED',now()),
('door_hanging','STRUCTURE','Door hanging',FALSE,NULL,'DOOR_HANGING','construction',
 'build a door hanging,hang a door hanging,door hanging',
 'door hanging',
 'A stiff hide hung across the doorway on a pole — it swings aside to pass and falls back to keep the wind and the weather out.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('raised_bed_platform_build','raised_bed_platform',1,'Lash the frame and deck it',NULL,0,NULL,FALSE,'You lash a low frame off the ground and deck it with springy boughs until it sleeps warm and dry.'),
('sleeping_bench_build','sleeping_bench',1,'Set the bench',NULL,0,NULL,FALSE,'You set a plank bench along the wall, off the floor draught, level enough to lie on.'),
('smoke_hood_build','smoke_hood',1,'Set the hood over the hearth',NULL,0,NULL,FALSE,'You raise a bark hood over the hearth and lead the smoke up and out until the air clears.'),
('rain_screen_build','rain_screen',1,'Pitch the screen to windward',NULL,0,NULL,FALSE,'You pitch a thatched screen to windward until it turns the driven rain off the ground behind it.'),
('rock_overhang_screen_build','rock_overhang_screen',1,'Close the overhang mouth',NULL,0,NULL,FALSE,'You close the open mouth of the overhang with poles and brush until it stands wind-tight.'),
('door_hanging_build','door_hanging',1,'Hang the door',NULL,0,NULL,FALSE,'You hang a stiff hide across the doorway on a pole so it swings aside to pass and falls back against the weather.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('raised_bed_platform_build','dry_branch',3),('raised_bed_platform_build','plant_fiber',1),
('sleeping_bench_build','timber_plank',1),('sleeping_bench_build','dry_branch',2),
('smoke_hood_build','bark_sheet',2),
('rain_screen_build','reed_bundle',2),
('rock_overhang_screen_build','dry_branch',3),
('door_hanging_build','tanned_leather',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;
