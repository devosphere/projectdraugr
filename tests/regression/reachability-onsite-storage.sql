-- Regression: the ONE reachability model reaches on-site storage contents and racked tools (DR-0022 Layer 1).
--
-- The old carried-only CTE rooted only at `current_owner_id = :chronicle`, so materials sitting in a bin at
-- the location and a tool hanging on a rack were UNREACHABLE (the "documents on a stone shelf can't be read"
-- gap, F3), and the sourcing paths disagreed (hasAtLeast carried-only vs hasAtLeastHere ground-only, F4).
-- The unified CTE (PhysicalItemService.REACHABLE_CTE) roots at carried ∪ location-sited and descends
-- item_containment from both, so a Chronicle can pull from an on-site store and take a tool from a rack.
--
-- This seeds the canonical basket scenario against the REAL schema, runs the exact CTE, and RAISEs if any
-- source is not in reach. Runs in a transaction and ROLLBACKs, leaving no residue. Any RAISE exits non-zero
-- under ON_ERROR_STOP, failing the suite.

BEGIN;

-- L: placeholder location (DESTROYED needs neither owner nor location; only an FK target for current_location_id)
INSERT INTO world_object (id,object_type,display_name,lifecycle_state) VALUES ('00000000-0000-0000-0000-0000000000a0','LOCATION','Chunk','DESTROYED');
-- CH: the Chronicle, standing at L
INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES ('00000000-0000-0000-0000-0000000000c0','CHRONICLE','Chronicle','00000000-0000-0000-0000-0000000000a0');
-- BIN and RACK: on-site containers (item_instances with container_properties)
INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES
 ('00000000-0000-0000-0000-0000000000b0','CONTAINER','Storage Bin','00000000-0000-0000-0000-0000000000a0'),
 ('00000000-0000-0000-0000-0000000000d0','CONTAINER','Tool Rack','00000000-0000-0000-0000-0000000000a0');
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES
 ('00000000-0000-0000-0000-0000000000b0','large_basket','SOUND'),
 ('00000000-0000-0000-0000-0000000000d0','large_basket','SOUND');
INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES
 ('00000000-0000-0000-0000-0000000000b0',500000,500000),
 ('00000000-0000-0000-0000-0000000000d0',500000,500000);
-- 5 dry_branch inside the bin (contents are owned BY the bin — how the game models containment)
INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES
 ('00000000-0000-0000-0000-0000000000b1','ITEM','Dry branch','00000000-0000-0000-0000-0000000000b0'),
 ('00000000-0000-0000-0000-0000000000b2','ITEM','Dry branch','00000000-0000-0000-0000-0000000000b0'),
 ('00000000-0000-0000-0000-0000000000b3','ITEM','Dry branch','00000000-0000-0000-0000-0000000000b0'),
 ('00000000-0000-0000-0000-0000000000b4','ITEM','Dry branch','00000000-0000-0000-0000-0000000000b0'),
 ('00000000-0000-0000-0000-0000000000b5','ITEM','Dry branch','00000000-0000-0000-0000-0000000000b0');
INSERT INTO item_instance (object_id,item_key,condition_state) SELECT id,'dry_branch','SOUND' FROM world_object WHERE display_name='Dry branch';
INSERT INTO item_containment (item_id,container_id) SELECT id,'00000000-0000-0000-0000-0000000000b0' FROM world_object WHERE display_name='Dry branch';
-- a stone_knife on the rack
INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES ('00000000-0000-0000-0000-0000000000e0','ITEM','Stone knife','00000000-0000-0000-0000-0000000000d0');
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES ('00000000-0000-0000-0000-0000000000e0','stone_knife','SOUND');
INSERT INTO item_containment (item_id,container_id) VALUES ('00000000-0000-0000-0000-0000000000e0','00000000-0000-0000-0000-0000000000d0');
-- loose ground plant_fiber, and a carried vine
INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES ('00000000-0000-0000-0000-0000000000f0','ITEM','Plant fiber','00000000-0000-0000-0000-0000000000a0');
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES ('00000000-0000-0000-0000-0000000000f0','plant_fiber','SOUND');
INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES ('00000000-0000-0000-0000-0000000000a1','ITEM','Vine','00000000-0000-0000-0000-0000000000c0');
INSERT INTO item_instance (object_id,item_key,condition_state) VALUES ('00000000-0000-0000-0000-0000000000a1','vine','SOUND');

-- Assert the exact reachable counts via the real REACHABLE_CTE (chronicle=CH, location=L).
DO $$
DECLARE branches int; knife int; fibre int; vine int;
BEGIN
    CREATE TEMP TABLE reach ON COMMIT DROP AS
    WITH RECURSIVE reachable(id) AS (
        SELECT id FROM world_object WHERE lifecycle_state='ACTIVE'
          AND (current_owner_id='00000000-0000-0000-0000-0000000000c0'
               OR (current_owner_id IS NULL AND current_location_id='00000000-0000-0000-0000-0000000000a0'))
        UNION ALL
        SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id
          JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE')
    SELECT i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id;

    SELECT count(*) INTO branches FROM reach WHERE item_key='dry_branch';
    SELECT count(*) INTO knife    FROM reach WHERE item_key='stone_knife';
    SELECT count(*) INTO fibre    FROM reach WHERE item_key='plant_fiber';
    SELECT count(*) INTO vine     FROM reach WHERE item_key='vine';

    IF branches <> 5 THEN RAISE EXCEPTION 'REGRESSION: on-site bin contents not reachable (dry_branch=% expected 5)', branches; END IF;
    IF knife    <> 1 THEN RAISE EXCEPTION 'REGRESSION: racked tool not reachable (stone_knife=% expected 1)', knife; END IF;
    IF fibre    <> 1 THEN RAISE EXCEPTION 'REGRESSION: ground item not reachable (plant_fiber=% expected 1)', fibre; END IF;
    IF vine     <> 1 THEN RAISE EXCEPTION 'REGRESSION: carried item not reachable (vine=% expected 1)', vine; END IF;
    RAISE NOTICE 'PASS: carried + ground + on-site bin contents + racked tool are all in reach';
END $$;

ROLLBACK;
