-- #77 — the names you can mend a thing by but not build it by.
--
-- PR #795 taught REPAIR_STRUCTURE to read the catalogue, and it reads each structure's `subjects` — the curated
-- short forms. Building routes on `keywords`. The two columns disagree, and the result is an asymmetry no
-- player would ever guess at. Measured live, four for four:
--
--   repair the drain       -> REPAIR_STRUCTURE    build a drain       -> UNKNOWN
--   mend the stump         -> REPAIR_STRUCTURE    build a stump       -> UNKNOWN
--   repair the ramp        -> REPAIR_STRUCTURE    build a ramp        -> UNKNOWN
--   repair the sand filter -> REPAIR_STRUCTURE    build a sand filter -> UNKNOWN
--
-- YOU CAN MEND A THING BY A NAME YOU CANNOT BUILD IT BY. Found by putting `build a <name>` through the live
-- classifier for all 218 structure subjects in the catalogue: 195 reach their own assembly, 0 got no reply, and
-- of the 23 that go elsewhere most are correct (a bed is MAKE_BED, a fence is BUILD_FENCE, and a bare material
-- word like "stone" or "daub" reaching a recipe is the right answer to a nonsense sentence). Seven were names
-- the catalogue claims in `subjects` and does not honour in `keywords`. Five of those are fixed here.
--
-- TWO ARE DELIBERATELY LEFT, because guessing would be worse than the gap:
--
--   * "bank" (earth_sheltered_hut). `bank the fire` is the Java BANK_FIRE intent and must stay so, and
--     `build a bank` is genuinely ambiguous between this hut and an earth berm wall. A player saying it could
--     mean either, so the answer is a question, not a pick — and this migration has no way to ask one.
--   * "rail" (split_rail_fence). A rail is a PART, and `build a rail` could mean the fence or the hitching
--     rail, which is a different structure for a different job.
--
-- AND THREE FAMILY WORDS ARE OUT OF SCOPE HERE, recorded so the next cycle does not mistake them for this
-- defect. They are shared, so they want the "name the one you mean" treatment V318 built, not a longest-keyword
-- pick:
--
--   pit   -> 7 assemblies (lime_burning_pit, pit_house, cool_food_pit, manure_pit, offal_pit, earth_oven, stone_fire_pit)
--   frame -> 6 (drying_rack, hide_scraping_frame, smoke_rack, hide_frame, simple_gate, roofing_frame)
--   rack  -> 4 (smoke_rack, drying_rack, hay_rack, harness_rack) — and `build a rack` currently reaches the
--            Java CRAFT_SHELF intent, so one specific thing answers for four.

-- A ditch that drains is a drain. It already answers to "dig a ditch", "build a ditch" and "drain the ground";
-- the bare noun was the one form missing.
UPDATE assembly_definition SET keywords = keywords || ',build a drain,dig a drain,cut a drain'
 WHERE assembly_key = 'drainage_ditch' AND ',' || keywords || ',' NOT LIKE '%,build a drain,%';

-- The only ramp in the catalogue, so the short word is unambiguous.
UPDATE assembly_definition SET keywords = keywords || ',build a ramp,raise a ramp,bank up a ramp'
 WHERE assembly_key = 'animal_loading_ramp' AND ',' || keywords || ',' NOT LIKE '%,build a ramp,%';

-- The only stump you set in the ground on purpose.
UPDATE assembly_definition SET keywords = keywords || ',build a stump,set up a stump,sink a stump'
 WHERE assembly_key = 'woodworking_stump' AND ',' || keywords || ',' NOT LIKE '%,build a stump,%';

-- "sand filter bed" was a keyword and "sand filter" was not, which is the longer name answering while the
-- shorter and more natural one reached nothing.
UPDATE assembly_definition SET keywords = keywords || ',build a sand filter,sand filter,make a sand filter'
 WHERE assembly_key = 'sand_filter_bed' AND ',' || keywords || ',' NOT LIKE '%,sand filter,%';

-- It knew "wall the spring head" and "protect the spring head" but not the plainest way to ask for one.
UPDATE assembly_definition SET keywords = keywords || ',build a spring head,spring head,make a spring head'
 WHERE assembly_key = 'spring_head_protection' AND ',' || keywords || ',' NOT LIKE '%,spring head,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Each name now reaches exactly ONE assembly. These are specific things, not family words — that is the
    --    whole reason "pit", "frame" and "rack" are excluded above.
    FOR bad IN SELECT w FROM (VALUES ('build a drain'),('build a ramp'),('build a stump'),
                                     ('sand filter'),('spring head')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n <> 1 THEN RAISE EXCEPTION 'V425: "%" is claimed by % assemblies; a named thing is one thing', bad, n; END IF;
    END LOOP;

    -- 2. Nothing has displaced the catalogue's own longer names.
    SELECT string_agg(f.a, ', ') INTO bad
      FROM (VALUES ('drainage_ditch','drainage ditch'),('animal_loading_ramp','loading ramp'),
                   ('woodworking_stump','woodworking stump'),('sand_filter_bed','sand filter bed'),
                   ('spring_head_protection','spring head protection')) f(a,w)
     WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.assembly_key = f.a
                          AND ',' || replace(ad.keywords,', ',',') || ',' LIKE '%,' || f.w || ',%');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V425: % no longer answers to its own name', bad; END IF;

    -- 3. No new name sits inside a material process keyword.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('drain'),('ramp'),('stump'),('sand filter'),('spring head')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V425: a structure name collides with a recipe: %', bad; END IF;

    -- 4. THE TWO LEFT ALONE STAY LEFT ALONE, so a later cycle cannot quietly claim them without reading the
    --    reasoning at the top of this file. "bank" belongs to BANK_FIRE in the sentences that matter, and
    --    "rail" names a part shared by two structures.
    SELECT string_agg(DISTINCT v.w, ', ') INTO bad
      FROM (VALUES ('build a bank'),('build a rail')) v(w), assembly_definition d
     WHERE ',' || replace(d.keywords,', ',',') || ',' LIKE '%,' || v.w || ',%';
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V425: % was claimed after all — if that is intended, say why at the top of this file first', bad; END IF;

    -- 5. WHAT THIS GUARD CANNOT SEE, stated because V424 learned it the hard way: a JAVA INTENT runs before the
    --    keyword matcher, so an addition can satisfy every check here and still be taken before the matcher is
    --    reached. `build a calving pen` (BUILD_PEN) and `build a root fodder cache` (STORE) both did exactly
    --    that while their migration's guard passed. Only a live measurement finds those, and the five phrases
    --    above were measured on a booted backend before this file was committed.
    SELECT string_agg(ad.assembly_key, ', ') INTO bad FROM assembly_definition ad
     WHERE ad.review_state <> 'VERIFIED' AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V425: % is fully staged and still unreachable', bad; END IF;
END $$;
