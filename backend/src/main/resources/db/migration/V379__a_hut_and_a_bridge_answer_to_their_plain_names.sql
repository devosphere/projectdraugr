-- #37 — "build a hut" and "build a bridge", which are what a person actually says.
--
-- Found by playing act five, a Chronicle settled enough to want walls:
--
--     build a hut     ->  UNKNOWN, "The stuff of it is willing enough; it is the working that will not come."
--     build a bridge  ->  UNKNOWN, "no method for what you meant comes to your hands yet"
--
-- FOUR huts exist and are built stage by stage — a debris hut, an earth-sheltered hut, a reed hut, a wattle and
-- daub hut — and a footbridge exists too. Every one of them answers only to its own full name, so the word a
-- person reaches for first reaches nothing. Same defect as V375's supper and V377's poultice, in the assembly
-- catalogue this time.
--
-- THE BRIDGE is a plain synonym: there is one bridge, so "bridge" names it and nothing else, and no question
-- arises. It is only missing because the keywords all carry "foot".
--
-- THE HUT is not a synonym, and this migration would have been a bug on its own. Four assemblies answering to
-- one word is a TIE, and until the change that accompanies this migration the assembly matcher took the
-- strictly-longer keyword only — so a tie kept whichever row the database happened to hand back first, from a
-- query with no ORDER BY. The catalogue had no tie in it when this was written, so nothing was being silently
-- mis-built; this migration is what would have created the first one. The matcher now keeps everything tied at
-- the winning length and asks which was meant, exactly as the material processes have since #593, and narrows
-- by what is already under way and what could actually be begun here before it asks anything at all.
UPDATE assembly_definition SET keywords = keywords || ',hut,build a hut,raise a hut,make a hut'
 WHERE assembly_key IN ('debris_hut', 'earth_sheltered_hut', 'reed_hut', 'wattle_and_daub_hut')
   AND ',' || keywords || ',' NOT LIKE '%,hut,%';

UPDATE assembly_definition SET keywords = keywords || ',bridge,build a bridge,make a bridge'
 WHERE assembly_key = 'footbridge'
   AND ',' || keywords || ',' NOT LIKE '%,bridge,%';

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE review_state = 'VERIFIED' AND ',' || keywords || ',' LIKE '%,hut,%';
    IF n <> 4 THEN RAISE EXCEPTION 'V379: expected four huts to answer to the bare word, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE review_state = 'VERIFIED' AND ',' || keywords || ',' LIKE '%,bridge,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V379: expected exactly one bridge to answer to the bare word, found %', n; END IF;

    -- Naming the KIND of hut must still settle it outright, which is what makes the tie safe: every hut keeps a
    -- keyword longer than the bare word, so "build a reed hut" beats "hut" on length and is never a question.
    SELECT COUNT(*) INTO n FROM assembly_definition ad
     WHERE ad.assembly_key IN ('debris_hut', 'earth_sheltered_hut', 'reed_hut', 'wattle_and_daub_hut')
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(ad.keywords, ',')) k
                       WHERE length(trim(k)) > length('build a hut'));
    IF n > 0 THEN RAISE EXCEPTION 'V379: % hut(s) have no keyword more specific than the bare words', n; END IF;

    -- And every hut must have a first stage whose requirements can be read, or the narrowing before the question
    -- would silently offer a hut nobody could ever begin.
    SELECT COUNT(*) INTO n FROM assembly_definition ad
     WHERE ad.assembly_key IN ('debris_hut', 'earth_sheltered_hut', 'reed_hut', 'wattle_and_daub_hut')
       AND NOT EXISTS (SELECT 1 FROM assembly_stage st WHERE st.assembly_key = ad.assembly_key);
    IF n > 0 THEN RAISE EXCEPTION 'V379: % hut(s) have no stages to begin', n; END IF;
END $$;
