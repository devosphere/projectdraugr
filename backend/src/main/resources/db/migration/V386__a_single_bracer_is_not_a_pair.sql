-- #37 — V383 took a phrase that already had a better owner. This gives it back.
--
-- V383 gave every paired process the side-less name of its gear, so "make leather gloves" would stop offering
-- a waterskin. It excluded families where another process ALREADY answered to the side-less name -- but it
-- tested for an existing KEYWORD, and the right test was an existing unsided ITEM.
--
-- sew_bracer makes a leather_bracer: one bracer, no side. Its longest keyword is the bare "bracer", six
-- characters, and V383 handed the sided pair "leather bracer" at fourteen. Longest wins, so:
--
--     sew a leather bracer  ->  make_leather_bracer_left/right, asking which side
--
-- when the catalogue has a process that makes exactly the thing asked for. Caught by the routing reachability
-- probe, which expects that phrase to reach sew_bracer.
--
-- Only ONE family is affected. Checking every paired family against the processes that make its unsided item
-- finds three -- fibre hand wrap, grass ankle wrap and leather bracer -- and the first two were already
-- excluded by V383's keyword test. This is the one that slipped through it.
--
-- THE SINGULAR GOES BACK AND THE PLURAL STAYS, which is the distinction that was there all along: one bracer
-- is the unsided thing sew_bracer makes, and a pair of them is two sided ones. So "sew a leather bracer"
-- reaches sew_bracer and "make leather bracers" still asks which side.
UPDATE material_process mp
   SET keywords = array_to_string(
        array_remove(string_to_array(mp.keywords, ','),
                     replace(regexp_replace(mp.output_item_key, '_(left|right)$', ''), '_', ' ')), ',')
 WHERE mp.process_key ~ '_(left|right)$'
   AND EXISTS (SELECT 1 FROM material_process other
               WHERE other.output_item_key = regexp_replace(mp.output_item_key, '_(left|right)$', '')
                 AND other.process_key <> mp.process_key);

DO $$
DECLARE n INT;
BEGIN
    -- The singular is gone from the sided pair...
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key ~ 'make_leather_bracer_(left|right)' AND ',' || keywords || ',' LIKE '%,leather bracer,%';
    IF n <> 0 THEN RAISE EXCEPTION 'V386: a sided bracer still answers to the unsided name'; END IF;

    -- ...and the plural is still there, so the pair keeps the phrase that is genuinely about a pair.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key ~ 'make_leather_bracer_(left|right)' AND ',' || keywords || ',' LIKE '%,leather bracers,%';
    IF n <> 2 THEN RAISE EXCEPTION 'V386: the sided bracers lost the plural too, found %', n; END IF;

    -- And the twenty-odd families V383 was actually for keep their side-less name, because no other process
    -- makes their unsided item. If this ever drops, V383 has been undone by accident.
    SELECT COUNT(DISTINCT regexp_replace(process_key, '_(left|right)$', '')) INTO n
      FROM material_process WHERE process_key ~ '_(left|right)$'
       AND ',' || keywords || ',' LIKE '%,' || replace(regexp_replace(output_item_key, '_(left|right)$', ''), '_', ' ') || ',%';
    IF n < 20 THEN RAISE EXCEPTION 'V386: only % paired families still answer to their own name', n; END IF;
END $$;
