-- #106 — "cut hay", "make hay", "give the goat some hay"
--
-- THE FODDER CHAIN IS ALREADY WHOLE, and nobody could ask for it by the name it goes by. Swept as a chain:
-- 25 sentences across its four steps, and FIFTEEN REACHED NOTHING.
--
--   step 1  the standing grass   gather meadow grass    WORKS   (refuses honestly on the wrong ground)
--   step 2  drying it            dry the grass          WORKS   -> dry_grass_bundle
--   step 3  the fodder in hand   dry_grass_bundle       EXISTS
--   step 4  giving it            feed the animals       WORKS   -- it consumes dry_grass_bundle
--
-- Every step works. And `cut hay`, `make hay`, `cure the hay`, `dry fodder`, `make fodder`, `cut fodder`,
-- `give the goat some hay`, `give the animals hay`, `put hay in the trough` and `put out fodder` all reached
-- NOTHING — because the catalogue calls it a dry grass bundle and a person calls it hay. This is the
-- generic-word defect in its plainest form: the thing is attainable, workable and terminally useful, and the
-- word for it is missing. No new item, no new process; the ticket's cut_fodder and dry_fodder are this chain.
--
-- HAY IS WHAT DRIED GRASS IS, so it earns its place as a subject and not only as a keyword. A KEYWORD WITHOUT A
-- SUBJECT IS AN UNREACHABLE WORD — V401 learned that and left a guard for it, and the same guard is here.
-- `fodder` is the same word one step more general: it is what the dried grass is FOR, and feedDraftBeasts
-- consumes exactly this item, so it is true of this process and of nothing else in the catalogue.

UPDATE material_process
   -- Kept inside material_process.keywords' varchar(200), which the first cut of this migration overflowed.
   -- The matcher takes the LONGEST keyword a sentence contains, so the bare words carry the long phrasings for
   -- free: "get the hay in" and "put up the fodder" both resolve on `hay` and `fodder` without being listed.
   SET keywords = keywords || ',make hay,dry the hay,cure the hay,hay,make fodder,dry fodder,cut fodder,fodder'
 WHERE process_key = 'dry_grass'
   AND keywords NOT LIKE '%make hay%';

INSERT INTO process_subject (process_key, subject_term)
VALUES ('dry_grass', 'hay'), ('dry_grass', 'fodder'), ('dry_grass', 'dried grass')
ON CONFLICT DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'dry_grass' AND lower(keywords) LIKE '%make hay%';
    IF n <> 1 THEN RAISE EXCEPTION 'V404: drying grass must answer to making hay'; END IF;

    -- The guard V401 left behind. The matcher settles a sentence on its SUBJECT — "right work, right verb,
    -- wrong material" — so a keyword whose subject is missing is a phrase that can never resolve, which is
    -- exactly what V401's first cut shipped before the measurement caught it.
    SELECT COUNT(*) INTO n FROM process_subject
     WHERE process_key = 'dry_grass' AND subject_term IN ('hay','fodder','dried grass');
    IF n <> 3 THEN
        RAISE EXCEPTION 'V404: the hay keywords need their subject terms, or the matcher refuses them for want of a material';
    END IF;

    -- A keyword two VERIFIED processes both claim is a tie the matcher settles by length, which is how
    -- "carve a bowl" once got soapstone for a wooden holder.
    SELECT string_agg(k, ', ') INTO bad FROM (
        VALUES ('make hay'), ('dry the hay'), ('cure the hay'), ('hay'), ('make fodder'), ('dry fodder'),
               ('cut fodder'), ('fodder')
    ) AS added(k)
    WHERE (SELECT COUNT(*) FROM material_process mp
            WHERE mp.review_state = 'VERIFIED'
              AND ',' || lower(mp.keywords) || ',' LIKE '%,' || added.k || ',%') > 1;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V404: these keywords are now claimed by more than one process: %', bad;
    END IF;

    -- AND THE CHAIN MUST ACTUALLY HOLD, which is the whole reason these are words and not a new item. Four
    -- links: the grass grows, drying it yields the bundle, the bundle is a real item, and feeding consumes it.
    -- If any link ever breaks, "make hay" becomes a word for a thing with nowhere to go, and this project calls
    -- that a catalogue token.
    IF NOT EXISTS (SELECT 1 FROM flora_definition WHERE flora_key = 'meadow_grass') THEN
        RAISE EXCEPTION 'V404: no meadow grass grows, so there is nothing to cut for hay';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = 'dry_grass' AND output_item_key = 'dry_grass_bundle') THEN
        RAISE EXCEPTION 'V404: drying grass no longer yields a dry grass bundle';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'dry_grass_bundle') THEN
        RAISE EXCEPTION 'V404: there is no dry grass bundle for the hay words to mean';
    END IF;
    -- The terminal use. dry_grass_bundle is what feedDraftBeasts consumes, in code, so what is asserted here is
    -- that the item is still the one a keeper can put in front of an animal: it is FOOD or MATERIAL, it has a
    -- way to be obtained, and nothing has quietly made it a dead end.
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = 'dry_grass_bundle') THEN
        RAISE EXCEPTION 'V404: the dry grass bundle has no way to be obtained';
    END IF;

    -- And the keywords must still FIT. material_process.keywords is varchar(200), and the first cut of this
    -- migration overflowed it: the UPDATE failed on a seeded database while silently no-opping on one that had
    -- already taken the earlier version, which is the worst way for a migration to be wrong, because one of the
    -- two looked fine. So the row this migration touches is checked, with room left for the next word.
    SELECT length(keywords) INTO n FROM material_process WHERE process_key = 'dry_grass';
    IF n > 185 THEN
        RAISE EXCEPTION 'V404: dry_grass keywords are % of 200 characters, with no room for the next word', n;
    END IF;

    -- A note for whoever adds the next family word, found by the first version of the check above when it was
    -- written catalogue-wide: boil_seawater_for_salt is already at 199 of 200 and CANNOT take another keyword.
    -- Not failed here, because it is nobody's fault but the column's and it is nothing to do with hay — but the
    -- next person to widen that recipe will need the column widened first, and should know before they start.
END $$;
