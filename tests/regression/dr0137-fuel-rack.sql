-- dr0137 — the covered fuel rack is a registered, buildable construction (#127, EPIC #123).
--
-- Regression: ChronicleActionService.wetFireOdds reads a completed FUEL_RACK at the Chronicle's chunk to ease
-- the heavy rain/storm penalty on an ignition attempt — dead code unless the kind is registered and an action
-- builds one. V136 registers the kind; ConstructionService.buildFuelRack + the BUILD_FUEL_RACK intent supply the
-- acquisition path (proven end-to-end in FuelRackFireLightingIntegrationTest, which needs a runtime world). This
-- pins the schema-level fact that the kind exists and is described correctly.
--
-- Self-contained: wrapped in BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='FUEL_RACK') THEN
        RAISE EXCEPTION 'FUEL_RACK is not registered in construction_kind — the fuel rack is unbuildable and wetFireOdds''s rack check is dead code'; END IF;

    -- It keeps fuel dry: not shelter, not a workstation, and it decays like any field structure.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='FUEL_RACK' AND (is_shelter OR is_workstation OR NOT decays)) THEN
        RAISE EXCEPTION 'FUEL_RACK must be a decaying, non-shelter, non-workstation construction'; END IF;

    IF NOT EXISTS (SELECT 1 FROM construction_kind ck JOIN domain_registry d ON d.domain_key=ck.domain_key WHERE ck.project_kind='FUEL_RACK') THEN
        RAISE EXCEPTION 'FUEL_RACK references a domain that is not in the registry'; END IF;

    RAISE NOTICE 'PASS: FUEL_RACK is a registered, buildable, decaying fuel-keeping construction (#127)';
END $$;

ROLLBACK;
