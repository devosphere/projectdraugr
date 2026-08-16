-- V131 — make the perimeter trip-line alarm buildable (#126/#127, EPIC #123).
--
-- WildlifeEncounterService.passiveEncounter already reads a completed CAMP_ALARM construction at the
-- Chronicle's chunk and drops a stalking predator's ambush chance by 15 — but nothing ever built one:
-- CAMP_ALARM was absent from construction_kind and no action produced it, so the effect was dead code and
-- the alarm was unreachable. This registers the kind (construction_project.project_kind is a registry, not
-- an FK, so this is documentation + parity with the other kinds); ConstructionService.buildCampAlarm and the
-- BUILD_ALARM intent (string/rig a trip-line, set a camp alarm) supply the acquisition path in the same change.
-- The alarm grants no protection — it buys warning time, turning a fatal ambush into a warning to act on.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('CAMP_ALARM', 'Perimeter trip-line alarm', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
