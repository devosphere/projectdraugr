-- Regression: quantified reachability (DR-0022 F2) + a "no" that names where the thing is (DR-0022 reachability).
--
-- Two behaviours the DR-0022 work added on top of the unified reachability CTE:
--   1. reachableInventory groups reachable items WITH COUNTS ("field_stone x5") so the Architect can weigh
--      "5 stones is enough", not merely "stone present".
--   2. knownLocationOf turns a blank wall into a decision: when a needed item is not here but sits at a place the
--      chronicle NAMED in another chunk, the reject names that store ("...sits at your Wood Store").
-- Seeds 5 carried stones + a "Wood Store" one chunk over holding clay, then replays the two exact queries.
-- Chronicle seeded DEAD to avoid the one_living_chronicle index. BEGIN/ROLLBACK — no residue.

BEGIN;

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a2000000-0000-0000-0000-000000000000', 'WORLD', 'Reach World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a2000000-0000-0000-0000-000000000000', 3, 'regression', 8, 8);

-- Here-chunk (b2) and a neighbouring chunk (b3) that will hold the named store.
INSERT INTO world_object (id, object_type, display_name, lifecycle_state) VALUES
 ('b2000000-0000-0000-0000-000000000000', 'CHUNK', 'Here chunk', 'DESTROYED'),
 ('b3000000-0000-0000-0000-000000000000', 'CHUNK', 'Store chunk', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome) VALUES
 ('b2000000-0000-0000-0000-000000000000', 'a2000000-0000-0000-0000-000000000000', 0, 0, 500, 500, 'GRASSLAND'),
 ('b3000000-0000-0000-0000-000000000000', 'a2000000-0000-0000-0000-000000000000', 1, 0, 500, 500, 'GRASSLAND');

INSERT INTO world_object (id, object_type, display_name, current_location_id)
 VALUES ('c2000000-0000-0000-0000-000000000000', 'CHRONICLE', 'Reach chronicle', 'b2000000-0000-0000-0000-000000000000');
INSERT INTO chronicle (id, world_id, sequence_number, life_state, died_at, death_cause)
 VALUES ('c2000000-0000-0000-0000-000000000000', 'a2000000-0000-0000-0000-000000000000', 1, 'DEAD', now(), 'regression fixture');

-- Five field stones CARRIED by the chronicle (owner = chronicle).
WITH new_stones AS (
    INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id)
    SELECT gen_random_uuid(), 'ITEM', 'Field stone', 'ACTIVE', 'c2000000-0000-0000-0000-000000000000'
      FROM generate_series(1, 5)
    RETURNING id
)
INSERT INTO item_instance (object_id, item_key) SELECT id, 'field_stone' FROM new_stones;

-- A named "Wood Store" one chunk over, holding three clay lumps sitting on that ground (owner NULL, located there).
INSERT INTO chronicle_named_location (chronicle_id, chunk_id, name, designated_at)
 VALUES ('c2000000-0000-0000-0000-000000000000', 'b3000000-0000-0000-0000-000000000000', 'Wood Store', now());
WITH new_clay AS (
    INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_location_id)
    SELECT gen_random_uuid(), 'ITEM', 'Clay lump', 'ACTIVE', 'b3000000-0000-0000-0000-000000000000'
      FROM generate_series(1, 3)
    RETURNING id
)
INSERT INTO item_instance (object_id, item_key) SELECT id, 'clay_lump' FROM new_clay;

DO $$
DECLARE stone_n int; clay_here int; store text;
BEGIN
    -- reachableInventory: field_stone counted 5, reachable from the chronicle at the here-chunk.
    SELECT COUNT(*) INTO stone_n
      FROM (
        WITH RECURSIVE reachable(id) AS (
            SELECT id FROM world_object WHERE lifecycle_state='ACTIVE'
               AND (current_owner_id='c2000000-0000-0000-0000-000000000000'
                    OR (current_owner_id IS NULL AND current_location_id='b2000000-0000-0000-0000-000000000000'))
            UNION ALL
            SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id
              JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE')
        SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='field_stone'
      ) s;

    -- Clay is NOT reachable from here (it is a chunk away) — reachability must not leak across chunks.
    SELECT COUNT(*) INTO clay_here
      FROM (
        WITH RECURSIVE reachable(id) AS (
            SELECT id FROM world_object WHERE lifecycle_state='ACTIVE'
               AND (current_owner_id='c2000000-0000-0000-0000-000000000000'
                    OR (current_owner_id IS NULL AND current_location_id='b2000000-0000-0000-0000-000000000000'))
            UNION ALL
            SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id
              JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE')
        SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='clay_lump'
      ) c;

    -- knownLocationOf: clay is not here, but the chronicle knows a store that holds it — name it.
    SELECT nl.name INTO store FROM chronicle_named_location nl
      JOIN world_object w ON w.current_location_id=nl.chunk_id AND w.lifecycle_state='ACTIVE'
      JOIN item_instance i ON i.object_id=w.id
     WHERE nl.chronicle_id='c2000000-0000-0000-0000-000000000000'
       AND nl.chunk_id<>'b2000000-0000-0000-0000-000000000000'
       AND i.item_key='clay_lump' LIMIT 1;

    IF stone_n <> 5 THEN RAISE EXCEPTION 'REGRESSION: reachableInventory miscounts carried stones (got %)', stone_n; END IF;
    IF clay_here <> 0 THEN RAISE EXCEPTION 'REGRESSION: reachability leaked across chunks — clay counted here (got %)', clay_here; END IF;
    IF store IS DISTINCT FROM 'Wood Store' THEN RAISE EXCEPTION 'REGRESSION: knownLocationOf did not name the cross-chunk store (got %)', store; END IF;
    RAISE NOTICE 'PASS: reachable field_stone x%, clay unreachable here (%), and the "no" names the Wood Store', stone_n, clay_here;
END $$;

ROLLBACK;
