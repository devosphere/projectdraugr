-- dr0136 — the raised lookout is a registered, buildable observation construction (#127/#128, EPIC #123).
--
-- Regression: WildlifeEncounterService.scoutBoundary reads a completed LOOKOUT at the Chronicle's chunk to
-- extend the boundary scout a second chunk out (and read it in full detail) — dead code unless the kind is
-- registered and an action builds one. V135 registers the kind; ConstructionService.buildLookout + the
-- BUILD_LOOKOUT intent supply the acquisition path (proven end-to-end in LookoutScoutIntegrationTest, which
-- needs a runtime world). This pins the schema-level fact that the kind exists and is described correctly.
--
-- Self-contained: wrapped in BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='LOOKOUT') THEN
        RAISE EXCEPTION 'LOOKOUT is not registered in construction_kind — the lookout is unbuildable and scoutBoundary''s lookout check is dead code'; END IF;

    -- A lookout changes what can be seen: not shelter, not a workstation, and it decays like any field structure.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='LOOKOUT' AND (is_shelter OR is_workstation OR NOT decays)) THEN
        RAISE EXCEPTION 'LOOKOUT must be a decaying, non-shelter, non-workstation construction'; END IF;

    IF NOT EXISTS (SELECT 1 FROM construction_kind ck JOIN domain_registry d ON d.domain_key=ck.domain_key WHERE ck.project_kind='LOOKOUT') THEN
        RAISE EXCEPTION 'LOOKOUT references a domain that is not in the registry'; END IF;

    RAISE NOTICE 'PASS: LOOKOUT is a registered, buildable, decaying observation construction (#127/#128)';
END $$;

ROLLBACK;
