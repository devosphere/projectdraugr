-- #77 — the names the story uses for structures the game already builds.
--
-- #77 names 100 structures. An audit against the catalogue found 35 present by key and 65 absent, which looks
-- like two thirds of a story outstanding. It is not. Testing all 65 LIVE through the composer — "build a <name>"
-- for each — only NINETEEN reach nothing at all. The other forty-six are already built and answer under another
-- name or through a Java intent: ADVANCE_ASSEMBLY (10), a material process (11), CRAFT_SHELF (4), MARK (3),
-- MAKE_BED (3), BUILD_LATRINE (3) and the rest.
--
-- That is this project's recurring lesson in a new place: a key absent from the catalogue is only a gap if
-- NOTHING ELSE owns the phrase. Key equality is not reachability, and the difference here is 65 against 19.
--
-- Of those nineteen, five are the same thing under a different word, and that is what this migration fixes.
-- The remaining fourteen are genuinely absent and are recorded on the story rather than invented here.

-- AN EARTH OVEN IS A COOKING PIT. Its own stages are "dig the pit, line it with stone, bank the earth", and it
-- was itself unreachable until V420 verified it — so the word has never had anything to answer to.
UPDATE assembly_definition SET keywords = keywords || ',cooking pit,build a cooking pit,dig a cooking pit,cook pit'
 WHERE assembly_key = 'earth_oven' AND ',' || keywords || ',' NOT LIKE '%,cooking pit,%';

-- A LAID PATH IS LAID OF BRUSH. "build a path" already reaches it; the story's own word for it did not.
UPDATE assembly_definition SET keywords = keywords || ',brush path,build a brush path,lay a brush path'
 WHERE assembly_key = 'laid_path' AND ',' || keywords || ',' NOT LIKE '%,brush path,%';

-- A DRYING RACK IS WHAT POTTERY DRIES ON. The story asks for a shelf; the catalogue built a rack, and greenware
-- wants exactly the airflow a rack gives.
UPDATE assembly_definition SET keywords = keywords || ',pottery drying shelf,drying shelf,build a drying shelf,pottery shelf'
 WHERE assembly_key = 'drying_rack' AND ',' || keywords || ',' NOT LIKE '%,drying shelf,%';

-- A SMOKE RACK IS THE FORM THIS GAME'S SMOKEHOUSE TAKES, and the honest wording matters: somebody asking for a
-- smokehouse gets the rack, which is a first-era structure rather than a building. The answer names what it
-- actually is, so the player is not told they have built a house.
UPDATE assembly_definition SET keywords = keywords || ',smokehouse,build a smokehouse,small smokehouse,smoke house'
 WHERE assembly_key = 'smoke_rack' AND ',' || keywords || ',' NOT LIKE '%,smokehouse,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Each name now reaches something.
    FOR bad IN SELECT w FROM (VALUES ('cooking pit'),('brush path'),('drying shelf'),('smokehouse')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n = 0 THEN RAISE EXCEPTION 'V422: nothing answers to "%"', bad; END IF;
    END LOOP;

    -- 2. And reaches exactly ONE thing. These are synonyms for a particular structure, not family words — an
    --    ambiguous ask would be wrong here, because the player named a specific thing and there is a specific
    --    thing to give them.
    FOR bad IN SELECT w FROM (VALUES ('cooking pit'),('brush path'),('drying shelf'),('smokehouse')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n > 1 THEN RAISE EXCEPTION 'V422: "%" is claimed by % assemblies; a named thing must be one thing', bad, n; END IF;
    END LOOP;

    -- 3. THE COLLISION GUARD, MADE PERMANENT. None of these names may appear inside a material process keyword,
    --    which is how a bare "fold" or "screen" would have taken a recipe's own phrase in V420.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('cooking pit'),('brush path'),('drying shelf'),('smokehouse')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V422: a story name collides with a recipe: %', bad; END IF;

    -- 4. EACH ASSEMBLY STILL ANSWERS TO ITS OWN NAME. The first version of this guard asked whether the
    --    assembly kept a keyword LONGER than the name added, copied from V420 — and it failed drying_rack,
    --    correctly by its own terms and for no good reason. V420 added short FAMILY words, which can shadow a
    --    long specific one, so length was the thing to check there. These are specific SYNONYMS, often longer
    --    than the catalogue's own name ("pottery drying shelf" is 20 characters, "drying rack" is 11), and
    --    longest-keyword-wins then shadows nothing in either direction: each word reaches the thing it names.
    --
    --    What actually matters is that adding the story's word has not displaced the catalogue's own, so that
    --    is what this asserts.
    SELECT string_agg(f.a, ', ') INTO bad
      FROM (VALUES ('earth_oven','earth oven'),('laid_path','build a path'),
                   ('drying_rack','drying rack'),('smoke_rack','smoke rack')) f(a,w)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.assembly_key = f.a
                          AND ',' || replace(ad.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V422: % no longer answers to its own name', bad; END IF;
END $$;
