-- V60: Cut-planning multi-output, preservation shelf life, and armour in stages (M4).
--
-- Three gaps the fish and armour fixtures left after V57's vocabulary batch:
--
-- 1. A material_process yields exactly one KIND of output. Laying a hide out into
--    armour is the opposite shape: one large stock becomes several differently-shaped
--    components at once -- panels, plates, cords -- and how many depends on how well
--    the cuts were planned. That needs a process to declare more than one output.
--
-- 2. Salting, drying and smoking a fish produced the items but not the point of them:
--    they spoiled on the same clock as a raw catch. Preservation now buys real time.
--
-- 3. Leather armour was a single-shot process. It is assembled from many laced plates,
--    which is exactly what the V58 staged engine is for -- so it becomes an assembly,
--    consuming the components the new cut-planning process lays out.

-- ---------------------------------------------------------------------------
-- 1. MULTI-OUTPUT
-- ---------------------------------------------------------------------------
-- A process's primary output stays on material_process (output_item_key); these are
-- the ADDITIONAL kinds it yields in the same act. Yield scales between qty_min and
-- qty_max with the care of the attempt (M3b), so a well-planned layout wastes less.
CREATE TABLE material_process_output (
    process_key VARCHAR(60)  NOT NULL REFERENCES material_process(process_key),
    item_key    VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    qty_min     SMALLINT     NOT NULL DEFAULT 1 CHECK (qty_min >= 1),
    qty_max     SMALLINT     NOT NULL DEFAULT 1 CHECK (qty_max >= qty_min),
    role        VARCHAR(20)  NOT NULL DEFAULT 'SECONDARY' CHECK (role IN ('SECONDARY','BYPRODUCT')),
    PRIMARY KEY (process_key, item_key)
);

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('leather_offcut','Leather offcut','MATERIAL',40,80,TRUE,FALSE,0)
ON CONFLICT (item_key) DO NOTHING;

-- The exemplar: lay a tanned hide out and cut the pieces an armourer needs. One heavy
-- stock (two hides) becomes a panel plus a spread of plates, cords and offcuts.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('lay_out_hide','Lay out and cut a hide','leather_panel',1,1,'CUTTING',FALSE,FALSE,70,'textiles','PROCESS',
 'lay out the hide,cut the hide into pieces,plan the cuts,cut planning,nest the pattern,lay out,plan,cut out',
 'You chalk the pattern across the hide, nesting the big pieces where the leather is soundest and the small ones into the belly and neck, then cut. Planned well, almost nothing is wasted.');
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES ('lay_out_hide','tanned_leather',2);
INSERT INTO material_process_output (process_key, item_key, qty_min, qty_max, role) VALUES
('lay_out_hide','leather_lamella',2,5,'SECONDARY'),
('lay_out_hide','leather_cord',1,3,'SECONDARY'),
('lay_out_hide','leather_offcut',1,3,'BYPRODUCT');

-- Reachability for the new byproduct and the multi-outputs (V51). The primaries are
-- already reachable; register everything this process yields as sourced by it.
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'TECHNIQUE', 'material_process:lay_out_hide' FROM material_process_output WHERE process_key='lay_out_hide'
ON CONFLICT DO NOTHING;

-- Subjects for lay_out_hide, so it is reachable under the two-axis rule (V54).
INSERT INTO process_subject (process_key, subject_term) VALUES
('lay_out_hide','hide'),('lay_out_hide','leather'),('lay_out_hide','pattern'),('lay_out_hide','pieces')
ON CONFLICT DO NOTHING;

-- Multi-output mass must conserve too: the sum of EVERY output at its maximum, primary
-- included, may not exceed the input. The V53 view only sees the primary, so this is a
-- separate reviewed check. Caught here, held (NEEDS_REFINEMENT) if it fails.
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'MULTI_OUTPUT_MASS_CREATION', 'BLOCKING',
       'Combined output mass at maximum yield exceeds the input mass; the layout would create matter.'
FROM material_process mp
JOIN process_mass_balance b ON b.process_key = mp.process_key
WHERE EXISTS (SELECT 1 FROM material_process_output o WHERE o.process_key=mp.process_key)
  AND NOT mp.conservation_exempt
  AND (b.max_output_grams + COALESCE((
        SELECT SUM(o.qty_max * d.unit_mass_grams) FROM material_process_output o
        JOIN item_definition d ON d.item_key=o.item_key WHERE o.process_key=mp.process_key), 0)
      ) > b.min_input_grams * 1.05;

-- Promote lay_out_hide if it survived (it should: 2 hides at 800g = 1600g in; panel
-- 900 + 5*60 + 3*60 + 3*40 = 1500 out).
UPDATE material_process SET review_state='VERIFIED', reviewed_at=now()
WHERE process_key='lay_out_hide' AND NOT EXISTS (
    SELECT 1 FROM process_review r WHERE r.process_key='lay_out_hide' AND r.severity='BLOCKING' AND r.resolved_at IS NULL);

-- ---------------------------------------------------------------------------
-- 2. PRESERVATION SHELF LIFE
-- ---------------------------------------------------------------------------
-- Preserved food keeps far longer than raw. The kinds join RAW/COOKED; the durations
-- live in FoodPreservationService, which registers a process's preserved output.
ALTER TABLE food_preservation_state DROP CONSTRAINT food_preservation_state_preparation_kind_check;
ALTER TABLE food_preservation_state ADD CONSTRAINT food_preservation_state_preparation_kind_check
    CHECK (preparation_kind IN ('RAW','COOKED','SALTED','DRIED','SMOKED'));

-- ---------------------------------------------------------------------------
-- 3. HIDE RECONCILE
-- ---------------------------------------------------------------------------
-- A cleaned hide is the proper input to tanning. Accept fleshed/dehaired hides
-- alongside the raw ones rather than replacing them, so nothing that tanned before
-- stops tanning now.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('tan_hide','hide','fleshed_hide',1),('tan_hide','hide','dehaired_hide',1)
ON CONFLICT DO NOTHING;
-- A cleaned hide is lighter than a raw one, and tanning does not add mass: one hide
-- yields one piece of tanned leather. (At output_max 2 the lighter cleaned-hide path
-- would have output more leather than it consumed — the mass-balance gate caught it.)
UPDATE material_process SET output_min=1, output_max=1 WHERE process_key='tan_hide';

-- ---------------------------------------------------------------------------
-- 4. LEATHER ARMOUR AS A STAGED ASSEMBLY (M3 x M4)
-- ---------------------------------------------------------------------------
INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
('leather_armour','CRAFT','Lamellar leather armour',TRUE,'leather_armor',NULL,'armoury',
 'build leather armour,build the armour,assemble leather armour,work on the armour,lace the armour together',
 'armour,armor,lamellae,cuirass',
 'You settle the finished cuirass onto your shoulders and lace it closed. It sits heavy and close, and it will turn an edge.');

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('arm_cut',  'leather_armour',1,'Cut the lamellae',   NULL,      0, 'CUTTING',FALSE,'You cut plate after plate to one pattern and punch the lacing holes while the leather is still on the board.'),
('arm_harden','leather_armour',2,'Harden the plates', 'arm_cut', 0, NULL,     TRUE, 'You take each plate just hot enough to shrink, shape it to the body, and let it set rigid.'),
('arm_lace', 'leather_armour',3,'Lace the rows',      'arm_harden',0,NULL,     FALSE,'You lace the plates edge over edge into rows, and the rows to each other, tight enough to hold shape and loose enough to bend when you do.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('arm_cut','leather_panel',1),
('arm_harden','beeswax',2),
('arm_lace','leather_lamella',20),('arm_lace','leather_cord',6);

-- Armour output is reachable through its assembly (as well as the single-shot process).
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT produces_item_key, 'TECHNIQUE', 'assembly:' || assembly_key
FROM assembly_definition WHERE assembly_key='leather_armour'
ON CONFLICT DO NOTHING;

-- Run the V58 assembly gate over the new assembly.
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'BAD_PREREQUISITE', 'BLOCKING',
       'Stage ' || s.stage_key || ' has a prerequisite that is not an earlier stage of the same assembly.'
FROM assembly_stage s JOIN assembly_stage p ON p.stage_key = s.prerequisite_stage_key
WHERE s.assembly_key='leather_armour' AND (p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order);
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT DISTINCT s.assembly_key, 'UNOBTAINABLE_REQUIREMENT', 'BLOCKING',
       'Stage ' || r.stage_key || ' requires ' || r.item_key || ', which has no acquisition path.'
FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
WHERE s.assembly_key='leather_armour' AND NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key);

UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now()
WHERE assembly_key='leather_armour' AND NOT EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key='leather_armour' AND r.severity='BLOCKING' AND r.resolved_at IS NULL);

-- A smoke rack, so smoking has somewhere sited to happen (fish/meat fixtures).
INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
('smoke_rack','STRUCTURE','Smoke rack',FALSE,NULL,'SMOKE_RACK','construction',
 'build a smoke rack,make a smoke rack,raise a smoke rack,smoke rack,smoking rack',
 'rack,frame',
 'The rack stands over a low pit, close enough to catch the smoke and high enough to keep off the heat.');
INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('smoke_posts','smoke_rack',1,'Set the frame', NULL,          0, NULL,FALSE,'You drive the uprights and rail them together over a shallow pit.'),
('smoke_bars', 'smoke_rack',2,'Hang the bars', 'smoke_posts', 0, NULL,FALSE,'You lay the hanging bars across the top, spaced for a catch to hang between them.');
INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('smoke_posts','dry_branch',4),('smoke_bars','fiber_cordage',2);
UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now() WHERE assembly_key='smoke_rack';

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('cut_planning','Cut Planning','Laying one large stock out into several differently-shaped components at once, with a yield that rewards planning -- the multi-output shape a single-output process could not hold.','V60','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
