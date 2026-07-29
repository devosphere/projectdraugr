CREATE TABLE world_weather (
    world_id UUID PRIMARY KEY REFERENCES world_genesis(world_id),
    weather_kind VARCHAR(30) NOT NULL CHECK (weather_kind IN ('CLEAR','OVERCAST','RAIN','STORM','SNOW')),
    intensity SMALLINT NOT NULL CHECK (intensity BETWEEN 0 AND 100),
    ambient_temperature_c NUMERIC(4,1) NOT NULL CHECK (ambient_temperature_c BETWEEN -40 AND 55),
    wind_speed_kph SMALLINT NOT NULL CHECK (wind_speed_kph BETWEEN 0 AND 180),
    observed_at TIMESTAMPTZ NOT NULL
);
