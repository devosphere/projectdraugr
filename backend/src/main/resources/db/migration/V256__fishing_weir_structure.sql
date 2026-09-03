-- V256 — story #77 (group 4, key 97): fishing_weir, a buildable structure with a GENUINELY NEW effect. A weir is a
-- fixed fence of stakes and woven panels set across a stream that funnels and holds fish, so once one stands the water
-- fishes far better with no gear in hand — the weir does the work. Wired in Java (WildlifeEncounterService.fish): a
-- completed weir at the water raises the catch chance to a high floor. Pure-data one-stage STRUCTURE assembly
-- (construction_kind, decaying field structure). The keyword is 'weir' (never 'fish weir'): any 'fish' word is stolen
-- by the FISH intent before the assembly matcher runs, so the build phrase must avoid it.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('FISHING_WEIR', 'Fishing weir', 'construction', FALSE, FALSE, TRUE, 'V256')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('fishing_weir','STRUCTURE','Fishing weir',FALSE,NULL,'FISHING_WEIR','construction',
 'build a weir,stake out a weir,set out a weir,raise a weir,river weir,weir',
 'weir,river weir',
 'A fence of driven stakes and woven panels set across the shallows, angled to guide the run of fish into a narrowing trap they will not turn back out of — a fixed weir that keeps taking long after it is built, the water worked for you while you tend other things.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('fishing_weir_build','fishing_weir',1,'Drive the stakes and hang the panels',NULL,0,NULL,FALSE,'You drive a line of stakes across the shallows and hang woven panels between them, angling the fence to guide the run of fish into its narrowing trap.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('fishing_weir_build','dry_branch',4),('fishing_weir_build','plant_fiber',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;
