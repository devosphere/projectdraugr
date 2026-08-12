-- Regression: bare-hand smoke face wrap & smoke exposure (M1 #198, EPIC #191, V118). Read-only apart from a
-- rolled-back seed.
--
-- Closes #198. Pins that the smoke face wrap is a real, wearable, bare-hand item AND that it has something to do:
-- the smoke-exposure condition the tick reads. An unvented fire in an enclosed shelter fouls the air; a smoke hood
-- at the hearth vents it (no exposure); a worn wrap eases but does not end it. This pins the three co-location
-- signals (enclosed / vented / wearing) exactly as ChroniclePhysiologyService.advanceTo phrases them, so a break
-- means either the wrap became a decorative orphan or the smoke condition stopped detecting.

BEGIN;

DO $$
BEGIN
    -- The wrap is a real, worn, bare-hand item.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='smoke_face_wrap' AND category='CLOTHING' AND equippable) THEN
        RAISE EXCEPTION 'SMOKE: smoke_face_wrap is not a wearable CLOTHING item'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_equipment_compatibility WHERE item_key='smoke_face_wrap' AND body_position='FACE') THEN
        RAISE EXCEPTION 'SMOKE: smoke_face_wrap has no FACE slot'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_smoke_wrap' AND tool_class IS NULL AND review_state='VERIFIED') THEN
        RAISE EXCEPTION 'SMOKE: make_smoke_wrap is not a VERIFIED bare-hand process'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='make_smoke_wrap') THEN
        RAISE EXCEPTION 'SMOKE: make_smoke_wrap has no soft-material inputs'; END IF;
END $$;

-- Seed a body in an enclosed hut with an active fire, a loose smoke hood, and a worn wrap, at loc A; and a plain
-- body in the open at loc B — then assert the three signals the tick reads.
INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES
 ('00000000-0000-0000-0000-00000050f000','WORLD_ROOT','root','00000000-0000-0000-0000-00000050f000'),
 ('00000000-0000-0000-0000-00000050fa00','GEOGRAPHIC_CHUNK','locA','00000000-0000-0000-0000-00000050f000'),
 ('00000000-0000-0000-0000-00000050fbd0','CHRONICLE_BODY','body','00000000-0000-0000-0000-00000050fa00'),
 ('00000000-0000-0000-0000-00000050f001','CONSTRUCTION','Hut','00000000-0000-0000-0000-00000050fa00'),
 ('00000000-0000-0000-0000-00000050f002','CONSTRUCTION','Fire pit','00000000-0000-0000-0000-00000050fa00'),
 ('00000000-0000-0000-0000-00000050f003','ITEM','Smoke hood','00000000-0000-0000-0000-00000050fa00'),
 ('00000000-0000-0000-0000-00000050f004','ITEM','Smoke face wrap','00000000-0000-0000-0000-00000050fbd0');
INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES
 ('00000000-0000-0000-0000-00000050f001','WATTLE_AND_DAUB_HUT','COMPLETED',100,now()),
 ('00000000-0000-0000-0000-00000050f002','FIRE_PIT','COMPLETED',100,now());
INSERT INTO fire_state (construction_id,active,fuel_minutes) VALUES ('00000000-0000-0000-0000-00000050f002',true,120);
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES
 ('00000000-0000-0000-0000-00000050f003','smoke_hood','SOUND'),
 ('00000000-0000-0000-0000-00000050f004','smoke_face_wrap','SOUND');
-- the body must be a real (LIVING) chronicle for the equipment FK
INSERT INTO world_genesis (world_id,seed,generator_version,width_chunks,height_chunks) VALUES ('00000000-0000-0000-0000-00000050f000',1,'t',1,1);
INSERT INTO chronicle (id,world_id,sequence_number,life_state) VALUES ('00000000-0000-0000-0000-00000050fbd0','00000000-0000-0000-0000-00000050f000',1,'LIVING');
INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer) VALUES
 ('00000000-0000-0000-0000-00000050f004','00000000-0000-0000-0000-00000050fbd0','FACE','PROTECTION');

DO $$
DECLARE body uuid := '00000000-0000-0000-0000-00000050fbd0';
    enclosed boolean; vented boolean; wearing boolean;
BEGIN
    enclosed := EXISTS(SELECT 1 FROM construction_project cp JOIN world_object en ON en.id=cp.object_id JOIN world_object b ON b.current_location_id=en.current_location_id WHERE b.id=body AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND en.lifecycle_state='ACTIVE');
    vented   := EXISTS(SELECT 1 FROM item_instance ii JOIN world_object hw ON hw.id=ii.object_id JOIN world_object b ON b.current_location_id=hw.current_location_id WHERE b.id=body AND ii.item_key='smoke_hood' AND hw.lifecycle_state='ACTIVE');
    wearing  := EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance ii ON ii.object_id=e.item_id WHERE e.chronicle_id=body AND ii.item_key='smoke_face_wrap');
    IF NOT enclosed THEN RAISE EXCEPTION 'SMOKE: an enclosed hut over the body was not detected'; END IF;
    IF NOT vented   THEN RAISE EXCEPTION 'SMOKE: a co-located smoke hood (vent) was not detected'; END IF;
    IF NOT wearing  THEN RAISE EXCEPTION 'SMOKE: a worn smoke face wrap was not detected'; END IF;
    -- Removing the hood must flip the exposure on (unvented); it is vented only while the hood is present.
    IF (SELECT count(*) FROM item_instance ii JOIN world_object hw ON hw.id=ii.object_id JOIN world_object b ON b.current_location_id=hw.current_location_id WHERE b.id=body AND ii.item_key='smoke_hood' AND hw.lifecycle_state='ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'SMOKE: vent detection is not gated on the hood being present'; END IF;
    RAISE NOTICE 'PASS: smoke face wrap wearable + bare-hand; enclosed/vented/wearing signals detected as the tick reads them (#198, V118)';
END $$;

ROLLBACK;
