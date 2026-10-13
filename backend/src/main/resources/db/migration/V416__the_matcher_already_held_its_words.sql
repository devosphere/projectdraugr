-- V416 — the matcher already held its words, so bow, cape and wrap can have theirs (#37)
--
-- THIS CORRECTS REASONING I PUBLISHED IN V415 ONE SLICE AGO, AND IN V411 BEFORE IT.
--
-- Both migrations refused a family word on the grounds that it sits inside an ordinary English word with no
-- longer keyword to outrank it. V411 on "cape":
--
--     "cape" sits inside ESCAPE, and "the goat escaped" is a sentence this game hears; unlike the two
--      collisions below there is no longer competing keyword to beat it on length.
--
-- and V415 inherited that and extended it to "bow" (inside ELBOW) and "wrap" (inside WRAPPED).
--
-- THE PREMISE WAS WRONG. The process keyword matcher does not match raw substrings. `ActivityClassifier.
-- normalise` SPACE-PADS both sides — " escape the wolf " — and `containsTerm` asks whether the padded text
-- contains the padded term, so " cape " is not found inside " escape ". The matcher has held keywords to whole
-- words all along, and the longest-keyword rule is about choosing between COMPETING whole-word matches, not
-- about defending against substrings.
--
-- I conflated two different matchers. `ChronicleActionService.classify` genuinely does use raw `contains`, and
-- that is where every collision this project has suffered came from — "stable" inside CRAFT_DESK's "table",
-- "fly agaric" inside the impossible-act list, "boar" inside floorboard, "weather" inside "eat". The process
-- KEYWORD matcher is a different mechanism with a different rule, and I carried the classifier's hazard across
-- to it without checking.
--
-- MEASURED, not reasoned, this time. "cap" has been a declared keyword since V411 and "es-CAP-e" contains it as
-- a raw substring. On a booted backend:
--
--     escape the wolf   -> UNKNOWN      (no hat offered)
--     escape from here  -> DISENGAGE    (no hat offered)
--     is the death cap poisonous -> the death cap, because "death cap" contains the WORD "cap"
--
-- If the matcher were raw-contains, the first two would have answered with millinery. They do not.
--
-- SO THESE THREE FAMILY WORDS ARE SAFE, and they are worth having: 4 bows, 7 capes and 12 wraps exist, every
-- one reachable only by naming its material outright. `make a bow` — in a game whose core loop is hunting —
-- reached nothing at all.
--
--   bow   -> 4 processes. " elbow ", " bowl " and " bowstring " are each ONE word and cannot contain " bow ".
--   cape  -> 7 processes. " escape " likewise.
--   wrap  -> 12 processes. " wrapped " and " wrapping " likewise.
--
-- The one real collision in this family remains the one V411 found and this does not change: "death cap"
-- contains the word "cap", separately spelled, so it genuinely competes — and loses on length, which is what
-- the longest-keyword rule is actually for.

-- A FAMILY WORD IS TWO HALVES, NOT ONE, and the first cut of this migration only did one. The matcher requires
-- a keyword match AND a subject match, so a word declared as a keyword alone reaches nothing. `make a bow` and
-- `make a cape` worked on the first try because those processes already carried a bare "bow"/"cape" SUBJECT from
-- their own naming; `make a wrap` stayed dead because the wrap processes carry only compound subjects — "bark
-- wrap", "face wrap", "hand wrap" — and never the bare word. Measured all three rather than one, which is the
-- only reason this was caught here instead of shipping as a fix that fixed nothing.
--
-- AND THE IDEMPOTENCY GUARD ITSELF HAD THE SUBSTRING DEFECT. `keywords NOT LIKE '%,wrap%'` is a substring test,
-- so it skipped every row whose list already held a keyword merely BEGINNING with "wrap" — `make_leaf_wrap`
-- declares ",wrap it in leaves" and was silently excluded, which is exactly the row that mattered because it is
-- one of only two carrying the bare subject. The guards below use the same exact unnest/trim comparison the
-- assertions do, so the skip condition and the assertion cannot disagree.

UPDATE material_process mp SET keywords = keywords || ',bow'
 WHERE mp.process_key IN ('assemble_bow','assemble_long_self_bow','assemble_recurve_wood_bow',
                          'assemble_short_self_bow')
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'bow');

UPDATE material_process mp SET keywords = keywords || ',cape'
 WHERE mp.process_key IN ('make_bark_cape','make_bark_shoulder_cape','make_grass_cape','make_hide_shoulder_cape',
                          'make_leather_shoulder_cape','make_reed_rain_cape','make_reed_shoulder_cape')
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'cape');

UPDATE material_process mp SET keywords = keywords || ',wrap'
 WHERE mp.output_item_key LIKE '%wrap'
   AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = 'wrap');

-- The subject half. Only for the three family words this migration adds, and only on the processes that already
-- answer to the keyword — a subject without a keyword is as inert as a keyword without a subject.
INSERT INTO process_subject (process_key, subject_term)
SELECT mp.process_key, 'wrap' FROM material_process mp
 WHERE mp.output_item_key LIKE '%wrap'
ON CONFLICT (process_key, subject_term) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term)
SELECT mp.process_key, 'bow' FROM material_process mp
 WHERE mp.process_key IN ('assemble_bow','assemble_long_self_bow','assemble_recurve_wood_bow',
                          'assemble_short_self_bow')
ON CONFLICT (process_key, subject_term) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term)
SELECT mp.process_key, 'cape' FROM material_process mp
 WHERE mp.process_key IN ('make_bark_cape','make_bark_shoulder_cape','make_grass_cape','make_hide_shoulder_cape',
                          'make_leather_shoulder_cape','make_reed_rain_cape','make_reed_shoulder_cape')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- 1. EACH FAMILY WORD MUST REACH SEVERAL PROCESSES. One would mean it is a specific name, and none would mean
  --    the IN-list has drifted from the catalogue's own process keys — which does nothing and looks fine.
  FOREACH bad IN ARRAY ARRAY['bow','cape','wrap'] LOOP
    SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = bad;
    IF n < 2 THEN
      RAISE EXCEPTION 'V416: the family word "%" reaches % processes; expected several', bad, n;
    END IF;
  END LOOP;

  -- 1b. AND EACH MUST BE A SUBJECT ON A PROCESS THAT ALSO ANSWERS TO IT AS A KEYWORD. This is the guard the
  --     first cut of this migration did not have, and its absence is why `make a wrap` stayed dead while the
  --     keyword count looked healthy at ten. The matcher wants BOTH halves on the SAME process; either alone
  --     is inert, and a count of keywords alone cannot tell you that.
  FOREACH bad IN ARRAY ARRAY['bow','cape','wrap'] LOOP
    SELECT count(*) INTO n FROM material_process mp
     WHERE EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k) = bad)
       AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key = mp.process_key
                                                      AND ps.subject_term = bad);
    IF n = 0 THEN
      RAISE EXCEPTION 'V416: no single process carries "%" as BOTH a keyword and a subject, so the family word '
        'is inert however many keywords declare it', bad;
    END IF;
  END LOOP;

  -- 2. THE SPACE-PADDING THIS MIGRATION RESTS ON MUST STILL BE HOW THE MATCHER WORKS. It is a Java property and
  --    cannot be asserted from SQL — but the integration test alongside this migration drives the live matcher
  --    with `escape`, `elbow` and `wrapped`, which is the only honest way to hold it. What CAN be asserted here
  --    is that the one genuine competitor still exists and still outranks the family word on length, because
  --    that is the rule the "death cap" case actually depends on.
  IF NOT EXISTS (SELECT 1 FROM material_process, unnest(string_to_array(keywords, ',')) k
                  WHERE trim(k) = 'death cap') THEN
    -- Not fatal: V411 added the bare "cap" and the death cap is a FLORA, gathered rather than made, so it need
    -- not own a process keyword. Checked and reported rather than assumed either way.
    RAISE NOTICE 'V416: no process declares "death cap"; the cap/death-cap competition is resolved elsewhere';
  END IF;

  -- 3. NO KEYWORD LIST MAY EXCEED THE COLUMN. V404 failed on a seeded database while silently no-opping on a
  --    fresh one, which is the worst way for a migration to be wrong. The tightest row touched here was
  --    make_smoke_wrap at 79 of 200, so there is room to spare — measured, not hoped.
  SELECT string_agg(process_key, ', ') INTO bad FROM material_process WHERE length(keywords) > 200;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V416: these keyword lists no longer fit varchar(200): %', bad;
  END IF;

  -- 4. AND THE WORDS V415 ADDED MUST STILL BE THERE, since this migration contradicts that one's REASONING
  --    without touching its data. If a later edit reverts V415 on the strength of the old argument, this fails.
  FOREACH bad IN ARRAY ARRAY['cuirass','ingot','panel'] LOOP
    SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = bad;
    IF n = 0 THEN
      RAISE EXCEPTION 'V416: V415''s family word "%" has gone; its data was right even though its argument '
        'for refusing bow/cape/wrap was not', bad;
    END IF;
  END LOOP;
END $$;
