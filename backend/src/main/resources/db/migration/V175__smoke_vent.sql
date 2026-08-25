-- V175 — a roof smoke-vent: structural venting of woodsmoke from an enclosed shelter (EPIC #215, story #219
-- hazard footprints / emission mitigation; extends the #198 enclosed-fire smoke model).
--
-- Woodsmoke in an enclosed shelter with an unvented fire already fouls the air (ChroniclePhysiologyService #198): a
-- small, non-lethal pressure toward illness. But the ONLY thing that cleared it was possessing a `smoke_hood` item —
-- there was no STRUCTURAL venting. A first-era dwelling's real defence against its own hearth-smoke is a hole cut in
-- the roof: the smoke rises and leaves through it. A Chronicle who raised a proper wattle-and-daub hut and lit a fire
-- in it still choked unless they happened to hold the hood item — the hut was enclosed but had no way to breathe.
--
-- This registers a SMOKE_VENT construction. ConstructionService.buildSmokeVent cuts and daubs a smoke-hole into an
-- existing enclosing shelter, and ChroniclePhysiologyService now reads a completed SMOKE_VENT at the site as venting
-- the smoke (alongside the smoke_hood item), so the built structure earns its terminal effect: an enclosed hearth a
-- Chronicle can live beside. It is neither shelter nor workstation and weathers like any field structure (#220).
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    ('SMOKE_VENT', 'Roof smoke-vent', 'construction', FALSE, FALSE, TRUE, 'INVENTED')
ON CONFLICT (project_kind) DO NOTHING;
