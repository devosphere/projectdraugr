-- Regression: carried fresh kill draws predators (M1 #123/#127, code). Read-only apart from a rolled-back seed.
--
-- passiveEncounter raises the ambush chance (+10) when the Chronicle carries a raw kill (raw_game_meat or
-- raw_fish) — blood and scent that draw a predator in. Cooking, storing, or caching the meat removes it from the
-- body and ends the draw. This pins the ownership contract that hook reads: a body carrying raw meat is detected,
-- one carrying only cooked/other food is not.

BEGIN;

INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES
 ('00000000-0000-0000-0000-00000f00d000','WORLD_ROOT','root','00000000-0000-0000-0000-00000f00d000'),
 ('00000000-0000-0000-0000-00000f00da00','GEOGRAPHIC_CHUNK','loc','00000000-0000-0000-0000-00000f00d000'),
 ('00000000-0000-0000-0000-00000f00db00','CHRONICLE_BODY','hunter','00000000-0000-0000-0000-00000f00da00'),
 ('00000000-0000-0000-0000-00000f00dc00','CHRONICLE_BODY','cook','00000000-0000-0000-0000-00000f00da00');
-- the hunter carries a raw kill; the cook carries only cooked meat
INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES
 ('00000000-0000-0000-0000-00000f00d001','ITEM','Raw game meat','00000000-0000-0000-0000-00000f00db00'),
 ('00000000-0000-0000-0000-00000f00d002','ITEM','Cooked game meat','00000000-0000-0000-0000-00000f00dc00');
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES
 ('00000000-0000-0000-0000-00000f00d001','raw_game_meat','SOUND'),
 ('00000000-0000-0000-0000-00000f00d002','cooked_game_meat','SOUND');

DO $$
DECLARE hunter uuid := '00000000-0000-0000-0000-00000f00db00'; cook uuid := '00000000-0000-0000-0000-00000f00dc00';
BEGIN
    IF NOT (SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=hunter AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('raw_game_meat','raw_fish'))) THEN
        RAISE EXCEPTION 'DRAW: a carried raw kill was not detected on the hunter'; END IF;
    IF (SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=cook AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('raw_game_meat','raw_fish'))) THEN
        RAISE EXCEPTION 'DRAW: cooked meat wrongly counted as a fresh kill (the draw must end when the meat is cooked/stored)'; END IF;
    RAISE NOTICE 'PASS: a carried raw kill draws predators; cooked/stored meat does not (#123/#127)';
END $$;

ROLLBACK;
