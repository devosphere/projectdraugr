-- V417 — the family words for fishing tackle (#37)
--
-- Found by sweeping the FISHING CHAIN end to end, and recorded in PR #774 as the half that wanted a migration
-- rather than a classifier change. Measured live:
--
--   make a hook          ->  "The stuff of it is willing enough; it is the working that will not come."
--   make a fishing line  ->  "the working of it into that is beyond what your hands and knowledge can find"
--   make a creel         ->  nothing
--
-- while the catalogue holds FOUR fish hooks (bone, bronze, thorn, iron), TWO fishing lines (bast, sinew) and a
-- creel — every one of them reachable only by naming its material or its maker's verb outright. `carve a hook`
-- worked the whole time, because that exact phrase is declared.
--
-- A FAMILY WORD FAILS IF EITHER HALF IS MISSING, AND THIS SLICE CONTAINS BOTH DIRECTIONS. The matcher wants a
-- keyword AND a `process_subject` row on the SAME process, and the three words here were each broken in a
-- different way:
--
--   hook          3 processes already carry it as a SUBJECT and none as a keyword  -> the keyword is all it needs
--   creel         1 process carries it as a SUBJECT and none as a keyword          -> likewise
--   fishing line  neither half anywhere                                            -> both
--
-- V416's `wrap` was the mirror of the first two: ten keywords declared and no bare subject anywhere, so it
-- reached nothing while the keyword count looked perfectly healthy. Checking only one half tells you nothing.
--
-- SAFETY. The matcher space-pads both sides (`ActivityClassifier.normalise` + `containsTerm`), so these are
-- whole-word matches: " hook " is not inside " hooked " or " hooks ", and nothing in the catalogue is a non-fish
-- hook — all four are fish hooks, so a plain "hook" is unambiguous here. "fishing line" and "creel" are
-- specific two- and one-word nouns with no ordinary-English collision.
--
-- DELIBERATELY NOT HERE: `line` on its own, which would collide with the picket line, the tether line and a
-- line of trees; and `trap`, `net` and `snare`, which are owned by the SET_TRAP and CRAFT_NET Java intents and
-- answer correctly today — a process keyword cannot outrank a Java intent, so adding one would be inert.

-- The keyword half.
UPDATE material_process mp SET keywords = keywords || ',hook'
 WHERE mp.process_key IN ('carve_fish_hook','carve_thorn_hook','forge_bronze_fish_hook','forge_iron_fish_hook')
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'hook');

UPDATE material_process mp SET keywords = keywords || ',creel'
 WHERE mp.process_key = 'carve_fish_creel'
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'creel');

UPDATE material_process mp SET keywords = keywords || ',fishing line'
 WHERE mp.process_key IN ('twist_line_bast','twist_line_sinew')
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'fishing line');

-- The subject half, for the processes that do not already carry it.
INSERT INTO process_subject (process_key, subject_term)
SELECT process_key, 'hook' FROM material_process
 WHERE process_key IN ('carve_fish_hook','carve_thorn_hook','forge_bronze_fish_hook','forge_iron_fish_hook')
ON CONFLICT (process_key, subject_term) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term)
SELECT 'carve_fish_creel', 'creel'
ON CONFLICT (process_key, subject_term) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term)
SELECT process_key, 'fishing line' FROM material_process
 WHERE process_key IN ('twist_line_bast','twist_line_sinew')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- 1. BOTH HALVES ON THE SAME PROCESS, for each word. This is the guard V416's first cut lacked, and its
  --    absence is why `make a wrap` stayed dead with ten keywords declared. A keyword count proves nothing.
  FOREACH bad IN ARRAY ARRAY['hook','creel','fishing line'] LOOP
    SELECT count(*) INTO n FROM material_process mp
     WHERE EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = bad)
       AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key = mp.process_key
                                                      AND ps.subject_term = bad);
    IF n = 0 THEN
      RAISE EXCEPTION 'V417: no single process carries "%" as BOTH a keyword and a subject, so the family word '
        'is inert however many of either are declared', bad;
    END IF;
  END LOOP;

  -- 2. AND "hook" MUST REACH MORE THAN ONE, since four hooks exist and the point of the word is the ask.
  SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = 'hook';
  IF n < 2 THEN
    RAISE EXCEPTION 'V417: "hook" reaches only % process; four fish hooks exist and the family word should '
      'offer the choice', n;
  END IF;

  -- 3. NO BARE "line". It collides with the picket line, the tether line and a line of trees, and unlike the
  --    words above it names a dozen things that are not tackle. Guarded so a later cycle reaching for the
  --    obvious shortening has to argue for it first.
  SELECT string_agg(DISTINCT process_key, ', ') INTO bad
    FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = 'line';
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V417: a bare "line" keyword exists on %, and a line is also a picket line, a tether line '
      'and a line of trees', bad;
  END IF;

  -- 4. No keyword list may exceed the column. V404 failed on a seeded database while no-opping on a fresh one.
  SELECT string_agg(process_key, ', ') INTO bad FROM material_process WHERE length(keywords) > 200;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V417: these keyword lists no longer fit varchar(200): %', bad;
  END IF;
END $$;
