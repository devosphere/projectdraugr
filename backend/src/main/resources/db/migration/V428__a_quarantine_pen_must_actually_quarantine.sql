-- #108 — the five pens, settled.
--
-- The audit left one open question rather than guessing at it: five of #108's names reach the Java BUILD_PEN
-- intent, which raises a real ring of posts and rails. Is that a fair answer or a defect?
--
-- Checking the catalogue settles it, and splits the five:
--
--   build a pig pen           -> BUILD_PEN is CORRECT. A pig pen is a generic enclosure and that is what it gets.
--   build a paddock           -> CORRECT, same reason.
--   build an exercise paddock -> CORRECT, same reason.
--
--   build a quarantine pen    -> A FAILURE. The player named the job and got a plain ring of rails. The
--                               catalogue already holds sick_animal_shelter, which carries isolates_sick and
--                               answers to "quarantine shelter" and "isolation hut".
--   build a boar isolation pen-> A FAILURE, same shape. bull_isolation_yard carries separates_dangerous and
--                               already answers to "boar yard" and "isolation yard".
--
-- So neither of the two is built: both are RENAMES onto structures that already do the job and already carry
-- the flag. What was missing was only the word, and — because BUILD_PEN runs BEFORE the keyword matcher — a
-- narrow deference in the Java rule so the word can be reached at all. That is the companion change to this
-- migration; without it, noJavaIntentShadowsAnAssemblysOwnKeywords would fail on every keyword added here,
-- which is exactly how "root fodder cache" was caught in V424.
--
-- A quarantine pen that does not isolate and a boar pen that does not separate are failures rather than
-- approximations. A pig pen that is a ring of rails is simply a pig pen.

-- The shelter that already isolates the sick gets the story's other word for it.
--
-- TWO WORDS, NOT FOUR: assembly_definition.keywords is varchar(300), this row was already at 228 characters,
-- and the first draft of this line overflowed it by four — the migration refused itself with "value too long
-- for type character varying(300)" rather than silently truncating, which is the constraint doing its job.
-- Measure the room before adding keywords to a row that is already long: SELECT 300-length(keywords).
UPDATE assembly_definition SET keywords = keywords || ',quarantine pen,build a quarantine pen'
 WHERE assembly_key = 'sick_animal_shelter' AND ',' || keywords || ',' NOT LIKE '%,quarantine pen,%';

-- The yard that already separates a dangerous beast gets the boar's and the bull's penning words.
UPDATE assembly_definition SET keywords = keywords || ',boar isolation pen,build a boar isolation pen,boar pen,build a boar pen,bull pen,build a bull pen'
 WHERE assembly_key = 'bull_isolation_yard' AND ',' || keywords || ',' NOT LIKE '%,boar pen,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Each new name reaches exactly ONE assembly.
    FOR bad IN SELECT w FROM (VALUES ('quarantine pen'),('boar isolation pen'),
                                     ('boar pen'),('bull pen')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n <> 1 THEN RAISE EXCEPTION 'V428: "%" is claimed by % assemblies; a named thing is one thing', bad, n; END IF;
    END LOOP;

    -- 2. THE WHOLE POINT: the thing each word now reaches must actually do the job the word names. A rename
    --    onto a structure that lacks the flag would be the same defect wearing a better name.
    IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN construction_kind ck ON ck.project_kind=ad.construction_kind
                    WHERE ad.assembly_key='sick_animal_shelter' AND ck.isolates_sick)
    THEN RAISE EXCEPTION 'V428: a quarantine pen that does not isolate the sick is not a quarantine pen'; END IF;

    IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN construction_kind ck ON ck.project_kind=ad.construction_kind
                    WHERE ad.assembly_key='bull_isolation_yard' AND ck.separates_dangerous)
    THEN RAISE EXCEPTION 'V428: a boar pen that does not separate a dangerous beast is not a boar pen'; END IF;

    -- 3. Neither was built as a second structure. The catalogue already had both jobs covered, and a second
    --    thing for the same job is a token however well named.
    SELECT string_agg(v.a, ', ') INTO bad FROM (VALUES ('quarantine_pen'),('boar_isolation_pen')) v(a)
     WHERE EXISTS (SELECT 1 FROM assembly_definition d WHERE d.assembly_key = v.a);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V428: % was built as well as renamed; one of the two is wrong', bad; END IF;

    -- 4. Neither renamed assembly has lost its own name.
    SELECT string_agg(f.a, ', ') INTO bad
      FROM (VALUES ('sick_animal_shelter','sick animal shelter'),('bull_isolation_yard','bull isolation yard')) f(a,w)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.assembly_key = f.a
                          AND ',' || replace(ad.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V428: % no longer answers to its own name', bad; END IF;

    -- 5. And the three that BUILD_PEN rightly answers must NOT have been claimed here. If a later cycle wants
    --    a pig sty distinct from a pig pen, that is a decision to write down, not a keyword to slip in.
    SELECT string_agg(DISTINCT v.w, ', ') INTO bad
      FROM (VALUES ('pig pen'),('paddock'),('exercise paddock'),('calving pen')) v(w), assembly_definition d
     WHERE ',' || replace(d.keywords,', ',',') || ',' LIKE '%,' || v.w || ',%';
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V428: % was claimed, but a generic pen is the right answer for it — say why at the top of this file first', bad; END IF;

    -- 6. No new name sits inside a material process keyword.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('quarantine pen'),('boar pen'),('bull pen')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V428: a structure name collides with a recipe: %', bad; END IF;
END $$;
