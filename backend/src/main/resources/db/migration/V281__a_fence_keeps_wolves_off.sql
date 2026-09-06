-- #100/#77 — a fence keeps wolves off.
--
-- The predator raid on a keeper's tamed herd spares the herd only if something is standing on that ground, and it
-- asked for that with `is_shelter`. That is the third distinct question the same flag has been made to answer, and
-- it is the wrong one again. Keeping wolves off a herd is a matter of a BARRIER, not a roof:
--
--   * a WATTLE_FENCE, a SPLIT_RAIL_FENCE, a BRUSH_FENCE, a DRY_STONE_WALL, a SIMPLE_GATE and the ANIMAL_PEN
--     itself are all is_shelter = FALSE, so the very things a keeper builds to protect a herd protected nothing;
--   * a BARK_DOOR, a REED_DOOR and a SMOKE_HOOD are is_shelter = TRUE, so a door lying on open ground did.
--
-- V280 split out `encloses` for "can a Chronicle be inside this, out of the weather". This adds the barrier
-- question alongside it, and the raid check asks for either: an animal is safe behind a fence, and safe inside a
-- shelter, and a door on its own is neither.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS is_barrier BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.is_barrier IS
  'Does this stand in something''s way? Fences, walls, berms, gates and pens. Distinct from encloses (a roof over '
  'a Chronicle) and from is_shelter (any shelter-domain build, including its doors and furniture).';

UPDATE construction_kind SET is_barrier = TRUE WHERE project_kind IN (
    'ANIMAL_PEN','BRUSH_FENCE','WATTLE_FENCE','SPLIT_RAIL_FENCE','SIMPLE_GATE',
    'DRY_STONE_WALL','STONE_WALL_LOW','EARTH_BERM_WALL','DAUB_WALL','WATTLE_WALL');

-- Deliberately NOT barriers, recorded so the next reader does not add them back:
--   doors and hangings — a door is what a barrier OPENS, and one lying on open ground stops nothing;
--   screens (RAIN_SCREEN, WINDWARD_SCREEN) — they break weather, not a charging animal;
--   roofs, frames, furniture and the smoke hood — nothing about them stands in a wolf's way.
-- A roofed shelter is not listed either: it does not need to be, because the raid check asks for
-- `is_barrier OR encloses`, so an animal inside a hut is already safe.

DO $$
DECLARE wrong text; n int;
BEGIN
  SELECT count(*) INTO n FROM construction_kind WHERE is_barrier;
  IF n < 8 THEN RAISE EXCEPTION 'V281: only % barriers; the fences a keeper builds must be among them', n; END IF;

  -- The whole point: the ordinary fences must protect a herd.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['WATTLE_FENCE','SPLIT_RAIL_FENCE','BRUSH_FENCE','ANIMAL_PEN']) k
   WHERE k NOT IN (SELECT project_kind FROM construction_kind WHERE is_barrier);
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V281: these must keep wolves off a herd: %', wrong; END IF;

  -- And the things that are not barriers must not become them.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong FROM construction_kind
   WHERE is_barrier AND project_kind IN ('BARK_DOOR','REED_DOOR','DOOR_HANGING','SMOKE_HOOD','ROOFING_FRAME',
                                         'RAIN_SCREEN','WINDWARD_SCREEN','SLEEPING_BENCH','THATCH_ROOF');
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V281: these stand in nothing''s way: %', wrong; END IF;
END $$;
