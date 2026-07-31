-- Every locking read the services issue must be executable as written.
-- A bare FOR UPDATE across an outer join is a plan-time error in Postgres, so
-- this file exists to catch it without needing a populated world.
BEGIN;

-- confront(): population + LEFT JOIN species registry, locking only the population
SELECT wp.id,wp.species_key,wp.ecological_role,wp.behavior_state,wp.population_count,
       ws.movement_class,ws.base_resistance,ws.ambush_hunter
FROM wildlife_population wp
JOIN ecology_site es ON es.id=wp.site_id
LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key
WHERE es.chunk_id=gen_random_uuid() AND wp.population_count>0
ORDER BY CASE wp.ecological_role WHEN 'CARNIVORE' THEN 0 WHEN 'OMNIVORE' THEN 1 ELSE 2 END
LIMIT 1 FOR UPDATE OF wp;

-- tame(): same shape, inner join, scoped lock
SELECT wp.id,wp.species_key,ws.tamability,wp.behavior_state
FROM wildlife_population wp
JOIN ecology_site es ON es.id=wp.site_id
JOIN wildlife_species ws ON ws.species_key=wp.species_key
WHERE es.chunk_id=gen_random_uuid() AND wp.population_count>0 AND ws.tamability>0
ORDER BY ws.tamability DESC LIMIT 1 FOR UPDATE OF wp;

-- harvest(): carcass lock
SELECT wc.object_id,wc.species_key,wc.remaining_meat_units,wc.hide_available
FROM wildlife_carcass wc JOIN world_object w ON w.id=wc.object_id
WHERE w.current_location_id=gen_random_uuid() AND w.lifecycle_state='ACTIVE'
ORDER BY wc.died_at LIMIT 1 FOR UPDATE;

-- checkTrap(): placed trap lock
SELECT object_id,trap_kind,set_at,baited_with FROM placed_trap
WHERE chunk_id=gen_random_uuid() AND NOT sprung ORDER BY set_at LIMIT 1 FOR UPDATE;

-- tame(): existing bond lock
SELECT id,trust_level,interaction_count,bond_stage FROM wildlife_bond
WHERE chronicle_id=gen_random_uuid() AND population_id=gen_random_uuid() FOR UPDATE;

-- survey()/planTravel(): the visit lookup that must survive a missing row
SELECT COALESCE((SELECT visit_count FROM chronicle_chunk_visit WHERE chronicle_id=gen_random_uuid() AND chunk_id=gen_random_uuid()),0);

ROLLBACK;
