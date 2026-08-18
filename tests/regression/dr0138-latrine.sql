-- dr0138 — the camp latrine is a registered, buildable sanitation construction (#127/#218, EPIC #123).
--
-- Regression: ChroniclePhysiologyService.advanceTo reads a completed LATRINE at the Chronicle's chunk to halve
-- the passive hygiene loss (which in turn eases the low-hygiene illness pressure) — dead code unless the kind is
-- registered and an action builds one. V137 registers the kind; ConstructionService.buildLatrine + the
-- BUILD_LATRINE intent supply the acquisition path (proven end-to-end in LatrineHygieneIntegrationTest, which
-- needs a runtime world). This pins the schema-level fact that the kind exists and is described correctly.
--
-- Self-contained: wrapped in BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='LATRINE') THEN
        RAISE EXCEPTION 'LATRINE is not registered in construction_kind — the latrine is unbuildable and advanceTo''s hygiene check is dead code'; END IF;

    -- It keeps filth from the living space: not shelter, not a workstation, and it decays like any field structure.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='LATRINE' AND (is_shelter OR is_workstation OR NOT decays)) THEN
        RAISE EXCEPTION 'LATRINE must be a decaying, non-shelter, non-workstation construction'; END IF;

    IF NOT EXISTS (SELECT 1 FROM construction_kind ck JOIN domain_registry d ON d.domain_key=ck.domain_key WHERE ck.project_kind='LATRINE') THEN
        RAISE EXCEPTION 'LATRINE references a domain that is not in the registry'; END IF;

    RAISE NOTICE 'PASS: LATRINE is a registered, buildable, decaying sanitation construction (#127/#218)';
END $$;

ROLLBACK;
