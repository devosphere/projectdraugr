-- #37 — "make a meal" reached nothing, while "cook a meal" asked which meal.
--
-- Found by playing, and it is the cleanest illustration of the two-sided family word this project keeps
-- rediscovering:
--
--     cook a meal  ->  "Those words fit more than one piece of work — wilt a pan of greens, cook foraged
--                       mushrooms, boil grain porridge, or simmer a root vegetable stew — and you have the
--                       makings of none of them within reach. Say which you mean..."
--     make a meal  ->  "The stuff of it is willing enough; it is the working that will not come."
--
-- Same request. Same four processes. One word different.
--
-- WHY. A process matches only when all three agree: CATEGORY, a KEYWORD appearing whole-word in the text, and a
-- SUBJECT. V375 gave these four processes the subject 'meal' and the bare keyword 'cook'. That is two of the
-- three for "cook a meal" and only ONE for "make a meal":
--
--   * category — 'cook' is a PROCESS term at weight 3; 'make' is a CRAFT term at weight 1. So "make a meal"
--     classifies CRAFT and the PROCESS keywords are never consulted at all.
--   * keyword  — none of the four declares anything that appears in "make a meal". The subject 'meal' is
--     sitting right there and is never reached, because the keyword gate comes first.
--
-- A SUBJECT WITHOUT A KEYWORD ON THE SAME PROCESS IS INERT. That is written in this project's notes in those
-- words, and V375 is where it had happened without being noticed — its own guard checked that four processes
-- answer to the subject 'food', which they do, so nothing failed.

-- 1. THE CATEGORY. 'meal' and 'supper' are PROCESS words at weight 2, which beats CRAFT's 'make' at 1 — and
--    PROCESS outranks CRAFT on precedence as well, so the ordering is not resting on the weight alone.
--
--    'food' is deliberately NOT added. It is the broad word that would pull a food cache, a food basket and a
--    store of food into the cooking pot; "make some food" stays unanswered and is recorded on #37 as such. A
--    generic word is only generous where the work is generic, which is the rule V375 set for itself.
INSERT INTO category_term (category_key, term, weight) VALUES
 ('PROCESS', 'meal', 2),
 ('PROCESS', 'supper', 2)
ON CONFLICT (category_key, term) DO NOTHING;

-- 2. THE KEYWORD, as a PHRASE and not the bare word. A bare 'meal' would be four characters, exactly as long as
--    the bare 'make' that `form_vessel` already declares, and the longest-keyword rule settles ties by asking
--    which named output has its inputs in reach (V318) — a coin-toss between supper and a clay pot is not worth
--    inviting. These phrases are longer than anything they could tie with, so they win outright.
UPDATE material_process
   SET keywords = keywords || ',make a meal,make a supper,make supper,prepare a meal,prepare supper,fix a meal'
 WHERE process_key IN ('cook_greens', 'cook_mushrooms', 'cook_porridge', 'cook_root_stew')
   AND ',' || keywords || ',' NOT LIKE '%,make a meal,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- All four must now carry the keyword, or "make a meal" asks a narrower question than "cook a meal" does.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key IN ('cook_greens','cook_mushrooms','cook_porridge','cook_root_stew')
       AND ',' || keywords || ',' LIKE '%,make a meal,%';
    IF n <> 4 THEN RAISE EXCEPTION 'V419: expected four ways to make supper, found %', n; END IF;

    -- BOTH SIDES, ON THE SAME PROCESS. This is the check V375 did not have: a keyword with no subject beside it,
    -- or a subject with no keyword, answers nothing — and each looks correct when inspected on its own.
    SELECT string_agg(mp.process_key, ', ') INTO bad
      FROM material_process mp
     WHERE ',' || mp.keywords || ',' LIKE '%,make a meal,%'
       AND NOT EXISTS (SELECT 1 FROM process_subject ps
                        WHERE ps.process_key = mp.process_key AND ps.subject_term = 'meal');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V419: % declares the keyword with no matching subject', bad; END IF;

    -- The supper words must reach PROCESS work only. A question asked across categories is noise, not a choice.
    SELECT COUNT(*) INTO n FROM process_subject ps JOIN material_process mp ON mp.process_key = ps.process_key
     WHERE ps.subject_term IN ('meal','supper') AND mp.category_key <> 'PROCESS';
    IF n > 0 THEN RAISE EXCEPTION 'V419: % non-PROCESS work answers to a supper word', n; END IF;

    -- And every one of them must keep a keyword longer than the phrases added here, so naming a PARTICULAR
    -- supper still beats asking for any supper at all.
    SELECT string_agg(process_key, ', ') INTO bad FROM (
        SELECT mp.process_key, MAX(length(trim(k))) AS longest
          FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
         WHERE mp.process_key IN ('cook_greens','cook_mushrooms','cook_porridge','cook_root_stew')
         GROUP BY mp.process_key) t
     WHERE longest <= length('prepare a meal');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V419: % has nothing more specific than the family phrase', bad; END IF;
END $$;
