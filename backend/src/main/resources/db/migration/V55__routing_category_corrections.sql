-- V55: Four routing corrections V54's own fixture could not see.
--
-- V54 blocked all eight recorded collisions and its fixture passed. Wiring the Java
-- half and probing with ordinary phrasing found the other direction: three processes
-- that no plausible sentence can reach any more, and one that resolves to the wrong
-- recipe. Both are the false-negative failure -- not a wrong answer handed to a
-- chronicle, but an Architect call spent on something already built, or a basket
-- woven as cloth.
--
-- The cause is the same in each case: a process's declared category disagrees with
-- the category its own verbs classify to. The category axis is only as good as that
-- agreement, and nothing in V54 checked it. The Auditor now does.

-- 1. dress_foundation was unreachable. "Dress the foundation stone" classifies
--    CONSTRUCT, because 'foundation' is a weight-3 construction term and 'dress' only
--    leans PROCESS at weight 1. But a foundation STONE is a material, not an act of
--    building -- the phrase names what is being worked, not what is being raised.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','foundation stone',3)
ON CONFLICT DO NOTHING;

-- 2. reinforce_timber was unreachable, and the honest fix is on the other side: it
--    was filed PROCESS, but every verb it answers to -- reinforce, lash, bind -- and
--    everything it makes -- a structural beam for a building -- is construction. The
--    category was simply wrong. Lashing and structural members are construction
--    vocabulary too, and were missing.
UPDATE material_process SET category_key = 'CONSTRUCT' WHERE process_key = 'reinforce_timber';

INSERT INTO category_term (category_key, term, weight) VALUES
('CONSTRUCT','lash',2),('CONSTRUCT','structural',2)
ON CONFLICT DO NOTHING;

-- 3. weave_large_basket resolved to weave_textile: it is filed CRAFT, but 'weave' was
--    PROCESS-only, so "weave a large basket" classified PROCESS and the basket was
--    never a candidate. Weaving is genuinely ambiguous between making cloth and making
--    an object, so it should lean rather than decide -- dropped to weight 1 -- and the
--    object named carries the category instead.
--
--    'basket' is weight 2 deliberately, not 3: at 3 it would beat 'gather' and pull
--    "gather berries into a basket" out of ACQUIRE. At 2 that phrase ties and ACQUIRE
--    wins on precedence, which is the right reading.
UPDATE category_term SET weight = 1 WHERE category_key = 'PROCESS' AND term = 'weave';

INSERT INTO category_term (category_key, term, weight) VALUES
('CRAFT','basket',2)
ON CONFLICT DO NOTHING;

-- 4. fire_vessel classified to nothing at all. "Fire the clay pot in the kiln" matched
--    no category term, so only V54's null-classification fallback was reaching it --
--    correct today, but by accident rather than by agreement, and it would break the
--    moment any term in that sentence was added elsewhere. Firing is a processing act
--    and the words for it were simply absent.
--
--    'fire' itself is deliberately NOT added: it belongs to hearths and to the INHABIT
--    sense of lighting one, and claiming it for PROCESS would drag ordinary fire-
--    tending into material processing.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','bake',3),('PROCESS','kiln',3),('PROCESS','harden',2)
ON CONFLICT DO NOTHING;

-- 5. Every process must be reachable by at least one sentence a person would write.
--    V54 guaranteed a process has subject terms; it did not guarantee its keywords
--    classify to its own category, which is the second way a process can go silently
--    unreachable. This checks the weakest form of that agreement: at least one of a
--    process's own keywords must share a word with a term of its own category, or
--    classification can never select it from its own vocabulary.
--
--    Both are compared whole-word in both directions, since either side may be a
--    phrase: the term 'basket' has to be found inside the keyword 'large basket', and
--    the term 'foundation stone' has to be found from the keyword 'foundation'.
DO $$
DECLARE orphaned TEXT;
BEGIN
    SELECT string_agg(mp.process_key, ', ') INTO orphaned
    FROM material_process mp
    WHERE NOT EXISTS (
        SELECT 1
        FROM unnest(string_to_array(mp.keywords, ',')) k
        JOIN category_term ct ON ct.category_key = mp.category_key
        WHERE ' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %'
           OR ' ' || ct.term  || ' ' LIKE '% ' || btrim(k) || ' %'
    );
    IF orphaned IS NOT NULL THEN
        RAISE EXCEPTION 'V55: process(es) whose keywords cannot classify to their own category: %', orphaned;
    END IF;
END $$;
