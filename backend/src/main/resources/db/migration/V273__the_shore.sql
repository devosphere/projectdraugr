-- V273 — the world gets a shore (#157).
--
-- COAST is the second biome the catalogue believed in and the generator never made. Sea beet, samphire and sea
-- buckthorn grow on it; an osprey works it; a bonecrab, a saltback crocodilian, a wave roc and a storm moth
-- cloud live on it. Eight catalogue entries whose only ground did not exist anywhere in the world.
--
-- The generator now derives it rather than inventing it: ground that touches open water IS the shore. Freshwater
-- marsh is deliberately left alone — WETLAND here carries reeds, clay, cranberries and freshwater fish, and
-- calling the sea-margin ones "coast" would move that ecology into salt water. Mountain is left alone too: a sea
-- cliff is still a cliff, and that is where the ore is.
--
-- This migration makes the shore survivable and worth walking, rather than a strip of ground that reads as empty.

-- 1. Arrival viability. A shore has water at its feet and forage and stone along it, so it must not read as a
--    barren edge — and, as with the river, it now REPLACES chunks that used to satisfy a neighbour's tests.
--    The sea is not drinkable, so COAST counts for the envelope's water the way an OCEAN margin already does,
--    and for material, forage and tool stone as any other land.
CREATE OR REPLACE FUNCTION arrival_viability(p_chunk uuid) RETURNS text AS $$
DECLARE
    v_biome text; v_world uuid; v_x int; v_y int;
    has_water boolean; water_immediate boolean; has_material boolean; has_forage boolean; has_tool boolean; has_escape boolean;
    predator_here boolean; predator_adjacent boolean;
BEGIN
    SELECT biome, world_id, grid_x, grid_y INTO v_biome, v_world, v_x, v_y FROM world_chunk WHERE id = p_chunk;
    IF v_biome IS NULL THEN RETURN 'REJECTED'; END IF;
    IF v_biome = 'OCEAN' THEN RETURN 'REJECTED'; END IF;

    SELECT EXISTS(SELECT 1 FROM wildlife_population wp JOIN ecology_site es ON es.id = wp.site_id
                  WHERE es.chunk_id = p_chunk AND wp.ecological_role = 'CARNIVORE' AND wp.population_count > 0
                    AND wp.behavior_state IN ('HUNTING','PACK_HUNT','ALERT','AGGRESSIVE')) INTO predator_here;
    IF predator_here THEN RETURN 'REJECTED'; END IF;

    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND (c.grid_x <> v_x OR c.grid_y <> v_y)
                  AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1 AND c.biome <> 'OCEAN') INTO has_escape;

    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND (c.biome IN ('WETLAND','OCEAN','RIVER_BANK','COAST')
                       OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = c.id
                                 AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%' OR es.site_kind ILIKE '%freshwater%')))) INTO has_water;
    -- Water underfoot means water FIT TO DRINK. The sea is not, so a shore does not qualify — standing on one
    -- is a short walk from fresh water, not a comfort.
    SELECT (v_biome IN ('WETLAND','RIVER_BANK') OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = p_chunk
             AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%'))) INTO water_immediate;
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('TEMPERATE_FOREST','HIGHLAND','GRASSLAND','WETLAND','MOUNTAIN','RIVER_BANK','COAST')) INTO has_material;
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome <> 'OCEAN') INTO has_forage;
    -- A shingle beach is where water-rounded cobble and flint actually lie, the same argument as a gravel bar.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('HIGHLAND','MOUNTAIN','GRASSLAND','TEMPERATE_FOREST','RIVER_BANK','COAST')) INTO has_tool;

    IF NOT (has_escape AND has_water AND has_material AND has_forage AND has_tool) THEN RETURN 'REJECTED'; END IF;

    SELECT EXISTS(SELECT 1 FROM world_chunk c JOIN ecology_site es ON es.chunk_id = c.id JOIN wildlife_population wp ON wp.site_id = es.id
                  WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                    AND wp.ecological_role = 'CARNIVORE' AND wp.population_count > 0) INTO predator_adjacent;

    IF predator_adjacent OR v_biome = 'MOUNTAIN' OR (v_biome = 'HIGHLAND' AND NOT water_immediate) THEN RETURN 'CHALLENGING'; END IF;
    RETURN 'VIABLE';
END $$ LANGUAGE plpgsql STABLE;

-- 2. What grows on a shore. Only plants the catalogue actually holds, and only ones that belong on salt ground:
--    samphire, sea beet and sea buckthorn already carry COAST and now have somewhere to be. These are the ones
--    that also tolerate a shore. Marsh specialists stay in the marsh.
UPDATE flora_definition SET biome_affinity = biome_affinity || ',COAST'
WHERE flora_key IN ('meadow_grass','wild_grass','curly_dock','silverweed','nettle','elder_shrub','burdock')
  AND biome_affinity NOT ILIKE '%COAST%';

-- 3. What works a tideline. Again, only real entries: the fish-hunting birds already at home on water, the
--    scavengers that walk any shore, and the otter of a rocky coast. Freshwater fish are NOT given salt water.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',COAST'
WHERE species_key IN ('grey_heron','stork','greylag_goose','mallard_duck','river_otter','carrion_crow',
                      'common_raven','golden_eagle','raccoon','forest_fox','red_deer','wild_boar')
  AND biome_affinity NOT ILIKE '%COAST%';

-- 4. Shore foraging. A rocky shore is the richest shellfish ground there is, which is exactly what the shellfish
--    pry was carved for (V222/V270), and a strandline turns up worms for bait.
UPDATE insect_colony_kind SET biome_affinity = biome_affinity || ',COAST'
WHERE colony_kind IN ('mussel_bed','river_snail_bed','earthworm_patch') AND biome_affinity NOT ILIKE '%COAST%';

UPDATE wildlife_species SET biome_affinity = biome_affinity || ',COAST'
WHERE species_key IN ('freshwater_mussel','freshwater_snail','earthworm') AND biome_affinity NOT ILIKE '%COAST%';

-- 5. Shingle and sand are the shore's own stone: water-rounded cobble, and the sand a potter tempers with.
UPDATE mineral_definition SET biome_affinity = biome_affinity || ',COAST'
WHERE mineral_key IN ('river_sand','river_gravel','quartzite_cobble','granite_cobble','flint_stone','sandstone_piece')
  AND biome_affinity NOT ILIKE '%COAST%';
