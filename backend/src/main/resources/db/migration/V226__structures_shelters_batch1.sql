-- V226 — story #77 (100 staged structures), slice 1: portable field shelters. Four one-stage STRUCTURE assemblies,
-- each a real construction_kind marked is_shelter (so a completed one shelters the body through the existing
-- ConstructionService/physiology shelter effect) and buildable through the data-driven assembly matcher — no Java.
-- 'build/raise a <name>' routes to the assembly (AssemblyService.match, longest-keyword), clear of the Java BUILD_
-- intents (fire pit/fence/pen/lookout/latrine/tool shed/smoke vent/storage), and not a bed (which MAKE_BED owns).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('DEBRIS_HUT',   'Debris hut',   'construction', TRUE, FALSE, TRUE, 'V226'),
('BARK_SHELTER', 'Bark shelter', 'construction', TRUE, FALSE, TRUE, 'V226'),
('HIDE_TENT',    'Hide tent',    'construction', TRUE, FALSE, TRUE, 'V226'),
('REED_HUT',     'Reed hut',     'construction', TRUE, FALSE, TRUE, 'V226')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('debris_hut','STRUCTURE','Debris hut',FALSE,NULL,'DEBRIS_HUT','construction',
 'build a debris hut,pile up a debris hut,heap a debris hut,debris hut',
 'debris hut,hut',
 'A low ridge-pole leaned into a fork and ribbed with branches, then heaped deep with leaf litter and duff — a debris hut that holds a body''s own warmth against a cold night.','VERIFIED',now()),
('bark_shelter','STRUCTURE','Bark shelter',FALSE,NULL,'BARK_SHELTER','construction',
 'build a bark shelter,raise a bark shelter,shingle a bark shelter,bark shelter',
 'bark shelter',
 'Slabs of bark lapped over a lean frame like shingles, shedding the rain — a dry bark shelter to sit out weather under.','VERIFIED',now()),
('hide_tent','STRUCTURE','Hide tent',FALSE,NULL,'HIDE_TENT','construction',
 'build a hide tent,raise a hide tent,pitch a hide tent,hide tent',
 'hide tent,tent',
 'Hides stretched and pegged over a cone of poles — a hide tent that keeps wind and rain out and a fire''s warmth in.','VERIFIED',now()),
('reed_hut','STRUCTURE','Reed hut',FALSE,NULL,'REED_HUT','construction',
 'build a reed hut,raise a reed hut,thatch a reed hut,reed hut',
 'reed hut',
 'A frame of bent withies thatched thick with reed — a reed hut that turns weather and holds still air within.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('debris_hut_build','debris_hut',1,'Frame and heap the hut',NULL,0,NULL,FALSE,'You lean a ridge into a fork, rib it with branches, and heap it deep with litter until it sheds cold and rain.'),
('bark_shelter_build','bark_shelter',1,'Frame and shingle with bark',NULL,0,NULL,FALSE,'You lean a light frame and lap slabs of bark down it like shingles until the rain runs off clear.'),
('hide_tent_build','hide_tent',1,'Raise the poles and peg the hides',NULL,0,NULL,FALSE,'You stand a cone of poles, stretch the hides over, and peg them down tight against the wind.'),
('reed_hut_build','reed_hut',1,'Bend the frame and thatch it',NULL,0,NULL,FALSE,'You bend a frame of withies and thatch it thick with reed until still air holds within.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('debris_hut_build','dry_branch',3),
('bark_shelter_build','bark_sheet',3),
('hide_tent_build','tanned_leather',2),('hide_tent_build','dry_branch',2),
('reed_hut_build','hazel_rod',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;
