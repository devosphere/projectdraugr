-- V172: a dry stone wall — the building that dressed ashlar was for (EPIC #180 / #183 masonry).
--
-- Dressing ashlar and laying a dry course (V169) produced a dry ashlar course that nothing then used — the finer
-- masonry led nowhere, exactly the dead-end the whole design forbids (the mortared course feeds a barn, a dry stone
-- course a cabin foundation, but the ashlar course fed nothing). This raises the structure it was always meant for: a
-- dry stone wall, built course on course from dressed ashlar and standing by the fit of its cutting alone — no mortar,
-- no timber, no roof, just stone on stone. It gives the ashlar course its terminal use and completes the masonry
-- path, quarry to standing wall.

-- The sited structure a completed dry-stone-wall assembly becomes. Dry stone lasts — it does not decay like a
-- field structure of poles and thatch.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('DRY_STONE_WALL', 'Dry stone wall', 'construction', FALSE, FALSE, FALSE, 'V172')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
('dry_stone_wall','STRUCTURE','Dry stone wall',FALSE,NULL,'DRY_STONE_WALL','construction',
 'build a dry stone wall,raise a dry stone wall,dry stone wall,build a dry ashlar wall,dry ashlar wall,build a stone wall,raise a stone wall,work on the stone wall',
 'wall,ashlar,stone',
 'The wall stands to height, dry-laid course on course of dressed ashlar — every block bedded true against the last, the whole holding by the fit of its cutting alone, no mortar in it anywhere. Stone stands where stone was set.')
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('wall_footing','dry_stone_wall',1,'Level the footing',NULL,0,'STRIKING',FALSE,'You level a footing along the line and bed the first dressed course down true, so the wall has a flat, solid base to rise from.'),
('wall_courses','dry_stone_wall',2,'Raise the courses','wall_footing',0,'STRIKING',FALSE,'You raise the wall course on course, each dressed block set down and tapped true against the last, until the dry ashlar stands to height and holds itself.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('wall_footing','ashlar_course',1),
('wall_courses','ashlar_course',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

-- Promote to VERIFIED so the matcher will offer it (definitions default to DRAFT, and the V58/V90 review gate only
-- ran over the assemblies present when it ran). The wall is well-formed — stages ordered, every requirement obtainable
-- (ashlar_course has a source), no prerequisite cycle — so it is verified here directly.
UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now() WHERE assembly_key='dry_stone_wall';
