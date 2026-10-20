-- #77 — the three family words: pit, frame, rack.
--
-- V425 recorded them and left them alone, because each is SHARED and a longest-keyword pick would be a guess:
--
--   build a pit   -> reached nothing        (seven structures are pits)
--   build a frame -> reached nothing        (six are frames)
--   build a rack  -> CRAFT_SHELF, a stone shelf, which is none of the four racks the catalogue raises
--
-- The matcher already knows what to do with a phrase several assemblies share equally (#37): narrow by what is
-- under way, then by what the Chronicle has the makings of, and ASK if a real choice is left, building nothing
-- while it asks. So this gives each member of a family the same plain phrases, at the same length, and lets
-- that machinery answer. "build a cool food pit" still beats "build a pit" on length, so naming the kind settles
-- it outright, as it does for the huts.
--
-- WHO IS IN EACH FAMILY. A thing whose name ENDS in the word, or whose own `subjects` claim the bare word as its
-- name, is one. Left out on purpose:
--
--   * pit_house    — its head noun is "house". You dig a pit to make one; asking for a pit does not ask for a
--                    dwelling. It keeps "build a pit house", which is longer than "build a pit" and still wins.
--   * simple_gate  — its subjects claim "frame" for MENDING ("the frame of the gate is sprung"), but nobody
--                    asks for a gate by asking for a frame.
--
-- AND THREE PHRASES ARE NOT TAKEN, because the material processes already use them for things you carry:
-- "make a frame" and "lash a frame" (pack, burden and drag frames), and "work on the frame" (assemble_frame,
-- the cart frame). The assembly matcher runs before the processes, so claiming them would take a cart's frame.
--
-- WHAT THE JAVA SIDE DOES (same PR): CRAFT_SHELF stops claiming a bare "rack" — a shelf is still asked for as
-- shelf, shelves or archive — and the tie-narrowing no longer lets an assembly whose first stage asks for
-- NOTHING settle a tie alone. The earth oven's first stage is digging its pit, so it is "within reach" with
-- empty hands, and without that change `dig a pit` would have begun an earth oven every time.

UPDATE assembly_definition SET keywords = keywords || ',build a pit,dig a pit,work on the pit'
 WHERE assembly_key IN ('cool_food_pit','lime_burning_pit','manure_pit','offal_pit','earth_oven','compost_bay','stone_fire_pit')
   AND ',' || replace(keywords,', ',',') || ',' NOT LIKE '%,build a pit,%';

UPDATE assembly_definition SET keywords = keywords || ',build a frame,raise a frame,put up a frame'
 WHERE assembly_key IN ('hide_frame','hide_scraping_frame','roofing_frame','drying_rack','smoke_rack','milking_stanchion')
   AND ',' || replace(keywords,', ',',') || ',' NOT LIKE '%,build a frame,%';

UPDATE assembly_definition SET keywords = keywords || ',build a rack,raise a rack,make a rack,put up a rack,work on the rack'
 WHERE assembly_key IN ('drying_rack','smoke_rack','hay_rack','harness_rack')
   AND ',' || replace(keywords,', ',',') || ',' NOT LIKE '%,build a rack,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Each family phrase is owned by exactly its family — no more (a stranger in the question) and no fewer
    --    (a member the question forgets).
    SELECT string_agg(f.w || ' (' || coalesce(got, 0) || ' of ' || f.want || ')', ', ') INTO bad
      FROM (VALUES ('build a pit',7),('dig a pit',7),('work on the pit',7),
                   ('build a frame',6),('raise a frame',6),('put up a frame',6),
                   ('build a rack',4),('raise a rack',4),('make a rack',4),('put up a rack',4),('work on the rack',4)) f(w, want)
      LEFT JOIN LATERAL (SELECT COUNT(*)::int got FROM assembly_definition d
                          WHERE d.review_state = 'VERIFIED'
                            AND ',' || replace(d.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%') c ON true
     WHERE coalesce(got, 0) <> f.want;
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V427: a family phrase is not owned by exactly its family: %', bad; END IF;

    -- 2. Every member can still be named more precisely than its family phrase, so the question is answerable
    --    (the rule ConstructionRegistryCompleteIntegrationTest holds over every shared phrase).
    SELECT string_agg(d.assembly_key, ', ') INTO bad
      FROM assembly_definition d
     WHERE ',' || replace(d.keywords,', ',',') || ',' ~ ',(build a pit|build a frame|build a rack),'
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(d.keywords, ',')) x
                        WHERE length(trim(x)) > length('work on the rack'));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V427: % has no name of its own longer than its family word', bad; END IF;

    -- 3. No family phrase sits inside a material process keyword — the assembly matcher runs first and would
    --    take it.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('build a pit'),('dig a pit'),('work on the pit'),('build a frame'),('raise a frame'),
                   ('put up a frame'),('build a rack'),('raise a rack'),('make a rack'),('put up a rack'),
                   ('work on the rack')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V427: a family phrase collides with a recipe: %', bad; END IF;

    -- 4. The two left out are still reached by their own names, and only those.
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE assembly_key IN ('pit_house','simple_gate')
       AND ',' || replace(keywords,', ',',') || ',' ~ ',(build a pit|build a frame|build a rack),';
    IF n <> 0 THEN RAISE EXCEPTION 'V427: pit_house or simple_gate was put in a family it does not belong to'; END IF;
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE assembly_key = 'pit_house' AND ',' || replace(keywords,', ',',') || ',' LIKE '%,build a pit house,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V427: build a pit house no longer outranks build a pit'; END IF;
END $$;
