-- #37 — "make a poultice" and "brew a tea", which are what a person actually says.
--
-- Found by playing act four, a Chronicle in their second month with a wound that would not close:
--
--     make a poultice  ->  UNKNOWN
--     "You handle and test it, feeling for the trick of it, but the way to make what you intend does not come
--      to you yet."
--     brew a tea       ->  UNKNOWN
--     "You begin, and get as far as beginning."
--
-- Nothing is wrong with the medicine. NINE poultices exist and work — agrimony, comfrey, meadowsweet, plantain,
-- sanicle, self-heal, sphagnum, woundwort, yarrow — and so does the herbal infusion. What is wrong is that every
-- one of them answers only to its own herb's name. A Chronicle who has not yet learned which plant makes a
-- dressing cannot ask for a dressing, which is precisely the knowledge a person without the knowledge would be
-- reaching for. The world knew nine answers and had none to the question.
--
-- Same shape as V375 ("cook some food"), and for the same reason: the machinery for a vague request already
-- exists and is the right one. When words fit more than one piece of work the resolver ASKS which was meant and
-- changes nothing while it asks (#38/#593), and it prefers the ones whose inputs are in reach — so a Chronicle
-- carrying yarrow and nothing else is not asked a question at all, they pound the yarrow. Only somebody with a
-- real choice is asked, and somebody with none is told all nine, which is how they learn what to look for.

-- 1. THE CATEGORY. This is the part that keyword work alone would not have fixed, and it is worth recording why.
--    A process matches only when the CATEGORY agrees as well as the keyword and the subject, and "make" is a
--    CRAFT term (weight 1) while "pound" and "crush" are PROCESS. So "make a poultice" classified as CRAFT and
--    never saw a single poultice, however many keywords they carried. "poultice" is a PROCESS word wherever it
--    appears, at the same weight as pound and crush, which settles it: PROCESS 2 beats CRAFT 1.
--
--    "tea" likewise. Without it "brew a tea" classifies to nothing at all and only matches because the matcher
--    drops the category condition when it has none — working by accident rather than by rule.
INSERT INTO category_term (category_key, term, weight) VALUES
 ('PROCESS', 'poultice', 2),
 ('PROCESS', 'tea', 2)
ON CONFLICT (category_key, term) DO NOTHING;

-- 2. THE KEYWORD. Every poultice keyword today names its own herb ("yarrow poultice", "pound yarrow"), so a
--    sentence that names no herb contains none of them. The bare word is added for exactly that reason.
--
--    It is safe because the LONGEST matching keyword wins (ProcessMatcher): "pound a yarrow poultice" still
--    matches "yarrow poultice" at fifteen characters and beats the bare "poultice" at eight, so naming the herb
--    goes on resolving to that herb alone. Only a sentence naming no herb — the sentence this migration is
--    about — reaches more than one.
UPDATE material_process SET keywords = keywords || ',poultice'
 WHERE process_key LIKE 'poultice\_%'
   AND ',' || keywords || ',' NOT LIKE '%,poultice,%';

-- The infusion already answers to "brew tea" and "make tea" as PHRASES, which "brew a tea" does not contain.
UPDATE material_process SET keywords = keywords || ',tea'
 WHERE process_key = 'brew_infusion'
   AND ',' || keywords || ',' NOT LIKE '%,tea,%';

-- 3. THE SUBJECT. All three must agree. The herbs stay as they are; "poultice" joins them so a sentence naming
--    no herb still has a subject to match on.
INSERT INTO process_subject (process_key, subject_term)
SELECT process_key, 'poultice' FROM material_process WHERE process_key LIKE 'poultice\_%'
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process WHERE process_key LIKE 'poultice\_%'
      AND ',' || keywords || ',' LIKE '%,poultice,%';
    IF n < 9 THEN RAISE EXCEPTION 'V377: expected at least nine poultices to answer to the bare word, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM process_subject WHERE subject_term = 'poultice';
    IF n < 9 THEN RAISE EXCEPTION 'V377: expected at least nine poultices to take poultice as a subject, found %', n; END IF;

    -- Naming the herb must still settle it outright. Every poultice keeps a keyword LONGER than the bare word,
    -- which is what makes "pound a yarrow poultice" unambiguous; without that this migration would have made
    -- nine specific asks into one vague one.
    SELECT COUNT(*) INTO n FROM material_process mp WHERE mp.process_key LIKE 'poultice\_%'
      AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k
                      WHERE length(trim(k)) > length('poultice'));
    IF n > 0 THEN RAISE EXCEPTION 'V377: % poultice(s) have no keyword more specific than the bare word', n; END IF;

    -- The bare words must reach only PROCESS work, or the question would be asked across categories.
    SELECT COUNT(*) INTO n FROM process_subject ps JOIN material_process mp ON mp.process_key = ps.process_key
     WHERE ps.subject_term IN ('poultice', 'tea') AND mp.category_key <> 'PROCESS';
    IF n > 0 THEN RAISE EXCEPTION 'V377: % non-PROCESS work answers to a medicine word', n; END IF;

    SELECT COUNT(*) INTO n FROM material_process WHERE process_key = 'brew_infusion'
      AND ',' || keywords || ',' LIKE '%,tea,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V377: the infusion does not answer to tea'; END IF;
END $$;
