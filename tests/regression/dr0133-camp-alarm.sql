-- dr0133 — the perimeter trip-line alarm is a registered, buildable construction (#126/#127, EPIC #123).
--
-- Regression: WildlifeEncounterService.passiveEncounter reads a completed CAMP_ALARM construction to rob a
-- stalking predator of its surprise, but for a long time nothing could build one — CAMP_ALARM was absent from
-- construction_kind and no action produced it, so the ambush-reducing effect was dead code and the alarm was
-- unreachable. V131 registers the kind; ConstructionService.buildCampAlarm + the BUILD_ALARM intent supply the
-- acquisition path (proven end-to-end in CampAlarmIntegrationTest, which needs a runtime world). This pins the
-- schema-level fact that the kind exists and is described correctly, so the gap cannot silently reopen.
--
-- Self-contained: wrapped in BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    -- The kind must exist — this is the exact row whose absence made the alarm unbuildable.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='CAMP_ALARM') THEN
        RAISE EXCEPTION 'CAMP_ALARM is not registered in construction_kind — the alarm is unbuildable and passiveEncounter''s alarm check is dead code'; END IF;

    -- It is defence-by-warning, not shelter and not a workstation, and it decays like any field structure.
    IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='CAMP_ALARM' AND (is_shelter OR is_workstation OR NOT decays)) THEN
        RAISE EXCEPTION 'CAMP_ALARM must be a decaying, non-shelter, non-workstation construction'; END IF;

    -- Its domain must be a real registered domain (the construction_kind.domain_key FK, restated as intent).
    IF NOT EXISTS (SELECT 1 FROM construction_kind ck JOIN domain_registry d ON d.domain_key=ck.domain_key WHERE ck.project_kind='CAMP_ALARM') THEN
        RAISE EXCEPTION 'CAMP_ALARM references a domain that is not in the registry'; END IF;

    RAISE NOTICE 'PASS: CAMP_ALARM is a registered, buildable, decaying defence construction (#126/#127)';
END $$;

ROLLBACK;
