-- Regression: playtest bug fixes — net/belt craftables (#35/#36/#43/#44) and mark-and-name persistence (#39).
--
-- 1. The finished objects a core first-era craft must be able to produce exist in the catalogue with the
--    right shape: a fishing net and landing net (V71) and the primitive utility belt (V47), each equippable
--    with a sensible body slot. The recipes themselves are explicit craft intents in ChronicleActionService;
--    this pins the DATA those intents create.
-- 2. Carving a mark AND naming the place in one act must not raise a raw persistence error — before the fix
--    markLandmark used ON CONFLICT (chronicle_id, chunk_id), which stopped matching any constraint once V70
--    relaxed the PK to (chronicle_id, chunk_id, name). This replays the exact upsert with the corrected
--    conflict target, twice (a re-mark of the same name), and proves it is idempotent, not an error.
-- BEGIN/ROLLBACK — no residue.

BEGIN;

DO $$
DECLARE nets int; belt int; belt_slot int; net_slots int;
BEGIN
    SELECT count(*) INTO nets FROM item_definition WHERE item_key IN ('fishing_net','landing_net') AND category='TOOL' AND equippable;
    SELECT count(*) INTO belt FROM item_definition WHERE item_key='utility_belt' AND equippable;
    SELECT count(*) INTO belt_slot FROM item_equipment_compatibility WHERE item_key='utility_belt' AND body_position='WAIST';
    SELECT count(*) INTO net_slots FROM item_equipment_compatibility WHERE item_key IN ('fishing_net','landing_net');

    IF nets <> 2 THEN RAISE EXCEPTION 'REGRESSION: fishing_net/landing_net craftables missing or wrong shape (got %)', nets; END IF;
    IF belt <> 1 THEN RAISE EXCEPTION 'REGRESSION: utility_belt craftable missing (got %)', belt; END IF;
    IF belt_slot < 1 THEN RAISE EXCEPTION 'REGRESSION: utility_belt has no waist equip slot'; END IF;
    IF net_slots < 3 THEN RAISE EXCEPTION 'REGRESSION: nets have no carry/equip slots (got %)', net_slots; END IF;
    RAISE NOTICE 'PASS: net(%)+belt craftables present and equippable (#35/#36/#43/#44)', nets;
END $$;

-- Marking + naming a place: seed a minimal world/chunk/chronicle, then replay the corrected upsert.
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a3000000-0000-0000-0000-000000000000', 'WORLD', 'Mark World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a3000000-0000-0000-0000-000000000000', 4, 'regression', 8, 8);
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('b3000000-0000-0000-0000-000000000001', 'CHUNK', 'Camp chunk', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome)
 VALUES ('b3000000-0000-0000-0000-000000000001', 'a3000000-0000-0000-0000-000000000000', 0, 0, 500, 500, 'GRASSLAND');
INSERT INTO world_object (id, object_type, display_name, current_location_id)
 VALUES ('c3000000-0000-0000-0000-000000000000', 'CHRONICLE', 'Marker', 'b3000000-0000-0000-0000-000000000001');
INSERT INTO chronicle (id, world_id, sequence_number, life_state, died_at, death_cause)
 VALUES ('c3000000-0000-0000-0000-000000000000', 'a3000000-0000-0000-0000-000000000000', 1, 'DEAD', now(), 'regression fixture');

-- The corrected markLandmark upsert (ON CONFLICT on the full relaxed key). Run twice for the SAME name
-- (a re-mark) and once for a SECOND name on the same chunk — none may error.
INSERT INTO chronicle_named_location (chronicle_id, chunk_id, name, purpose_tag, designated_at, source_action_id, memorized, last_visited_at)
 VALUES ('c3000000-0000-0000-0000-000000000000','b3000000-0000-0000-0000-000000000001','Camp Site',NULL,now(),NULL,true,now())
 ON CONFLICT (chronicle_id, chunk_id, name) DO UPDATE SET designated_at=EXCLUDED.designated_at,
   source_action_id=EXCLUDED.source_action_id, memorized=chronicle_named_location.memorized OR EXCLUDED.memorized,
   last_visited_at=EXCLUDED.last_visited_at;
INSERT INTO chronicle_named_location (chronicle_id, chunk_id, name, purpose_tag, designated_at, source_action_id, memorized, last_visited_at)
 VALUES ('c3000000-0000-0000-0000-000000000000','b3000000-0000-0000-0000-000000000001','Camp Site',NULL,now(),NULL,false,now())
 ON CONFLICT (chronicle_id, chunk_id, name) DO UPDATE SET designated_at=EXCLUDED.designated_at,
   source_action_id=EXCLUDED.source_action_id, memorized=chronicle_named_location.memorized OR EXCLUDED.memorized,
   last_visited_at=EXCLUDED.last_visited_at;
INSERT INTO chronicle_named_location (chronicle_id, chunk_id, name, purpose_tag, designated_at, source_action_id, memorized, last_visited_at)
 VALUES ('c3000000-0000-0000-0000-000000000000','b3000000-0000-0000-0000-000000000001','Water Draw',NULL,now(),NULL,false,now())
 ON CONFLICT (chronicle_id, chunk_id, name) DO UPDATE SET designated_at=EXCLUDED.designated_at,
   source_action_id=EXCLUDED.source_action_id, memorized=chronicle_named_location.memorized OR EXCLUDED.memorized,
   last_visited_at=EXCLUDED.last_visited_at;

DO $$
DECLARE names int; camp_memorized boolean;
BEGIN
    SELECT count(*) INTO names FROM chronicle_named_location
     WHERE chronicle_id='c3000000-0000-0000-0000-000000000000' AND chunk_id='b3000000-0000-0000-0000-000000000001';
    -- The re-mark must have upserted, not duplicated, and OR-merged the memorized flag to TRUE.
    SELECT memorized INTO camp_memorized FROM chronicle_named_location
     WHERE chronicle_id='c3000000-0000-0000-0000-000000000000' AND chunk_id='b3000000-0000-0000-0000-000000000001' AND name='Camp Site';

    IF names <> 2 THEN RAISE EXCEPTION 'REGRESSION: mark-and-name did not upsert cleanly (got % rows)', names; END IF;
    IF camp_memorized IS NOT TRUE THEN RAISE EXCEPTION 'REGRESSION: re-mark lost the memorized flag'; END IF;
    RAISE NOTICE 'PASS: mark-and-name is idempotent on the relaxed key, no raw persistence error (#39)';
END $$;

ROLLBACK;
