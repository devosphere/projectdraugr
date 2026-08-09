-- Regression: perception names the life actually present (DR-0022 Layer 2a, ExaminationService.presentLife).
--
-- When the chronicle examines a place, the narration must name the flora, wildlife, and insect colonies that
-- are really seeded on that chunk — not generic biome boilerplate. presentLife runs three queries (chunk_flora,
-- wildlife_population JOIN ecology_site, insect_colony); this seeds one of each and replays those exact queries,
-- asserting the seeded life comes back. Guards against a schema/column drift silently emptying the examine text.
-- BEGIN/ROLLBACK — no residue.

BEGIN;

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('a1000000-0000-0000-0000-000000000000', 'WORLD', 'Life World', 'DESTROYED');
INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks)
 VALUES ('a1000000-0000-0000-0000-000000000000', 2, 'regression', 8, 8);

INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('b1000000-0000-0000-0000-000000000000', 'CHUNK', 'Wooded chunk', 'DESTROYED');
INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome)
 VALUES ('b1000000-0000-0000-0000-000000000000', 'a1000000-0000-0000-0000-000000000000', 1, 0, 500, 600, 'TEMPERATE_FOREST');

-- Flora: reuse whatever flora keys the seed guarantees (flora_key is an FK into flora_definition). Pick two
-- real keys so the FK holds regardless of which flora set shipped.
INSERT INTO chunk_flora (chunk_id, flora_key, quantity)
 SELECT 'b1000000-0000-0000-0000-000000000000', flora_key, 5
   FROM flora_definition ORDER BY flora_key LIMIT 2;

-- Wildlife: an ecology site + a population on this chunk.
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('d1000000-0000-0000-0000-000000000000', 'ECOLOGY_SITE', 'Deer range', 'DESTROYED');
INSERT INTO ecology_site (id, world_id, chunk_id, site_category, site_kind, baseline_abundance)
 VALUES ('d1000000-0000-0000-0000-000000000000', 'a1000000-0000-0000-0000-000000000000',
         'b1000000-0000-0000-0000-000000000000', 'WILDLIFE', 'deer_range', 50);
INSERT INTO wildlife_population (id, site_id, species_key, ecological_role, activity_cycle, population_count, carrying_capacity, behavior_state, last_simulated_at)
 VALUES ('e1000000-0000-0000-0000-000000000000', 'd1000000-0000-0000-0000-000000000000',
         'red_deer', 'HERBIVORE', 'DIURNAL', 6, 12, 'CALM', now());

-- Insects: a colony instance (honeybee_hive is a seeded colony kind).
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('f1000000-0000-0000-0000-000000000000', 'INSECT_COLONY', 'Wild hive', 'DESTROYED');
INSERT INTO insect_colony (object_id, colony_kind, chunk_id)
 VALUES ('f1000000-0000-0000-0000-000000000000', 'honeybee_hive', 'b1000000-0000-0000-0000-000000000000');

DO $$
DECLARE flora_n int; deer text; hive text;
BEGIN
    -- presentLife flora query
    SELECT count(*) INTO flora_n FROM chunk_flora
     WHERE chunk_id = 'b1000000-0000-0000-0000-000000000000' AND quantity > 0;
    -- presentLife wildlife query (the join that must survive)
    SELECT wp.species_key INTO deer
      FROM wildlife_population wp JOIN ecology_site es ON es.id = wp.site_id
     WHERE es.chunk_id = 'b1000000-0000-0000-0000-000000000000' AND wp.population_count > 0
     ORDER BY wp.population_count DESC LIMIT 1;
    -- presentLife insect query
    SELECT colony_kind INTO hive FROM insect_colony
     WHERE chunk_id = 'b1000000-0000-0000-0000-000000000000' ORDER BY colony_kind LIMIT 1;

    IF flora_n <> 2 THEN RAISE EXCEPTION 'REGRESSION: presentLife flora query lost rows (got %)', flora_n; END IF;
    IF deer IS DISTINCT FROM 'red_deer' THEN RAISE EXCEPTION 'REGRESSION: presentLife wildlife join broke (got %)', deer; END IF;
    IF hive IS DISTINCT FROM 'honeybee_hive' THEN RAISE EXCEPTION 'REGRESSION: presentLife insect query broke (got %)', hive; END IF;
    RAISE NOTICE 'PASS: perception names seeded flora(%), wildlife(%), and insect colony(%) — Layer 2a', flora_n, deer, hive;
END $$;

ROLLBACK;
