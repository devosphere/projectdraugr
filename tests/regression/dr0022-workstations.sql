-- Regression: workstations EASE crafts (V69, DR-0022 Layer 4b) — they never gate, never set grade.
--
-- Pins the V69 data: station_kind is populated on the precision/large operations a bench/loom realistically
-- eases (woodworking joinery, stoneworking, weaving), the three buildable workstations exist, and there is NO
-- "station required" column on material_process (a station is optional efficiency, not a gate — the settled
-- principle). Runs against the migrated schema; any RAISE exits non-zero under ON_ERROR_STOP.

BEGIN;

DO $$
DECLARE ww int; sw int; lm int; benches int; has_required_col int;
BEGIN
    SELECT count(*) INTO ww FROM material_process WHERE station_kind = 'woodworking_bench';
    SELECT count(*) INTO sw FROM material_process WHERE station_kind = 'stoneworking_bench';
    SELECT count(*) INTO lm FROM material_process WHERE station_kind = 'loom';
    SELECT count(*) INTO benches FROM item_definition WHERE item_key IN ('woodworking_bench','stoneworking_bench','loom');

    IF ww < 5 THEN RAISE EXCEPTION 'REGRESSION: woodworking_bench eases too few processes (% , expected >=5 joinery ops)', ww; END IF;
    IF sw < 1 THEN RAISE EXCEPTION 'REGRESSION: stoneworking_bench station_kind not populated (%)', sw; END IF;
    IF lm < 1 THEN RAISE EXCEPTION 'REGRESSION: loom station_kind not populated (%)', lm; END IF;
    IF benches <> 3 THEN RAISE EXCEPTION 'REGRESSION: the 3 workstation item_definitions are not all present (%)', benches; END IF;

    -- The gate-guard: material_process must NOT carry a "station required" NOT NULL column. A station eases,
    -- never gates; bare-handed always works. If someone adds a required-station column, this catches it.
    SELECT count(*) INTO has_required_col FROM information_schema.columns
     WHERE table_name = 'material_process' AND column_name IN ('station_required','requires_station');
    IF has_required_col <> 0 THEN RAISE EXCEPTION 'REGRESSION: material_process gained a station-REQUIRED column — workstations must only EASE, never gate'; END IF;

    RAISE NOTICE 'PASS: station_kind eases woodworking(%)/stoneworking(%)/loom(%); 3 benches present; never a gate', ww, sw, lm;
END $$;

ROLLBACK;
