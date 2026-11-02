-- V456: eight catalogued belts, and every material-named "belt" sentence reached one generic fibre belt (#37, #99).
--
-- THE INVERSE OF THE FAMILY-WORD DEFECT. Every earlier finding of that shape was a family word reaching
-- NOTHING while specific entries existed. This is the mirror: the family word is over-reachable and swallows
-- the specific ones. The game's word works and the player's word does not.
--
-- Measured live, 12 phrases, 0 no-reply. The two columns are the whole defect:
--
--   make a leather belt        CRAFT_BELT        "You lay out the FIBRE to make a belt…"   a leather belt of fibre
--   make a leather girdle      PROCESS_MATERIAL  "Fit a leather girdle…"                   correct
--   make a cordage belt        CRAFT_BELT        fibre                                     wrong
--   make a cordage girdle      PROCESS_MATERIAL  cordage                                   correct
--   make a leather tool belt   CRAFT_BELT        fibre     (make_leather_utility_belt exists and is missed)
--   make a rawhide belt        CRAFT_BELT        fibre                                     wrong
--   make a bark belt           CRAFT_BELT        fibre                                     wrong
--
-- The cause is in the data, not only in the routing. EIGHT processes produce an item whose own display_name
-- ends in the word "belt", and not one of them answered to that word:
--
--   make_bark_belt            -> bark_belt             "Bark belt"
--   make_cordage_belt         -> cordage_belt          "Cordage belt"
--   make_cordage_tool_belt    -> cordage_tool_belt     "Cordage tool belt"
--   make_fibre_belt           -> fibre_belt            "Fibre belt"
--   make_leather_belt         -> leather_belt          "Leather belt"
--   make_leather_pouch_belt   -> leather_pouch_belt    "Leather pouch belt"
--   make_leather_utility_belt -> leather_utility_belt  "Leather utility belt"
--   make_rawhide_belt         -> rawhide_belt          "Rawhide belt"
--
-- Seven of the eight advertise GIRDLE phrases and the eighth advertises STRAP phrases, and each carries only
-- the matching subject term. So "make a leather belt" fails BOTH of the matcher's gates — no keyword and no
-- subject — and the ungated "belt" arm of the CRAFT_BELT rule answers it with one generic fibre belt. V110
-- gave these recipes the catalogue's word for the garment and never the player's.
--
-- This mirrors each recipe's own phrases with belt ones and gives each the subject term "belt", because a
-- family word needs a keyword AND a process_subject row on the SAME process — a keyword alone would still be
-- turned away at the subject gate, which is exactly how these eight came to be unreachable by the common word.
--
-- THE BARE WORD IS DELIBERATELY LEFT ALONE. No recipe gains a plain "belt" keyword, so "make a belt" keeps
-- reaching the generic CRAFT_BELT exactly as "make a girdle" does today — which that rule's own comment
-- documents as intended, and which is the right answer to a sentence that names no material. Only the six
-- material-named phrasings move.
--
-- Measured over all 2,337 declared keywords through the shipped ActivityClassifier.classify and
-- ProcessMatcher.resolveForPlay, driven from a dump of this catalogue rather than a re-implementation: these
-- rows change only the sixteen new phrases, each resolving to the recipe that now declares it. No existing
-- keyword changes its answer, and nothing moves between two strangers. No other catalogue entry claims any
-- "belt" phrase except sew_leather_pouch ("belt bag") and make_bark_wrap ("belt on a bark wrap"), and neither
-- is touched here.

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('make_fibre_belt',           'fibre belt,make a fibre belt,knot a fibre belt'),
    ('make_bark_belt',            'bark belt,make a bark belt,bind a bark belt'),
    ('make_cordage_belt',         'cordage belt,make a cordage belt,tie a cordage belt'),
    ('make_rawhide_belt',         'rawhide belt,make a rawhide belt'),
    ('make_leather_belt',         'leather belt,make a leather belt,fit a leather belt'),
    ('make_leather_utility_belt', 'leather tool belt,make a leather tool belt,tool belt'),
    ('make_leather_pouch_belt',   'leather pouch belt,make a leather pouch belt,pouch belt'),
    -- The one that never had a girdle phrase either: it advertises "cordage tool strap" and "tool strap", so
    -- its own name is the word it could not be asked for. No bare "tool belt" here — make_leather_utility_belt
    -- claims that, and the longest-keyword rule keeps "make a cordage tool belt" with this one regardless.
    ('make_cordage_tool_belt',    'cordage tool belt,make a cordage tool belt')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   -- Anchored on the comma boundaries the keyword list is actually joined by. A plain LIKE '%belt%' guard
   -- would be the very substring defect this migration exists to undo, and would also match "tool belt"
   -- inside "leather tool belt".
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');

INSERT INTO process_subject (process_key, subject_term)
SELECT k, 'belt'
  FROM (VALUES ('make_fibre_belt'), ('make_bark_belt'), ('make_cordage_belt'),
               ('make_rawhide_belt'), ('make_leather_belt'), ('make_leather_utility_belt'),
               ('make_leather_pouch_belt'), ('make_cordage_tool_belt')) AS s(k)
ON CONFLICT (process_key, subject_term) DO NOTHING;
