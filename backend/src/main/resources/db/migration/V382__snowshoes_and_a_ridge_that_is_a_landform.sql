-- #37 — two wrong answers, which are worse than missing ones.
--
-- Found by playing act six on a world built from nothing.
--
-- 1. A RIDGE IS A LANDFORM before it is a roof timber.
--
--        climb the ridge to see further
--     -> "Make a ridge beam turns on an axe, and there is none within reach."
--
--    set_ridge_beam carried the bare keyword "ridge", so any mention of the commonest word there is for high
--    ground -- climb the ridge, camp on the ridge, follow the ridge north -- was read as a request for
--    carpentry. The beam keeps every phrasing that actually names it: "ridge beam", "ridge pole", "set the
--    ridge", "beam". Only the bare landform word goes.
--
--    I swept the rest of the catalogue for the same shape before writing this. Three processes carry a bare
--    common word: "ridge" (set_ridge_beam), "hollow" (carve_wooden_bowl, carve_wooden_trough) and "fire"
--    (fire_vessel). Only "ridge" actually misfires -- "camp in the hollow", "shelter in the hollow" and "sit by
--    the fire" all reach UNKNOWN rather than a process, because the category and subject gates stop them. So
--    only "ridge" is removed. The other two are left exactly as they are.
UPDATE material_process SET keywords = array_to_string(
        array_remove(string_to_array(keywords, ','), 'ridge'), ',')
 WHERE process_key = 'set_ridge_beam';

-- 2. SNOWSHOES ARE NOT BOOTS, and this one is mine.
--
--        make snowshoes  ->  CRAFT_GARMENT  ->  a pair of hide boots
--
--    #708 added "shoe" to CRAFT_GARMENT's nouns so that "sew a pair of shoes" would reach the maker that had
--    always known how to make them. "snowshoes" contains "shoe". So a widening that fixed one phrase broke
--    another, and asking for snowshoes made something else entirely -- silently, and successfully, which is the
--    worst way for it to be wrong. The Java side of that is fixed alongside this migration.
--
--    And the plural could not have been answered anyway. make_snowshoe_left and make_snowshoe_right exist and
--    work, but every keyword names a SIDE, so "make snowshoes" -- which is what a person says, because you do
--    not set out to make one shoe -- reached neither. Giving both the plural makes it a tie, and a tie is the
--    right answer here: they are lashed one at a time, so the resolver asks which, and prefers whichever side
--    has its materials in reach.
UPDATE material_process SET keywords = keywords || ',snowshoes,make snowshoes,lash snowshoes'
 WHERE process_key IN ('make_snowshoe_left', 'make_snowshoe_right')
   AND ',' || keywords || ',' NOT LIKE '%,snowshoes,%';

INSERT INTO process_subject (process_key, subject_term)
SELECT process_key, 'snowshoes' FROM material_process WHERE process_key IN ('make_snowshoe_left', 'make_snowshoe_right')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'set_ridge_beam' AND ',' || keywords || ',' LIKE '%,ridge,%';
    IF n <> 0 THEN RAISE EXCEPTION 'V382: the bare landform word is still a carpentry keyword'; END IF;

    -- The beam must still be reachable by its own name, or removing the word has cost more than it saved.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'set_ridge_beam' AND ',' || keywords || ',' LIKE '%,ridge beam,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V382: the ridge beam can no longer be asked for by name'; END IF;

    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key IN ('make_snowshoe_left', 'make_snowshoe_right')
       AND ',' || keywords || ',' LIKE '%,snowshoes,%';
    IF n <> 2 THEN RAISE EXCEPTION 'V382: expected both snowshoes to answer to the plural, found %', n; END IF;

    -- Naming the side must still settle it outright, so the tie is only ever reached by the plural.
    SELECT COUNT(*) INTO n FROM material_process mp
     WHERE mp.process_key IN ('make_snowshoe_left', 'make_snowshoe_right')
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k
                       WHERE length(trim(k)) > length('snowshoes'));
    IF n > 0 THEN RAISE EXCEPTION 'V382: % snowshoe(s) have no keyword more specific than the plural', n; END IF;
END $$;
