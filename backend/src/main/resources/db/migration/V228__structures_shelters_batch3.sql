-- V228 — story #77 slice 3: walls, doors and roofs. Six more one-stage STRUCTURE assemblies, each a construction_kind
-- marked is_shelter (a completed wall/door/roof turns wind, rain and weather off the ground behind it, through the
-- existing ConstructionService/physiology shelter effect). Same pure-data pattern as V226/V227. Keywords are
-- distinctive multiword phrases so none collides with dry_stone_wall ('stone wall'), wattle_fence, MAKE_BED, or any
-- Java BUILD_ intent; 'thatch a roof' does not contain reed_hut's 'thatch a reed hut'.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('TARP_SHELTER',  'Tarp shelter',  'construction', TRUE, FALSE, TRUE, 'V228'),
('WATTLE_WALL',   'Wattle wall',   'construction', TRUE, FALSE, TRUE, 'V228'),
('REED_DOOR',     'Reed door',     'construction', TRUE, FALSE, TRUE, 'V228'),
('BARK_DOOR',     'Bark door',     'construction', TRUE, FALSE, TRUE, 'V228'),
('ROOFING_FRAME', 'Roofing frame', 'construction', TRUE, FALSE, TRUE, 'V228'),
('THATCH_ROOF',   'Thatch roof',   'construction', TRUE, FALSE, TRUE, 'V228')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('tarp_shelter','STRUCTURE','Tarp shelter',FALSE,NULL,'TARP_SHELTER','construction',
 'build a tarp shelter,rig a tarp shelter,pitch a tarp shelter,tarp shelter',
 'tarp shelter',
 'A hide sheet slung taut between two anchors and pitched off to windward — a quick tarp shelter that sheds rain and breaks the wind over a dry patch of ground.','VERIFIED',now()),
('wattle_wall','STRUCTURE','Wattle wall',FALSE,NULL,'WATTLE_WALL','construction',
 'build a wattle wall,weave a wattle wall,raise a wattle wall,wattle wall',
 'wattle wall',
 'Withies woven thick between upright stakes into a standing panel of wattle — a wall that closes a side against the wind and the driving weather.','VERIFIED',now()),
('reed_door','STRUCTURE','Reed door',FALSE,NULL,'REED_DOOR','construction',
 'build a reed door,hang a reed door,reed door',
 'reed door',
 'A stiff mat of bound reed hung to close a doorway — it swings across to shut out the wind and falls open to pass.','VERIFIED',now()),
('bark_door','STRUCTURE','Bark door',FALSE,NULL,'BARK_DOOR','construction',
 'build a bark door,hang a bark door,bark door',
 'bark door',
 'A slab of stiff bark trimmed to the opening and hung on withy hinges — a bark door that seals a shelter mouth against the cold.','VERIFIED',now()),
('roofing_frame','STRUCTURE','Roofing frame',FALSE,NULL,'ROOFING_FRAME','construction',
 'build a roofing frame,raise a roofing frame,roofing frame',
 'roofing frame',
 'Rafters of straight poles pitched from a ridge and battened across — the bare roofing frame a covering is laid over to keep the weather off.','VERIFIED',now()),
('thatch_roof','STRUCTURE','Thatch roof',FALSE,NULL,'THATCH_ROOF','construction',
 'build a thatch roof,thatch a roof,lay a thatch roof,thatch roof',
 'thatch roof',
 'Bundles of reed laid course on course up the rafters and combed down — a thick thatch roof that turns rain and holds warmth beneath.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('tarp_shelter_build','tarp_shelter',1,'Sling and pitch the sheet',NULL,0,NULL,FALSE,'You sling a hide sheet taut between two anchors and pitch it to windward until it sheds rain over dry ground.'),
('wattle_wall_build','wattle_wall',1,'Weave the wattle panel',NULL,0,NULL,FALSE,'You drive a line of stakes and weave withies thick between them until a standing wall of wattle closes the side.'),
('reed_door_build','reed_door',1,'Bind and hang the reed mat',NULL,0,NULL,FALSE,'You bind a stiff mat of reed and hang it to swing across the doorway against the wind.'),
('bark_door_build','bark_door',1,'Trim and hang the bark slab',NULL,0,NULL,FALSE,'You trim a slab of stiff bark to the opening and hang it on withy hinges to seal the mouth.'),
('roofing_frame_build','roofing_frame',1,'Pitch the rafters',NULL,0,NULL,FALSE,'You pitch straight poles from a ridge and batten them across into a bare roofing frame.'),
('thatch_roof_build','thatch_roof',1,'Lay and comb the thatch',NULL,0,NULL,FALSE,'You lay bundles of reed course on course up the rafters and comb them down until the roof turns rain.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('tarp_shelter_build','tanned_leather',2),
('wattle_wall_build','hazel_rod',3),
('reed_door_build','reed_bundle',2),
('bark_door_build','bark_sheet',2),
('roofing_frame_build','dry_branch',4),
('thatch_roof_build','reed_bundle',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;
