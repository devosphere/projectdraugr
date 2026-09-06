-- #77/#220 — the rack you built does the work.
--
-- SMOKE_RACK and DRYING_RACK are buildable construction kinds with staged assemblies behind them, and they were
-- read by nothing at all: no Java named either, and no process asked for them. A Chronicle could cut the rods,
-- raise a smoke rack, and smoke exactly as well as they had over a bare fire — while three smoking processes and
-- five drying processes ran beside it and never noticed it was there.
--
-- Meanwhile smoking and drying are two of the four preservation tiers (SMOKED 30 days, DRIED 45), so these are
-- the structures that make keeping food through a winter worth building for, and they did nothing.
--
-- A station EASES work and never gates it — that is the existing contract and it is the right one here. You can
-- smoke fish over a fire with no rack at all; the rack means better work and more of it, which is exactly what a
-- rack is for. So no process becomes unreachable by this and nothing that worked before stops working.
--
-- station_kind has always held an ITEM key (a loom, a quern, a sewing table) mapped to a structure by a small
-- hand-written switch. A rack has no item twin — it is only ever raised — so builtStationAt now also accepts a
-- station_kind that names a construction kind directly, looked up in the registry.

UPDATE material_process SET station_kind = 'SMOKE_RACK'
 WHERE process_key IN ('smoke_fish', 'smoke_meat', 'smoke_fowl') AND station_kind IS NULL;

UPDATE material_process SET station_kind = 'DRYING_RACK'
 WHERE process_key IN ('dry_fish', 'dry_meat', 'dry_herbs', 'dry_mushrooms') AND station_kind IS NULL;

-- dry_grass is deliberately left alone: grass is dried where it is cut, spread on the ground in the sun, and a
-- rack is not what anybody uses for it. course_dry_stone is not drying at all — it is laying a dry-stone course.

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT count(*) INTO n FROM material_process WHERE station_kind IN ('SMOKE_RACK','DRYING_RACK');
  IF n <> 7 THEN RAISE EXCEPTION 'V285: expected seven rack-worked processes, found %', n; END IF;

  -- Both racks must exist in the registry, or builtStationAt looks them up and finds nothing.
  SELECT string_agg(k, ', ') INTO bad FROM unnest(ARRAY['SMOKE_RACK','DRYING_RACK']) k
   WHERE k NOT IN (SELECT project_kind FROM construction_kind);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V285: these racks are named as stations but are not buildable kinds: %', bad; END IF;

  -- And each must still be raisable: a station nobody can build is a station nobody can use.
  SELECT string_agg(k, ', ') INTO bad FROM unnest(ARRAY['SMOKE_RACK','DRYING_RACK']) k
   WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad WHERE ad.construction_kind = k);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V285: these racks have no assembly to raise them: %', bad; END IF;

  -- A station eases and never gates, so none of these may have become impossible without one: they keep their
  -- inputs and their tools exactly as before, and this asserts the one thing that would break that contract.
  IF EXISTS (SELECT 1 FROM material_process WHERE station_kind IN ('SMOKE_RACK','DRYING_RACK') AND review_state <> 'VERIFIED') THEN
    RAISE EXCEPTION 'V285: a rack-worked process must stay VERIFIED — easing work cannot unverify a recipe';
  END IF;
END $$;
