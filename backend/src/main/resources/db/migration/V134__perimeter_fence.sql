-- V134 — make a perimeter fence buildable (#127, EPIC #123 defensible-site infrastructure).
--
-- The camp defence catalogue had a warning layer (CAMP_ALARM, V131) and a flight layer (DISENGAGE escape), but
-- no BARRIER: nothing a Chronicle could put between themselves and a predator's rush. WildlifeEncounterService
-- .passiveEncounter now reads a completed fence at the Chronicle's chunk and drops a predator's ambush chance —
-- a woven wattle wall stands stronger than a piled brush one — and ConstructionService.buildFence + the
-- BUILD_FENCE intent supply the acquisition path in the same change. project_kind is a registry (not an FK), so
-- registering the kinds keeps parity with the other constructions and lets the Auditor account for them.
--
-- A fence is a real, decaying field structure — not shelter, not a workstation. It buys time and turns many a
-- rush aside; it is no fortress and guarantees nothing against a determined or superior being (#127 contract).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('BRUSH_FENCE',  'Brush perimeter fence',  'construction', FALSE, FALSE, TRUE, 'PREBUILT'),
    ('WATTLE_FENCE', 'Wattle perimeter fence', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
