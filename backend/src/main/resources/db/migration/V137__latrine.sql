-- V137 — make a camp latrine buildable (#127/#218, EPIC #123 defensible-site infrastructure + sanitation).
--
-- Low hygiene already drives an illness pressure in ChroniclePhysiologyService (a grubby Chronicle sickens),
-- and hygiene decays passively with every hour — but a Chronicle had no way to keep a camp clean. advanceTo now
-- reads a completed LATRINE at the Chronicle's chunk and halves the passive hygiene loss while one stands there,
-- which in turn eases the low-hygiene illness pressure. ConstructionService.buildLatrine + the BUILD_LATRINE
-- intent supply the acquisition path in the same change. project_kind is a registry (not an FK), so registering
-- the kind keeps parity with the other constructions and lets the Auditor account for it.
--
-- A latrine keeps filth out of the living space — it is neither defence nor shelter and not a workstation. It
-- decays like any field structure and only slows grubbiness; it is no cure for illness already taken (#127).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('LATRINE', 'Camp latrine and refuse pit', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
