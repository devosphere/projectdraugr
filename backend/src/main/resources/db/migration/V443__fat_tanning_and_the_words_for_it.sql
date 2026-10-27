-- #37 — the hide chain's missing work, and the five sentences that reached nothing because of it.
--
-- Swept the hide axis on a booted world. Nine of the sentences a player would use were answered properly —
-- scraping, dehairing, tanning, stretching, salting all reach their process — and five reached nothing at all:
--
--     soften the hide        ->  nothing at all
--     work the hide soft     ->  nothing at all
--     break the hide         ->  nothing at all
--     dress the hide with fat->  nothing at all
--     rub fat into the hide  ->  nothing at all
--
-- This is NOT a routing gap, which is why it is a migration and not a keyword edit: the catalogue holds bark
-- tanning (tan_hide: a hide and two oak bark, in water, three hours) and holds no softening work at all. A hide
-- that has been through bark liquor is leather, and the game has nothing to say about the hours of working it
-- soft that every real hide needs — so the words had nothing to reach.
--
-- FAT TANNING IS THE WORK, and it is a real second route rather than an invented one: fat and brain tanning
-- dress the hide with grease and break the fibres by working it, and it wants no bark, no water and no tool —
-- only fat, and a long time. So:
--
--     bark tanning:  a hide + 2 oak bark, in water, CUTTING tool, 180 minutes
--     fat tanning:   a hide + 2 rendered tallow, no water, no tool,  300 minutes
--
-- Two ways to the same leather, each wanting what the keeper happens to have. And it gives rendered_tallow a
-- fourth use: it was candles, rush lights, soap and pemmican, all of which burn or eat it.
--
-- NO NEW ITEM ON PURPOSE. A supple-leather item would be one more catalogue token to make terminally useful,
-- and every garment recipe already takes tanned_leather — so the output is the leather the chain already has,
-- and nothing downstream needs touching. That is the rule this project keeps relearning: close a chain where it
-- is already closed, do not open a second one beside it.

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class,
                              requires_fire, requires_water, duration_minutes, domain_key, keywords, narration,
                              review_state, reviewed_at, category_key)
VALUES ('fat_tan_hide', 'Fat-tan a hide', 'tanned_leather', 1, 1, NULL,
        FALSE, FALSE, 300, 'textiles',
        'fat tan the hide,brain tan the hide,dress the hide with fat,rub fat into the hide,work the hide soft,'
        || 'soften the hide,soften the leather,break the hide,stake the hide,fat tan,brain tan',
        'You work the tallow in with your hands until the hide drinks it, then begin the long part: pulling it '
        || 'over the stake, back and forth, breaking the fibre as it dries. Stop early and it sets board-stiff. '
        || 'Keep at it and it comes up soft, and stays soft.',
        'VERIFIED', now(), 'PROCESS')
ON CONFLICT (process_key) DO NOTHING;

-- The hide, as any one of the five the chain can offer — the same group bark tanning takes, so a keeper is never
-- told to go and get a different hide than the one in their hands.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity)
SELECT 'fat_tan_hide', 'hide', k, 1
  FROM (VALUES ('animal_hide'), ('deer_hide'), ('boar_hide'), ('fleshed_hide'), ('dehaired_hide')) AS v(k)
 WHERE EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = v.k)
ON CONFLICT DO NOTHING;

-- And the fat, which is what makes it this process and not the other one.
INSERT INTO material_process_input (process_key, item_key, quantity)
VALUES ('fat_tan_hide', 'rendered_tallow', 2)
ON CONFLICT DO NOTHING;

-- The subjects: the nouns a sentence about this work actually names. Without one of these the process can never
-- match, whatever its keywords say — the Auditor has a standing check for exactly that.
INSERT INTO process_subject (process_key, subject_term)
SELECT 'fat_tan_hide', t
  FROM (VALUES ('hide'), ('skin'), ('pelt'), ('leather'), ('fat'), ('tallow'), ('grease'),
               ('deer'), ('boar'), ('animal'), ('dehaired')) AS v(t)
ON CONFLICT DO NOTHING;

DO $$
DECLARE n INT; out_g INT; in_g INT;
BEGIN
    -- 1. It is there, and it is live.
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='fat_tan_hide' AND review_state='VERIFIED')
    THEN RAISE EXCEPTION 'V443: fat_tan_hide is not live'; END IF;

    -- 2. It takes a hide and it takes fat. A bill read from one input table only reads as matter from nothing,
    --    and this process exists BECAUSE of the fat, so both are asserted.
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='fat_tan_hide' AND group_name='hide')
    THEN RAISE EXCEPTION 'V443: fat tanning takes no hide'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='fat_tan_hide' AND item_key='rendered_tallow')
    THEN RAISE EXCEPTION 'V443: fat tanning takes no fat, which is the whole of what it is'; END IF;

    -- 3. IT MAKES NO MATTER. The Auditor's own rule, reproduced here rather than discovered 90 minutes later in
    --    CI: one bad row fails nearly every integration test in the suite, because they all assert consistency.
    SELECT max_output_grams, min_input_grams INTO out_g, in_g
      FROM process_mass_balance WHERE process_key='fat_tan_hide';
    IF out_g IS NULL OR in_g IS NULL THEN RAISE EXCEPTION 'V443: fat tanning has no mass balance'; END IF;
    IF out_g > in_g * 1.05 THEN
        RAISE EXCEPTION 'V443: fat tanning would create matter: % g out of % g in', out_g, in_g;
    END IF;

    -- 4. It can be reached. Both of the Auditor's reachability rules, for this row: a subject term, and a
    --    keyword that classifies to its own declared category — without the second, the category gate rejects
    --    every sentence that would have named it.
    SELECT COUNT(*) INTO n FROM process_subject WHERE process_key='fat_tan_hide';
    IF n = 0 THEN RAISE EXCEPTION 'V443: fat tanning has no subject terms and could never match'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM material_process mp
          JOIN unnest(string_to_array(mp.keywords, ',')) k ON TRUE
          JOIN category_term ct ON ct.category_key = mp.category_key
         WHERE mp.process_key='fat_tan_hide'
           AND (' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %'
                OR ' ' || ct.term || ' ' LIKE '% ' || btrim(k) || ' %'))
    THEN RAISE EXCEPTION 'V443: no keyword of fat tanning classifies to PROCESS'; END IF;

    -- 5. NOBODY ELSE OWNS THESE WORDS. An equal-length keyword tie is settled by asking since V318, which is
    --    correct and still a question a player did not need to be asked. None of these phrases was taken when
    --    this was written, and this is what says so.
    SELECT COUNT(*) INTO n
      FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
     WHERE mp.process_key <> 'fat_tan_hide'
       AND btrim(lower(k)) IN ('fat tan the hide','brain tan the hide','dress the hide with fat',
                               'rub fat into the hide','work the hide soft','soften the hide',
                               'soften the leather','break the hide','stake the hide','fat tan','brain tan');
    IF n > 0 THEN RAISE EXCEPTION 'V443: % of fat tanning''s keywords are already another process''s', n; END IF;
    SELECT COUNT(*) INTO n
      FROM assembly_definition a, unnest(string_to_array(a.keywords, ',')) k
     WHERE btrim(lower(k)) IN ('soften the hide','work the hide soft','break the hide','stake the hide');
    IF n > 0 THEN RAISE EXCEPTION 'V443: % of these words belong to an assembly', n; END IF;

    -- 6. AND THE LEATHER IT MAKES IS NOT A DEAD END. The reason there is no new item: the output must be
    --    something the catalogue can go on working, and tanned leather is what every garment recipe asks for.
    SELECT COUNT(*) INTO n FROM (
        SELECT 1 FROM material_process_input WHERE item_key='tanned_leather'
        UNION ALL SELECT 1 FROM material_process_input_group WHERE item_key='tanned_leather'
        UNION ALL SELECT 1 FROM assembly_stage_requirement WHERE item_key='tanned_leather') u;
    IF n = 0 THEN RAISE EXCEPTION 'V443: tanned leather leads nowhere, so this process makes a token'; END IF;
END $$;
