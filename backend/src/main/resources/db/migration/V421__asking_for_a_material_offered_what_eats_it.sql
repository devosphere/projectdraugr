-- #37 — asking to MAKE a material offered the things made FROM it.
--
-- Found by playing, and these are not misses. They are confident wrong answers:
--
--     make cordage  ->  "Those words fit more than one piece of work — haft a stone axe, or make a bark
--                        container — and you have the makings of none of them within reach."
--     make a cord   ->  "... make a waterskin, or weave a quiver ..."
--     make a sheet  ->  "... make a bark container, or weave a quiver ..."
--     make a bundle ->  "... make an oil lamp, or make tallow candles ..."
--
-- Asked for cordage, offered an axe. Asked for a bundle, offered a lamp. Every one of those is a thing that
-- CONSUMES the material named, reached because the material is a legitimate INPUT subject on it.
--
-- And the producer exists, in quantity. SEVENTEEN processes output fiber_cordage — twist_cordage,
-- ret_flax_cordage, ret_hemp_cordage, twist_nettle, twist_bast, twist_bulrush, twist_milkweed, twist_bramble,
-- twist_cattail, twist_grass, twist_willow, twist_root, strip_bark_cordage and the rest — and twist_cordage
-- declares the BARE keywords "cordage", "cord", "rope" and "twine". So "twist cordage" has always worked.
--
-- THE WHOLE DIFFERENCE IS THE VERB. Every cordage producer is PROCESS; haft_stone_axe and make_bark_container
-- are CRAFT and declare the bare keyword "make". `make` is a CRAFT term at weight 1 and nothing in
-- "make cordage" is a PROCESS term, so the sentence classifies CRAFT — and the seventeen producers are
-- EXCLUDED FROM CONSIDERATION ENTIRELY before any keyword is compared. The longest-keyword rule never gets to
-- see that "cordage" (8) beats "make" (4).
--
-- This is V419's mechanism exactly ("make a meal" vs "cook a meal"), with a worse symptom: there, the wrong
-- category produced silence; here it produces an authoritative list of the wrong work.
--
-- WHY MULTI-WORD TERMS AND NOT THE BARE NOUN. A bare 'cordage' PROCESS term would be the obvious fix and it
-- would break four CRAFT recipes that carry the word in their own phrases — cordage_arm_sling ("cordage arm
-- sling"), make_cordage_belt ("cordage girdle"), make_cordage_tool_belt ("cordage tool strap") and
-- knot_cordage_loop ("cordage loop"). PROCESS at weight 2 beats CRAFT's "make" at 1, and no belt, sling or loop
-- word counterweights it, so asking to make a cordage girdle would have stopped working.
--
-- ActivityClassifier.containsTerm normalises BOTH sides and compares, so a term may be a phrase. These are
-- phrases, chosen so that each one appears in the sentence that asks for the material and in no recipe's name:
-- "make a rope" is deliberately absent, because make_rope_harness declares "make a rope harness".
INSERT INTO category_term (category_key, term, weight) VALUES
 ('PROCESS', 'make cordage', 3),
 ('PROCESS', 'make some cordage', 3),
 ('PROCESS', 'make cord', 3),
 ('PROCESS', 'make a cord', 3),
 ('PROCESS', 'make rope', 3),
 ('PROCESS', 'make some rope', 3),
 ('PROCESS', 'make twine', 3),
 ('PROCESS', 'make a sheet', 3)
ON CONFLICT (category_key, term) DO NOTHING;

-- A BUNDLE NEEDS NO CATEGORY FLIP, because the thing that ties one is already CRAFT. tie_forage_bundle declares
-- "tie a bundle", "bind a bundle" and "bundle it up" — every verb for it except the one most people reach for.
-- So this is a keyword, not a term, and it stays in the category "make" already chooses.
UPDATE material_process
   SET keywords = keywords || ',make a bundle,make a carrying bundle,make up a bundle'
 WHERE process_key = 'tie_forage_bundle' AND ',' || keywords || ',' NOT LIKE '%,make a bundle,%';

-- THE CATEGORY WAS ONLY TWO THIRDS OF IT, and measuring is what showed the rest. With the terms above in
-- place, `make cordage`, `make rope` and `make twine` all reached the producer — and `make a cord` still
-- answered "make a waterskin, or weave a quiver".
--
-- Because a process matches on CATEGORY, KEYWORD and SUBJECT, and twist_cordage's subject list is
-- braid, bundle, cordage, fiber, plant, processed, rope, string, twine — with no "cord". Its KEYWORD list does
-- carry "cord", so the keyword gate passed and the SUBJECT gate dropped it; the category was right and the
-- producer still lost. The consumers passed both gates, because cordage is a legitimate INPUT subject on them.
--
-- THAT IS THE GENERAL SHAPE OF THIS WHOLE DEFECT: a process's subjects are what it CONSUMES, so a sentence
-- naming a material always fits its consumers better than its maker, and the maker must name its own output as
-- a subject to compete at all.
INSERT INTO process_subject (process_key, subject_term)
 SELECT process_key, 'cord' FROM material_process WHERE output_item_key = 'fiber_cordage'
ON CONFLICT (process_key, subject_term) DO NOTHING;

-- The bark sheet needed BOTH sides. flatten_bark is PROCESS (so the term above does flip the category for it),
-- its only subject was "bark", and not one of its four keywords appears in "make a sheet" — so there was
-- nothing for either gate to catch.
UPDATE material_process
   SET keywords = keywords || ',make a sheet,make a bark sheet,bark sheet,flatten a sheet'
 WHERE process_key = 'flatten_bark' AND ',' || keywords || ',' NOT LIKE '%,bark sheet,%';

INSERT INTO process_subject (process_key, subject_term) VALUES
 ('flatten_bark', 'sheet'), ('flatten_bark', 'bark sheet')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Every term landed.
    SELECT COUNT(*) INTO n FROM category_term
     WHERE category_key='PROCESS' AND term IN ('make cordage','make some cordage','make cord','make a cord',
                                               'make rope','make some rope','make twine','make a sheet');
    IF n <> 8 THEN RAISE EXCEPTION 'V421: expected eight material phrases, found %', n; END IF;

    -- 2. THE COLLISION GUARD, MADE PERMANENT. None of these phrases may appear inside a NON-PROCESS recipe's
    --    own keyword, or this migration would silently stop that recipe being reachable by its own name — which
    --    is precisely what a bare 'cordage' term would have done to four belts, slings and loops.
    SELECT string_agg(DISTINCT ct.term || ' -> ' || mp.process_key || ' (' || mp.category_key || ')', ', ') INTO bad
      FROM category_term ct, material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ct.category_key = 'PROCESS'
       AND ct.term IN ('make cordage','make some cordage','make cord','make a cord','make rope','make some rope',
                       'make twine','make a sheet')
       AND mp.category_key <> 'PROCESS'
       AND ' ' || lower(trim(k)) || ' ' LIKE '% ' || ct.term || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V421: a material phrase would shadow a recipe: %', bad; END IF;

    -- 3. The flip only helps if the producers are actually in the category it flips to. If cordage ever stopped
    --    being made by a PROCESS, these terms would send the asker somewhere emptier than before.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE output_item_key = 'fiber_cordage' AND category_key = 'PROCESS';
    IF n < 5 THEN RAISE EXCEPTION 'V421: only % PROCESS rows still make cordage; the category flip is pointless', n; END IF;

    -- 4. And the bundle keeps the verb it already had, so the old phrasing is not traded for the new one.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'tie_forage_bundle' AND ',' || keywords || ',' LIKE '%,tie a bundle,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V421: tie_forage_bundle lost its own verb'; END IF;

    -- 5. BOTH GATES, ON THE SAME ROW. The maker must declare the material as its own subject AND carry a keyword
    --    the asking sentence contains. Either alone loses to the consumers, which is the whole lesson here and
    --    the reason "make a cord" survived the category fix.
    SELECT string_agg(f.p || ' (' || f.w || ')', ', ') INTO bad
      FROM (VALUES ('twist_cordage','cord'),('twist_cordage','cordage'),('twist_cordage','rope'),
                   ('twist_cordage','twine'),('flatten_bark','sheet')) f(p,w)
     WHERE NOT EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key=f.p AND ps.subject_term=f.w);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V421: % is not declared as the maker''s own subject', bad; END IF;

    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key='flatten_bark' AND ',' || keywords || ',' LIKE '%,make a sheet,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V421: the bark sheet has a subject with no keyword beside it'; END IF;
END $$;
