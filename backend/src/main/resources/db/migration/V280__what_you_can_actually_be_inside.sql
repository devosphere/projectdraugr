-- #77/#219 — what you can actually be inside.
--
-- `is_shelter` is asked by five places in the Java that all mean the same thing: "is there something standing
-- here that I can be INSIDE, out of the weather". It does not answer that question. Thirty-four kinds carry it,
-- and among them are a bark door, a reed door, a door hanging, a low stone wall, an earth berm, a roofing frame,
-- a rain screen, a windward screen, a smoke hood, a sleeping bench and a bed platform. Every one of those is a
-- shelter-domain build — it belongs to the shelter you are making, and it wears like one — but not one of them
-- is a thing you can stand inside. A bark door lying on open grassland is not a roof over your head.
--
-- So `is_shelter` keeps the meaning it has always had and everything that reads it keeps working, including the
-- decay grouping and the two batch tests that count it. `encloses` is the narrower question, asked separately:
-- can a Chronicle be inside this and out of the weather? That is the one the Body HUD, rest, sleep, taking cover
-- and cutting a smoke vent actually need.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS encloses BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.encloses IS
  'Can a Chronicle be inside this, out of the weather? Narrower than is_shelter, which covers every '
  'shelter-domain build including its doors, walls, screens and furniture.';

-- Roofed or enclosing forms: a body fits inside or under them.
UPDATE construction_kind SET encloses = TRUE WHERE project_kind IN (
    'LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN',   -- the four the Java named literally
    'BARK_CABIN','BARK_SHELTER','BRUSH_DOME','CAVE_NICHE','DEBRIS_HUT',
    'HIDE_LODGE','HIDE_TENT','LEANING_BRANCH_SHELTER','LOG_SHELTER','PIT_HOUSE',
    'REED_HUT','ROCK_OVERHANG_SCREEN','SNOW_SHELTER','TARP_SHELTER','THATCH_ROOF','TIMBER_BARN');

-- Deliberately NOT enclosing, recorded so the next reader does not "fix" them back:
--   doors and hangings (BARK_DOOR, REED_DOOR, DOOR_HANGING) — a door is a part, not a place;
--   walls and berms (DAUB_WALL, STONE_WALL_LOW, EARTH_BERM_WALL, WATTLE_WALL) — one wall is not a room;
--   screens (RAIN_SCREEN, WINDWARD_SCREEN) — they break weather from one side, which is what a WINDBREAK
--     already does through its own path, and standing behind one is not being indoors;
--   ROOFING_FRAME — a frame is the shelter unfinished, which is exactly when it should not shelter;
--   SMOKE_HOOD — it takes smoke out of a shelter, so treating it as one is circular;
--   SLEEPING_BENCH, RAISED_BED_PLATFORM, RAISED_SLEEPING_PLATFORM — furniture; a bed is already its own
--     comfort in rest and sleep, and counting it twice would make a bench on open ground a roof.

-- Guard: the flag must be real and must not have quietly caught the components it was written to exclude.
DO $$
DECLARE wrong text; n int;
BEGIN
  SELECT count(*) INTO n FROM construction_kind WHERE encloses;
  IF n < 15 THEN RAISE EXCEPTION 'V280: only % kinds enclose; the four named ones alone would be a regression', n; END IF;

  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='PIT_HOUSE' AND encloses) THEN
    RAISE EXCEPTION 'V280: the pit house must enclose';
  END IF;

  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong FROM construction_kind
   WHERE encloses AND project_kind IN ('BARK_DOOR','REED_DOOR','DOOR_HANGING','ROOFING_FRAME','SMOKE_HOOD',
                                       'SLEEPING_BENCH','RAISED_BED_PLATFORM','RAISED_SLEEPING_PLATFORM',
                                       'DAUB_WALL','STONE_WALL_LOW','EARTH_BERM_WALL','WATTLE_WALL',
                                       'RAIN_SCREEN','WINDWARD_SCREEN');
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V280: these are parts, not places, and must not enclose: %', wrong; END IF;

  -- Everything that encloses is still a shelter-domain build, so it must keep wearing and needing mending.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong
    FROM construction_kind WHERE encloses AND NOT decays;
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V280: an enclosing shelter that never wears never needs mending: %', wrong; END IF;
END $$;
