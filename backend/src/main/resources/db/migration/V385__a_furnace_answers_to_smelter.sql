-- #37 — "build a smelter" reached nothing, and the bloomery is the gate on every metal in the game.
--
-- make_bloomery_furnace answers to "bloomery furnace", "smelting furnace" and "lay up a bloomery". It did not
-- answer to "smelter", which is what the thing is called by anyone who has not read the catalogue, nor to a bare
-- "furnace".
--
-- This matters more than a word usually does. smelt_copper and alloy_bronze both name bloomery_furnace as their
-- station, so without one there is no copper, no bronze, and nothing forged from either -- and the phrase a
-- player would reach for to build it reached nothing at all. The Java side of the same defect (CRAFT_WORKSTATION
-- claiming "build a bloomery furnace" and answering with a workbench's branches and fibre) is fixed alongside
-- this migration.
--
-- Only the two BARE words are added, and that is a budget rather than a preference: material_process.keywords
-- is varchar(200) and this row was already at 174. "smelter" and "furnace" cost sixteen characters and are
-- enough, because a keyword needs only to be FOUND in what the player types -- "build a smelter" contains
-- "smelter". Spelling out every verb form would have overflowed the column, which is worth knowing before the
-- next migration of this kind.
UPDATE material_process SET keywords = keywords || ',smelter,furnace'
 WHERE process_key = 'make_bloomery_furnace'
   AND ',' || keywords || ',' NOT LIKE '%,smelter,%';

INSERT INTO process_subject (process_key, subject_term) VALUES
 ('make_bloomery_furnace', 'smelter'), ('make_bloomery_furnace', 'furnace')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'make_bloomery_furnace' AND ',' || keywords || ',' LIKE '%,smelter,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V385: the furnace still does not answer to smelter'; END IF;

    -- The LINED bloomery is a separate, better furnace and must stay separately askable: naming it must not
    -- become a question between the two, which the longest-keyword rule guarantees only while it keeps a
    -- keyword longer than anything added here.
    SELECT COUNT(*) INTO n FROM material_process mp
     WHERE mp.process_key = 'make_lined_bloomery_furnace'
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k
                       WHERE length(trim(k)) > length('make a smelter'));
    IF n > 0 THEN RAISE EXCEPTION 'V385: the lined bloomery can no longer be named more precisely'; END IF;

    -- And the station the whole metal chain turns on must still be the one these processes name, or this has
    -- pointed the words at the wrong furnace.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key IN ('smelt_copper', 'alloy_bronze') AND station_kind = 'bloomery_furnace';
    IF n <> 2 THEN RAISE EXCEPTION 'V385: the metal chain no longer turns on the bloomery'; END IF;
END $$;
