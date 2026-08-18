-- dr0135 — the perimeter fence is a registered, buildable barrier construction (#127, EPIC #123).
--
-- Regression: WildlifeEncounterService.passiveEncounter reads a completed BRUSH_FENCE / WATTLE_FENCE at the
-- Chronicle's chunk and drops a predator's ambush chance (the barrier layer alongside the alarm's warning and
-- the escape's flight) — but the effect is dead code unless the kinds are registered and an action builds them.
-- V134 registers both kinds; ConstructionService.buildFence + the BUILD_FENCE intent supply the acquisition
-- path (proven end-to-end in FenceDeterrentIntegrationTest, which needs a runtime world). This pins the
-- schema-level fact that both kinds exist and are described correctly, so the gap cannot silently reopen.
--
-- Self-contained: wrapped in BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    -- Both kinds must exist — their absence is what would make a built fence unreadable by the ambush check.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='BRUSH_FENCE') THEN
        RAISE EXCEPTION 'BRUSH_FENCE is not registered in construction_kind — the brush fence is unbuildable and passiveEncounter''s fence check is dead code'; END IF;
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='WATTLE_FENCE') THEN
        RAISE EXCEPTION 'WATTLE_FENCE is not registered in construction_kind — the wattle fence is unbuildable and passiveEncounter''s fence check is dead code'; END IF;

    -- A fence is a barrier, not shelter and not a workstation, and it decays like any field structure.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind IN ('BRUSH_FENCE','WATTLE_FENCE') AND (is_shelter OR is_workstation OR NOT decays)) THEN
        RAISE EXCEPTION 'a fence must be a decaying, non-shelter, non-workstation construction'; END IF;

    -- Each references a real registered domain (the construction_kind.domain_key FK, restated as intent).
    IF EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind IN ('BRUSH_FENCE','WATTLE_FENCE')
              AND NOT EXISTS (SELECT 1 FROM domain_registry d WHERE d.domain_key=ck.domain_key)) THEN
        RAISE EXCEPTION 'a fence kind references a domain that is not in the registry'; END IF;

    RAISE NOTICE 'PASS: BRUSH_FENCE and WATTLE_FENCE are registered, buildable, decaying barrier constructions (#127)';
END $$;

ROLLBACK;
