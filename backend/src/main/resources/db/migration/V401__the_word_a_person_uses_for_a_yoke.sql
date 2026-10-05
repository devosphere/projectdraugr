-- #37 — "carve a yoke"
--
-- `carve a yoke` reached the MARKING intent — *"You set a hand to the bark, but with no blade…"* — because
-- make_draft_yoke declares `make a yoke`, `shape a yoke`, `ox yoke` and `ox-yoke`, and not `carve`. The Java
-- rule that claims a bare "carve" for blazing a trail is gated on `!actionMatchesProcess`, so it was RIGHT to
-- take the sentence: no process answered to it. Give the process the word and the gate does the rest.
--
-- Keywords only. No new process, no new item: the recipe already exists and already works from the words a
-- maker would use. Measured after: `carve a yoke` reaches PROCESS_MATERIAL and refuses for want of wooden
-- components, which is the true answer.
--
-- A NOTE ON WHAT IS NOT HERE. `brew something hot` and `make a hot drink` were in this migration and have been
-- taken out again, because the keywords could not be reached and this project does not ship words that reach
-- nothing. ProcessMatcher skips any candidate whose category is not the one the sentence classified to;
-- brew_infusion is PROCESS, while "make" classifies CRAFT and "drink" classifies INHABIT. Adding `brew`,
-- `steep` and `infuse` as PROCESS terms did not move it either — measured, and none of the five phrasings
-- changed. That is the CRAFT-vs-PROCESS gate, and it wants a slice of its own rather than five dead rows here.

UPDATE material_process
   SET keywords = keywords || ',carve a yoke,carve an ox-yoke,cut a yoke'
 WHERE process_key = 'make_draft_yoke'
   AND keywords NOT LIKE '%carve a yoke%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key='make_draft_yoke' AND lower(keywords) LIKE '%carve a yoke%';
    IF n <> 1 THEN RAISE EXCEPTION 'V401: the yoke must answer to being carved'; END IF;

    -- A keyword two VERIFIED processes both claim is a tie the matcher settles by length, which is how "carve a
    -- bowl" once got soapstone for a wooden holder. These three are new words in a catalogue of thousands.
    SELECT string_agg(k, ', ') INTO bad FROM (
        VALUES ('carve a yoke'), ('carve an ox-yoke'), ('cut a yoke')
    ) AS added(k)
    WHERE (SELECT COUNT(*) FROM material_process mp
            WHERE mp.review_state='VERIFIED'
              AND ',' || lower(mp.keywords) || ',' LIKE '%,' || added.k || ',%') > 1;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V401: these keywords are now claimed by more than one process: %', bad;
    END IF;

    -- And the yoke must still be makeable, or the new words reach a dead end.
    IF NOT EXISTS (SELECT 1 FROM material_process mp JOIN item_definition d ON d.item_key=mp.output_item_key
                    WHERE mp.process_key='make_draft_yoke') THEN
        RAISE EXCEPTION 'V401: make_draft_yoke has no output item';
    END IF;

    -- The process must be in the category a sentence about carving classifies to, or the keyword is added and
    -- the gate refuses it anyway — which is exactly what happened to the hot-drink words taken out above.
    SELECT mp.category_key INTO bad FROM material_process mp WHERE mp.process_key='make_draft_yoke';
    IF NOT EXISTS (SELECT 1 FROM category_term ct WHERE ct.category_key=bad AND ct.term='carve') THEN
        RAISE EXCEPTION 'V401: make_draft_yoke is %, and "carve" is not a term of it, so the gate would refuse the new keyword', bad;
    END IF;
END $$;
