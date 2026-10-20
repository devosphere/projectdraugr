-- #108 — the eleven pieces of animal infrastructure that reached nothing at all.
--
-- The audit on this story measured all 47 names live: 19 present by key, 6 reachable under another name, 5
-- answered by the Java BUILD_PEN intent, 6 wrongly routed (fixed in V424) and ELEVEN that reached nothing.
-- These are the eleven, and the same question was asked of each before anything was built, because that question
-- has now changed the answer five times running on #77: A KEY ABSENT FROM THE CATALOGUE IS ONLY A GAP IF
-- NOTHING ELSE OWNS THE PHRASE.
--
-- SIX ARE BUILT, because each needs something the catalogue has nowhere to put:
--
--   nesting_box        the only thing in the catalogue where a bird LAYS. shelters_birth at SMALL.
--   poultry_run        an OUTDOOR enclosure; poultry_coop is the enclosed night shelter and is not it.
--   duck_house         a wetland shelter. #108 is explicit: "not a dry coop copy".
--   waterfowl_run      an enclosure WITH open water in it, which no run or pool is on its own.
--   yak_shelter        cold and highland. Every stock shelter in the catalogue gives SHADE; not one breaks a
--                      wind, and shade is the wrong answer on a cold hillside.
--   double_gate_entry  a biosecurity airlock — you pass the first gate before the second opens. simple_gate is
--                      one gate, which is precisely what this is not.
--
-- FIVE ARE RENAMED, because something already does the job and a second one would be a token:
--
--   donkey shelter          -> horse_stable       an equine shelter at LARGE with shade. A donkey is an equine.
--   buffalo shed            -> cattle_byre        a bovine house at HUGE with shade. A buffalo is a bovine.
--   loading platform        -> animal_loading_ramp the thing you walk stock up to cart height.
--   rotational grazing gate -> simple_gate        a gate in a fence line is a gate.
--   tool wash station       -> washing_place      a flat stone at the water with bedded standing ground.
--
-- Those five were each measured live first: the thing being renamed to answers the sentence, and does so with
-- the capability the story asks for. Where it did not, the structure was built instead — which is why the yak
-- shelter is in the first list and the buffalo shed is in the second.

-- ------------------------------------------------------------------------------------------- THE SIX BUILT
-- Every one carries a capability flag live code already reads; a structure whose only property is existing is
-- worse than no structure, because it is a promise the game cannot keep. The CHECK constraint
-- sized_for_the_stock_it_holds requires a size wherever stock or birth are sheltered, and it is right to:
-- a shelter whose size is unknown cannot be matched against the beast trying to get into it.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays,
                               encloses, is_barrier, barrier_strength, holds_fire, eases_going,
                               crosses_soft_ground, holds_open_water, shelters_birth, aids_mounting,
                               shelters_stock, shelters_gear, gives_shade, separates_dangerous,
                               shelters_up_to_size) VALUES
 ('NESTING_BOX',       'Nesting box',       'construction', true,  false, true, false, false, 0, false, 0, false, false, true,  false, true,  false, false, false, 'SMALL'),
 ('POULTRY_RUN',       'Poultry run',       'construction', false, false, true, true,  true,  1, false, 0, false, false, false, false, true,  false, true,  false, 'SMALL'),
 ('DUCK_HOUSE',        'Duck house',        'construction', true,  false, true, false, false, 0, false, 0, false, false, false, false, true,  false, false, false, 'SMALL'),
 ('WATERFOWL_RUN',     'Waterfowl run',     'construction', false, false, true, true,  true,  1, false, 0, false, true,  false, false, true,  false, false, false, 'SMALL'),
 -- The only stock shelter in the catalogue that does NOT give shade. A yak wants the wind kept off, and on a
 -- cold hillside shade is the opposite of help — so it encloses instead.
 ('YAK_SHELTER',       'Yak shelter',       'construction', true,  false, true, true,  false, 0, false, 0, false, false, false, false, true,  false, false, false, 'HUGE'),
 -- is_barrier at strength 3 and NOTHING ELSE. The first draft also set separates_dangerous, which was a loose
 -- reading of the flag and would have broken ABullAmongTheYoungIntegrationTest's "there is one bull yard" —
 -- correctly, because that test asserts a real design fact: separating a DANGEROUS animal from the others,
 -- isolating a SICK one, and restraining one to work on it are three different jobs with three structures. A
 -- double gate entry does none of them. It stops anything slipping past you while you are busy with a latch,
 -- which is what a barrier is for. Caught before pushing by re-running the exact-count sweep, not by CI.
 ('DOUBLE_GATE_ENTRY', 'Double gate entry', 'construction', false, false, true, false, true,  3, false, 0, false, false, false, false, false, false, false, false, NULL)
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, construction_kind,
                                 domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
 ('nesting_box', 'STRUCTURE', 'Nesting box', false, 'NESTING_BOX', 'construction',
  'build a nesting box,make a nesting box,nesting box,build a nest box,nest box,laying box,build a laying box',
  'nesting box,nest box,laying box',
  'A dim, close box bedded with soft straw and set off the ground, where a bird will sit because it feels unseen.',
  'VERIFIED', now()),
 ('poultry_run', 'STRUCTURE', 'Poultry run', false, 'POULTRY_RUN', 'construction',
  'build a poultry run,fence a poultry run,poultry run,build a chicken run,chicken run,build a hen run,hen run',
  'poultry run,chicken run,hen run',
  'A fenced stretch of ground with brush laid over part of it, so the birds can scratch in the open and still have somewhere to be when a shadow crosses.',
  'VERIFIED', now()),
 ('duck_house', 'STRUCTURE', 'Duck house', false, 'DUCK_HOUSE', 'construction',
  'build a duck house,raise a duck house,duck house,build a duck shelter,duck shelter',
  'duck house,duck shelter',
  'A low shelter floored above the wet with a ramp down to the water, because a duck put in a dry coop will stand at the door all night.',
  'VERIFIED', now()),
 ('waterfowl_run', 'STRUCTURE', 'Waterfowl run', false, 'WATERFOWL_RUN', 'construction',
  'build a waterfowl run,fence a waterfowl run,waterfowl run,build a duck run,duck run',
  'waterfowl run,duck run',
  'A fenced stretch taking in standing water, so the birds can get on and off it without being herded there.',
  'VERIFIED', now()),
 ('yak_shelter', 'STRUCTURE', 'Yak shelter', false, 'YAK_SHELTER', 'construction',
  'build a yak shelter,raise a yak shelter,yak shelter,build a yak shed,yak shed,highland stock shelter',
  'yak shelter,yak shed',
  'A heavy three-sided shelter with its back to the weather and its mouth downhill, built to break a wind rather than to cast a shade.',
  'VERIFIED', now()),
 ('double_gate_entry', 'STRUCTURE', 'Double gate entry', false, 'DOUBLE_GATE_ENTRY', 'construction',
  'build a double gate entry,raise a double gate entry,double gate entry,build a double gate,double gate,stock airlock',
  'double gate entry,double gate',
  'Two gates and a short pen between them, so one is shut before the other is opened and nothing gets past you while you are busy with the latch.',
  'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
 ('nestbox_cut',    'nesting_box',       1, 'Cut and join the box',   NULL,              0, 'CUTTING',  false, 'You cut the boards and join them into a box open at one end, sized so a sitting bird fills it.'),
 ('nestbox_bed',    'nesting_box',       2, 'Bed and set it',         'nestbox_cut',     0, NULL,       false, 'You bed it deep with soft straw and set it off the ground in the dimmest corner you can find.'),
 ('poultryrun_fence','poultry_run',      1, 'Fence the ground',       NULL,              0, 'AXE',      false, 'You drive stakes and weave the fence close enough at the bottom that nothing can push under it.'),
 ('poultryrun_cover','poultry_run',      2, 'Lay cover over part',    'poultryrun_fence',0, 'CUTTING',  false, 'You lay brush over one end, because a bird in the open with nowhere to go is a bird a hawk has already seen.'),
 ('duckhouse_frame','duck_house',        1, 'Frame it above the wet', NULL,              0, 'AXE',      false, 'You frame the floor clear of the wet ground and set the whole thing where the morning sun will reach it.'),
 ('duckhouse_ramp', 'duck_house',        2, 'Lay the ramp to water',  'duckhouse_frame', 0, 'CUTTING',  false, 'You lay a ramp from the door down to the water, cleated so wet feet do not slide on it.'),
 ('fowlrun_fence',  'waterfowl_run',     1, 'Fence to the water',     NULL,              0, 'AXE',      false, 'You run the fence down to the water and a little way into it, so the run holds both the bank and the shallows.'),
 ('fowlrun_bank',   'waterfowl_run',     2, 'Bed the bank',           'fowlrun_fence',   0, NULL,       false, 'You gravel the going on and off the water, because a trodden bank becomes a mire and then a trap.'),
 ('yakshed_back',   'yak_shelter',       1, 'Raise the back wall',    NULL,              0, 'AXE',      false, 'You raise the back wall into the weather first and build heavy, because this is the side that has to stand.'),
 ('yakshed_sides',  'yak_shelter',       2, 'Close two sides',        'yakshed_back',    0, 'AXE',      false, 'You close two sides and leave the third open downhill, so the wind goes around and the air still moves.'),
 ('yakshed_roof',   'yak_shelter',       3, 'Roof it low',            'yakshed_sides',  60, 'CUTTING',  false, 'You roof it low and pitch it steep, so snow slides off instead of sitting until the rafters complain.'),
 ('doublegate_pen', 'double_gate_entry', 1, 'Pen the space between',  NULL,              0, 'AXE',      false, 'You fence a short pen the length of one beast, which is the whole idea: somewhere to be while both gates are shut.'),
 ('doublegate_hang','double_gate_entry', 2, 'Hang both gates',        'doublegate_pen',  0, 'CUTTING',  false, 'You hang both gates to swing inward and latch, and try them one at a time before you trust the pair.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('nestbox_cut', 'timber_plank', 2),
 ('nestbox_bed', 'green_grass_bundle', 2),
 ('poultryrun_fence', 'hazel_rod', 4), ('poultryrun_fence', 'fiber_cordage', 2),
 ('poultryrun_cover', 'dry_branch', 3),
 ('duckhouse_frame', 'timber_plank', 3), ('duckhouse_frame', 'dry_branch', 2),
 ('duckhouse_ramp', 'timber_plank', 2),
 ('fowlrun_fence', 'hazel_rod', 4), ('fowlrun_fence', 'fiber_cordage', 2),
 ('fowlrun_bank', 'river_gravel', 3),
 ('yakshed_back', 'timber_plank', 4), ('yakshed_back', 'field_stone', 4),
 ('yakshed_sides', 'timber_plank', 4),
 ('yakshed_roof', 'thatch_bundle', 4),
 ('doublegate_pen', 'dry_branch', 4), ('doublegate_pen', 'fiber_cordage', 2),
 ('doublegate_hang', 'timber_plank', 4), ('doublegate_hang', 'fiber_cordage', 2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

-- -------------------------------------------------------------------------------------- THE FIVE RENAMED
-- Each of these was put through the live composer first, and the thing named below answers the sentence AND
-- carries the capability the story asks for. Where that was not true the structure was built instead.
UPDATE assembly_definition SET keywords = keywords || ',donkey shelter,build a donkey shelter,raise a donkey shelter,donkey shed,build a donkey shed'
 WHERE assembly_key = 'horse_stable' AND ',' || keywords || ',' NOT LIKE '%,donkey shelter,%';

UPDATE assembly_definition SET keywords = keywords || ',buffalo shed,build a buffalo shed,raise a buffalo shed,buffalo house'
 WHERE assembly_key = 'cattle_byre' AND ',' || keywords || ',' NOT LIKE '%,buffalo shed,%';

UPDATE assembly_definition SET keywords = keywords || ',loading platform,build a loading platform,raise a loading platform'
 WHERE assembly_key = 'animal_loading_ramp' AND ',' || keywords || ',' NOT LIKE '%,loading platform,%';

UPDATE assembly_definition SET keywords = keywords || ',rotational grazing gate,build a rotational grazing gate,grazing gate,build a grazing gate'
 WHERE assembly_key = 'simple_gate' AND ',' || keywords || ',' NOT LIKE '%,grazing gate,%';

UPDATE assembly_definition SET keywords = keywords || ',tool wash station,build a tool wash station,wash station,build a wash station'
 WHERE assembly_key = 'washing_place' AND ',' || keywords || ',' NOT LIKE '%,wash station,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. All six exist, are VERIFIED, point at a kind that exists, and are staged. A DRAFT row is a structure
    --    nobody can build — V420 found the earth oven in exactly that state, complete and unreachable.
    SELECT string_agg(v.a, ', ') INTO bad FROM (VALUES
        ('nesting_box'),('poultry_run'),('duck_house'),('waterfowl_run'),('yak_shelter'),('double_gate_entry')) v(a)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition d
                        WHERE d.assembly_key = v.a AND d.review_state = 'VERIFIED'
                          AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = d.construction_kind)
                          AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = v.a));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: % is missing, DRAFT, unstaged, or points at no kind', bad; END IF;

    -- 2. Every stage is made of something, and of something that exists. A stage with no requirement is matter
    --    from nothing, which the Auditor's conservation rule fails most of the suite over.
    SELECT string_agg(s.stage_key, ', ') INTO bad FROM assembly_stage s
     WHERE s.assembly_key IN ('nesting_box','poultry_run','duck_house','waterfowl_run','yak_shelter','double_gate_entry')
       AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: % needs nothing to build it', bad; END IF;

    SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
      FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
     WHERE s.assembly_key IN ('nesting_box','poultry_run','duck_house','waterfowl_run','yak_shelter','double_gate_entry')
       AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = r.item_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: no such material: %', bad; END IF;

    -- 3. NOT ONE IS INERT, checked as a property of the row rather than trusted from the INSERT above.
    SELECT string_agg(ck.project_kind, ', ') INTO bad FROM construction_kind ck
     WHERE ck.project_kind IN ('NESTING_BOX','POULTRY_RUN','DUCK_HOUSE','WATERFOWL_RUN','YAK_SHELTER','DOUBLE_GATE_ENTRY')
       AND NOT (ck.is_shelter OR ck.is_workstation OR ck.encloses OR ck.is_barrier OR ck.holds_fire
                OR ck.crosses_soft_ground OR ck.holds_open_water OR ck.shelters_birth OR ck.aids_mounting
                OR ck.shelters_stock OR ck.shelters_gear OR ck.gives_shade OR ck.separates_dangerous
                OR ck.eases_going > 0 OR ck.barrier_strength > 0);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: % declares no capability and would be a token', bad; END IF;

    -- 4. THE YAK SHELTER IS THE ONE THAT DOES NOT GIVE SHADE, which is the whole reason it was built rather
    --    than renamed onto the byre. If it ever starts giving shade, the argument for it existing has gone.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='YAK_SHELTER' AND gives_shade) THEN
        RAISE EXCEPTION 'V426: a yak shelter that gives shade is a cattle byre — rename it or restate the reasoning';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='YAK_SHELTER' AND encloses AND is_shelter) THEN
        RAISE EXCEPTION 'V426: a yak shelter must break a wind — it has to enclose and shelter';
    END IF;

    -- 5. Each renamed name reaches exactly ONE assembly, and has not displaced the catalogue's own name.
    FOR bad IN SELECT w FROM (VALUES ('donkey shelter'),('buffalo shed'),('loading platform'),
                                     ('grazing gate'),('wash station')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n <> 1 THEN RAISE EXCEPTION 'V426: "%" is claimed by % assemblies; a named thing is one thing', bad, n; END IF;
    END LOOP;

    SELECT string_agg(f.a, ', ') INTO bad
      FROM (VALUES ('horse_stable','horse stable'),('cattle_byre','cattle byre'),
                   ('animal_loading_ramp','loading ramp'),('simple_gate','simple gate'),
                   ('washing_place','washing place')) f(a,w)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.assembly_key = f.a
                          AND ',' || replace(ad.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: % no longer answers to its own name', bad; END IF;

    -- 6. No new name sits inside a material process keyword.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('nesting box'),('nest box'),('poultry run'),('chicken run'),('duck house'),('duck run'),
                   ('yak shelter'),('double gate'),('donkey shelter'),('buffalo shed'),('loading platform'),
                   ('grazing gate'),('wash station')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: a structure name collides with a recipe: %', bad; END IF;

    -- 7. WHAT THIS GUARD CANNOT SEE. A Java intent runs BEFORE the keyword matcher, so every addition here can
    --    satisfy every check above and still be taken first — `build a calving pen` (BUILD_PEN) and
    --    `build a root fodder cache` (STORE) both did, and the second one was only caught by the standing guard
    --    noJavaIntentShadowsAnAssemblysOwnKeywords after a full CI round. Every keyword added here was replayed
    --    through a booted backend AS ITS OWN BARE PHRASE, not inside a sentence of my own invention, before this
    --    file was committed.
    SELECT string_agg(ad.assembly_key, ', ') INTO bad FROM assembly_definition ad
     WHERE ad.review_state <> 'VERIFIED' AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V426: % is fully staged and still unreachable', bad; END IF;
END $$;
