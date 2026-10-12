-- V411 — the family word for a hat (#37)
--
-- Twenty-one head and hand garments exist and the plain words for them reach nothing:
--
--   make a hat      ->  "You handle and test it, feeling for the trick of it, but the way to make what you
--                        intend does not come."
--   make mittens    ->  "The stuff of it is willing enough; it is the working that will not come."
--   make a cap      ->  "You get your hands properly into it and still cannot find the step…"
--   make gloves     ->  the same
--   make a hood     ->  the same
--
-- Measured live, not inferred: none of them even reaches the AMBIGUOUS "which one did you mean" ask. Every
-- process declares only its SPECIFIC name — make_fur_cap answers to "fur cap", "make a fur cap", "sew a fur
-- cap" — and not one declares the word a cold person actually uses. That is the generic-word defect: N specific
-- entries exist and the family word reaches none of them.
--
-- WHY THIS IS DATA AND NOT A CLASSIFIER RULE. Adding "cap" to the CRAFT_GARMENT intent would shadow
-- make_leather_cap and the dozen like it, because a Java intent runs BEFORE the material matcher — the
-- CRAFT-vs-PROCESS trap. Declared as process keywords instead, the family word reaches the siblings and V318's
-- tie-breaker does the rest: one candidate resolves outright, several produce the ask.
--
-- WHAT EACH FAMILY WORD WILL DO, from the catalogue as it stands:
--   hat      -> ONE candidate (make_reed_hat), so it resolves outright
--   mitten/s -> ONE pair (make_fur_mitten_left/right), which already share "fur mittens" and pair correctly
--   cap      -> THREE (fur, grass, leather) -> the ask
--   hood     -> FOUR garments (bark, fur, hide, leather) -> the ask
--   glove/s  -> TWO materials, paired (hide, leather) -> the ask
--
-- "CAPE" IS DELIBERATELY NOT ADDED. "cape" sits inside ESCAPE, and "the goat escaped" is a sentence this game
-- hears; unlike the two collisions below there is no longer competing keyword to beat it on length. Five capes
-- keep their specific names until that can be done with a word-boundary matcher.
--
-- THE TWO COLLISIONS THAT ARE SAFE, AND WHY — both verified against the live catalogue rather than reasoned:
--   "cap"  sits inside DEATH_CAP, a real item (a mushroom). The longest keyword wins, and "death cap" is longer.
--   "hat"  sits inside THATCH, and bundle_thatch declares the bare keyword "thatch" — again longer, again wins.
-- The guards below assert exactly that, and the routing replay checks it against every keyword in the catalogue.

UPDATE material_process SET keywords = keywords || ',hat'
 WHERE process_key = 'make_reed_hat' AND keywords NOT LIKE '%,hat%';

UPDATE material_process SET keywords = keywords || ',mitten,mittens'
 WHERE process_key IN ('make_fur_mitten_left','make_fur_mitten_right') AND keywords NOT LIKE '%,mitten,%';

UPDATE material_process SET keywords = keywords || ',cap'
 WHERE process_key IN ('make_fur_cap','make_grass_cap','make_leather_cap') AND keywords NOT LIKE '%,cap%';

UPDATE material_process SET keywords = keywords || ',hood'
 WHERE process_key IN ('make_bark_hood','make_fur_hood','make_hide_hood','make_leather_hood')
   AND keywords NOT LIKE '%,hood%';

UPDATE material_process SET keywords = keywords || ',glove,gloves'
 WHERE process_key IN ('make_hide_glove_left','make_hide_glove_right',
                       'make_leather_glove_left','make_leather_glove_right')
   AND keywords NOT LIKE '%,glove,%';

DO $$
DECLARE bad text; n int;
BEGIN
  -- 1. EVERY FAMILY WORD MUST NOW REACH SOMETHING. The whole point, asserted rather than assumed.
  FOREACH bad IN ARRAY ARRAY['hat','mitten','mittens','cap','hood','glove','gloves'] LOOP
    SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k
     WHERE trim(k) = bad;
    IF n = 0 THEN
      RAISE EXCEPTION 'V411: the family word "%" still reaches no process', bad;
    END IF;
  END LOOP;

  -- 2. THE LENGTH ARGUMENT THE SAFETY RESTS ON. A family word is only safe beside a longer word that contains
  --    it because the matcher takes the LONGEST keyword. If a competing keyword ever gets shorter than the
  --    family word, the family word starts winning and "thatch the roof" becomes millinery.
  IF NOT EXISTS (SELECT 1 FROM material_process, unnest(string_to_array(keywords, ',')) k
                  WHERE trim(k) = 'thatch' AND length('thatch') > length('hat')) THEN
    RAISE EXCEPTION 'V411: nothing declares the longer word "thatch" any more, so the bare "hat" keyword is no '
      'longer safe — thatching a roof would reach the hat';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'death_cap') THEN
    RAISE EXCEPTION 'V411: death_cap has gone, so re-check whether the bare "cap" keyword still needs its '
      'longer competitor (it was safe only because "death cap" outranks "cap" on length)';
  END IF;

  -- 3. AND "CAPE" MUST NOT HAVE CREPT IN. It sits inside ESCAPE with nothing longer to beat it, so it is the
  --    one family word this migration deliberately refuses. A later cycle may add it with a word-boundary
  --    matcher; adding it as a bare keyword would make fleeing a wolf into making a rain cape.
  SELECT string_agg(DISTINCT process_key, ', ') INTO bad
    FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = 'cape';
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V411: a bare "cape" keyword exists on %, and "cape" is inside ESCAPE with no longer '
      'keyword to outrank it', bad;
  END IF;

  -- 4. No keyword may exceed the column, which is how V404 broke a seeded database while silently no-opping on
  --    a fresh one.
  SELECT string_agg(process_key, ', ') INTO bad FROM material_process WHERE length(keywords) > 200;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V411: these keyword lists no longer fit varchar(200): %', bad;
  END IF;
END $$;
