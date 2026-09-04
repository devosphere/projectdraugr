-- V262 — story #106 (feeding infrastructure): hay_rack and fodder_store.
-- Fodder shaken out on bare ground is trampled into the mud and soiled, and most of a bundle is wasted. A rack holds
-- it at muzzle height and a covered store keeps it dry, so the same bundle goes much further. The effect is wired in
-- PhysicalItemService.feedDraftBeasts: with either structure standing at the keeper's ground, one bundle of cut
-- fodder eases far more hunger. It deliberately does NOT feed beasts by itself — that would create food from nothing.
-- Real fodder is still cut, carried, and consumed; the structure only stops it being wasted.
--
-- Routing: 'build a hay rack' would otherwise be taken by CRAFT_SHELF, which claims (build|make|craft…) + 'rack'.
-- That guard already excludes the fuel/wood/firewood/log/kindling racks, and 'hay rack' is added to the same list.
-- 'build a fodder store' is clear of BUILD_STORAGE_AREA, which needs 'storage area'/'store area'/'storehouse'/
-- 'resource store'/'supply store'.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('HAY_RACK',     'Hay rack',     'construction', FALSE, FALSE, TRUE, 'V262'),
('FODDER_STORE', 'Fodder store', 'construction', FALSE, FALSE, TRUE, 'V262')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('hay_rack','STRUCTURE','Hay rack',FALSE,NULL,'HAY_RACK','construction',
 'build a hay rack,raise a hay rack,set up a hay rack,hay rack,fodder rack',
 'hay rack,fodder rack',
 'A slatted cradle set at muzzle height, so the beasts pull their fodder from it instead of treading it into the mud — most of a bundle is eaten rather than wasted.','VERIFIED',now()),
('fodder_store','STRUCTURE','Fodder store',FALSE,NULL,'FODDER_STORE','construction',
 'build a fodder store,raise a fodder store,fodder store,hay store',
 'fodder store,hay store',
 'A raised, roofed stack that keeps cut fodder off the wet ground and out of the rain, so it stays sweet instead of going black and sour before it can be fed out.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('hay_rack_build','hay_rack',1,'Frame and slat the rack',NULL,0,NULL,FALSE,'You frame a cradle at muzzle height and slat it, close enough to hold the fodder and open enough for the beasts to pull it through.'),
('fodder_store_build','fodder_store',1,'Raise and roof the stack',NULL,0,NULL,FALSE,'You raise a platform clear of the wet ground and roof it over, so the cut fodder stacked on it stays dry and sweet.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('hay_rack_build','dry_branch',3),('hay_rack_build','fiber_cordage',2),
('fodder_store_build','timber_log',1),('fodder_store_build','dry_branch',2),('fodder_store_build','bark_sheet',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;
