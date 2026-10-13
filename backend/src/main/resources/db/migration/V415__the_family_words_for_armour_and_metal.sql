-- V415 — the family words for armour, metal and panelling (#37)
--
-- THE GENERIC-WORD DEFECT, found by an audit rather than by stumbling on it. The recipe: take every catalogue
-- display name with more than one word, group by its HEAD NOUN, keep the heads shared by three or more items,
-- and subtract the heads that some keyword already declares. What is left is a list of words a person would
-- say that reach nothing while N specific entries exist.
--
-- Measured live on a booted backend, with a living Chronicle (the first run of this sweep killed one and every
-- answer came back blank, which reads exactly like a universal defect and is not one):
--
--   make a cuirass  ->  "You turn it over and try it two or three ways. Each one stops at the same place."
--   make an ingot   ->  "the way to make what you intend does not come to you"
--   make a panel    ->  the same
--
-- while the catalogue holds FIVE armours, FIVE ingots and ELEVEN processes that produce a panel, every one of
-- them reachable only by naming its material outright.
--
-- WHAT IS DELIBERATELY NOT HERE, and this is the more useful half of the finding:
--
--   bow   — FIVE bows exist and `make a bow` reaches nothing. But "bow" sits inside ELBOW, and
--           `make an elbow guard` also reaches nothing today, so a bare "bow" keyword would trade a missing
--           answer for a WRONG one. (It is safe beside "bowl", which is a declared four-character keyword and
--           wins on length — the same argument that makes "hat" safe beside "thatch". ELBOW has no such
--           competitor.) Wants the word-boundary matcher, which is a separate piece of work.
--   cape  — SEVEN capes, and `escape the wolf` reaches nothing, so the same trade. V411 refused it for this
--           reason and the refusal still stands.
--   wrap  — TEN wraps, but "wrapped" and "wrapping" contain it and both are ordinary speech here.
--
-- AND THE PROCESSES ARE CHOSEN, NOT SWEPT. A family word belongs on the processes that MAKE the thing, never on
-- the ones that transform or destroy one:
--   carburise_iron_cuirass hardens a cuirass you already own — "make a cuirass" must not offer it.
--   melt_down_bronze / melt_down_copper destroy an axe to recover metal; they do yield an ingot, and offering
--   them to someone who asked to MAKE one is answering a question with its opposite.
--   build_shutter makes a shutter, carve_fish_weir makes a weir, and lay_out_hide yields a panel as a BYPRODUCT.
--
-- Keyword headroom measured before writing, because `material_process.keywords` is varchar(200) and V404's
-- first cut overflowed it — failing on a seeded database while silently no-opping on a fresh one, which is the
-- worst way for a migration to be wrong. The tightest row touched here is forge_iron_cuirass at 170 of 200.

UPDATE material_process SET keywords = keywords || ',cuirass'
 WHERE process_key IN ('craft_scale_armour','forge_bronze_cuirass','forge_iron_cuirass','make_leather_cuirass')
   AND keywords NOT LIKE '%,cuirass%';

UPDATE material_process SET keywords = keywords || ',ingot'
 WHERE process_key IN ('alloy_bronze','smelt_copper','smelt_lead','smelt_silver','smelt_tin')
   AND keywords NOT LIKE '%,ingot%';

UPDATE material_process SET keywords = keywords || ',panel'
 WHERE process_key IN ('daub_panel','edge_join_boards','make_bark_back_panel','make_rawhide_chest_panel',
                       'make_reed_back_panel','sew_leather_panel','weave_wattle_panel')
   AND keywords NOT LIKE '%,panel%';

DO $$
DECLARE bad text; n int;
BEGIN
  -- 1. EVERY FAMILY WORD MUST NOW REACH SOMETHING, which is the whole point and the thing most likely to be
  --    silently false: an IN-list against a catalogue whose keys have moved does nothing and looks fine.
  FOREACH bad IN ARRAY ARRAY['cuirass','ingot','panel'] LOOP
    SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = bad;
    IF n = 0 THEN
      RAISE EXCEPTION 'V415: the family word "%" still reaches no process — the process keys have probably '
        'drifted from the names used here', bad;
    END IF;
  END LOOP;

  -- 2. AND IT MUST REACH MORE THAN ONE, or it is not a family word at all and the ask it is meant to produce
  --    would never happen.
  FOREACH bad IN ARRAY ARRAY['cuirass','ingot','panel'] LOOP
    SELECT count(*) INTO n FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) = bad;
    IF n < 2 THEN
      RAISE EXCEPTION 'V415: the family word "%" reaches only % process, so it is a specific name and not a '
        'family word', bad, n;
    END IF;
  END LOOP;

  -- 3. NOT ON A PROCESS THAT TRANSFORMS OR DESTROYS ONE. Asserted rather than left to the IN-lists above, so a
  --    later edit that sweeps "every process whose output is a cuirass" fails here instead of answering
  --    "make a cuirass" with "carburise the iron cuirass you do not have".
  SELECT string_agg(process_key, ', ') INTO bad
    FROM material_process, unnest(string_to_array(keywords, ',')) k
   WHERE trim(k) IN ('cuirass','ingot','panel')
     AND (process_key LIKE 'carburise%' OR process_key LIKE 'melt_down%' OR process_key = 'lay_out_hide'
          OR process_key = 'build_shutter' OR process_key = 'carve_fish_weir');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V415: % transforms or destroys the thing rather than making it, and must not answer to '
      'the family word', bad;
  END IF;

  -- 4. NO KEYWORD LIST MAY EXCEED THE COLUMN. V404 failed on a seeded database while no-opping on a fresh one.
  SELECT string_agg(process_key, ', ') INTO bad FROM material_process WHERE length(keywords) > 200;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V415: these keyword lists no longer fit varchar(200): %', bad;
  END IF;

  -- 5. AND THE THREE WORDS THIS MIGRATION REFUSES MUST STAY REFUSED. "bow" is inside ELBOW, "cape" inside
  --    ESCAPE and "wrap" inside WRAPPED, and in each case the colliding sentence reaches nothing today — so
  --    adding them would trade a missing answer for a wrong one, which is the worse direction. A later cycle
  --    may add them once the matcher holds keywords to word boundaries; adding them before that is the defect.
  SELECT string_agg(DISTINCT process_key, ', ') INTO bad
    FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k) IN ('bow','cape','wrap');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V415: a bare "bow", "cape" or "wrap" keyword exists on %, and each of those sits inside an '
      'ordinary English word with no longer keyword to outrank it', bad;
  END IF;
END $$;
