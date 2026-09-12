-- #108/#100 — a cart left in the rain.
--
-- A cart, a sledge, a travois and a pack saddle are the four things in `draft_vehicle`, and they are the reason
-- a Chronicle can move a season's timber instead of an armful. Two things were wrong with them, and both are
-- the same shape this cycle keeps finding.
--
-- 1. THEY DO NOT WEATHER. Metal rusts (weatherExposedMetal), hides rot (rotExposedOrganics), unfired pottery
--    slakes back to mud (slakeExposedGreenware) — and a wooden cart left standing in the open through a winter
--    is exactly as good as the day it was built, for ever. It is the largest wooden thing a Chronicle owns and
--    the only one the weather cannot touch.
--
-- 2. A BROKEN ONE STILL HAULS. `item_instance.condition_state` has carried SOUND/WORN/BROKEN/DESTROYED all
--    along, and the haul and bulk bonuses ask only `lifecycle_state='ACTIVE'`. A cart with its axle broken gave
--    a keeper the full load of a sound one. The column was declared and the thing that matters ignored it.
--
-- WHY THERE IS NO CART SHED HERE, though #108 names one. A TOOL_SHED and a TIMBER_BARN are already roofed
-- stores a keeper builds, and nothing in this model distinguishes what will fit inside one — there is no size or
-- capacity anywhere in the catalogue. So a cart shed would keep a cart dry exactly as a tool shed does, under a
-- different name, which is the shape already rejected for three fireplaces and for the quarantine pen. Instead
-- the stores that already exist gain the job: `shelters_gear` is what keeps the weather off a cart, and a keeper
-- who has raised a tool shed or a barn has already solved this. If a size model ever arrives, a cart shed
-- becomes a real distinction and can be added then — it is recorded here as deliberately absent, not forgotten.
--
-- The loop closes on machinery that already exists: REPAIR_ITEM takes BROKEN back to WORN and WORN back to
-- SOUND with cordage, so nothing here is a one-way loss.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS shelters_gear BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.shelters_gear IS
  'A roofed store that keeps the weather off heavy gear left on this ground — a cart, a sledge, a travois. '
  'Distinct from shelters_stock (animals) and encloses (a Chronicle out of the weather).';

-- The roofed stores a keeper actually builds and could put a cart under: the shed gear is kept in, the barn, and
-- the covered wood store. FUEL_RACK is deliberately not among them — it is a rack sized for split firewood, not
-- a store you back a sledge into, and the first draft of this list included it until the reachability guard
-- below pointed out it has no assembly either.
UPDATE construction_kind SET shelters_gear = TRUE
 WHERE project_kind IN ('TOOL_SHED','TIMBER_BARN','WOOD_STORE');

DO $$
DECLARE bad text; n int;
BEGIN
  -- Somewhere must keep gear dry, or the weathering below is a loss a keeper cannot do anything about.
  SELECT count(*) INTO n FROM construction_kind WHERE shelters_gear;
  IF n = 0 THEN RAISE EXCEPTION 'V300: nothing keeps the weather off gear, so a cart could never be saved'; END IF;

  -- And it must be possible to get it wrong, or the rule never bites.
  SELECT count(*) INTO n FROM construction_kind WHERE NOT shelters_gear;
  IF n = 0 THEN RAISE EXCEPTION 'V300: everything shelters gear, so nothing is ever exposed'; END IF;

  -- A gear store must be something a keeper can actually raise. TOOL_SHED is built by a Java intent rather than
  -- an assembly, so it is exempt from the assembly check the others take.
  SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO bad FROM construction_kind ck
   WHERE ck.shelters_gear AND ck.project_kind <> 'TOOL_SHED'
     AND NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key = ad.assembly_key
                      WHERE ad.construction_kind = ck.project_kind AND ad.review_state = 'VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V300: nobody can build these, so they shelter nothing: %', bad; END IF;

  -- The vehicles this protects must exist, or the rule has no subject.
  SELECT count(*) INTO n FROM draft_vehicle;
  IF n < 3 THEN RAISE EXCEPTION 'V300: only % draft vehicles; there is nothing much to leave in the rain', n; END IF;

  -- Every one of them must be a real item, or weathering would chase a key nothing can own.
  SELECT string_agg(dv.item_key, ', ' ORDER BY dv.item_key) INTO bad FROM draft_vehicle dv
   WHERE dv.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V300: these vehicles are not items: %', bad; END IF;

  -- And the condition vocabulary the haul gate is about to read must still be what this assumes.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='item_instance'::regclass AND contype='c'
                   AND pg_get_constraintdef(oid) LIKE '%BROKEN%') THEN
    RAISE EXCEPTION 'V300: item_instance no longer carries a BROKEN condition for the haul gate to read';
  END IF;
END $$;
