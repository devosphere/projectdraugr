-- #108 — the byre you built holds nothing.
--
-- V284 added the animal houses a keeper would actually raise — POULTRY_COOP, CATTLE_BYRE, GOAT_FOLD, PIG_STY —
-- and TIMBER_BARN was already there. Every one of them is buildable today: assembly_definition carries a
-- VERIFIED recipe with stages and requirements for each. Build a cattle byre over two stages, put your oxen in
-- it, and the oxen do not rest. PhysicalItemService.restPennedDraftBeasts names three literals:
--
--     cp.project_kind IN ('ANIMAL_PEN','HITCHING_POST','TETHER_LINE')
--
-- so the only thing that rests a beast is the generic pen, and a byre — the building whose entire purpose is a
-- place an ox stands overnight — is scenery. This is the same defect the cycle keeps turning up: the catalogue
-- declares a capability and the code names literals instead, so the declaration does nothing. The fix is the
-- same one: put the list in the data and let the code ask.
--
-- The second half is the day/night disagreement. V281 made `is_barrier` the thing the NIGHT predator raid reads,
-- so a dry stone wall, a split-rail fence, an earth berm and the animal houses all keep wolves off a herd after
-- dark. WildlifeEncounterService still scores the DAY encounter from its own two literals:
--
--     MAX(CASE cp.project_kind WHEN 'WATTLE_FENCE' THEN 22 WHEN 'BRUSH_FENCE' THEN 12 ELSE 0 END)
--
-- so the wall that turns a wolf aside at midnight is invisible at noon. That is not a rule anyone chose; it is
-- two lists written at different times. `barrier_strength` replaces the CASE with one number per kind, read by
-- the day check, carrying the material's actual worth: piled brush and slipped-between rails are weak, woven
-- withies and daub are middling, stacked stone is the best of them, and a gate on its own is only the gap it
-- closes. The night check keeps asking the boolean; the day check asks how much. They no longer disagree about
-- which things stand in a wolf's way, only about how finely they measure it.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS shelters_stock    BOOLEAN  NOT NULL DEFAULT FALSE;
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS barrier_strength  SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN construction_kind.shelters_stock IS
  'Can a kept beast stand here and rest? Pens, folds, styes, byres, coops, barns, and the post or line a beast '
  'is held on. Distinct from is_shelter (a roof for a Chronicle) and from is_barrier (something stands in the way).';

COMMENT ON COLUMN construction_kind.barrier_strength IS
  'How much this turns a predator aside by day, in percentage points off the encounter chance. Non-zero exactly '
  'where is_barrier is true, so the day check and the night raid (V281) never disagree about what is a barrier.';

-- A beast rests where it is contained and unharried. The three the Java already named are here, so swapping the
-- literals for this column loses nothing; the five animal houses are the point.
UPDATE construction_kind SET shelters_stock = TRUE WHERE project_kind IN (
    'ANIMAL_PEN','GOAT_FOLD','PIG_STY','CATTLE_BYRE','POULTRY_COOP','TIMBER_BARN','HITCHING_POST','TETHER_LINE');

-- Deliberately NOT stock shelter, recorded so the next reader does not add them back:
--   the fences and walls on their own — a fence line is something to be behind, not something to be in, and the
--     pen that is a ring of fence is already ANIMAL_PEN;
--   human dwellings (LOG_CABIN, PIT_HOUSE, LEAN_TO, DEBRIS_HUT, ...) — stock are not brought indoors here;
--   HAY_RACK, FODDER_STORE, WATERING_STATION — these feed and water a beast, which is already read where feeding
--     and watering happen; they are not somewhere it stands.

UPDATE construction_kind SET barrier_strength = CASE project_kind
    WHEN 'SIMPLE_GATE'      THEN 6    -- a gate alone is the gap, not the ring
    WHEN 'SPLIT_RAIL_FENCE' THEN 10   -- stops stock; a wolf goes between the rails
    WHEN 'BRUSH_FENCE'      THEN 12   -- piled dead branches, as V281's predecessor scored it
    WHEN 'ANIMAL_PEN'       THEN 16
    WHEN 'GOAT_FOLD'        THEN 18
    WHEN 'PIG_STY'          THEN 18
    WHEN 'CATTLE_BYRE'      THEN 20
    WHEN 'POULTRY_COOP'     THEN 20
    WHEN 'WATTLE_FENCE'     THEN 22   -- woven withies, as scored before
    WHEN 'WATTLE_WALL'      THEN 22
    WHEN 'EARTH_BERM_WALL'  THEN 24
    WHEN 'DAUB_WALL'        THEN 26
    WHEN 'STONE_WALL_LOW'   THEN 26
    WHEN 'DRY_STONE_WALL'   THEN 30   -- stacked stone is the best of them
    ELSE barrier_strength END
WHERE is_barrier;

DO $$
DECLARE wrong text; n int;
BEGIN
  -- The declaration must not be able to drift from the flag it refines, in either direction. A barrier with no
  -- strength is invisible by day; a strength on something that is not a barrier is a wall that only exists at noon.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong FROM construction_kind
   WHERE is_barrier <> (barrier_strength > 0);
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V293: is_barrier and barrier_strength disagree for: %', wrong;
  END IF;

  -- The whole complaint: the buildings a keeper raises for animals must hold animals.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['CATTLE_BYRE','GOAT_FOLD','PIG_STY','POULTRY_COOP','TIMBER_BARN']) k
   WHERE k NOT IN (SELECT project_kind FROM construction_kind WHERE shelters_stock);
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V293: these are built to hold stock and must rest a beast: %', wrong;
  END IF;

  -- And nothing may be lost by the Java giving up its literals.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['ANIMAL_PEN','HITCHING_POST','TETHER_LINE']) k
   WHERE k NOT IN (SELECT project_kind FROM construction_kind WHERE shelters_stock);
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V293: restPennedDraftBeasts already rested a beast at these, and must keep doing so: %', wrong;
  END IF;

  -- Things a beast plainly does not live in must not creep in.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong FROM construction_kind
   WHERE shelters_stock AND project_kind IN ('LOG_CABIN','PIT_HOUSE','LEAN_TO','DEBRIS_HUT','BARK_DOOR','REED_DOOR',
                                             'HAY_RACK','FODDER_STORE','WATERING_STATION','LATRINE','THATCH_ROOF');
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V293: no beast is kept in these: %', wrong;
  END IF;

  -- A keeper must be able to reach every one of these, or the wiring changes nothing. Each stock shelter has to
  -- be a structure someone can actually raise: a verified assembly with at least one stage.
  SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO wrong FROM construction_kind ck
   WHERE ck.shelters_stock AND NOT EXISTS (
     SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key = ad.assembly_key
      WHERE ad.construction_kind = ck.project_kind AND ad.review_state = 'VERIFIED')
     AND ck.project_kind <> 'ANIMAL_PEN';  -- ANIMAL_PEN is raised by the BUILD_PEN intent, not by assembly
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V293: nobody can build these, so resting a beast in them changes nothing: %', wrong;
  END IF;

  SELECT count(*) INTO n FROM construction_kind WHERE shelters_stock;
  IF n < 8 THEN RAISE EXCEPTION 'V293: only % places a beast can be kept', n; END IF;
END $$;
