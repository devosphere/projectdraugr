-- Regression: multi-zone settlements (V70, DR-0022 F8) — a chunk holds MANY named zones, and the chronicle
-- remembers which one it is standing in.
--
-- Before V70 the PK was (chronicle_id, chunk_id): one named place per chunk, so a settlement could not have both
-- a "Tool Shed" and a "Wood Store" on the same ground. V70 relaxes it to (chronicle_id, chunk_id, name) and adds
-- chronicle.current_zone. This seeds two zones on one chunk (the pre-V70 PK violation) and pins current_zone.
-- The chronicle is seeded DEAD so it never trips the one_living_chronicle unique index that a migrated DB may
-- already satisfy. Wrapped in BEGIN/ROLLBACK — leaves no trace.

BEGIN;

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a0000000-0000-0000-0000-000000000000', 'WORLD', 'Regression World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a0000000-0000-0000-0000-000000000000', 1, 'regression', 8, 8);

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('b0000000-0000-0000-0000-000000000000', 'CHUNK', 'Home chunk', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome)
 VALUES ('b0000000-0000-0000-0000-000000000000', 'a0000000-0000-0000-0000-000000000000', 0, 0, 500, 500, 'GRASSLAND');

INSERT INTO world_object (id, object_type, display_name, current_location_id)
 VALUES ('c0000000-0000-0000-0000-000000000000', 'CHRONICLE', 'Regression chronicle', 'b0000000-0000-0000-0000-000000000000');
INSERT INTO chronicle (id, world_id, sequence_number, life_state, died_at, death_cause)
 VALUES ('c0000000-0000-0000-0000-000000000000', 'a0000000-0000-0000-0000-000000000000', 1, 'DEAD', now(), 'regression fixture');

-- Two named zones on the SAME chunk — impossible under the pre-V70 (chronicle_id, chunk_id) PK.
INSERT INTO chronicle_named_location (chronicle_id, chunk_id, name, designated_at) VALUES
 ('c0000000-0000-0000-0000-000000000000', 'b0000000-0000-0000-0000-000000000000', 'Tool Shed',  now()),
 ('c0000000-0000-0000-0000-000000000000', 'b0000000-0000-0000-0000-000000000000', 'Wood Store', now());

UPDATE chronicle SET current_zone = 'Tool Shed' WHERE id = 'c0000000-0000-0000-0000-000000000000';

DO $$
DECLARE zones int; cz text;
BEGIN
    SELECT count(*) INTO zones FROM chronicle_named_location
     WHERE chronicle_id = 'c0000000-0000-0000-0000-000000000000'
       AND chunk_id     = 'b0000000-0000-0000-0000-000000000000';
    SELECT current_zone INTO cz FROM chronicle WHERE id = 'c0000000-0000-0000-0000-000000000000';

    IF zones <> 2 THEN RAISE EXCEPTION 'REGRESSION: a chunk cannot hold two named zones (got %) — V70 PK not relaxed', zones; END IF;
    IF cz <> 'Tool Shed' THEN RAISE EXCEPTION 'REGRESSION: chronicle.current_zone not tracked (got %)', cz; END IF;
    RAISE NOTICE 'PASS: many named zones per chunk + current_zone tracked (V70 / DR-0022 F8)';
END $$;

ROLLBACK;
