-- #77 — the twelve structures that were really absent.
--
-- V422 established the real number: of the 100 structures #77 names, 35 were present by key and 46 more already
-- answered under another name or through a Java intent, leaving NINETEEN unreachable. Five of those were the
-- same thing under a different word and V422 gave them that word. These are the other fourteen, and they are
-- built rather than renamed because nothing in the catalogue is them.
--
-- EVERY ONE CARRIES A CAPABILITY FLAG THAT LIVE CODE ALREADY READS. That is the whole standard here: this
-- project's rule is that an object must be functional end to end and never a catalogue token, so a structure
-- whose only property was existing would be worse than no structure at all. Each flag below was confirmed to
-- have a functional reader while auditing #108:
--
--   is_workstation          eases a process sited within reach   -> woodworking stump, hide scraping frame
--   (calving shelter: NOT BUILT. ox_shed already shelters birth and answers to "calving shed".)
--   aids_mounting           getting up onto a beast or a load    -> animal loading ramp
--   is_shelter              getting out of the weather           -> gatehouse shelter
--   holds_fire              a fire may be set in it              -> lime burning pit
--   is_barrier + strength   stops a thing getting through        -> quarry safety screen
--   crosses_soft_ground     ground that would otherwise mire     -> culvert log
--   eases_going             a way that costs less to walk        -> stile, riverbank steps, drainage ditch
--   holds_open_water        standing water a vessel can draw     -> water draw platform, washing place
--   (feeding station: NOT BUILT. hay_rack already is one — it gets the word, below.)
--
-- Materials are all existing item keys, and the tool classes are the three the schema allows (CUTTING, AXE,
-- STRIKING). Stage narration is written in the voice the rest of the catalogue uses: what the hands do.

-- ---------------------------------------------------------------------------------------------------- KINDS
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays,
                               encloses, is_barrier, barrier_strength, holds_fire, eases_going,
                               crosses_soft_ground, holds_open_water, shelters_birth, aids_mounting,
                               shelters_stock, gives_shade, shelters_up_to_size) VALUES
 ('WOODWORKING_STUMP',   'Woodworking stump',    'woodworking',  false, true,  true, false, false, 0, false, 0, false, false, false, false, false, false, NULL),
 ('HIDE_SCRAPING_FRAME', 'Hide scraping frame',  'construction', false, true,  true, false, false, 0, false, 0, false, false, false, false, false, false, NULL),
 -- A COW IS LARGE, AND THE SCHEMA INSISTS ON KNOWING. sized_for_the_stock_it_holds refuses any kind that
 -- shelters stock or birth without saying up to what size, which is exactly right: a shelter whose size is
 -- unknown cannot be matched against the beast trying to get into it. The existing farrowing_shelter is the
 -- same flags at the same size for pigs; this is cattle, and both are wanted.
 ('ANIMAL_LOADING_RAMP', 'Animal loading ramp',  'construction', false, false, true, false, false, 0, false, 0, false, false, false, true,  false, false, NULL),
 ('GATEHOUSE_SHELTER',   'Gatehouse shelter',    'construction', true,  false, true, false, false, 0, false, 0, false, false, false, false, false, true,  NULL),
 ('LIME_BURNING_PIT',    'Lime burning pit',     'stoneworking', false, true,  true, false, false, 0, true,  0, false, false, false, false, false, false, NULL),
 ('QUARRY_SAFETY_SCREEN','Quarry safety screen', 'stoneworking', false, false, true, false, true,  2, false, 0, false, false, false, false, false, false, NULL),
 ('CULVERT_LOG',         'Culvert log',          'construction', false, false, true, false, false, 0, false, 1, true,  false, false, false, false, false, NULL),
 ('DRAINAGE_DITCH',      'Drainage ditch',       'construction', false, false, true, false, false, 0, false, 1, true,  false, false, false, false, false, NULL),
 ('STILE',               'Stile',                'construction', false, false, true, false, false, 0, false, 1, false, false, false, false, false, false, NULL),
 ('RIVERBANK_STEPS',     'Riverbank steps',      'construction', false, false, true, false, false, 0, false, 2, false, false, false, false, false, false, NULL),
 ('WATER_DRAW_PLATFORM', 'Water draw platform',  'construction', false, false, true, false, false, 0, false, 1, false, true,  false, false, false, false, NULL),
 ('WASHING_PLACE',       'Washing place',        'construction', false, false, true, false, false, 0, false, 0, false, true,  false, false, false, false, NULL)
ON CONFLICT (project_kind) DO NOTHING;

-- TWO OF THE FOURTEEN WERE NOT ABSENT AT ALL, and checking the catalogue before building them took them off the
-- list again. Both were found the same way: the schema or the live matcher refused to pretend.
--
--  * A FEEDING STATION IS A HAY RACK. hay_rack already answers to "fodder rack" and watering_station to "stock
--    trough", so a new structure would have been a second thing for the same job. The CHECK constraint
--    sized_for_the_stock_it_holds is what asked the question: it wanted to know how big the stock were, and for
--    a trough there was no honest answer.
--
--  * A CALVING SHELTER IS AN OX SHED. ox_shed already declares shelters_birth at HUGE and already answers to
--    "calving shed". Measuring the new rows live is what asked this one: "build a calving pen" came back
--    BUILD_PEN, and looking at why turned up the shed that was already there.
--
-- So this is TWELVE BUILT AND TWO RENAMED — which is the fourth time in one story that A KEY ABSENT FROM THE
-- CATALOGUE IS ONLY A GAP IF NOTHING ELSE OWNS THE PHRASE. The number of real gaps in #77 has gone 65 -> 19 ->
-- 14 -> 12, every reduction from checking rather than counting.
UPDATE assembly_definition SET keywords = keywords || ',feeding station,build a feeding station,feed trough,feeding trough,build a feed trough'
 WHERE assembly_key = 'hay_rack' AND ',' || keywords || ',' NOT LIKE '%,feeding station,%';

-- "calving pen" IS DELIBERATELY NOT ADDED. A pen is a pen: the Java BUILD_PEN intent owns the whole word and
-- gives an enclosure, which is a fair answer to that sentence, and claiming it here would put a catalogue
-- keyword permanently in the shadow of a Java intent — the thing noJavaIntentShadowsAnAssemblysOwnKeywords
-- exists to forbid. The roofed words are the ones this shed can honestly claim.
UPDATE assembly_definition SET keywords = keywords || ',calving shelter,build a calving shelter,raise a calving shelter,calving house,birthing shelter'
 WHERE assembly_key = 'ox_shed' AND ',' || keywords || ',' NOT LIKE '%,calving shelter,%';
-- ----------------------------------------------------------------------------------------------- ASSEMBLIES
-- VERIFIED, not DRAFT. V420 found exactly one DRAFT assembly in the catalogue — the earth oven — complete in
-- every respect and unbuildable by any words, because AssemblyService selects only VERIFIED rows. A structure
-- added as DRAFT is a structure nobody can build, and the standing guard V420 added would fail on it.
INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, construction_kind,
                                 domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
 ('woodworking_stump', 'STRUCTURE', 'Woodworking stump', false, 'WOODWORKING_STUMP', 'woodworking',
  'build a woodworking stump,set up a woodworking stump,woodworking stump,chopping block,build a chopping block,working stump',
  'woodworking stump,chopping block,working stump,stump',
  'A sound log set upright and bedded firm, to take the shock of an axe or a chisel without walking away under it.',
  'VERIFIED', now()),
 ('hide_scraping_frame', 'STRUCTURE', 'Hide scraping frame', false, 'HIDE_SCRAPING_FRAME', 'construction',
  'build a hide scraping frame,raise a hide scraping frame,hide scraping frame,scraping frame,build a scraping frame,hide frame',
  'hide scraping frame,scraping frame,hide frame',
  'A frame of lashed poles with the hide drawn taut across it, so both hands can work the flesh side at once.',
  'VERIFIED', now()),
 ('animal_loading_ramp', 'STRUCTURE', 'Animal loading ramp', false, 'ANIMAL_LOADING_RAMP', 'construction',
  'build an animal loading ramp,raise a loading ramp,animal loading ramp,loading ramp,build a loading ramp,stock ramp',
  'animal loading ramp,loading ramp,stock ramp,ramp',
  'An earth and timber slope rising to cart height, walled on both sides so a beast walks up it rather than off it.',
  'VERIFIED', now()),
 ('gatehouse_shelter', 'STRUCTURE', 'Gatehouse shelter', false, 'GATEHOUSE_SHELTER', 'construction',
  'build a gatehouse shelter,raise a gatehouse,gatehouse shelter,gatehouse,build a gatehouse,gate shelter',
  'gatehouse shelter,gatehouse,gate shelter',
  'A roofed stand beside the way in, big enough for one person and the weather they are keeping out of while they watch it.',
  'VERIFIED', now()),
 ('lime_burning_pit', 'STRUCTURE', 'Lime burning pit', false, 'LIME_BURNING_PIT', 'stoneworking',
  'build a lime burning pit,dig a lime burning pit,lime burning pit,lime pit,build a lime kiln,burn lime',
  'lime burning pit,lime pit,lime kiln',
  'A stone-lined pit with a draught at the base, where limestone is burned long and hard enough to change what it is.',
  'VERIFIED', now()),
 ('quarry_safety_screen', 'STRUCTURE', 'Quarry safety screen', false, 'QUARRY_SAFETY_SCREEN', 'stoneworking',
  'build a quarry safety screen,raise a quarry screen,quarry safety screen,quarry screen,build a rockfall screen,rockfall screen',
  'quarry safety screen,quarry screen,rockfall screen',
  'A braced timber and brush wall set below the working face, to take what comes down before it reaches whoever is standing there.',
  'VERIFIED', now()),
 ('culvert_log', 'STRUCTURE', 'Culvert log', false, 'CULVERT_LOG', 'construction',
  'build a culvert log,lay a culvert log,culvert log,culvert,build a culvert,lay a culvert',
  'culvert log,culvert',
  'A hollowed log bedded under the way, so the water goes through the ground instead of standing on top of it.',
  'VERIFIED', now()),
 ('drainage_ditch', 'STRUCTURE', 'Drainage ditch', false, 'DRAINAGE_DITCH', 'construction',
  'build a drainage ditch,dig a drainage ditch,drainage ditch,dig a ditch,build a ditch,drain the ground',
  'drainage ditch,ditch,drain',
  'A cut line falling away downhill, with the spoil banked on the low side, so the wet has somewhere to go.',
  'VERIFIED', now()),
 ('stile', 'STRUCTURE', 'Stile', false, 'STILE', 'construction',
  'build a stile,set up a stile,stile,build a step over,step stile',
  'stile,step stile',
  'Two steps and a post set into the fence line, so a person crosses where a beast cannot.',
  'VERIFIED', now()),
 ('riverbank_steps', 'STRUCTURE', 'Riverbank steps', false, 'RIVERBANK_STEPS', 'construction',
  'build riverbank steps,cut riverbank steps,riverbank steps,bank steps,build bank steps,cut steps in the bank',
  'riverbank steps,bank steps',
  'Steps cut back into the bank and faced with flat stone, so the way down to the water does not slide out from under you.',
  'VERIFIED', now()),
 ('water_draw_platform', 'STRUCTURE', 'Water draw platform', false, 'WATER_DRAW_PLATFORM', 'construction',
  'build a water draw platform,raise a water draw platform,water draw platform,draw platform,build a drawing platform',
  'water draw platform,draw platform',
  'A short staging out over the water, standing on driven posts, so a vessel is filled from clear water instead of the churned edge.',
  'VERIFIED', now()),
 ('washing_place', 'STRUCTURE', 'Washing place', false, 'WASHING_PLACE', 'construction',
  'build a washing place,make a washing place,washing place,washing stone,build a washing stone,laundry place',
  'washing place,washing stone,laundry place',
  'A flat stone set at the water''s edge at the height of a kneeling person, with the ground before it bedded so it does not turn to mud.',
  'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

-- --------------------------------------------------------------------------------------------------- STAGES
INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
 ('stump_sink',      'woodworking_stump',   1, 'Sink and bed the stump',      NULL,              0, 'AXE',      false, 'You cut the log to length and sink it upright, tamping the earth hard around it until it will not shift.'),
 ('scrapeframe_lash','hide_scraping_frame', 1, 'Lash the frame',              NULL,              0, 'CUTTING',  false, 'You lash four poles into a frame and brace the corners, testing it against your weight before you trust a hide to it.'),
 ('ramp_bank',       'animal_loading_ramp', 1, 'Bank the slope',              NULL,              0, NULL,       false, 'You barrow earth into a slope rising to cart height, treading each course down so it will carry weight without slumping.'),
 ('ramp_wall',       'animal_loading_ramp', 2, 'Wall both sides',             'ramp_bank',       0, 'AXE',      false, 'You set rails along both sides, high enough that a beast walking up sees only the way forward.'),
 ('gatehouse_frame', 'gatehouse_shelter',   1, 'Frame the stand',             NULL,              0, 'AXE',      false, 'You frame a stand beside the way in, set square to the gate so whoever waits in it can see who is coming.'),
 ('gatehouse_roof',  'gatehouse_shelter',   2, 'Roof it',                     'gatehouse_frame', 0, 'CUTTING',  false, 'You thatch the roof and overhang the open side, so the rain comes off clear of anyone standing beneath.'),
 ('limepit_dig',     'lime_burning_pit',    1, 'Dig the pit and the draught', NULL,              0, NULL,       false, 'You dig the pit and cut a draught channel in at the base, because without air the stone will only sit there and warm.'),
 ('limepit_line',    'lime_burning_pit',    2, 'Line it with stone',          'limepit_dig',    90, 'STRIKING', false, 'You line the pit with field stone, close-set and sound, so the heat is held in and the walls do not spall into the burn.'),
 ('quarryscreen_set','quarry_safety_screen',1, 'Set and brace the screen',    NULL,              0, 'AXE',      false, 'You drive posts below the working face and brace them back into the ground, then fill between with brush until nothing can pass it.'),
 ('culvert_hollow',  'culvert_log',         1, 'Hollow the log',              NULL,              0, 'AXE',      false, 'You hollow the log end to end, checking the bore by sighting through it against the sky.'),
 ('culvert_bed',     'culvert_log',         2, 'Bed it under the way',        'culvert_hollow',  0, NULL,       false, 'You bed the log across the low point and cover it over, leaving both ends clear so the water finds them.'),
 ('ditch_cut',       'drainage_ditch',      1, 'Cut the line',                NULL,              0, NULL,       false, 'You cut the ditch along the fall of the ground and bank the spoil on the low side, so the water runs and does not pool.'),
 ('stile_set',       'stile',               1, 'Set the steps and post',      NULL,              0, 'AXE',      false, 'You set two steps and a post into the fence line, close enough together to climb and too close for a beast to follow.'),
 ('banksteps_cut',   'riverbank_steps',     1, 'Cut the steps back',          NULL,              0, NULL,       false, 'You cut the steps back into the bank rather than out of it, so what you stand on is undisturbed ground.'),
 ('banksteps_face',  'riverbank_steps',     2, 'Face them with stone',        'banksteps_cut',   0, 'STRIKING', false, 'You face each tread with flat stone and bed it level, so the wet does not wear the step out from under you.'),
 ('drawplat_posts',  'water_draw_platform', 1, 'Drive the posts',             NULL,              0, 'STRIKING', false, 'You drive posts into the bed until they stop moving, out far enough to reach past the churned edge.'),
 ('drawplat_deck',   'water_draw_platform', 2, 'Deck the staging',            'drawplat_posts',  0, 'AXE',      false, 'You lay and pin the decking, leaving a gap at the end to kneel and draw through.'),
 ('washplace_bed',   'washing_place',       1, 'Bed the standing ground',     NULL,              0, NULL,       false, 'You bed the ground before the water with gravel and tread it in, so a season of kneeling does not turn it to mud.'),
 ('washplace_stone', 'washing_place',       2, 'Set the washing stone',       'washplace_bed',   0, 'STRIKING', false, 'You set a flat stone at the edge at the height of a kneeling person, sloped a little so the water runs back.')
ON CONFLICT (stage_key) DO NOTHING;

-- --------------------------------------------------------------------------------------------- REQUIREMENTS
INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('stump_sink', 'dry_branch', 2),
 ('scrapeframe_lash', 'dry_branch', 4), ('scrapeframe_lash', 'fiber_cordage', 2),
 ('ramp_bank', 'field_stone', 4),
 ('ramp_wall', 'dry_branch', 4), ('ramp_wall', 'fiber_cordage', 2),
 ('gatehouse_frame', 'dry_branch', 4),
 ('gatehouse_roof', 'thatch_bundle', 2),
 ('limepit_dig', 'field_stone', 2),
 ('limepit_line', 'field_stone', 8),
 ('quarryscreen_set', 'dry_branch', 6), ('quarryscreen_set', 'fiber_cordage', 2),
 ('culvert_hollow', 'dry_branch', 3),
 ('culvert_bed', 'river_gravel', 3),
 ('ditch_cut', 'field_stone', 2),
 ('stile_set', 'dry_branch', 3),
 ('banksteps_cut', 'field_stone', 3),
 ('banksteps_face', 'field_stone', 4),
 ('drawplat_posts', 'dry_branch', 4),
 ('drawplat_deck', 'timber_plank', 3),
 ('washplace_bed', 'river_gravel', 2),
 ('washplace_stone', 'field_stone', 3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. All twelve exist, are VERIFIED, and point at a kind that exists. A DRAFT row would be a structure
    --    nobody can build — V420's finding — and a dangling construction_kind is a build with no behaviour.
    SELECT string_agg(a, ', ') INTO bad FROM (VALUES
        ('woodworking_stump'),('hide_scraping_frame'),('animal_loading_ramp'),
        ('gatehouse_shelter'),('lime_burning_pit'),('quarry_safety_screen'),('culvert_log'),
        ('drainage_ditch'),('stile'),('riverbank_steps'),('water_draw_platform'),('washing_place')) v(a)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition d
                        WHERE d.assembly_key = v.a AND d.review_state = 'VERIFIED'
                          AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = d.construction_kind));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: % is missing, DRAFT, or points at no kind', bad; END IF;

    -- 2. Every one has at least one stage, and every stage something to build it from. An assembly with no
    --    stages has nothing to run; a stage with no requirement is matter from nothing.
    SELECT string_agg(d.assembly_key, ', ') INTO bad FROM assembly_definition d
     WHERE d.assembly_key IN ('woodworking_stump','hide_scraping_frame','animal_loading_ramp',
                              'gatehouse_shelter','lime_burning_pit','quarry_safety_screen','culvert_log',
                              'drainage_ditch','stile','riverbank_steps','water_draw_platform','washing_place')
       AND NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = d.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: % has no stages', bad; END IF;

    SELECT string_agg(s.stage_key, ', ') INTO bad FROM assembly_stage s
     WHERE s.assembly_key IN ('woodworking_stump','hide_scraping_frame','animal_loading_ramp',
                              'gatehouse_shelter','lime_burning_pit','quarry_safety_screen','culvert_log',
                              'drainage_ditch','stile','riverbank_steps','water_draw_platform','washing_place')
       AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: % needs nothing to build it', bad; END IF;

    -- 3. Every material named is a real item. A requirement on a key that does not exist is a stage that can
    --    never be satisfied, and nothing throws at runtime to say so.
    SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
      FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
     WHERE s.assembly_key IN ('woodworking_stump','hide_scraping_frame','animal_loading_ramp',
                              'gatehouse_shelter','lime_burning_pit','quarry_safety_screen','culvert_log',
                              'drainage_ditch','stile','riverbank_steps','water_draw_platform','washing_place')
       AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = r.item_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: no such material: %', bad; END IF;

    -- 4. NOT ONE OF THEM IS INERT. This is the standard that matters: each must declare at least one capability
    --    the running game reads, or it is a catalogue token with a display name. Checked as a property of the
    --    row rather than trusted from the INSERT above.
    SELECT string_agg(ck.project_kind, ', ') INTO bad FROM construction_kind ck
     WHERE ck.project_kind IN ('WOODWORKING_STUMP','HIDE_SCRAPING_FRAME','ANIMAL_LOADING_RAMP',
                               'GATEHOUSE_SHELTER','LIME_BURNING_PIT','QUARRY_SAFETY_SCREEN','CULVERT_LOG',
                               'DRAINAGE_DITCH','STILE','RIVERBANK_STEPS','WATER_DRAW_PLATFORM','WASHING_PLACE')
       AND NOT (ck.is_shelter OR ck.is_workstation OR ck.encloses OR ck.is_barrier OR ck.holds_fire
                OR ck.crosses_soft_ground OR ck.holds_open_water OR ck.shelters_birth OR ck.aids_mounting
                OR ck.shelters_stock OR ck.gives_shade OR ck.eases_going > 0 OR ck.barrier_strength > 0);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: % declares no capability and would be a token', bad; END IF;

    -- 5. And the standing guard V420 added still holds across the whole catalogue: nothing fully staged may be
    --    left unreachable.
    SELECT string_agg(ad.assembly_key, ', ') INTO bad FROM assembly_definition ad
     WHERE ad.review_state <> 'VERIFIED' AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: % is fully staged and still unreachable', bad; END IF;

    -- 6. THE RENAMED TWO. "feeding station" must now reach something, reach exactly ONE thing (it names a
    --    particular structure, so an ambiguous ask would be the wrong answer), and must not have displaced
    --    hay_rack's own name. V422's guard got this wrong first time by testing keyword LENGTH; what matters is
    --    that the catalogue's own word still answers.
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,feeding station,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V423: "feeding station" is claimed by % assemblies; a named thing is one thing', n; END IF;
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key = 'hay_rack'
                    AND ',' || replace(keywords,', ',',') || ',' LIKE '%,hay rack,%')
    THEN RAISE EXCEPTION 'V423: hay_rack no longer answers to its own name'; END IF;

    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,calving shelter,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V423: "calving shelter" is claimed by % assemblies', n; END IF;
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key = 'ox_shed'
                    AND ',' || replace(keywords,', ',',') || ',' LIKE '%,calving shed,%')
    THEN RAISE EXCEPTION 'V423: ox_shed no longer answers to its own name'; END IF;

    -- 7. NO NEW NAME COLLIDES WITH A RECIPE'S OWN PHRASE. This is the whole-word form of the defect that made
    --    a bare "fly " unspeakable: a structure keyword living inside a material_process keyword silently takes
    --    the recipe's sentence. Every word this migration introduces is held to it.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('stile'),('culvert'),('gatehouse'),('ditch'),('drain'),('ramp'),('washing place'),
                   ('feeding station'),('feed trough'),('lime pit'),('quarry screen'),('bank steps'),
                   ('chopping block'),('scraping frame'),('draw platform')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V423: a structure name collides with a recipe: %', bad; END IF;
END $$;
