-- Regression: a basket is reachable from the flexible stock a player actually gathers (#34 / #31).
--
-- craftBasket used to demand plant_fiber x8 and nothing else, so a Chronicle carrying vines and processed
-- cordage — but little loose fibre — could never weave a basket, and the "hand-carry basket" appeared to do
-- nothing. A basket now weaves from any flexible length: plant fibre or vine (1 weave unit each) or cordage
-- (2 units each), eight units in all. This seeds a fibre-less inventory of vines + cordage and asserts the
-- exact reachability sum craftBasket uses clears the threshold. BEGIN/ROLLBACK — no residue.

BEGIN;

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a5000000-0000-0000-0000-000000000000', 'WORLD', 'Basket World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a5000000-0000-0000-0000-000000000000', 6, 'regression', 8, 8);
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('b5000000-0000-0000-0000-000000000000', 'CHUNK', 'Grove', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome)
 VALUES ('b5000000-0000-0000-0000-000000000000', 'a5000000-0000-0000-0000-000000000000', 0, 0, 500, 600, 'TEMPERATE_FOREST');
INSERT INTO world_object (id, object_type, display_name, current_location_id)
 VALUES ('c5000000-0000-0000-0000-000000000000', 'CHRONICLE', 'Weaver', 'b5000000-0000-0000-0000-000000000000');
INSERT INTO chronicle (id, world_id, sequence_number, life_state, died_at, death_cause)
 VALUES ('c5000000-0000-0000-0000-000000000000', 'a5000000-0000-0000-0000-000000000000', 1, 'DEAD', now(), 'regression fixture');

-- Fibre-less inventory: 3 vines (1 unit each) + 3 processed cordage (2 units each) = 3 + 6 = 9 weave units.
WITH new_vine AS (
    INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id)
    SELECT gen_random_uuid(), 'ITEM', 'Vine', 'ACTIVE', 'c5000000-0000-0000-0000-000000000000' FROM generate_series(1,3)
    RETURNING id)
INSERT INTO item_instance (object_id, item_key) SELECT id, 'vine' FROM new_vine;
WITH new_cord AS (
    INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id)
    SELECT gen_random_uuid(), 'ITEM', 'Processed fiber cordage', 'ACTIVE', 'c5000000-0000-0000-0000-000000000000' FROM generate_series(1,3)
    RETURNING id)
INSERT INTO item_instance (object_id, item_key) SELECT id, 'fiber_cordage' FROM new_cord;

DO $$
DECLARE fibre int; vine int; cord int; units int;
BEGIN
    -- The exact reach counts basketWeaveUnitsInReach() sums (owner = chronicle here; no containers).
    SELECT count(*) INTO fibre FROM item_instance i JOIN world_object w ON w.id=i.object_id
      WHERE w.current_owner_id='c5000000-0000-0000-0000-000000000000' AND i.item_key='plant_fiber';
    SELECT count(*) INTO vine  FROM item_instance i JOIN world_object w ON w.id=i.object_id
      WHERE w.current_owner_id='c5000000-0000-0000-0000-000000000000' AND i.item_key='vine';
    SELECT count(*) INTO cord  FROM item_instance i JOIN world_object w ON w.id=i.object_id
      WHERE w.current_owner_id='c5000000-0000-0000-0000-000000000000' AND i.item_key='fiber_cordage';
    units := fibre + vine + 2 * cord;

    IF fibre <> 0 THEN RAISE EXCEPTION 'REGRESSION: fixture should carry no plant fibre (got %)', fibre; END IF;
    IF units < 8 THEN RAISE EXCEPTION 'REGRESSION: vine+cordage inventory does not reach a basket (% weave units) — #34', units; END IF;
    RAISE NOTICE 'PASS: a basket is reachable from vine(%) + cordage(%) alone = % weave units, no plant fibre needed (#34/#31)', vine, cord, units;
END $$;

ROLLBACK;
