-- #37 — cutting a hide was answered by the process for drying MEAT.
--
-- Swept the whole `cut the …` axis on a booted world, ten materials. Nine were right or honestly refused:
--
--   cut the leather into strips   -> asked which work was meant (V318's AMBIGUOUS, exactly as designed)
--   cut the rawhide into thongs   -> the rawhide work
--   cut the meat into strips      -> dry meat, correctly
--   cut a notch in the log        -> notch a log
--   cut the plank to length       -> split planks
--   cut the bark / cut the reeds  -> honest gather refusals
--   cut the sinew / the antler    -> no method exists (breadth, recorded on the ticket)
--
-- And one was confidently WRONG:
--
--   cut the hide into strips      -> "Dry meat turns on a cutting edge"
--
-- ELEVEN processes declare the bare keyword `cut` — cut_boot_soles, cut_lamellae, cut_leather_cord, cut_mortise,
-- cut_tenon, dovetail_corner, dry_meat, lap_joint_planks, notch_log, scarf_joint, split_planks — which is fine,
-- because V318 settles a tie by the named output, then by what is in reach, then by asking. What it cannot settle
-- is a sentence where the only matching keyword is that bare `cut`: the player said HIDE, and no process claimed
-- that word, so the hide counted for nothing and the tie was settled among eleven strangers.
--
-- Why the leather sentence is fine and the hide one was not: `cut_leather_cord` declares "cut leather", so the
-- leather sentence has a longer match to win with. `lay_out_hide` declares the whole phrase "cut the hide into
-- pieces" and nothing shorter, so "cut the hide into STRIPS" — the same work, said the way a person says it —
-- matched none of it.
--
-- So the hide gets the same footing the leather already had. "cut the hide" is twelve characters against the bare
-- `cut`'s three, so any sentence about cutting a hide now reaches the work that lays one out, whatever the player
-- means to cut it into.

DO $$
DECLARE kw TEXT; n INT;
BEGIN
    SELECT keywords INTO kw FROM material_process WHERE process_key = 'lay_out_hide';
    IF kw IS NULL THEN RAISE EXCEPTION 'V436: lay_out_hide is gone; this file has nothing to fix'; END IF;

    -- Idempotent on the exact entry, not on a substring: "cut the hide into pieces" CONTAINS "cut the hide", so a
    -- LIKE '%cut the hide%' guard would read as already-done and no-op for ever. That is the shape of defect this
    -- very file is about, and it has bitten this project before (see the note on V411's `wrap` guard).
    IF NOT ('cut the hide' = ANY (string_to_array(kw, ','))) THEN
        UPDATE material_process SET keywords = keywords || ',cut the hide' WHERE process_key = 'lay_out_hide';
    END IF;
END $$;

DO $$
DECLARE kw TEXT; n INT; bad TEXT;
BEGIN
    SELECT keywords INTO kw FROM material_process WHERE process_key = 'lay_out_hide';

    -- 1. The keyword is there, as its own entry.
    IF NOT ('cut the hide' = ANY (string_to_array(kw, ','))) THEN
        RAISE EXCEPTION 'V436: lay_out_hide does not declare "cut the hide"'; END IF;

    -- 2. Once, not twice. A keyword list that grows a duplicate every run is how the column overflowed in V404.
    SELECT COUNT(*) INTO n FROM unnest(string_to_array(kw, ',')) k WHERE k = 'cut the hide';
    IF n <> 1 THEN RAISE EXCEPTION 'V436: "cut the hide" appears % times in lay_out_hide', n; END IF;

    -- 3. AND THE COLUMN STILL FITS. material_process.keywords is varchar(200) and V404's first cut overflowed it,
    --    failing on a seeded database while silently no-opping on a fresh one — the worst way to be wrong.
    IF length(kw) > 200 THEN RAISE EXCEPTION 'V436: lay_out_hide keywords are % characters, over the column', length(kw); END IF;

    -- 4. NOBODY ELSE CLAIMS IT. The whole point is that this phrase has exactly one owner, so the eleven
    --    processes that share the bare `cut` can never be the ones a hide sentence lands among again.
    SELECT string_agg(process_key, ', ' ORDER BY process_key) INTO bad FROM material_process
     WHERE process_key <> 'lay_out_hide' AND 'cut the hide' = ANY (string_to_array(keywords, ','));
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V436: % also claims "cut the hide"', bad; END IF;

    -- 5. AND THE WORK AGREES WITH THE WORD, in the catalogue's own language. This process is named "Lay out and
    --    cut a hide" and already declares "cut the hide into pieces", which is why it is the right owner of the
    --    shorter phrase — even though its INPUT is tanned leather rather than a raw hide. That is the catalogue
    --    calling tanned hide "the hide", as a person does; checked on the live rows rather than assumed, because
    --    the first draft of this file guarded on a hide input and would have failed here. The refusal a player
    --    now gets names the work they asked for and what it wants, which is the whole improvement over being
    --    told about meat.
    IF NOT EXISTS (SELECT 1 FROM material_process
                    WHERE process_key = 'lay_out_hide' AND lower(display_name) LIKE '%hide%')
    THEN RAISE EXCEPTION 'V436: lay_out_hide no longer calls itself a hide work; the keyword would mislead'; END IF;
END $$;
