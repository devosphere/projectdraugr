-- #77 / #218 — the ox's dung fouled the camp and the keeper's did not.
--
-- chunk_refuse is a fully wired consequence: refuse draws wildlife to the camp (WildlifeEncounterService), costs
-- the body condition, and lets pests dock the shelf life of stored food (FoodPreservationService). Three things
-- put refuse on the ground -- butchering (15, or 3 where an offal pit stands), kept livestock (4 a turn, or none
-- where a manure pit or compost bay stands), and a monster lair. Relieving yourself put none there at all. It
-- created a WASTE world_object at the Chronicle's feet, every time, for ever, and NOTHING has ever read one.
--
-- So a Chronicle could live on one chunk for a season, relieve themselves on it twice a day, and the ground stayed
-- as clean as the day they arrived -- while their oxen standing beside them fouled it. That is the asymmetry, and
-- it is also the reason the LATRINE's own purpose was hard to see: it halves the passive hygiene loss and drains
-- refuse at four an hour, but nothing a person did without one ever put refuse there to drain.
--
-- WHY A COLUMN AND NOT A FOURTH LITERAL. 'LATRINE' was written as a string in three separate readers -- the
-- hygiene decay, the refuse drain in the tick, and (now) this. That is how shelters_stock started, before V293
-- made it data and a byre, a fold, a sty and a barn all began resting stock without another literal to remember.
-- takes_relief is the same move made before the drift rather than after it: a privy, an earth closet or a
-- screened pit added later answers the question by being data, not by being remembered in three places.

ALTER TABLE construction_kind ADD COLUMN takes_relief BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.takes_relief IS
 'Whether this project takes a person''s own waste, so relieving yourself where it stands leaves nothing on the '
 'camp ground (#77/#218). The latrine is the first; the question is asked of the data so a privy or an earth '
 'closet added later needs no code.';

UPDATE construction_kind SET takes_relief = TRUE WHERE project_kind = 'LATRINE';

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM construction_kind WHERE takes_relief;
    IF n <> 1 THEN RAISE EXCEPTION 'V388: expected exactly one kind to take relief, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM construction_kind WHERE takes_relief AND project_kind='LATRINE';
    IF n <> 1 THEN RAISE EXCEPTION 'V388: the kind that takes relief must be the latrine'; END IF;

    -- The three consequences this hangs on must all still exist, or fouling the ground costs nothing and this
    -- migration has added a number that no part of the world reads.
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='chunk_refuse') THEN
        RAISE EXCEPTION 'V388: chunk_refuse is gone, so refuse has no consequence to attach to';
    END IF;
END $$;
