-- #77/#220 — the siblings that forgot their bench.
--
-- THE GAP. A station is declared per process, so a family of processes done at the same bench each has to name it,
-- and five did not. Wool and plain textile are woven on the loom and linen was woven on nothing; fifty-odd pieces of
-- leather and cloth are sewn at the sewing table and the winter stock blanket was sewn in the lap; a single fletched
-- arrow is bound in the fletching jig and a bundle of four was fletched freehand; grain is ground in the quern and
-- rock salt and ochre were ground on nothing. In each case the Chronicle had built or made the thing the work is
-- done on, and it was read for every sibling but this one.
--
-- WHAT IT DOES. Names the station on those five. The station rule already in the process engine does the rest:
-- one workmanship grade truer, capped by the grade of what goes in, and a yield biased high where the yield is a
-- range (fewer shafts spoiled in the jig, less salt lost off the quern). It EASES, never gates: all five still work
-- without it, exactly as before.
--
-- WHAT IT DOES NOT DO. It does not put knapping on a bench — flint is worked on the knee over a pad of hide, and the
-- one knapping process that names the stoneworking bench is dressing stone, not flaking it. And it does not send
-- `dry_grass` to the hay rack, which is a feeder that keeps fodder out of the mud, not a drying frame.

UPDATE material_process SET station_kind = 'loom'                WHERE process_key = 'weave_linen_cloth'        AND station_kind IS NULL;
UPDATE material_process SET station_kind = 'sewing_table'        WHERE process_key = 'sew_winter_stock_blanket' AND station_kind IS NULL;
UPDATE material_process SET station_kind = 'arrow_fletching_jig' WHERE process_key = 'fletch_arrows'            AND station_kind IS NULL;
UPDATE material_process SET station_kind = 'quern_stone'         WHERE process_key IN ('grind_salt','grind_pigment') AND station_kind IS NULL;

DO $$
DECLARE bad text;
BEGIN
  -- Each of the five now names its family's bench.
  SELECT string_agg(k, ', ') INTO bad
    FROM (VALUES ('weave_linen_cloth','loom'), ('sew_winter_stock_blanket','sewing_table'),
                 ('fletch_arrows','arrow_fletching_jig'), ('grind_salt','quern_stone'), ('grind_pigment','quern_stone')) v(k, s)
   WHERE NOT EXISTS (SELECT 1 FROM material_process p WHERE p.process_key = v.k AND p.station_kind = v.s);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V342: these did not take their bench: %', bad; END IF;

  -- And every bench named is a thing someone can actually make: a station nobody can obtain is a gate in disguise
  -- for nobody and an empty promise for everyone.
  SELECT string_agg(DISTINCT s, ', ') INTO bad
    FROM (VALUES ('loom'), ('sewing_table'), ('arrow_fletching_jig'), ('quern_stone')) v(s)
   WHERE NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key = v.s);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V342: nobody can make these benches: %', bad; END IF;
END $$;
