-- V263 — story #106 (sanitation infrastructure for kept stock): manure_pit and compost_bay.
-- Tamed draft beasts stood at a camp produced nothing at all: the ground never grew foul however long stock were
-- kept on it, which is the one certainty of keeping animals. PhysicalItemService.foulGroundWithLivestock now raises
-- chunk_refuse where tamed draft stock are kept — and a completed manure pit or compost bay CONTAINS it, so the
-- muck is gathered instead of trodden through the camp.
--
-- This matters because refuse is already wired to real consequences: it draws wildlife (WildlifeEncounterService),
-- costs the body condition (ChroniclePhysiologyService), and lets pests dock the shelf life of stored food
-- (FoodPreservationService). Keeping stock without mucking out now has those costs; digging a pit answers them.
-- MAINTAIN_CAMP already clears refuse, so a fouled camp is always recoverable.
--
-- Routing: BUILD_LATRINE claims (dig|build|make|…) with latrine/privy/cesspit/refuse pit/waste pit/rubbish pit/
-- toilet pit/midden. 'manure pit' and 'compost bay' are none of those, so both reach the assembly matcher.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('MANURE_PIT',  'Manure pit',  'construction', FALSE, FALSE, TRUE, 'V263'),
('COMPOST_BAY', 'Compost bay', 'construction', FALSE, FALSE, TRUE, 'V263')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('manure_pit','STRUCTURE','Manure pit',FALSE,NULL,'MANURE_PIT','construction',
 'dig a manure pit,build a manure pit,manure pit,dung pit,muck pit',
 'manure pit,dung pit,muck pit',
 'A pit dug downwind of the sleeping ground, banked so it will not wash back, where the muck from the stock is forked and left to rot down instead of being trodden through the camp.','VERIFIED',now()),
('compost_bay','STRUCTURE','Compost bay',FALSE,NULL,'COMPOST_BAY','construction',
 'build a compost bay,make a compost bay,compost bay,muck bay',
 'compost bay,muck bay',
 'A slatted bay of stakes and brush that holds a heap of muck and bedding together while it heats and rots down, keeping the camp clean and the heap where it was put.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('manure_pit_build','manure_pit',1,'Dig and bank the pit',NULL,0,NULL,FALSE,'You dig a pit downwind of the sleeping ground and bank its lip so the wet will not wash back through the camp.'),
('compost_bay_build','compost_bay',1,'Stake and slat the bay',NULL,0,NULL,FALSE,'You drive stakes and weave brush between them into a bay that will hold a heap of muck and bedding together while it rots down.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('manure_pit_build','dry_branch',2),
('compost_bay_build','dry_branch',3),('compost_bay_build','fiber_cordage',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;
