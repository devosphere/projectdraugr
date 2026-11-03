-- V457: nine garments whose own name did not reach the recipe that makes them (#37, #99).
--
-- The same shape as V456's belts, found by a wider reading of the same question: which processes carry a
-- display_name that never names the thing they make? Of 518 verified processes with an output, 62 do — and most
-- of those 62 are perfectly correct, because the work and the product genuinely differ (you tan a HIDE and get
-- LEATHER; you smelt ORE and get an INGOT; you cut a MORTISE and get a mortised BEAM). The defect is the
-- narrower set where the label and the item are two words for the SAME object:
--
--   make_bark_tunic             "Lap a bark jerkin"            makes "Bark tunic"
--   make_hide_tunic             "Lace a hide jerkin"           makes "Hide tunic"
--   make_leather_tunic          "Sew a leather jerkin"         makes "Leather tunic"
--   make_reed_tunic             "Weave a reed jerkin"          makes "Reed tunic"
--   make_fur_leggings           "Sew fur chaps"                makes "Fur leggings"
--   make_leather_leggings       "Sew leather chaps"            makes "Leather leggings"
--   make_hide_trousers          "Lace hide breeches"           makes "Hide trousers"
--   make_fibre_sling            "Wind a fibre carry strap"     makes "Fibre carry sling"
--   make_leather_bottle_sling   "Sew a leather bottle carrier" makes "Leather water-bottle sling"
--
-- jerkin/tunic, chaps/leggings, breeches/trousers, strap/sling, carrier/sling. Each recipe's keywords and its
-- single subject term carried only the first word of each pair, so the item's OWN name could pass neither of
-- the matcher's two gates, and the ungated CRAFT_GARMENT rule in the classifier answered instead.
--
-- AND IT WAS WORSE THAN A GENERIC ANSWER. Measured through the shipped ActivityClassifier.classify and
-- ProcessMatcher.resolveForPlay over a dump of this catalogue, these phrases did not merely miss their recipe —
-- several reached a stranger:
--
--   make a leather tunic        -> make_waterskin         a water bottle
--   make leather leggings       -> make_waterskin         a water bottle
--   make a bark tunic           -> make_bark_container    a basket
--   fur leggings                -> line_with_fur          lining a garment, not making one
--   make a fibre carry sling    -> make_grass_sling       a different sling, of grass
--   make a leather bottle sling -> make_waterskin         a water bottle
--
-- So the data had to be fixed BEFORE or WITH the classifier gate, never after: gating CRAFT_GARMENT on its own
-- would have handed "make a leather tunic" to a waterskin instead of to a generic garment, which is worse than
-- what it did before.
--
-- Measured over all 2,337 declared keywords: these rows change only the nineteen new phrases, each resolving to
-- the process that now declares it. NO existing keyword changes its answer.
--
-- THE SLING FAMILY WAS THE RISK AND IT IS CLEAR. Five processes share that word — make_sling, make_grass_sling,
-- knot_arm_sling, sew_sling_pouch, shape_sling_stone — and make_grass_sling already claims the bare "carry
-- sling". Checked by which process each phrase RESOLVES to rather than by the refusal it prints, because one
-- refusal fits several recipes and grading on it has misled before: "make a sling", "make a grass sling",
-- "carry sling", "twist a carrying sling", "make an arm sling", "leather sling", "shot sling" and "sling stone"
-- all keep exactly the answers they had. The longest-keyword rule settles the two that move — "fibre carry
-- sling" beats the bare "carry sling", and "leather bottle sling" beats nothing that claimed it.
--
-- THE BARE WORDS ARE DELIBERATELY LEFT ALONE, as in V456: no recipe gains a plain "tunic", "leggings" or
-- "trousers" keyword, so "make a tunic", "make leggings" and "make trousers" still resolve to nothing and reach
-- the generic CRAFT_GARMENT, which is the right answer to a sentence that names no material.

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('make_bark_tunic',           'bark tunic,lap a bark tunic,make a bark tunic,bark body tunic'),
    ('make_hide_tunic',           'hide tunic,make a hide tunic,lace a hide tunic'),
    ('make_leather_tunic',        'leather tunic,make a leather tunic,sew a leather tunic'),
    ('make_reed_tunic',           'reed tunic,weave a reed tunic,make a reed tunic,reed body tunic'),
    ('make_fur_leggings',         'fur leggings,make fur leggings,sew fur leggings'),
    ('make_leather_leggings',     'leather leggings,make leather leggings,sew leather leggings'),
    ('make_hide_trousers',        'hide trousers,make hide trousers,lace hide trousers'),
    ('make_fibre_sling',          'fibre carry sling,make a fibre carry sling'),
    ('make_leather_bottle_sling', 'leather bottle sling,make a leather bottle sling,bottle sling')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   -- Anchored on the comma boundaries the keyword list is joined by. A LIKE '%tunic%' guard would be the very
   -- substring defect this undoes, and would also match "bark tunic" inside "bark body tunic".
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');

-- A keyword alone would still be turned away at the subject gate, which is exactly how these nine came to be
-- unreachable by the words their own items are called. Keyword AND subject, on the same process.
INSERT INTO process_subject (process_key, subject_term) VALUES
    ('make_bark_tunic',           'tunic'),
    ('make_hide_tunic',           'tunic'),
    ('make_leather_tunic',        'tunic'),
    ('make_reed_tunic',           'tunic'),
    ('make_fur_leggings',         'leggings'),
    ('make_leather_leggings',     'leggings'),
    ('make_hide_trousers',        'trousers'),
    -- Mirrors each one's existing 'carry strap' / 'bottle carrier' subject rather than a bare 'sling', which
    -- five other processes would then share.
    ('make_fibre_sling',          'carry sling'),
    ('make_leather_bottle_sling', 'bottle sling')
ON CONFLICT (process_key, subject_term) DO NOTHING;
