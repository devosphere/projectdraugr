-- V83: first-era shelter and settlement-core assemblies (M1 #61, EPIC #54).
--
-- The staged-assembly engine (V58) already runs multi-stage sited structures: an ordered list of stages,
-- each consuming inputs, needing a tool, or waiting out a cure, tracked per chronicle instance and raised
-- into a persistent construction_project at the final stage. Nothing in the runtime needs to learn these
-- twelve structures — they are pure data, and the same review gate that guards the bow and the drying rack
-- guards them here: a forward/self prerequisite, an assembly with no stages, or a stage that needs an
-- unobtainable item all block VERIFIED, so a broken build can never be reached from the action boundary.
--
-- Every stage requirement below is drawn from stock that already has an acquisition path (V52/V82): saplings,
-- hazel rods and willow whips, withy rope, dry branches, field stone, clay lumps, straw and thatch, reeds,
-- and cordage. The two enclosing, roofed forms — the wattle-and-daub hut and the earth-sheltered hut — are
-- wired into the exposure model (ChroniclePhysiologyService / shelterInReach) so their weather benefit derives
-- from the completed physical form, exactly as the lean-to's does, and only for the kinds that actually
-- enclose. Daub and clay linings carry a real cure: the world has to turn before the next stage can be worked.

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
('wattle_and_daub_hut','STRUCTURE','Wattle-and-daub hut',FALSE,NULL,'WATTLE_AND_DAUB_HUT','construction',
 'build a wattle and daub hut,raise a wattle and daub hut,work on the wattle and daub hut,wattle and daub hut,build the daub hut',
 'hut,wall,daub',
 'The hut stands closed to the weather — sapling frame, woven walls packed hard with daub, thatch turned to the wind. It will keep a body dry and out of the cold.'),
('earth_sheltered_hut','STRUCTURE','Earth-sheltered hut',FALSE,NULL,'EARTH_SHELTERED_HUT','construction',
 'build an earth sheltered hut,dig an earth sheltered hut,work on the earth sheltered hut,earth sheltered hut,dugout hut',
 'hut,dugout,bank',
 'The dugout is finished, cut back into the bank and roofed over with turf. The earth around it holds the day''s warmth and turns the weather without a fire.'),
('raised_sleeping_platform','STRUCTURE','Raised sleeping platform',FALSE,NULL,'RAISED_SLEEPING_PLATFORM','construction',
 'build a raised sleeping platform,raise a sleeping platform,raised sleeping platform,build a sleeping platform,work on the sleeping platform,bed platform',
 'platform,bed',
 'The platform stands firm off the ground, decked and dressed with bedding — a dry place to lie above the cold and the wet.'),
('reed_screen','STRUCTURE','Reed screen',FALSE,NULL,'REED_SCREEN','construction',
 'build a reed screen,weave a reed screen,reed screen,make a reed screen,work on the reed screen',
 'screen,reed',
 'The reed screen stands woven and taut on its frame, a wall against the wind and the eye.'),
('clay_lined_hearth','STRUCTURE','Clay-lined hearth',FALSE,NULL,'CLAY_LINED_HEARTH','construction',
 'build a clay lined hearth,line the hearth with clay,clay lined hearth,make a clay hearth,work on the clay hearth',
 'hearth,clay',
 'The hearth sits ringed in stone and lined with fired-hard clay — a bed that will hold and throw a fire''s heat.'),
('wood_store','STRUCTURE','Wood store',FALSE,NULL,'WOOD_STORE','construction',
 'build a wood store,raise a wood store,wood store,work on the wood store,log store,build a woodpile shelter',
 'store,woodpile',
 'The wood store stands raised and roofed, its rails ready to hold split wood up off the ground and out of the rain.'),
('rainwater_catchment','STRUCTURE','Rainwater catchment',FALSE,NULL,'RAINWATER_CATCHMENT','construction',
 'build a rainwater catchment,dig a rainwater catchment,rainwater catchment,make a rainwater catchment,work on the rainwater catchment,water catchment',
 'catchment,basin',
 'The catchment is dug and lined, its clay fired watertight. What rain falls will gather and hold here.'),
('split_rail_fence','STRUCTURE','Split-rail fence',FALSE,NULL,'SPLIT_RAIL_FENCE','construction',
 'build a split rail fence,split rail fence,raise a split rail fence,work on the split rail fence,build a rail fence',
 'fence,rail',
 'The split-rail fence runs straight and stands square on its posts, a plain boundary that will turn stock and mark a line.'),
('wattle_fence','STRUCTURE','Wattle fence',FALSE,NULL,'WATTLE_FENCE','construction',
 'build a wattle fence,weave a wattle fence,wattle fence,work on the wattle fence,make a wattle fence',
 'fence,wattle',
 'The wattle fence stands woven tight between its stakes, close enough to turn a hand and hold a line.'),
('simple_gate','STRUCTURE','Simple gate',FALSE,NULL,'SIMPLE_GATE','construction',
 'build a simple gate,hang a simple gate,simple gate,make a gate,work on the gate,hang the gate',
 'gate,frame',
 'The gate hangs true on its withy hinges, swinging clear and closing snug into the fence line.'),
('footbridge','STRUCTURE','Footbridge',FALSE,NULL,'FOOTBRIDGE','construction',
 'build a footbridge,lay a footbridge,footbridge,make a footbridge,work on the footbridge,build a foot bridge',
 'bridge,crossing',
 'The footbridge lies decked and lashed across the gap, firm enough to carry a body dry over the water.'),
('fishing_landing','STRUCTURE','Fishing landing',FALSE,NULL,'FISHING_LANDING','construction',
 'build a fishing landing,fishing landing,landing stage,build a landing stage,work on the fishing landing,build a jetty',
 'landing,jetty',
 'The landing stands out over the water on its driven piles, decked firm — a dry place to stand and work a line or a net.');

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
-- wattle-and-daub hut
('hut_prepare_site','wattle_and_daub_hut',1,'Prepare the hut site',NULL,0,'CUTTING',FALSE,'You clear the growth and level the ground where the hut will stand, and mark out its floor.'),
('hut_raise_frame','wattle_and_daub_hut',2,'Raise the frame','hut_prepare_site',0,'CUTTING',FALSE,'You set the sapling uprights and lash a ring-plate around their tops until the frame stops swaying.'),
('hut_weave_wattle','wattle_and_daub_hut',3,'Weave the wattle wall','hut_raise_frame',0,NULL,FALSE,'You weave hazel and willow between the uprights, course on course, until the walls close in.'),
('hut_apply_daub','wattle_and_daub_hut',4,'Apply the daub','hut_weave_wattle',0,NULL,FALSE,'You pack clay and chopped straw into the wattle from both faces and work it smooth.'),
('hut_daub_cure','wattle_and_daub_hut',5,'Let the daub dry','hut_apply_daub',720,NULL,FALSE,'The daub has dried hard and pale, cracked only where it should and set fast to the wattle.'),
('hut_thatch_roof','wattle_and_daub_hut',6,'Thatch the roof','hut_daub_cure',0,NULL,FALSE,'You lay thatch up the roof from the eave and bind each course down against the wind.'),
-- earth-sheltered hut
('earth_excavate','earth_sheltered_hut',1,'Excavate the shelter',NULL,0,'STRIKING',FALSE,'You cut back into the bank, breaking and hauling out earth until the floor lies square and deep enough to stand in.'),
('earth_revet','earth_sheltered_hut',2,'Revet the walls','earth_excavate',0,'STRIKING',FALSE,'You face the cut earth with stone so the walls will hold and not slump back in.'),
('earth_frame_roof','earth_sheltered_hut',3,'Frame the roof','earth_revet',0,'CUTTING',FALSE,'You lay saplings across the cut as rafters and lash them off to the revetment.'),
('earth_turf_roof','earth_sheltered_hut',4,'Turf the roof','earth_frame_roof',0,NULL,FALSE,'You thatch the rafters and lay turf back over them until the roof reads as bank again.'),
-- raised sleeping platform
('plat_set_posts','raised_sleeping_platform',1,'Set the posts',NULL,0,'CUTTING',FALSE,'You drive four stout saplings into the ground and check them level with your eye.'),
('plat_lay_deck','raised_sleeping_platform',2,'Lay the deck','plat_set_posts',0,NULL,FALSE,'You lay branches across the posts and lash them down until the deck holds your weight without give.'),
('plat_dress_bedding','raised_sleeping_platform',3,'Dress the bedding','plat_lay_deck',0,NULL,FALSE,'You spread straw and reed over the deck into a dry, springing bed.'),
-- reed screen
('screen_frame','reed_screen',1,'Frame the screen',NULL,0,'CUTTING',FALSE,'You lash a light sapling frame and stand it where the wind comes hardest.'),
('screen_weave','reed_screen',2,'Weave the reeds','screen_frame',0,NULL,FALSE,'You weave reed through the frame and bind it off tight enough to lean on the wind.'),
-- clay-lined hearth
('hearth_ring_stones','clay_lined_hearth',1,'Ring the stones',NULL,0,'STRIKING',FALSE,'You set field stone into a close, level ring for the hearth.'),
('hearth_line_clay','clay_lined_hearth',2,'Line with clay','hearth_ring_stones',0,NULL,FALSE,'You pack clay across the floor of the ring and up its inner face, and smooth it to a bed.'),
('hearth_cure_lining','clay_lined_hearth',3,'Let the lining cure','hearth_line_clay',480,NULL,FALSE,'The clay has dried hard and grey, ready to take a fire without cracking apart.'),
-- wood store
('store_set_posts','wood_store',1,'Set the posts',NULL,0,'CUTTING',FALSE,'You drive four posts and check them plumb where the wood will stack.'),
('store_rail','wood_store',2,'Rail the frame','store_set_posts',0,NULL,FALSE,'You lash rails between the posts to carry the wood up off the wet ground.'),
('store_roof','wood_store',3,'Roof the store','store_rail',0,NULL,FALSE,'You thatch a low roof over the rails so the stack will stay dry.'),
-- rainwater catchment
('catch_dig_basin','rainwater_catchment',1,'Dig the basin',NULL,0,'STRIKING',FALSE,'You dig out a broad, shallow basin and ring its lip with stone.'),
('catch_line_basin','rainwater_catchment',2,'Line the basin','catch_dig_basin',0,NULL,FALSE,'You line the basin with clay, working it into the walls until it will not weep.'),
('catch_cure_lining','rainwater_catchment',3,'Let the lining cure','catch_line_basin',480,NULL,FALSE,'The clay lining has dried tight and watertight, ready to hold what rain the sky gives.'),
-- split-rail fence
('fence_set_posts','split_rail_fence',1,'Set the posts',NULL,0,'AXE',FALSE,'You dig and set the fence posts down the line, tamping each one firm.'),
('fence_lay_rails','split_rail_fence',2,'Lay the rails','fence_set_posts',0,'AXE',FALSE,'You split saplings into rails and lay them between the posts, course on course.'),
-- wattle fence
('wfence_set_stakes','wattle_fence',1,'Set the stakes',NULL,0,'CUTTING',FALSE,'You drive a line of stakes into the ground, close-spaced for weaving.'),
('wfence_weave','wattle_fence',2,'Weave the fence','wfence_set_stakes',0,NULL,FALSE,'You weave hazel and willow between the stakes until the fence stands close and tight.'),
-- simple gate
('gate_frame','simple_gate',1,'Build the frame',NULL,0,'CUTTING',FALSE,'You join saplings into a light, square gate frame and brace it corner to corner.'),
('gate_hang','simple_gate',2,'Hang the gate','gate_frame',0,NULL,FALSE,'You loop withy hinges over a post and hang the gate so it swings clear and closes snug.'),
-- footbridge
('bridge_stringers','footbridge',1,'Lay the stringers',NULL,0,'AXE',FALSE,'You lay stout saplings across the gap as stringers and settle their ends into the banks.'),
('bridge_deck','footbridge',2,'Deck the bridge','bridge_stringers',0,NULL,FALSE,'You lay branches across the stringers and lash them down into a deck firm enough to cross.'),
-- fishing landing
('landing_piles','fishing_landing',1,'Drive the piles',NULL,0,'STRIKING',FALSE,'You drive saplings into the bed at the water''s edge until they stand fast against a push.'),
('landing_deck','fishing_landing',2,'Deck the landing','landing_piles',0,NULL,FALSE,'You deck the piles over with branches and lash them firm into a standing platform above the water.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
-- wattle-and-daub hut
('hut_raise_frame','straight_sapling',6),('hut_raise_frame','withy_rope',3),
('hut_weave_wattle','hazel_rod',8),('hut_weave_wattle','willow_branch',6),
('hut_apply_daub','clay_lump',8),('hut_apply_daub','straw_bundle',3),
('hut_thatch_roof','thatch_bundle',6),('hut_thatch_roof','withy_rope',2),
-- earth-sheltered hut
('earth_revet','field_stone',6),
('earth_frame_roof','straight_sapling',4),('earth_frame_roof','withy_rope',2),
('earth_turf_roof','straw_bundle',3),('earth_turf_roof','thatch_bundle',2),
-- raised sleeping platform
('plat_set_posts','straight_sapling',4),
('plat_lay_deck','dry_branch',8),('plat_lay_deck','withy_rope',2),
('plat_dress_bedding','straw_bundle',2),('plat_dress_bedding','reed_bundle',2),
-- reed screen
('screen_frame','straight_sapling',2),
('screen_weave','reed_bundle',4),('screen_weave','fiber_cordage',2),
-- clay-lined hearth
('hearth_ring_stones','field_stone',6),
('hearth_line_clay','clay_lump',5),
-- wood store
('store_set_posts','straight_sapling',4),
('store_rail','dry_branch',6),('store_rail','withy_rope',2),
('store_roof','thatch_bundle',3),
-- rainwater catchment
('catch_dig_basin','field_stone',4),
('catch_line_basin','clay_lump',6),
-- split-rail fence
('fence_set_posts','straight_sapling',4),
('fence_lay_rails','straight_sapling',6),
-- wattle fence
('wfence_set_stakes','straight_sapling',4),
('wfence_weave','hazel_rod',8),('wfence_weave','willow_branch',4),
-- simple gate
('gate_frame','straight_sapling',3),('gate_frame','withy_rope',2),
('gate_hang','withy_rope',2),
-- footbridge
('bridge_stringers','straight_sapling',4),
('bridge_deck','dry_branch',8),('bridge_deck','withy_rope',3),
-- fishing landing
('landing_piles','straight_sapling',4),
('landing_deck','dry_branch',8),('landing_deck','withy_rope',2);

-- Re-run the V58 review gate over the newly-inserted DRAFT rows: same three blocking checks (bad prerequisite,
-- no stages, unobtainable requirement) plus the empty-work-stage advisory. Anything clean is promoted to
-- VERIFIED and becomes matchable from the action boundary; anything broken stays out.
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'BAD_PREREQUISITE', 'BLOCKING',
       'Stage ' || s.stage_key || ' has a prerequisite that is not an earlier stage of the same assembly.'
FROM assembly_stage s JOIN assembly_stage p ON p.stage_key = s.prerequisite_stage_key
WHERE (p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order)
  AND s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT');

INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT d.assembly_key, 'NO_STAGES', 'BLOCKING', 'Assembly declares no stages.'
FROM assembly_definition d
WHERE d.review_state='DRAFT' AND NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = d.assembly_key);

INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT DISTINCT s.assembly_key, 'UNOBTAINABLE_REQUIREMENT', 'BLOCKING',
       'Stage ' || r.stage_key || ' requires ' || r.item_key || ', which has no acquisition path.'
FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
WHERE s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT')
  AND NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key = r.item_key);

INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'EMPTY_WORK_STAGE', 'ADVISORY',
       'Stage ' || s.stage_key || ' is a work stage with no requirements.'
FROM assembly_stage s
WHERE s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT')
  AND s.cure_minutes = 0
  AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);

UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now()
WHERE review_state='DRAFT' AND NOT EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);
UPDATE assembly_definition SET review_state='NEEDS_REFINEMENT', reviewed_at=now()
WHERE review_state='DRAFT' AND EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);
