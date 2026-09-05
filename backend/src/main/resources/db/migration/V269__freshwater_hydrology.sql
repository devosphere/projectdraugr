-- V269 — the world gets running water (#156).
--
-- RIVER_BANK was a biome the whole codebase already believed in and the generator never made. Sixteen places in
-- Java branch on it — clay yields, willow and withy cutting, reeds, water in reach, safe drinking water, thirst
-- relief for draft stock, the ground-description line, the "you catch the sound of water" perception — and
-- thirteen mineral affinities name it. Every one of those paths was dead, because WorldGenesisService derived
-- biome from elevation and moisture alone and emitted only OCEAN, WETLAND, MOUNTAIN, HIGHLAND, TEMPERATE_FOREST
-- and GRASSLAND. There was no running fresh water anywhere in the world.
--
-- The generator now carves channels downhill from headwaters until they meet standing water. This migration
-- makes the ground they cross actually hold something: a river that no plant grows beside and no fish swims in
-- is scenery, not habitat.

-- 1. Arrival viability (V112) must count a river as water, and as land worth arriving on.
--    Without this a river bank would read as waterless — the one biome in the world defined by having water —
--    and, worse, would REPLACE chunks that used to satisfy a neighbour's water test.
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

    -- Water: a wetland/ocean margin, a RIVER BANK, or a freshwater ecology site, in the envelope.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND (c.biome IN ('WETLAND','OCEAN','RIVER_BANK')
                       OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = c.id
                                 AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%' OR es.site_kind ILIKE '%freshwater%')))) INTO has_water;
    SELECT (v_biome IN ('WETLAND','RIVER_BANK') OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id = p_chunk
             AND (es.site_category = 'WATER' OR es.site_kind ILIKE '%spring%' OR es.site_kind ILIKE '%stream%' OR es.site_kind ILIKE '%river%'))) INTO water_immediate;
    -- Fuel & shelter material: a river bank carries willow, reed and withy, so it stands with the other land.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('TEMPERATE_FOREST','HIGHLAND','GRASSLAND','WETLAND','MOUNTAIN','RIVER_BANK')) INTO has_material;
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome <> 'OCEAN') INTO has_forage;
    -- Tool & cordage stone: a gravel bar is where water-rounded cobble and flint actually lie.
    SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                  AND c.biome IN ('HIGHLAND','MOUNTAIN','GRASSLAND','TEMPERATE_FOREST','RIVER_BANK')) INTO has_tool;

    IF NOT (has_escape AND has_water AND has_material AND has_forage AND has_tool) THEN RETURN 'REJECTED'; END IF;

    SELECT EXISTS(SELECT 1 FROM world_chunk c JOIN ecology_site es ON es.chunk_id = c.id JOIN wildlife_population wp ON wp.site_id = es.id
                  WHERE c.world_id = v_world AND abs(c.grid_x - v_x) <= 1 AND abs(c.grid_y - v_y) <= 1
                    AND wp.ecological_role = 'CARNIVORE' AND wp.population_count > 0) INTO predator_adjacent;

    IF predator_adjacent OR v_biome = 'MOUNTAIN' OR (v_biome = 'HIGHLAND' AND NOT water_immediate) THEN RETURN 'CHALLENGING'; END IF;
    RETURN 'VIABLE';
END $$ LANGUAGE plpgsql STABLE;

-- 2. Riverside flora. Willow above all — ChronicleActionService already answers a withy cut on RIVER_BANK ground
--    with "willow", and until now there was no ground to cut it on. Reeds, cress, mint and dock line running
--    water as readily as they line a marsh; bog specialists (sphagnum, cranberry, cloudberry) deliberately do not.
UPDATE flora_definition SET biome_affinity = biome_affinity || ',RIVER_BANK'
WHERE flora_key IN ('willow','reed_bed','bulrush','cattail','arrowhead','watercress','watercress_plant','mint',
                    'nettle','comfrey','meadowsweet_plant','silverweed','curly_dock','fibrous_roots','elderberry',
                    'climbing_vine','sapling','water_lily','woundwort_plant')
  AND biome_affinity NOT ILIKE '%RIVER_BANK%';

-- 3. River fish. Running water holds the moving-water species; still-water carp and the bog things stay in the mire.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',RIVER_BANK'
WHERE species_key IN ('river_trout','chub','dace','minnow','common_perch','pike','glassfin_pike','freshwater_eel',
                      'lamprey','freshwater_sturgeon','freshwater_bream','catfish','crayfish')
  AND biome_affinity NOT ILIKE '%RIVER_BANK%';

-- 4. The life that lives off a river bank: the animals that fish it, the birds that work it, the insects that
--    hatch off it, and the browsers that come down to drink.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',RIVER_BANK'
WHERE species_key IN ('beaver','river_otter','mink','muskrat','kingfisher','grey_heron','mallard_duck','osprey',
                      'stork','dragonfly','damselfly','mayfly','caddisfly','freshwater_mussel','freshwater_snail',
                      'common_frog','bullfrog','grass_snake','river_turtle','marsh_shrew','polecat','raccoon',
                      'red_deer','wild_boar','elk')
  AND biome_affinity NOT ILIKE '%RIVER_BANK%';
