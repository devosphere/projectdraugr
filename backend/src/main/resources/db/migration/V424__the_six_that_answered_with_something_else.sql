-- #108 — the six phrases that asked to BUILD something and got a material recipe instead.
--
-- #108 names 47 pieces of animal infrastructure. Counting keys gave 19 present and 28 absent; measuring all 28
-- live gave 19 present, 6 reachable under another name, 5 answered by the Java BUILD_PEN intent, 11 genuinely
-- unreachable — and SIX that answered with something else entirely, which is worse than missing:
--
--   build a swine water trough      -> PROCESS_MATERIAL  "Carve a wooden trough turns on an axe..."
--   build a rainwater trough supply -> PROCESS_MATERIAL  the same carve
--   build a stone boundary wall     -> PROCESS_MATERIAL  "You have not got enough CLAY LUMP" (the daub recipe)
--   build a root fodder cache       -> STORE             "You have no container within reach to store anything in"
--   build a cart shed               -> PROCESS_MATERIAL  "You have not got enough CART WHEEL within reach"
--   build a wheel repair stand      -> PROCESS_MATERIAL  wants timber plank for another recipe
--
-- ASKING FOR A SHED AND BEING SENT TO FIND A CART WHEEL is the shape this project treats as a defect rather than
-- a gap: the composer sounded certain and did something unrelated. It is BUILDING IS NOT MAKING, the inverse of
-- the "making is not doing" face that `make a fish hook` had when it went fishing.
--
-- EVERY THIEF IS A BARE SINGLE WORD on a material_process, and the longest-keyword matcher simply had nothing
-- longer to prefer:
--
--   wall   -> course_dry_stone, lay_mortared_course, weave_wattle_panel, daub_panel
--   trough -> carve_wooden_trough
--   fodder -> dry_grass
--   wheel  -> make_cart_wheel
--   cart   -> make_cart  (and "build a cart", 12 characters, which is why "build a cart shed" lost)
--
-- Four of the six are things the catalogue ALREADY BUILDS and had not been told the story's word for; two are
-- genuinely absent and are built here. Nothing is taken away from any recipe: each addition is simply longer and
-- more specific than the bare word, which is how this matcher is meant to settle a contest.

-- ------------------------------------------------------------------------------------- THE FOUR THAT EXISTED
-- A watering station IS a water trough, and the swine have no separate plumbing. "water trough" at 12
-- characters beats the recipe's bare "trough" at 6.
UPDATE assembly_definition SET keywords = keywords || ',swine water trough,build a swine water trough,water trough,pig water trough,build a water trough'
 WHERE assembly_key = 'watering_station' AND ',' || keywords || ',' NOT LIKE '%,water trough,%';

-- A rainwater catchment IS the supply. The story calls it a trough supply, which reached the carving recipe.
UPDATE assembly_definition SET keywords = keywords || ',rainwater trough supply,build a rainwater trough supply,rainwater supply,rainwater trough'
 WHERE assembly_key = 'rainwater_catchment' AND ',' || keywords || ',' NOT LIKE '%,rainwater supply,%';

-- A low stone wall IS a stone boundary wall in the first era — #77 names both and there is one thing. The bare
-- "wall" belongs to four separate recipes, so the specific phrase has to be longer than all of them, and is.
UPDATE assembly_definition SET keywords = keywords || ',stone boundary wall,build a stone boundary wall,boundary wall,build a boundary wall,stone wall'
 WHERE assembly_key = 'stone_wall_low' AND ',' || keywords || ',' NOT LIKE '%,boundary wall,%';

-- A fodder store IS where root fodder is cached. "fodder" alone belonged to dry_grass, so asking to build the
-- cache went looking for a container to store something in.
UPDATE assembly_definition SET keywords = keywords || ',root fodder cache,build a root fodder cache,fodder cache,root cache,build a fodder cache'
 WHERE assembly_key = 'fodder_store' AND ',' || keywords || ',' NOT LIKE '%,fodder cache,%';

-- --------------------------------------------------------------------------------------- THE TWO THAT DID NOT
-- Both carry a capability flag live code reads, on the standard this project holds: an object must be functional
-- end to end and never a catalogue token. A cart shed keeps gear out of the weather (shelters_gear, which the
-- gear-condition pass reads); a wheel repair stand is a workstation (is_workstation, which siting a process
-- within reach reads).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays,
                               encloses, is_barrier, barrier_strength, holds_fire, eases_going,
                               crosses_soft_ground, holds_open_water, shelters_birth, aids_mounting,
                               shelters_stock, shelters_gear, gives_shade, shelters_up_to_size) VALUES
 ('CART_SHED',          'Cart shed',          'construction', true,  false, true, false, false, 0, false, 0, false, false, false, false, false, true,  true,  NULL),
 ('WHEEL_REPAIR_STAND', 'Wheel repair stand', 'woodworking',  false, true,  true, false, false, 0, false, 0, false, false, false, false, false, false, false, NULL)
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, construction_kind,
                                 domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
 ('cart_shed', 'STRUCTURE', 'Cart shed', false, 'CART_SHED', 'construction',
  -- "build a cart shed" is 17 characters against make_cart's "build a cart" at 12, which is the whole contest.
  'build a cart shed,raise a cart shed,cart shed,build a cart house,cart house,wagon shed,build a wagon shed',
  'cart shed,cart house,wagon shed',
  'An open-fronted bay roofed high and wide enough to run a cart in under cover, floored dry so the wheels do not stand in wet.',
  'VERIFIED', now()),
 ('wheel_repair_stand', 'STRUCTURE', 'Wheel repair stand', false, 'WHEEL_REPAIR_STAND', 'woodworking',
  'build a wheel repair stand,raise a wheel repair stand,wheel repair stand,wheel stand,build a wheel stand,wheelwright stand',
  'wheel repair stand,wheel stand,wheelwright stand',
  'A low cradle that takes a wheel off the ground and holds it steady at the height of a working hand, so a spoke can be driven without the whole cart leaning on it.',
  'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
 ('cartshed_frame', 'cart_shed',          1, 'Frame the bay',        NULL,             0, 'AXE',     false, 'You frame the bay wide enough to run a cart in and high enough to walk beside it, leaving the front open.'),
 ('cartshed_roof',  'cart_shed',          2, 'Roof it',              'cartshed_frame', 0, 'CUTTING', false, 'You thatch the roof with the fall running back, so what comes off it does not come off onto the cart.'),
 ('cartshed_floor', 'cart_shed',          3, 'Floor it dry',         'cartshed_roof',  0, NULL,      false, 'You bed the floor with gravel and tread it down, because a wheel left standing in wet is a wheel that rots from the rim in.'),
 ('wheelstand_cut', 'wheel_repair_stand', 1, 'Cut the cradle',       NULL,             0, 'AXE',     false, 'You cut a cradle of two notched uprights and a cross-piece, sized to the wheel rather than to the timber.'),
 ('wheelstand_set', 'wheel_repair_stand', 2, 'Set and test it',      'wheelstand_cut', 0, 'STRIKING',false, 'You bed it firm and lean on it hard before you trust a wheel to it, because a stand that shifts under a hammer is worse than none.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('cartshed_frame', 'dry_branch', 4), ('cartshed_frame', 'fiber_cordage', 2),
 ('cartshed_roof', 'thatch_bundle', 3),
 ('cartshed_floor', 'river_gravel', 3),
 ('wheelstand_cut', 'timber_plank', 2),
 ('wheelstand_set', 'field_stone', 2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT; thief TEXT; mine INT;
BEGIN
    -- 1. THE ACTUAL CONTEST, SETTLED IN SQL. For each of the six sentences, find the longest material_process
    --    keyword that matches it and the longest assembly keyword that matches it, and insist the assembly wins.
    --    This is the guard that matters: asserting the keyword merely EXISTS would pass while the bare word still
    --    took the sentence, which is exactly the mistake V411's `cap` guard made by testing a phrase a Java
    --    intent already owned.
    FOR bad IN SELECT s FROM (VALUES
            ('build a swine water trough'),('build a rainwater trough supply'),('build a stone boundary wall'),
            ('build a root fodder cache'),('build a cart shed'),('build a wheel repair stand')) v(s)
    LOOP
        SELECT COALESCE(MAX(length(trim(k))), 0) INTO n FROM material_process mp, unnest(string_to_array(mp.keywords,',')) k
         WHERE ' ' || bad || ' ' LIKE '% ' || lower(trim(k)) || ' %';
        SELECT COALESCE(MAX(length(trim(k))), 0) INTO mine FROM assembly_definition ad, unnest(string_to_array(ad.keywords,',')) k
         WHERE ad.review_state = 'VERIFIED' AND ' ' || bad || ' ' LIKE '% ' || lower(trim(k)) || ' %';
        IF mine <= n THEN
            SELECT string_agg(DISTINCT mp.process_key || ' via "' || lower(trim(k)) || '"', ', ') INTO thief
              FROM material_process mp, unnest(string_to_array(mp.keywords,',')) k
             WHERE ' ' || bad || ' ' LIKE '% ' || lower(trim(k)) || ' %' AND length(trim(k)) = n;
            RAISE EXCEPTION 'V424: "%" still loses to a recipe (% chars) with only % chars of its own: %',
                bad, n, mine, COALESCE(thief, 'none');
        END IF;
    END LOOP;

    -- 2. And each of the six names reaches exactly ONE assembly. They name particular things, so an ambiguous
    --    "which one?" would be the wrong answer.
    FOR bad IN SELECT w FROM (VALUES ('water trough'),('rainwater supply'),('boundary wall'),('fodder cache'),
                                     ('cart shed'),('wheel repair stand')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n <> 1 THEN RAISE EXCEPTION 'V424: "%" is claimed by % assemblies; a named thing is one thing', bad, n; END IF;
    END LOOP;

    -- 3. NOTHING THAT ALREADY WORKED HAS BEEN DISPLACED. The four renamed assemblies must still answer to the
    --    catalogue's own name — V422's guard, which got this wrong the first time by testing keyword LENGTH.
    SELECT string_agg(f.a, ', ') INTO bad
      FROM (VALUES ('watering_station','watering trough'),('rainwater_catchment','rainwater catchment'),
                   ('stone_wall_low','low stone wall'),('fodder_store','fodder store')) f(a,w)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.assembly_key = f.a
                          AND ',' || replace(ad.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: % no longer answers to its own name', bad; END IF;

    -- 4. The two built are VERIFIED, staged, made of real materials, and not inert — the same four questions
    --    V423 asked of its twelve, because a DRAFT row is a structure nobody can build and a flagless kind is a
    --    display name pretending to be a place.
    SELECT string_agg(v.a, ', ') INTO bad FROM (VALUES ('cart_shed'),('wheel_repair_stand')) v(a)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition d
                        WHERE d.assembly_key = v.a AND d.review_state = 'VERIFIED'
                          AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = d.construction_kind)
                          AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = v.a));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: % is missing, DRAFT, unstaged, or points at no kind', bad; END IF;

    SELECT string_agg(s.stage_key, ', ') INTO bad FROM assembly_stage s
     WHERE s.assembly_key IN ('cart_shed','wheel_repair_stand')
       AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: % needs nothing to build it', bad; END IF;

    SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
      FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
     WHERE s.assembly_key IN ('cart_shed','wheel_repair_stand')
       AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = r.item_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: no such material: %', bad; END IF;

    SELECT string_agg(ck.project_kind, ', ') INTO bad FROM construction_kind ck
     WHERE ck.project_kind IN ('CART_SHED','WHEEL_REPAIR_STAND')
       AND NOT (ck.is_shelter OR ck.is_workstation OR ck.encloses OR ck.is_barrier OR ck.holds_fire
                OR ck.crosses_soft_ground OR ck.holds_open_water OR ck.shelters_birth OR ck.aids_mounting
                OR ck.shelters_stock OR ck.shelters_gear OR ck.gives_shade OR ck.eases_going > 0 OR ck.barrier_strength > 0);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: % declares no capability and would be a token', bad; END IF;

    -- 5. The standing guard, still holding across the whole catalogue.
    SELECT string_agg(ad.assembly_key, ', ') INTO bad FROM assembly_definition ad
     WHERE ad.review_state <> 'VERIFIED' AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V424: % is fully staged and still unreachable', bad; END IF;
END $$;
