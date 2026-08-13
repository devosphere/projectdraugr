-- Regression: camp alarm early-warning (M1 #126 / #123 cat.7, code). Read-only apart from a rolled-back seed.
--
-- The camp alarm is placed in Java (ConstructionService.placeCover, kind CAMP_ALARM) and read by
-- WildlifeEncounterService.passiveEncounter to rob an ambush of its surprise (chance -15). This pins the data
-- contract that hook depends on: a CAMP_ALARM co-located with the body (same current_location_id) is detected,
-- and one elsewhere is not — exactly as passiveEncounter phrases it. A break here means a placed alarm silently
-- stops cutting the ambush chance.

BEGIN;

INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES
 ('00000000-0000-0000-0000-00000a1a2000','WORLD_ROOT','root','00000000-0000-0000-0000-00000a1a2000'),
 ('00000000-0000-0000-0000-00000a1a2a00','GEOGRAPHIC_CHUNK','locA','00000000-0000-0000-0000-00000a1a2000'),
 ('00000000-0000-0000-0000-00000a1a2b00','GEOGRAPHIC_CHUNK','locB','00000000-0000-0000-0000-00000a1a2000'),
 ('00000000-0000-0000-0000-00000a1a2a01','CONSTRUCTION','Camp alarm','00000000-0000-0000-0000-00000a1a2a00'),
 ('00000000-0000-0000-0000-00000a1a2b01','CONSTRUCTION','Far camp alarm','00000000-0000-0000-0000-00000a1a2b00');
INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES
 ('00000000-0000-0000-0000-00000a1a2a01','CAMP_ALARM','COMPLETED',100,now()),
 ('00000000-0000-0000-0000-00000a1a2b01','CAMP_ALARM','COMPLETED',100,now());

DO $$
DECLARE locA uuid := '00000000-0000-0000-0000-00000a1a2a00';
BEGIN
    -- passiveEncounter passes the chronicle's location as the chunk; the alarm at that location must be detected.
    IF NOT (SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=locA AND cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')) THEN
        RAISE EXCEPTION 'ALARM: a co-located CAMP_ALARM was not detected'; END IF;
    -- exactly one alarm at loc A — the loc-B alarm must not leak in.
    IF (SELECT count(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=locA AND cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'ALARM: alarm detection leaked across locations'; END IF;
    RAISE NOTICE 'PASS: a co-located camp alarm is detected (cuts the ambush chance); a distant one is not (#126)';
END $$;

ROLLBACK;
