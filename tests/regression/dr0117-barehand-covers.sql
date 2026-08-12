-- Regression: bare-hand partial covers (M1 #195, EPIC #191, code). Read-only apart from a rolled-back seed.
--
-- The covers are placed in Java (ConstructionService.placeCover) and read back by the physiology tick and by
-- warmByFire/beddedAt through co-location queries. This pins the data contract those queries depend on: a placed
-- cover of each kind, co-located with the body, is detected; a cover somewhere else is not; and none of the four
-- new project_kind values is rejected by a CHECK. A break here means a placed cover silently does nothing, or an
-- insert that the Java path performs would fail at runtime.

BEGIN;

-- Two locations: the body stands at loc A; loc B is elsewhere.
INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES
 ('00000000-0000-0000-0000-0000000caf00','WORLD_ROOT','root','00000000-0000-0000-0000-0000000caf00'),
 ('00000000-0000-0000-0000-0000000cafa0','GEOGRAPHIC_CHUNK','locA','00000000-0000-0000-0000-0000000caf00'),
 ('00000000-0000-0000-0000-0000000cafb0','GEOGRAPHIC_CHUNK','locB','00000000-0000-0000-0000-0000000caf00'),
 ('00000000-0000-0000-0000-0000000cafbd','CHRONICLE_BODY','body','00000000-0000-0000-0000-0000000cafa0'),
 -- covers co-located with the body at loc A
 ('00000000-0000-0000-0000-0000000caf01','CONSTRUCTION','Sunshade',   '00000000-0000-0000-0000-0000000cafa0'),
 ('00000000-0000-0000-0000-0000000caf02','CONSTRUCTION','Rain cover', '00000000-0000-0000-0000-0000000cafa0'),
 ('00000000-0000-0000-0000-0000000caf03','CONSTRUCTION','Groundsheet','00000000-0000-0000-0000-0000000cafa0'),
 ('00000000-0000-0000-0000-0000000caf04','CONSTRUCTION','Stone ring', '00000000-0000-0000-0000-0000000cafa0'),
 ('00000000-0000-0000-0000-0000000caf05','CONSTRUCTION','Windbreak',  '00000000-0000-0000-0000-0000000cafa0'),
 -- a sunshade at the OTHER location, which must not be detected for this body
 ('00000000-0000-0000-0000-0000000caf06','CONSTRUCTION','Far sunshade','00000000-0000-0000-0000-0000000cafb0');

-- All four new kinds plus WINDBREAK insert cleanly (project_kind is free-form; the completed-state CHECK is met).
INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES
 ('00000000-0000-0000-0000-0000000caf01','SUNSHADE',   'COMPLETED',100,now()),
 ('00000000-0000-0000-0000-0000000caf02','RAIN_COVER', 'COMPLETED',100,now()),
 ('00000000-0000-0000-0000-0000000caf03','GROUNDSHEET','COMPLETED',100,now()),
 ('00000000-0000-0000-0000-0000000caf04','STONE_RING', 'COMPLETED',100,now()),
 ('00000000-0000-0000-0000-0000000caf05','WINDBREAK',  'COMPLETED',100,now()),
 ('00000000-0000-0000-0000-0000000caf06','SUNSHADE',   'COMPLETED',100,now());

DO $$
DECLARE body uuid := '00000000-0000-0000-0000-0000000cafbd';
BEGIN
    -- co-location detector, exactly as the physiology queries phrase it
    IF NOT (SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object o ON o.id=cp.object_id JOIN world_object b ON b.current_location_id=o.current_location_id WHERE b.id=body AND cp.project_kind='SUNSHADE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND o.lifecycle_state='ACTIVE')) THEN
        RAISE EXCEPTION 'COVER: a co-located SUNSHADE is not detected'; END IF;
    IF NOT (SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object o ON o.id=cp.object_id JOIN world_object b ON b.current_location_id=o.current_location_id WHERE b.id=body AND cp.project_kind='RAIN_COVER' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND o.lifecycle_state='ACTIVE')) THEN
        RAISE EXCEPTION 'COVER: a co-located RAIN_COVER is not detected'; END IF;
    IF NOT (SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object o ON o.id=cp.object_id JOIN world_object b ON b.current_location_id=o.current_location_id WHERE b.id=body AND cp.project_kind IN ('GROUND_BED','RAISED_SLEEPING_PLATFORM','GROUNDSHEET') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND o.lifecycle_state='ACTIVE')) THEN
        RAISE EXCEPTION 'COVER: a co-located GROUNDSHEET is not bedded'; END IF;
    IF NOT (SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object o ON o.id=cp.object_id JOIN world_object b ON b.current_location_id=o.current_location_id WHERE b.id=body AND cp.project_kind='STONE_RING' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND o.lifecycle_state='ACTIVE')) THEN
        RAISE EXCEPTION 'COVER: a co-located STONE_RING is not detected'; END IF;
    -- the far sunshade (loc B) must NOT count for a body at loc A
    IF (SELECT count(*) FROM construction_project cp JOIN world_object o ON o.id=cp.object_id JOIN world_object b ON b.current_location_id=o.current_location_id WHERE b.id=body AND cp.project_kind='SUNSHADE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND o.lifecycle_state='ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'COVER: co-location leaked across locations (a distant cover was counted)'; END IF;
    RAISE NOTICE 'PASS: all four covers + windbreak insert and are co-location-detected at the body; a distant cover is not (#195)';
END $$;

ROLLBACK;
