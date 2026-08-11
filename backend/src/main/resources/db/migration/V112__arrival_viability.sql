-- V112: arrival viability validator (M1 #124, EPIC #123).
--
-- A generator invariant, not a UI promise: a random start coordinate must give a dangerous-but-viable first
-- survival window. This function classifies a candidate start chunk as VIABLE / CHALLENGING / REJECTED from the
-- actual world — the chunk's biome, its immediate travel envelope (the 8 neighbours), local water, and wildlife
-- pressure. Only VIABLE/CHALLENGING coordinates may be chosen for a start (enforced by ArrivalViabilityService).
--
-- REJECTED when the exact arrival is not survivable: standing in deep ocean, spawned inside a predator's active
-- range, no land route out (surrounded by water), or an essential (water / fuel-&-shelter material / forage /
-- tool-&-cordage stone) unreachable within the early envelope. CHALLENGING when land and all essentials are
-- reachable but harsh: a mountain start, water not in the immediate tile, or a predator in an adjacent tile
-- (nearby danger, but with a land escape). VIABLE otherwise. Grounded in what the world actually holds; the
-- player gets no hint.

CREATE OR REPLACE FUNCTION arrival_viability(p_chunk uuid) RETURNS text AS $$
DECLARE
    v_biome text; v_world uuid; v_x int; v_y int;
    has_water boolean; water_immediate boolean; has_material boolean; has_forage boolean; has_tool boolean; has_escape boolean;
    predator_here boolean; predator_adjacent boolean;
BEGIN
    SELECT biome, world_id, grid_x, grid_y INTO v_biome, v_world, v_x, v_y FROM world_chunk WHERE id = p_chunk;
    IF v_biome IS NULL THEN RETURN 'REJECTED'; END IF;                      -- unknown coordinate
    IF v_biome = 'OCEAN' THEN RETURN 'REJECTED'; END IF;                    -- cannot stand in deep water

    -- Spawned inside a predator's active range on the exact tile: immediately fatal, forbidden.
    SELECT EXISTS(SELECT 1 FROM wildlife_population wp JOIN ecology_site es ON es.id = wp.site_id
                  WHERE es.chunk_id = p_chunk AND wp.ecological_role = 'CARNIVORE' AND wp.population_count > 0
                    AND wp.behavior_state IN ('HUNTING','PACK_HUNT','ALERT','AGGRESSIVE')) INTO predator_here;
    IF predator_here THEN RETURN 'REJECTED'; END IF;

    -- Escape: at least one adjacent LAND tile to move to.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND (c.grid_x <> v_x OR c.grid_y <> v_y)
                  AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1 AND c.biome <> 'OCEAN') INTO has_escape;

    -- Envelope = this tile + its 8 neighbours (a bounded early travel reach).
    -- Water: a wetland/ocean margin, or a freshwater ecology site (spring/stream/river), in the envelope.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND (c.biome IN ('WETLAND','OCEAN')
                       OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = c.id
                                 AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%' OR es.site_kind ILIKE '%freshwater%')))) INTO has_water;
    -- Water in the IMMEDIATE tile (comfort vs a short walk).
    SELECT (v_biome = 'WETLAND' OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = p_chunk
             AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%'))) INTO water_immediate;
    -- Fuel & shelter material: wood (forest/highland) or grass/reed (grassland/wetland) — any land tile in envelope.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('TEMPERATE_FOREST','HIGHLAND','GRASSLAND','WETLAND','MOUNTAIN')) INTO has_material;
    -- Forage: any land tile (the flora catalogue covers every land biome).
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome <> 'OCEAN') INTO has_forage;
    -- Tool & cordage stone: field stone/flint ground (highland/mountain/grassland/forest) in envelope.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('HIGHLAND','MOUNTAIN','GRASSLAND','TEMPERATE_FOREST')) INTO has_tool;

    IF NOT (has_escape AND has_water AND has_material AND has_forage AND has_tool) THEN RETURN 'REJECTED'; END IF;

    -- A predator in an adjacent tile: real danger nearby, but there is ground to keep away on.
    SELECT EXISTS(SELECT 1 FROM world_chunk c JOIN ecology_site es ON es.chunk_id = c.id JOIN wildlife_population wp ON wp.site_id = es.id
                  WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                    AND wp.ecological_role = 'CARNIVORE' AND wp.population_count > 0) INTO predator_adjacent;

    -- Harsh terrain (mountain, or bare highland without water underfoot) or a predator next door: survivable,
    -- but not a comfortable start. Everything else with water close and no neighbour predator is viable.
    IF predator_adjacent OR v_biome = 'MOUNTAIN' OR (v_biome = 'HIGHLAND' AND NOT water_immediate) THEN RETURN 'CHALLENGING'; END IF;
    RETURN 'VIABLE';
END $$ LANGUAGE plpgsql STABLE;
