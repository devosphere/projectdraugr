-- V58: Staged assembly -- multi-stage things get somewhere to exist (M3).
--
-- material_process (V52) turns inputs into an output in one act. That is the wrong
-- shape for anything built in stages: a structure raised course by course, or a
-- craft whose parts must be made, joined, and left to set before the next step.
-- The cabin and the bow both hit this wall -- the bow especially, because one of
-- its stages is not work at all but WAITING: sinew glue has to dry for a day before
-- the bow can be strung, and no single-shot process can express "come back later".
--
-- So an assembly is a definition plus an ordered list of stages, each naming what it
-- consumes, what tool it needs, and -- for a cure stage -- how long the world must
-- turn before it completes. An instance tracks one chronicle's progress through the
-- stages of one thing, sited (a construction) or portable (a carried craft). The
-- clock a cure waits on is the same simulated tick the body and food already run on.
--
-- Structures and crafts share the schema deliberately: the bow fixture showed a
-- portable craft hits the identical staging wall a sited structure does.

CREATE TABLE assembly_definition (
    assembly_key      VARCHAR(60)  PRIMARY KEY,
    subject_kind      VARCHAR(20)  NOT NULL CHECK (subject_kind IN ('STRUCTURE','CRAFT')),
    display_name      VARCHAR(120) NOT NULL,
    -- A CRAFT is carried and yields an item; a STRUCTURE is sited and becomes a
    -- construction_project of construction_kind. The CHECK keeps the two coherent.
    portable          BOOLEAN      NOT NULL,
    produces_item_key VARCHAR(100) REFERENCES item_definition(item_key),
    construction_kind VARCHAR(100),
    domain_key        VARCHAR(60)  NOT NULL REFERENCES domain_registry(domain_key),
    keywords          VARCHAR(300) NOT NULL,   -- whole-word matched against action text
    subjects          VARCHAR(300) NOT NULL DEFAULT '',
    narration         TEXT         NOT NULL,   -- witnessed on completion
    review_state      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (review_state IN ('DRAFT','VERIFIED','NEEDS_REFINEMENT','REJECTED')),
    reviewed_at       TIMESTAMPTZ,
    CONSTRAINT assembly_shape_check CHECK (
        (subject_kind='CRAFT'     AND portable       AND produces_item_key IS NOT NULL) OR
        (subject_kind='STRUCTURE' AND NOT portable   AND construction_kind IS NOT NULL))
);

CREATE TABLE assembly_stage (
    stage_key              VARCHAR(80)  PRIMARY KEY,
    assembly_key           VARCHAR(60)  NOT NULL REFERENCES assembly_definition(assembly_key),
    stage_order            SMALLINT     NOT NULL CHECK (stage_order >= 1),
    name                   VARCHAR(120) NOT NULL,
    prerequisite_stage_key VARCHAR(80)  REFERENCES assembly_stage(stage_key),
    -- A cure stage completes on elapsed world time, not on inputs: 0 means an
    -- ordinary work stage; >0 means "wait this many minutes since the prerequisite".
    cure_minutes           INTEGER      NOT NULL DEFAULT 0 CHECK (cure_minutes >= 0),
    tool_class             VARCHAR(20)  CHECK (tool_class IS NULL OR tool_class IN ('CUTTING','STRIKING','AXE')),
    requires_fire          BOOLEAN      NOT NULL DEFAULT FALSE,
    narration              TEXT         NOT NULL,
    UNIQUE (assembly_key, stage_order)
);
CREATE INDEX assembly_stage_by_assembly ON assembly_stage(assembly_key, stage_order);

CREATE TABLE assembly_stage_requirement (
    stage_key VARCHAR(80)  NOT NULL REFERENCES assembly_stage(stage_key),
    item_key  VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    quantity  SMALLINT     NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    PRIMARY KEY (stage_key, item_key)
);

-- One chronicle's progress through one thing. object_id is the construction for a
-- sited assembly (set at completion) or the produced item for a craft.
CREATE TABLE assembly_instance (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_key VARCHAR(60)  NOT NULL REFERENCES assembly_definition(assembly_key),
    chronicle_id UUID         NOT NULL REFERENCES chronicle(id),
    location_id  UUID         REFERENCES world_chunk(id),
    object_id    UUID         REFERENCES world_object(id),
    state        VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (state IN ('IN_PROGRESS','COMPLETE','ABANDONED')),
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- At most one in-progress instance of a given assembly per chronicle, so "advance"
-- is unambiguous. (Sited kinds are still distinguished by location at query time.)
CREATE UNIQUE INDEX assembly_one_active_per_chronicle
    ON assembly_instance(chronicle_id, assembly_key) WHERE state='IN_PROGRESS';

CREATE TABLE assembly_stage_completion (
    instance_id  UUID        NOT NULL REFERENCES assembly_instance(id),
    stage_key    VARCHAR(80) NOT NULL REFERENCES assembly_stage(stage_key),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instance_id, stage_key)
);

-- ---------------------------------------------------------------------------
-- DEFINITIONS (written DRAFT; the review at the foot promotes what passes)
-- ---------------------------------------------------------------------------
INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
-- The flagship portable staged craft: a sinew-backed bow, with a real cure.
('bow','CRAFT','Sinew-backed hunting bow',TRUE,'hunting_bow',NULL,'archery',
 'build a bow,build a hunting bow,craft a bow,craft a hunting bow,work on the bow,work the bow',
 'bow,stave,limb',
 'You brace the finished bow against your foot and draw it to full for the first time. It holds, throws hard, and is finally a weapon.'),
-- A sited staged structure, defined entirely in data -- and useful: the fish
-- fixture wants somewhere to hang a catch.
('drying_rack','STRUCTURE','Drying rack',FALSE,NULL,'DRYING_RACK','construction',
 'build a drying rack,make a drying rack,raise a drying rack,drying rack,drying frame',
 'rack,frame',
 'The rack stands square and taut. What is hung here out of the sun and into the wind will keep.'),
-- Lean-to and fire pit, expressed as data to prove the migration path. Their old
-- intents (START_LEAN_TO, BUILD_FIRE_PIT) still run and are matched first, so
-- behaviour is unchanged; these rows demonstrate the same builds are now
-- expressible as staged data, ready for a later runtime cut-over.
('lean_to','STRUCTURE','Lean-to shelter',FALSE,NULL,'LEAN_TO','construction',
 'assemble a lean-to shelter in stages,staged lean-to','shelter',
 'The lean-to stands finished, its thatch turned to the weather.'),
('stone_fire_pit','STRUCTURE','Stone fire pit',FALSE,NULL,'STONE_FIRE_PIT','construction',
 'assemble a stone fire pit in stages,staged fire pit','hearth',
 'The ring of stones sits level and close, ready to hold a fire.');

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
-- bow
('bow_shape',  'bow',1,'Shape the stave',        NULL,        0,  'CUTTING',FALSE,'You work the stave down to a bow''s taper, floor-tillering as you go until both limbs bend even.'),
('bow_back',   'bow',2,'Lay on the sinew backing','bow_shape', 0,  NULL,     FALSE,'You comb the wet sinew along the back of the bow in overlapping courses and bind the tips. Now it has to be left alone.'),
('bow_cure',   'bow',3,'Let the backing cure',   'bow_back',  480,NULL,     FALSE,'The backing has dried hard and drawn the limbs into reflex. It is ready to be finished.'),
('bow_string', 'bow',4,'String and finish',      'bow_cure',  0,  'CUTTING',FALSE,'You cut the nocks, loop the string, and set the bow to brace height.'),
-- drying rack
('rack_posts', 'drying_rack',1,'Set the uprights', NULL,       0, NULL,FALSE,'You drive four uprights into the ground and check them for plumb.'),
('rack_bars',  'drying_rack',2,'Lash the crossbars','rack_posts',0,NULL,FALSE,'You lash horizontals between the posts until the frame stops racking under your hand.'),
-- lean-to (data proof)
('lean_ridge', 'lean_to',1,'Raise the ridgepole', NULL,        0, NULL,FALSE,'You set the ridgepole into the crooks of two uprights.'),
('lean_rafters','lean_to',2,'Lay the rafters',    'lean_ridge', 0, NULL,FALSE,'You lean poles against the ridge and bind them off.'),
('lean_thatch','lean_to',3,'Thatch the frame',    'lean_rafters',0,NULL,FALSE,'You work bundles of growth into the frame from the eave up.'),
-- fire pit (data proof)
('pit_ring',   'stone_fire_pit',1,'Ring the stones',NULL,      0, NULL,FALSE,'You set the stones into a close, level ring.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('bow_shape','bow_stave',1),
('bow_back','sinew_backing',1),('bow_back','hide_glue',1),
('bow_string','bowstring',1),
('rack_posts','dry_branch',4),
('rack_bars','fiber_cordage',2),
('lean_ridge','dry_branch',2),('lean_ridge','plant_fiber',1),
('lean_rafters','dry_branch',2),('lean_rafters','plant_fiber',1),
('lean_thatch','plant_fiber',2),
('pit_ring','field_stone',4);

-- Craft outputs are reachable through their assembly, exactly as material_process
-- outputs are reachable through the process (V51). Register before the review reads
-- item_source, so a brand-new craft's own output does not read as unobtainable.
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT produces_item_key, 'TECHNIQUE', 'assembly:' || assembly_key
FROM assembly_definition WHERE produces_item_key IS NOT NULL
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- THE GATE. The same spirit as V53: an assembly is not canon until reviewed, and
-- the checks are deterministic. Three ways a staged definition can be silently
-- broken -- a stage that depends on itself or a later one, an assembly with no
-- stages to run, and a stage that needs an item nobody can obtain.
-- ---------------------------------------------------------------------------
CREATE TABLE assembly_review (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_key VARCHAR(60) NOT NULL REFERENCES assembly_definition(assembly_key),
    finding_kind VARCHAR(40) NOT NULL,
    severity     VARCHAR(20) NOT NULL CHECK (severity IN ('BLOCKING','ADVISORY')),
    detail       TEXT        NOT NULL,
    raised_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX assembly_review_open ON assembly_review(assembly_key) WHERE resolved_at IS NULL;

-- Forward or self prerequisite: a stage may only depend on an earlier stage of the
-- same assembly. Anything else is a cycle or a leak across assemblies.
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'BAD_PREREQUISITE', 'BLOCKING',
       'Stage ' || s.stage_key || ' has a prerequisite that is not an earlier stage of the same assembly.'
FROM assembly_stage s JOIN assembly_stage p ON p.stage_key = s.prerequisite_stage_key
WHERE p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order;

-- No stages: nothing to advance.
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT d.assembly_key, 'NO_STAGES', 'BLOCKING', 'Assembly declares no stages.'
FROM assembly_definition d
WHERE NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = d.assembly_key);

-- Unobtainable requirement: a stage needs an item with no acquisition path (V51).
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT DISTINCT s.assembly_key, 'UNOBTAINABLE_REQUIREMENT', 'BLOCKING',
       'Stage ' || r.stage_key || ' requires ' || r.item_key || ', which has no acquisition path.'
FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
WHERE NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key = r.item_key);

-- A work stage (not a cure) with no requirements produces its result from nothing.
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'EMPTY_WORK_STAGE', 'ADVISORY',
       'Stage ' || s.stage_key || ' is a work stage with no requirements.'
FROM assembly_stage s
WHERE s.cure_minutes = 0
  AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);

UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now()
WHERE review_state='DRAFT' AND NOT EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);
UPDATE assembly_definition SET review_state='NEEDS_REFINEMENT', reviewed_at=now()
WHERE review_state='DRAFT' AND EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('staged_assembly','Staged Assembly','Multi-stage structures and crafts: an ordered list of stages, each consuming inputs or waiting out a cure, tracked per chronicle instance. The schema behind building a cabin course by course or a bow through its drying.','V58','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
