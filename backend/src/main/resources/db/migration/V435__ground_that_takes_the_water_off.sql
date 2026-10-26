-- #108 — drainage: ground a keeper has drained stops rotting their animals' feet.
--
-- #108 lists `grade_drainage` among its mandatory procedures and "mud, waste, drainage" among the things that
-- must use the actual structure condition. DRAINAGE_DITCH has been in the catalogue, buildable from a verified
-- assembly, decaying like any other field structure — and READ BY NOTHING. Grep the Java for its kind and there
-- is not one hit; it carries `eases_going` and `crosses_soft_ground`, which are about WALKING over soft ground,
-- so the one thing a ditch is for was the one thing nothing asked about.
--
-- And there was something for it to do, already modelled and already costing the keeper. V414's hoof rule:
--
--     "Two kinds of ground make it worse, and they are opposite faults: stony, broken going CHIPS AND BRUISES,
--      while standing wet SOFTENS THE HORN AND ROTS IT"
--
-- read from WETLAND and RIVER_BANK by biome alone. So a keeper who kept stock by the river watched their feet
-- rot every turn of the world with no remedy but to leave — while the answer to standing wet stood in the
-- catalogue, inert. A declared capability nothing reads, against a declared cost nothing could relieve.
--
-- THE TWO THAT DRAIN, each by its own words:
--
--   DRAINAGE_DITCH  the thing itself
--   HORSE_STABLE    "floored to drain and open enough at the eaves that the air moves" — the one house in the
--                   catalogue that says it takes the water away from under the animals standing in it
--
-- AND THE ONES THAT MUST NOT, which is why this is a named flag and not a guess from the shape of a structure:
-- WATERFOWL_RUN is "a fenced stretch TAKING IN STANDING WATER", WATERFOWL_POOL is "a hollow dug wide and
-- shallow and puddled tight with clay", and MUD_WALLOW is "a broad shallow hollow where the ground already lies
-- wet". Three structures whose whole purpose is to hold water on the ground, two of them standing on exactly
-- the wet ground this flag is about. A rule that drained the ground wherever something water-shaped stood
-- would have turned the pig's wallow into the thing that dried his ground out.

ALTER TABLE construction_kind
    ADD COLUMN IF NOT EXISTS drains_the_ground BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE construction_kind SET drains_the_ground = TRUE
 WHERE project_kind IN ('DRAINAGE_DITCH','HORSE_STABLE')
   AND NOT drains_the_ground;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. The two, and only the two. A kind swept in without being named above is a kind nobody decided on.
    SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
     WHERE drains_the_ground AND project_kind NOT IN ('DRAINAGE_DITCH','HORSE_STABLE');
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V435: % drains the ground without being named at the top of this file', bad; END IF;

    SELECT COUNT(*) INTO n FROM construction_kind WHERE drains_the_ground;
    IF n <> 2 THEN RAISE EXCEPTION 'V435: % kinds drain the ground; two were named', n; END IF;

    -- 2. THE DITCH IS IN IT. It is the whole reason for the column, and a ditch that does not drain is back to
    --    being a structure the game can build and no rule can see.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='DRAINAGE_DITCH' AND drains_the_ground)
    THEN RAISE EXCEPTION 'V435: a drainage ditch must drain the ground'; END IF;

    -- 3. NOTHING THAT HOLDS WATER ON PURPOSE DRAINS IT. The wallow and the two waterfowl features exist to keep
    --    water on the ground; if one of them ever carries this flag, somebody has derived drainage from the
    --    shape of a structure instead of from what it is for, and a pig's wallow has become his drain.
    SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
     WHERE drains_the_ground AND (is_wallow OR holds_open_water);
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V435: % holds water on purpose and cannot be what drains the ground', bad; END IF;

    -- 4. Both are really buildable, so the flag gates something a keeper can actually raise.
    SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO bad FROM construction_kind ck
     WHERE ck.drains_the_ground
       AND NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.construction_kind = ck.project_kind AND ad.review_state = 'VERIFIED');
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V435: % is not built by any verified assembly, so the flag relieves nothing', bad; END IF;

    -- 5. And the cost it relieves is still there to be relieved: the hoof rule reads the wet biomes, and if
    --    those ever stop being wet ground this column is answering a question nobody is asking any more.
    IF NOT EXISTS (SELECT 1 FROM terrain_going WHERE biome IN ('WETLAND','RIVER_BANK'))
    THEN RAISE EXCEPTION 'V435: the wet biomes this drains are gone from terrain_going'; END IF;
END $$;
