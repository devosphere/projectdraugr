-- #37 — "cook some food" is a thing a person says, and the world had no answer to it.
--
-- Found by playing: an ordinary first hour, a fire lit, and the most natural sentence there is about supper.
--
--     cook some food  ->  UNKNOWN
--     "You work the material over, turning it for a way in, but no method for what you meant comes to your
--      hands here."
--
-- Nothing is wrong with the cooking. cook_greens, cook_mushrooms, cook_porridge and cook_root_stew all exist and
-- all work. What is wrong is that every one of them is subject-matched to a PARTICULAR food — "greens",
-- "mushrooms", "porridge", "stew" — so a Chronicle who says "food" names none of them and is told the world does
-- not know how. It knows four ways.
--
-- WHY THIS IS DATA AND NOT A NEW INTENT. The machinery for a vague request already exists and is the right
-- answer: when words fit more than one piece of work, the resolver ASKS which was meant and changes nothing
-- while it asks (#38/#593). It could never fire for "food" because no cooking process answered to the word. Give
-- them the word and the existing question asks itself:
--
--     "Those words fit more than one piece of work you could do here — cook greens, cook mushrooms, or
--      make porridge. Say which you mean, and your hands will know where to begin."
--
-- And because the resolver prefers the ones whose inputs are actually in reach, a Chronicle carrying only
-- mushrooms is not asked a question at all — they cook the mushrooms. The question is only asked of somebody who
-- genuinely has a choice, which is when a question is worth asking.
--
-- DELIBERATELY NOT ADDED to bake_flatbread, cook_root_stew's neighbours in the baking line, or to any of the
-- boil_* processes. Boiling glue and rendering salt are not supper, and a person who says "cook some food"
-- beside a pot of hide glue does not mean that. A generic word is only generous where the work is generic.

-- A process matches only when ALL THREE agree: category, a keyword appearing whole-word in the text, and a
-- subject. Every cooking keyword today is a two-word phrase naming its own food ("cook greens", "make porridge"),
-- so "cook some food" contains none of them and the subject below would never be consulted. The bare verb is
-- added as a keyword for exactly that reason.
--
-- It is safe precisely because the subject still discriminates: "cook the greens" matches the keyword "cook" on
-- all four, and then matches a SUBJECT on cook_greens alone. Only a sentence that names no particular food —
-- which is the sentence this migration is about — reaches more than one.
UPDATE material_process SET keywords = keywords || ',cook'
 WHERE process_key IN ('cook_greens', 'cook_mushrooms', 'cook_porridge', 'cook_root_stew')
   AND ',' || keywords || ',' NOT LIKE '%,cook,%';

INSERT INTO process_subject (process_key, subject_term) VALUES
 ('cook_greens',    'food'),   ('cook_greens',    'meal'),   ('cook_greens',    'supper'),
 ('cook_mushrooms', 'food'),   ('cook_mushrooms', 'meal'),   ('cook_mushrooms', 'supper'),
 ('cook_porridge',  'food'),   ('cook_porridge',  'meal'),   ('cook_porridge',  'supper'),
 ('cook_root_stew', 'food'),   ('cook_root_stew', 'meal'),   ('cook_root_stew', 'supper')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(DISTINCT process_key) INTO n FROM process_subject WHERE subject_term = 'food';
    IF n <> 4 THEN RAISE EXCEPTION 'V375: expected four ways to cook supper, found %', n; END IF;
    -- The word must reach only PROCESS work. If it ever answered a CRAFT or a GATHER the question would be
    -- asked across categories, which is noise rather than a choice.
    SELECT COUNT(*) INTO n FROM process_subject ps JOIN material_process mp ON mp.process_key = ps.process_key
     WHERE ps.subject_term IN ('food', 'meal', 'supper') AND mp.category_key <> 'PROCESS';
    IF n > 0 THEN RAISE EXCEPTION 'V375: % non-PROCESS work answers to a supper word', n; END IF;
END $$;
