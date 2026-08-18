-- V136 — make a covered fuel rack buildable (#127, EPIC #123 defensible-site infrastructure).
--
-- Fire-lighting already takes a heavy rain/storm penalty (ChronicleActionService quarters the ignition odds
-- when the method needs dry material and the sky is wet), but a Chronicle had no way to answer it — no way to
-- keep dry fuel against the weather. ChronicleActionService.wetFireOdds now reads a completed FUEL_RACK at the
-- chunk and eases that penalty (dry kindling off a covered rack still takes a spark), and
-- ConstructionService.buildFuelRack + the BUILD_FUEL_RACK intent supply the acquisition path in the same change.
-- project_kind is a registry (not an FK), so registering the kind keeps parity with the other constructions and
-- lets the Auditor account for it.
--
-- A fuel rack is neither defence nor shelter and not a workstation — it keeps fuel dry. It decays like any field
-- structure and only eases the wet-weather penalty; it is no help in fair weather (#127 contract).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('FUEL_RACK', 'Covered fuel rack', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
