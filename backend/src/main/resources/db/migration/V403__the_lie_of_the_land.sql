-- #224 — the two fields the visual context never carried
--
-- The contract described a place by its BIOME alone, and two columns genesis has maintained since the table
-- existed were read into the service's own query, handed to BiomeClimate, and thrown away:
--
--   world_chunk.elevation   a saturated lowland fen and a dry upland heath were the same WETLAND
--   world_chunk.moisture    and the same GRASSLAND, and no caller could tell a bog from a moor
--
-- And `surroundings` said "moorland is visible from here" without ever saying WHICH WAY to look — though the
-- neighbours are found by their grid offset, and the offset was discarded on the way out. A sea to the west and
-- a mountain wall to the east were the same payload. From one real chunk of a seeded world, the view that was
-- being thrown away every time:
--
--   north  HIGHLAND          728   ABOVE
--   west   HIGHLAND          733   ABOVE
--   east   TEMPERATE_FOREST  536   BELOW
--   south  TEMPERATE_FOREST  538   BELOW
--
-- Both are now reported — the ground in BANDS rather than raw numbers, because a backdrop wants the kind of
-- ground and not a survey, and a raw elevation is a step toward the world-seed coordinates this payload must
-- never carry. The contract is at version 2 and is ADDITIVE: every version-1 field still means what it meant.
--
-- NO SCHEMA CHANGE AND NO DATA. This migration exists to make the dependency explicit: the contract is now built
-- out of these columns, so dropping one should fail here, loudly, rather than in a payload that quietly loses a
-- field. Schema and catalogue only — nothing about simulation state, which on a fresh database does not exist
-- yet, as V400 learned the hard way.

DO $$
DECLARE bad TEXT;
BEGIN
    -- The ground's own bands.
    SELECT string_agg(c, ', ') INTO bad FROM (VALUES ('elevation'), ('moisture'), ('biome'), ('grid_x'), ('grid_y'))
        AS want(c)
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'world_chunk' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V403: the visual context is built from world_chunk columns that do not exist: %', bad;
    END IF;

    -- A band is only meaningful if the column can hold a range. A world where every chunk is at one height
    -- would make the elevation band a constant and the bog/moor distinction no distinction at all — worth
    -- knowing, but only once a world exists to say it about.
    IF EXISTS (SELECT 1 FROM world_chunk) THEN
        IF (SELECT COUNT(DISTINCT elevation) FROM world_chunk) < 2 THEN
            RAISE EXCEPTION 'V403: every chunk in this world is at the same elevation, so the band says nothing';
        END IF;
        IF (SELECT COUNT(*) FROM world_chunk WHERE elevation IS NULL OR moisture IS NULL) > 0 THEN
            RAISE EXCEPTION 'V403: a chunk with no elevation or no moisture has no band to report';
        END IF;
    END IF;

    -- North is grid_y - 1, in six separate sun_warmed queries and now in Compass. The bearing the context
    -- reports has to be the bearing a Chronicle walks, or a place looks one way and feels another. Held in Java
    -- by TheLieOfTheLandTest, which reads ChronicleActionService.Direction and compares the two; recorded here
    -- because the SQL convention is what both of them are built on.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'world_chunk' AND column_name = 'grid_y') THEN
        RAISE EXCEPTION 'V403: without grid_y there is no north';
    END IF;
END $$;
