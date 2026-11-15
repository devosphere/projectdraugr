-- V469: a storm has edges, and you can walk out from under it (#912).
--
-- A Chronicle on seed 681013497 spawned in a storm, crossed 560 tiles of a 28x20 world, and never left it.
-- `world_weather` holds ONE ROW PER WORLD, so every chunk is inside whatever front is overhead. BiomeClimate
-- can already turn that storm into snow on high ground and make a peak colder and windier than the valley —
-- what it cannot do is put one region outside the front, because there is nothing outside it.
--
-- THE FRONT GETS A CENTRE AND A RADIUS. The daily draw is unchanged: the same (seed, day) still picks the
-- same kind, seasonally weighted, and WeatherSimulationService derives the footprint from the same SplitMix64
-- stream so it stays reproducible per seed. The centre drifts with the hour, so a front crosses the map over
-- a day and a Chronicle can walk out from under it or be caught by it.
ALTER TABLE world_weather
    ADD COLUMN front_centre_x      SMALLINT NULL,
    ADD COLUMN front_centre_y      SMALLINT NULL,
    ADD COLUMN front_radius_chunks SMALLINT NULL CHECK (front_radius_chunks IS NULL OR front_radius_chunks > 0);

COMMENT ON COLUMN world_weather.front_radius_chunks IS
    'Chunks from the centre that the front covers. NULL means it covers the world — which is what a save '
    'written before #912 says, and what CLEAR and OVERCAST always say: they are not fronts, they are what the '
    'sky does when no front is overhead.';

-- ONE PLACE THE RULE LIVES.
--
-- `LocalWeatherService` says it is the only place the environment query exists. It was not: twenty-two
-- statements across eight files read `world_weather` directly. Honouring the footprint in one of them and not
-- the others would hand the player a HUD that says clear while their fire's ember hazard, their wet
-- work-in-progress and the wildlife around them all still think it is storming — this project's recurring
-- defect (two readers of one question), introduced on purpose.
--
-- So the rule is SQL, and every reader — including the Java ones, which go through LocalWeatherService — asks
-- this function. There is no second copy to keep in step.
CREATE TYPE local_sky AS (
    weather_kind          TEXT,
    intensity             SMALLINT,
    ambient_temperature_c NUMERIC(4,1),
    wind_speed_kph        SMALLINT
);

-- What each kind is, away from the front: the figures WeatherSimulationService writes for a front of that
-- kind. `WeatherConstantsAgreeTest` holds these against the Java switch they mirror, because a table of
-- numbers written twice is exactly the shape this migration exists to remove.
--
-- `ambient_temperature_c` carries the FRONT'S OWN adjustment (+2 clear, 0 overcast, -3 rain and storm,
-- -5 snow) on top of the season and the hour. A chunk outside the front therefore has to have that backed
-- out and its own applied, or a sunlit tile three chunks past the edge reads three degrees cold for a storm
-- it is nowhere near.
CREATE FUNCTION sky_temperature_adjustment(kind TEXT) RETURNS NUMERIC AS $$
    SELECT CASE kind WHEN 'CLEAR' THEN 2 WHEN 'RAIN' THEN -3 WHEN 'STORM' THEN -3 WHEN 'SNOW' THEN -5 ELSE 0 END::NUMERIC;
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION sky_intensity(kind TEXT) RETURNS SMALLINT AS $$
    SELECT CASE kind WHEN 'CLEAR' THEN 8 WHEN 'OVERCAST' THEN 30 WHEN 'RAIN' THEN 55 WHEN 'SNOW' THEN 45 WHEN 'STORM' THEN 88 ELSE 8 END::SMALLINT;
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION sky_wind_kph(kind TEXT) RETURNS SMALLINT AS $$
    SELECT CASE kind WHEN 'CLEAR' THEN 8 WHEN 'OVERCAST' THEN 12 WHEN 'SNOW' THEN 18 WHEN 'RAIN' THEN 24 WHEN 'STORM' THEN 60 ELSE 8 END::SMALLINT;
$$ LANGUAGE sql IMMUTABLE;

-- THE SKY OVER ONE CHUNK.
--
-- Inside the radius, the front's own reading, exactly as stored. Within a margin beyond it, OVERCAST — the
-- edge of a weather system is not a wall, and a line where a storm becomes a blue sky in one step would be a
-- worse lie than the one being fixed. Beyond that, CLEAR.
--
-- Null-safe throughout, in the same spirit as LocalWeatherService: a world with no weather row, a chunk that
-- does not exist, or a front with no footprint all read as something sensible rather than throwing. A missing
-- front is not a reason to refuse an action.
CREATE FUNCTION local_sky(chunk UUID) RETURNS local_sky AS $$
DECLARE
    front    world_weather%ROWTYPE;
    gx       INTEGER;
    gy       INTEGER;
    distance DOUBLE PRECISION;
    kind     TEXT;
BEGIN
    SELECT ww.* INTO front
      FROM world_chunk wc JOIN world_weather ww ON ww.world_id = wc.world_id
     WHERE wc.id = chunk;
    IF NOT FOUND THEN
        RETURN ('CLEAR', sky_intensity('CLEAR'), 18.0, sky_wind_kph('CLEAR'))::local_sky;
    END IF;

    -- No footprint means the front covers the world: a save that has not ticked since this migration reads
    -- exactly as it did before it, which is the whole reason these columns are nullable.
    IF front.front_radius_chunks IS NULL OR front.front_centre_x IS NULL OR front.front_centre_y IS NULL THEN
        RETURN (front.weather_kind, front.intensity, front.ambient_temperature_c, front.wind_speed_kph)::local_sky;
    END IF;

    SELECT wc.grid_x, wc.grid_y INTO gx, gy FROM world_chunk wc WHERE wc.id = chunk;
    distance := sqrt((gx - front.front_centre_x)::double precision ^ 2
                   + (gy - front.front_centre_y)::double precision ^ 2);

    IF distance <= front.front_radius_chunks THEN
        RETURN (front.weather_kind, front.intensity, front.ambient_temperature_c, front.wind_speed_kph)::local_sky;
    END IF;

    kind := CASE WHEN distance <= front.front_radius_chunks + 2 THEN 'OVERCAST' ELSE 'CLEAR' END;
    RETURN (kind,
            sky_intensity(kind),
            GREATEST(-40, LEAST(55, front.ambient_temperature_c
                - sky_temperature_adjustment(front.weather_kind) + sky_temperature_adjustment(kind)))::NUMERIC(4,1),
            sky_wind_kph(kind))::local_sky;
END;
$$ LANGUAGE plpgsql STABLE;

-- The kind alone, which is what most of the twenty-two readers wanted: `ww.weather_kind` becomes
-- `local_weather_kind(wc.id)` and the statement is otherwise untouched.
CREATE FUNCTION local_weather_kind(chunk UUID) RETURNS TEXT AS $$
    SELECT (local_sky(chunk)).weather_kind;
$$ LANGUAGE sql STABLE;

DO $$
DECLARE
    world  UUID;
    chunks INTEGER;
BEGIN
    -- The function must answer for every chunk of every world that has weather, or some reader is about to
    -- get a NULL where it used to get a kind.
    FOR world IN SELECT world_id FROM world_weather LOOP
        SELECT COUNT(*) INTO chunks FROM world_chunk wc
         WHERE wc.world_id = world AND (local_sky(wc.id)).weather_kind IS NULL;
        IF chunks > 0 THEN
            RAISE EXCEPTION 'V469: local_sky answers nothing for % chunk(s) of world %', chunks, world;
        END IF;
    END LOOP;

    -- And with no footprint written yet it must answer exactly what the stored front says, because that is
    -- what an untouched save is entitled to.
    IF EXISTS (
        SELECT 1 FROM world_chunk wc JOIN world_weather ww ON ww.world_id = wc.world_id
         WHERE ww.front_radius_chunks IS NULL
           AND (local_sky(wc.id)).weather_kind IS DISTINCT FROM ww.weather_kind)
    THEN
        RAISE EXCEPTION 'V469: a front with no footprint must still cover the world';
    END IF;
END $$;
