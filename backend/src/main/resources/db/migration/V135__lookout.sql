-- V135 — make a raised lookout buildable (#127, EPIC #123 defensible-site infrastructure).
--
-- The survival-observation catalogue gained a boundary scout (#128 SCOUT: read a predator one chunk out), but
-- nothing let a Chronicle lift the eye above the near treeline to see further. WildlifeEncounterService
-- .scoutBoundary now reads a completed LOOKOUT at the Chronicle's chunk and, from its height, reports danger a
-- second chunk out along each way and always in full detail — but that is dead code unless the kind is
-- registered and an action builds one. ConstructionService.buildLookout and the BUILD_LOOKOUT intent supply the
-- acquisition path in the same change. project_kind is a registry (not an FK), so registering the kind keeps
-- parity with the other constructions and lets the Auditor account for it.
--
-- A lookout is neither defence nor shelter and not a workstation — it changes what can be seen. It decays like
-- any field structure and buys warning distance, nothing more (#127 contract).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('LOOKOUT', 'Raised lookout', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
