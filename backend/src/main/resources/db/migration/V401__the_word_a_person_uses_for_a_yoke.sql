-- #37 — "carve a yoke" and "make a hot drink"
--
-- Two made things the catalogue can make, which the plainest words for them could not reach. Both are the
-- generic-word defect: the recipe names itself the way a craftsman would, and the player says it another way.
--
-- CARVE A YOKE reached the MARKING intent — "You set a hand to the bark, but with no blade…" — because
-- make_draft_yoke declares `make a yoke`, `shape a yoke`, `ox yoke` and `ox-yoke`, and not `carve`. The Java
-- rule that claims a bare "carve" for blazing a trail is gated on `!actionMatchesProcess`, so it was RIGHT to
-- take the sentence: no process answered to it. Give the process the word and the gate does the rest.
--
-- MAKE A HOT DRINK reached DRINK and was answered by looking round for water to swallow — a making request
-- answered by drinking. BREW SOMETHING HOT reached nothing at all, while `brew a tea` reached the infusion.
--
-- AND THE REASON WAS THE SUBJECT, NOT THE CATEGORY. I recorded the category gate first and was wrong: the
-- category is already a HINT rather than a gate (ProcessMatcher.resolveAndRecord falls back to the whole
-- catalogue when the guessed category answers nothing, since #721). What refuses a sentence is the SUBJECT —
-- "right work, right verb, wrong material", in the matcher's own words. brew_infusion's subjects were
-- `infusion` and `tea`, and "a hot drink" names neither: it names a PROPERTY of the thing rather than the
-- thing. Adding `brew`, `steep` and `infuse` as PROCESS category terms changed nothing, measured, because the
-- category was never what was stopping it.
--
-- A hot drink is what an infusion IS, so it earns its place as a subject. With the two subject rows the
-- sentences resolve and refuse truthfully — "This work needs heat, and none is within reach of it" — while
-- `take a drink` and the bare `hot drink` stay DRINK, which is a person asking to swallow one rather than
-- make it.
--
-- Keywords and subjects only. No new process, no new item: both recipes already exist and already work from
-- the words a maker would use.

UPDATE material_process
   SET keywords = keywords || ',carve a yoke,carve an ox-yoke,cut a yoke'
 WHERE process_key = 'make_draft_yoke'
   AND keywords NOT LIKE '%carve a yoke%';

UPDATE material_process
   SET keywords = keywords || ',hot drink,brew something hot,make a hot drink'
 WHERE process_key = 'brew_infusion'
   AND keywords NOT LIKE '%hot drink%';

INSERT INTO process_subject (process_key, subject_term)
VALUES ('brew_infusion', 'hot drink'), ('brew_infusion', 'something hot')
ON CONFLICT DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key='make_draft_yoke' AND lower(keywords) LIKE '%carve a yoke%';
    IF n <> 1 THEN RAISE EXCEPTION 'V401: the yoke must answer to being carved'; END IF;

    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key='brew_infusion' AND lower(keywords) LIKE '%hot drink%';
    IF n <> 1 THEN RAISE EXCEPTION 'V401: the infusion must answer to a hot drink'; END IF;

    -- A KEYWORD WITHOUT A SUBJECT IS AN UNREACHABLE WORD. The matcher wants both, so adding the one without
    -- the other ships a phrase that can never resolve — which is what the first cut of this migration did, and
    -- what the measurement caught. Guard it, because the next person to add a family word will hit the same.
    SELECT COUNT(*) INTO n FROM process_subject
     WHERE process_key='brew_infusion' AND subject_term IN ('hot drink','something hot');
    IF n <> 2 THEN
        RAISE EXCEPTION 'V401: the hot-drink keywords need their subject terms, or the matcher refuses them for want of a material';
    END IF;

    -- A keyword two VERIFIED processes both claim is a tie the matcher settles by length, which is how "carve a
    -- bowl" once got soapstone for a wooden holder. These are new words in a catalogue of thousands.
    SELECT string_agg(k, ', ') INTO bad FROM (
        VALUES ('carve a yoke'), ('carve an ox-yoke'), ('cut a yoke'),
               ('hot drink'), ('brew something hot'), ('make a hot drink')
    ) AS added(k)
    WHERE (SELECT COUNT(*) FROM material_process mp
            WHERE mp.review_state='VERIFIED'
              AND ',' || lower(mp.keywords) || ',' LIKE '%,' || added.k || ',%') > 1;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V401: these keywords are now claimed by more than one process: %', bad;
    END IF;

    -- And each thing must still be makeable, or the new words reach a dead end.
    SELECT string_agg(p, ', ') INTO bad FROM (VALUES ('make_draft_yoke'), ('brew_infusion')) AS k(p)
     WHERE NOT EXISTS (SELECT 1 FROM material_process mp JOIN item_definition d ON d.item_key=mp.output_item_key
                        WHERE mp.process_key=k.p);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V401: no output item for: %', bad; END IF;
END $$;
