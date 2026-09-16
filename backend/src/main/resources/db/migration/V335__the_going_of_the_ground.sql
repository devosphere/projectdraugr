-- #77 / #155 — the going of the ground.
--
-- THE DEFECT. A journey costs eighteen minutes a chunk. Every chunk. Crossing a meadow, wading a fen, and
-- scrambling up a mountainside all take exactly as long as each other, and the world has ten kinds of ground that
-- differ in nothing so much as how hard they are to walk over. Distance is already read — travel scales with it,
-- and a ridden beast covers it faster — so the shape of the model is there; what is missing is the country.
--
-- That gap is also what two other tickets are waiting on. #77's paths tier cannot exist until a laid way has a
-- number to improve, and #155's "donkey scrub-steppe routes" are blocked on "no route/travel-cost model".
--
-- WHAT THIS ADDS. terrain_going: how long a chunk of each kind of country takes to cross on foot. The service
-- averages it over the chunks actually on the line between here and there, so a journey costs what the country it
-- crosses costs, not what its endpoints happen to be.
--
-- THE NUMBERS, and why each is what it is. The old flat rate was 18, so open grass is now quicker than before and
-- everything else is slower — which is the point: eighteen minutes was the pace of ground that gets out of your way.
--
--   GRASSLAND       15  open ground with nothing in it to stop you
--   COAST           18  shingle and soft sand underfoot, and the tide deciding where the walking is
--   TEMPERATE_FOREST 22 undergrowth, deadfall, and no line of sight to steer by
--   RIVER_BANK      24  soft bank, and you walk the water's line rather than your own
--   HIGHLAND        26  climbing and dropping again all day
--   CAVE_MOUTH      26  broken rock and rubble at the entrance
--   CAVE_INTERIOR   34  feeling the way over wet rock, usually in the dark
--   WETLAND         36  wading, and every step has to be found before it is taken
--   MOUNTAIN        40  scree, scrambling, and going round what cannot be gone over
--   OCEAN           60  not walking ground at all; kept honest rather than left to a default
--
-- WHAT IT DOES NOT DO. Load is not read here: a Chronicle under a full pack walks a mountain at the same pace as
-- one carrying nothing, which is wrong and is its own work. Nor does any laid way yet take anything off the going —
-- that is the paths tier, and it now has a number to improve, which it did not before.

CREATE TABLE terrain_going (
    biome             VARCHAR(50)  PRIMARY KEY,
    minutes_per_chunk SMALLINT     NOT NULL CHECK (minutes_per_chunk BETWEEN 5 AND 120),
    note              TEXT         NOT NULL
);

COMMENT ON TABLE terrain_going IS
  'How long one chunk of this country takes to cross on foot. Averaged over the chunks on the line actually '
  'walked, so a journey costs the ground it crosses. A biome with no row here walks at the historical flat rate.';

INSERT INTO terrain_going (biome, minutes_per_chunk, note) VALUES
  ('GRASSLAND',        15, 'Open ground with nothing in it to stop you.'),
  ('COAST',            18, 'Shingle and soft sand underfoot, and the tide deciding where the walking is.'),
  ('TEMPERATE_FOREST', 22, 'Undergrowth, deadfall, and no line of sight to steer by.'),
  ('RIVER_BANK',       24, 'Soft bank, and you walk the water''s line rather than your own.'),
  ('HIGHLAND',         26, 'Climbing and dropping again all day.'),
  ('CAVE_MOUTH',       26, 'Broken rock and rubble at the entrance.'),
  ('CAVE_INTERIOR',    34, 'Feeling the way over wet rock, usually in the dark.'),
  ('WETLAND',          36, 'Wading, and every step has to be found before it is taken.'),
  ('MOUNTAIN',         40, 'Scree, scrambling, and going round what cannot be gone over.'),
  ('OCEAN',            60, 'Not walking ground at all.')
ON CONFLICT (biome) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every kind of country the generator actually makes must have a going, or a journey over it silently falls back
  -- to the old flat rate and this table becomes a decoration that covers most of the map.
  SELECT string_agg(DISTINCT c.biome, ', ') INTO bad
    FROM world_chunk c WHERE NOT EXISTS (SELECT 1 FROM terrain_going g WHERE g.biome = c.biome);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V335: ground with no going: %', bad; END IF;

  -- The going must actually differ, or the model is the flat rate wearing a table.
  SELECT count(DISTINCT minutes_per_chunk) INTO n FROM terrain_going;
  IF n < 5 THEN RAISE EXCEPTION 'V335: ground that all walks the same is not a terrain model (% distinct rates)', n; END IF;

  -- And it must be ordered the way the world is: rock and bog are never quicker than open grass.
  IF (SELECT minutes_per_chunk FROM terrain_going WHERE biome='GRASSLAND')
     >= (SELECT MIN(minutes_per_chunk) FROM terrain_going WHERE biome IN ('MOUNTAIN','WETLAND','CAVE_INTERIOR')) THEN
    RAISE EXCEPTION 'V335: a mountain, a fen or a cave must not walk as easily as a meadow';
  END IF;
END $$;
