-- V261 — story #106 (handling and restraint infrastructure): hitching_post / hitching_rail and tether_line.
-- A tamed draft beast could only be rested by building a full ANIMAL_PEN. A hitching post or a picketed tether line
-- is the simple, portable way a beast is actually held at a camp — driven stakes and a run of rope — and it should
-- let the animal stand and recover just as a pen does. The effect is wired in PhysicalItemService.restPennedDraftBeasts,
-- which now accepts these two standing structures alongside ANIMAL_PEN.
--
-- Routing: 'build a hitching post' / 'run a tether line' carry no hard-intent trigger — MARK needs a marking verb
-- (blaze/cairn/mark), not 'build', and 'tether line' is none of the PLACE_COVER line kinds (trip/warning/noise/
-- perimeter). Neither phrase contains 'pen', so BUILD_PEN cannot steal them.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('HITCHING_POST', 'Hitching post', 'construction', FALSE, FALSE, TRUE, 'V261'),
('TETHER_LINE',   'Tether line',   'construction', FALSE, FALSE, TRUE, 'V261')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('hitching_post','STRUCTURE','Hitching post',FALSE,NULL,'HITCHING_POST','construction',
 'build a hitching post,set a hitching post,raise a hitching post,hitching post,hitching rail',
 'hitching post,hitching rail',
 'A stout post driven deep and braced, worn smooth at the height a lead rope is turned around it — somewhere a beast can be tied and left standing without wandering off the camp.','VERIFIED',now()),
('tether_line','STRUCTURE','Tether line',FALSE,NULL,'TETHER_LINE','construction',
 'run a tether line,build a tether line,set a tether line,picket a tether line,tether line,picket line',
 'tether line,picket line',
 'Two driven stakes and a long rope run taut between them, with slack enough to graze along its length — a picket line that holds stock together and quiet through the night.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('hitching_post_build','hitching_post',1,'Drive and brace the post',NULL,0,NULL,FALSE,'You dig in a stout post, brace it, and smooth the turn where a lead rope will bear against it.'),
('tether_line_build','tether_line',1,'Drive the stakes and run the line',NULL,0,NULL,FALSE,'You drive a stake at either end and run the rope taut between them, leaving slack enough for a beast to graze along it.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('hitching_post_build','timber_log',1),('hitching_post_build','fiber_cordage',1),
('tether_line_build','dry_branch',2),('tether_line_build','fiber_cordage',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;
