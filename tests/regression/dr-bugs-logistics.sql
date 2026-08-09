-- Regression: physical logistics — drop, store-in-container, and pick-up (#29 / #40 / #41).
--
-- These three share one model: an object keeps its UUID and history; only its owner/location/containment
-- change, and it stays ACTIVE and reachable throughout. This seeds a chronicle carrying a woven basket and a
-- field stone, then replays the EXACT queries/mutations that PhysicalItemService.storeInContainer, drop, and
-- pickUp run, asserting the object is never lost and is always retrievable. BEGIN/ROLLBACK — no residue.

BEGIN;

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a4000000-0000-0000-0000-000000000000', 'WORLD', 'Logistics World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a4000000-0000-0000-0000-000000000000', 5, 'regression', 8, 8);
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('b4000000-0000-0000-0000-000000000000', 'CHUNK', 'Camp', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome)
 VALUES ('b4000000-0000-0000-0000-000000000000', 'a4000000-0000-0000-0000-000000000000', 0, 0, 500, 500, 'GRASSLAND');
INSERT INTO world_object (id, object_type, display_name, current_location_id)
 VALUES ('c4000000-0000-0000-0000-000000000000', 'CHRONICLE', 'Hand', 'b4000000-0000-0000-0000-000000000000');
INSERT INTO chronicle (id, world_id, sequence_number, life_state, died_at, death_cause)
 VALUES ('c4000000-0000-0000-0000-000000000000', 'a4000000-0000-0000-0000-000000000000', 1, 'DEAD', now(), 'regression fixture');

-- A carried basket (container) and a carried field stone.
INSERT INTO world_object (id, object_type, display_name, current_owner_id)
 VALUES ('d4000000-0000-0000-0000-000000000000', 'ITEM', 'Woven basket', 'c4000000-0000-0000-0000-000000000000');
INSERT INTO item_instance (object_id, item_key) VALUES ('d4000000-0000-0000-0000-000000000000', 'woven_basket');
INSERT INTO container_properties (object_id, max_mass_grams, max_volume_ml) VALUES ('d4000000-0000-0000-0000-000000000000', 12000, 18000);
INSERT INTO world_object (id, object_type, display_name, current_owner_id)
 VALUES ('e4000000-0000-0000-0000-000000000000', 'ITEM', 'Field stone', 'c4000000-0000-0000-0000-000000000000');
INSERT INTO item_instance (object_id, item_key) VALUES ('e4000000-0000-0000-0000-000000000000', 'field_stone');

-- STORE: place the stone into the basket (containment insert + owner reparent). The trigger validates capacity.
INSERT INTO item_containment (item_id, container_id) VALUES ('e4000000-0000-0000-0000-000000000000', 'd4000000-0000-0000-0000-000000000000');
UPDATE world_object SET current_owner_id='d4000000-0000-0000-0000-000000000000', current_location_id=NULL WHERE id='e4000000-0000-0000-0000-000000000000';

DO $$
DECLARE reachable_stone int;
BEGIN
    -- The stone, now inside the carried basket, is still reachable from the chronicle via containment.
    WITH RECURSIVE reach(id) AS (
        SELECT id FROM world_object WHERE lifecycle_state='ACTIVE'
           AND (current_owner_id='c4000000-0000-0000-0000-000000000000'
                OR (current_owner_id IS NULL AND current_location_id='b4000000-0000-0000-0000-000000000000'))
        UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reach r ON r.id=ic.container_id
          JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE')
    SELECT count(*) INTO reachable_stone FROM reach WHERE id='e4000000-0000-0000-0000-000000000000';
    IF reachable_stone <> 1 THEN RAISE EXCEPTION 'REGRESSION: stored stone not reachable in the basket (#40)'; END IF;
    RAISE NOTICE 'PASS: store-in-container keeps the item reachable inside the basket (#40)';
END $$;

-- DROP the basket to the ground (owner cleared, located at the chunk). Its contents ride with it.
UPDATE world_object SET current_owner_id=NULL, current_location_id='b4000000-0000-0000-0000-000000000000' WHERE id='d4000000-0000-0000-0000-000000000000';

DO $$
DECLARE basket_active int; stone_active int; pickup_candidates int;
BEGIN
    SELECT count(*) INTO basket_active FROM world_object WHERE id='d4000000-0000-0000-0000-000000000000' AND lifecycle_state='ACTIVE' AND current_location_id='b4000000-0000-0000-0000-000000000000';
    SELECT count(*) INTO stone_active  FROM world_object WHERE id='e4000000-0000-0000-0000-000000000000' AND lifecycle_state='ACTIVE';
    IF basket_active <> 1 THEN RAISE EXCEPTION 'REGRESSION: dropped basket did not persist on the ground (#29/#41)'; END IF;
    IF stone_active  <> 1 THEN RAISE EXCEPTION 'REGRESSION: contents of a dropped basket were lost (#29)'; END IF;

    -- The pickUp candidate query: ground items here, plus items nested in a reachable container.
    WITH RECURSIVE reach(id) AS (
        SELECT id FROM world_object WHERE lifecycle_state='ACTIVE'
           AND (current_owner_id='c4000000-0000-0000-0000-000000000000'
                OR (current_owner_id IS NULL AND current_location_id='b4000000-0000-0000-0000-000000000000'))
        UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reach r ON r.id=ic.container_id
          JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE')
    SELECT count(*) INTO pickup_candidates
      FROM world_object w JOIN item_instance i ON i.object_id=w.id
     WHERE w.lifecycle_state='ACTIVE' AND (
        (w.current_owner_id IS NULL AND w.current_location_id='b4000000-0000-0000-0000-000000000000')
        OR w.id IN (SELECT c.item_id FROM item_containment c WHERE c.container_id IN (SELECT id FROM reach)));
    -- Both the grounded basket and the stone inside it are retrievable.
    IF pickup_candidates <> 2 THEN RAISE EXCEPTION 'REGRESSION: dropped basket + contents not both retrievable (got %) (#41)', pickup_candidates; END IF;
    RAISE NOTICE 'PASS: dropped basket persists on the ground with its contents, both pick-up-able (#29/#41)';
END $$;

-- PICK_UP the basket again (owner back to the chronicle). The stone stays inside it.
UPDATE world_object SET current_owner_id='c4000000-0000-0000-0000-000000000000', current_location_id=NULL WHERE id='d4000000-0000-0000-0000-000000000000';

DO $$
DECLARE still_nested int;
BEGIN
    SELECT count(*) INTO still_nested FROM item_containment WHERE item_id='e4000000-0000-0000-0000-000000000000' AND container_id='d4000000-0000-0000-0000-000000000000';
    IF still_nested <> 1 THEN RAISE EXCEPTION 'REGRESSION: picking the basket back up spilled its contents (#41)'; END IF;
    RAISE NOTICE 'PASS: picking the basket back up keeps its contents (#41)';
END $$;

ROLLBACK;
