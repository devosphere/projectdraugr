-- V47: Phase 0 heritage — the Wolf Kingdom's proven technology, folded into the schema.
--
-- The GPT-driven prototype (Days 1–10, "Wolf Kingdom") reached a working stone-age
-- settlement: cutting tools, carrying equipment, four specialised workstations, a
-- stone archive, and a documented friction-fire technique. That playthrough is the
-- evidence that these domains are reachable and worth having.
--
-- Per the monotonic-schema decision (DR-0013 / F8), a domain that has been invented
-- anywhere in the world's history gets its schema ONCE, globally. This migration is
-- that act for everything Phase 0 proved out, so the Persistent State Architect never
-- has to re-derive it.
--
-- WHAT THIS MIGRATION DELIBERATELY DOES NOT DO
-- It grants no chronicle any skill. Phase 0 recorded "Woodworking [Operational]" for
-- its chronicle; that familiarity died with them, per Rule 7 and the F8 decision. What
-- survives is the world's catalogue of what CAN be done — not anyone's ability to do
-- it. A new chronicle still starts at zero familiarity and must earn every technique
-- through the three-layer success model, exactly as before.
--
-- Source: database/current/*.json from projectdraugr-day6-10.zip; full provenance in
-- docs/systems/15-Phase0-Heritage.md.

-- ---------------------------------------------------------------------------
-- 1. BUG FIX: axe-class tools referenced by code but never defined.
--
-- PhysicalItemService.hasCuttingTool() has always tested for 'stone_hatchet', and
-- fellTree() (V39) tests for 'stone_axe'/'stone_hatchet'. Neither had an
-- item_definition row, so no such item could exist and FELL_TREE could never
-- succeed. Phase 0 crafted a "Primitive Stone Hatchet" on Day 5, which is the
-- proof this belongs in the world.
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('stone_hatchet', 'Stone hatchet', 'TOOL',  980, 1400, FALSE, TRUE),
('stone_axe',     'Stone axe',     'TOOL', 1650, 2400, FALSE, TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('stone_hatchet','HAND_RIGHT','ATTACHED'), ('stone_hatchet','WAIST','ATTACHED'),
('stone_axe','HAND_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Carrying and equipment the Wolf Kingdom built beyond the basic basket.
-- The utility belt reached Revision III in Phase 0 through the REFINE intent,
-- which already exists — it needs only a definition to hang revisions on.
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('utility_belt',     'Primitive utility belt',      'EQUIPMENT',  420,  1800, FALSE, TRUE),
('backpack_basket',  'Primitive backpack basket',   'CONTAINER', 1400, 26000, FALSE, TRUE),
('large_basket',     'Large hand-carry basket',     'CONTAINER', 1600, 34000, FALSE, TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('utility_belt','WAIST','OUTER'),
('backpack_basket','BACK','CARRIED'),
('large_basket','HAND_LEFT','CARRIED'), ('large_basket','HAND_RIGHT','CARRIED')
ON CONFLICT DO NOTHING;

-- Default capacities for container item kinds, so a container's size is a property
-- of what it IS rather than a number repeated at every craft site.
CREATE TABLE container_capacity_default (
    item_key       VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    max_mass_grams INTEGER NOT NULL CHECK (max_mass_grams > 0),
    max_volume_ml  INTEGER NOT NULL CHECK (max_volume_ml > 0)
);

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('woven_basket',    12000, 18000),
('backpack_basket', 22000, 26000),
('large_basket',    30000, 34000)
ON CONFLICT (item_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Workstations and storage structures. Phase 0 built four specialised tables
-- and a tool shed, and recorded the principle that dedicated workstations improve
-- specialised crafting. They are furniture-class objects placed at a location.
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('woodworking_table',  'Woodworking table',   'FURNITURE', 26000, 140000, FALSE, FALSE),
('stoneworking_table', 'Stoneworking table',  'FURNITURE', 48000, 130000, FALSE, FALSE),
('weaving_table',      'Weaving work table',  'FURNITURE', 19000, 120000, FALSE, FALSE),
('sewing_table',       'Sewing work table',   'FURNITURE', 17000, 110000, FALSE, FALSE),
('tool_shed',          'Small wooden tool shed','FURNITURE',95000, 400000, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Material grades the settlement learned to distinguish. Phase 0 recorded the
-- principle that stone tools require balancing durability against availability —
-- which only means anything if stone comes in grades.
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('precision_tool_stone',  'Precision tool stone',       'MATERIAL',  620,  260, TRUE,  FALSE),
('foundation_stone',      'Heavy foundation stone',     'MATERIAL', 4200, 1900, FALSE, FALSE),
('construction_stone',    'Medium construction stone',  'MATERIAL', 1800,  850, FALSE, FALSE),
('timber_log',            'Timber log',                 'MATERIAL', 9000, 42000, FALSE, FALSE),
('vine',                  'Vine',                       'MATERIAL',   60,  240, TRUE,  FALSE),
('fiber_cordage',         'Processed fiber cordage',    'MATERIAL',  110,  300, TRUE,  FALSE),
('wooden_component',      'Processed wooden component', 'MATERIAL',  700, 2200, TRUE,  FALSE),
('structural_timber',     'Reinforced structural timber','MATERIAL',3400, 12000, FALSE, FALSE),
('textile_material',      'Textile processing material','MATERIAL',  180,  900, TRUE,  FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. The technique catalogue — the Architect's ledger of what this world already
-- knows is possible.
--
-- This is NOT capability and NOT a skill tree. It records that a technique has
-- been proven reachable, what domain it belongs to, and what it needs. A chronicle
-- who has never done it still fails until their own hands learn it: the gate stays
-- physical (materials, tools, environment) plus the three-layer success model.
-- Its purpose is to stop the Architect re-inventing pottery every time someone
-- fires clay.
-- ---------------------------------------------------------------------------
CREATE TABLE technique_definition (
    technique_key   VARCHAR(80)  PRIMARY KEY,
    display_name    VARCHAR(160) NOT NULL,
    domain_key      VARCHAR(60)  NOT NULL REFERENCES domain_registry(domain_key),
    difficulty      VARCHAR(20)  NOT NULL,
    produces_item   VARCHAR(100) NULL REFERENCES item_definition(item_key),
    requires_tool   VARCHAR(40)  NULL,     -- tool CLASS, not a specific item
    proven_in       VARCHAR(20)  NOT NULL DEFAULT 'PHASE_0',
    principle       TEXT         NULL,     -- what the prototype learned, in one line
    CONSTRAINT technique_definition_difficulty_check
        CHECK (difficulty IN ('PRIMITIVE','INTERMEDIATE','ADVANCED'))
);
CREATE INDEX technique_definition_domain_idx ON technique_definition(domain_key);

-- ---------------------------------------------------------------------------
-- 6. Construction kinds. construction_project.project_kind is free text and only
-- two kinds have ever existed in code (LEAN_TO, STONE_FIRE_PIT). Phase 0 completed
-- seventeen projects across many more. This catalogues them so the kinds are data.
-- ---------------------------------------------------------------------------
CREATE TABLE construction_kind (
    project_kind   VARCHAR(100) PRIMARY KEY,
    display_name   VARCHAR(160) NOT NULL,
    domain_key     VARCHAR(60)  NOT NULL REFERENCES domain_registry(domain_key),
    is_shelter     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_workstation BOOLEAN      NOT NULL DEFAULT FALSE,
    decays         BOOLEAN      NOT NULL DEFAULT TRUE,
    proven_in      VARCHAR(20)  NOT NULL DEFAULT 'PHASE_0'
);

-- ---------------------------------------------------------------------------
-- 7. Domains Phase 0 reached. Registering them is the whole point of this
-- migration: the Architect reads domain_registry to know what already exists.
-- ---------------------------------------------------------------------------
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin) VALUES
('tools',              'Tool Crafting',      'Knapping, hafting, and binding stone tools — the first multiplier a pair of hands gets.', 'V47', 'INVENTED'),
('woodworking',        'Woodworking',        'Shaping timber into furniture, tool components, and structures with edged tools.', 'V47', 'INVENTED'),
('stoneworking',       'Stoneworking',       'Selecting and working stone by grade — precision tool stone, construction stone, foundation stone.', 'V47', 'INVENTED'),
('textiles',           'Textiles',           'Preparing fiber into cordage and worked textile materials; weaving and sewing stations.', 'V47', 'INVENTED'),
('cartography',        'Cartography',        'Drawing the country from memory and revising it toward accuracy across revisions.', 'V47', 'INVENTED'),
('documentation',      'Documentation',      'Recording technique on durable surfaces so knowledge outlives the hand that learned it.', 'V47', 'INVENTED'),
('settlement_planning','Settlement Planning','Laying out a settlement into purposed districts and siting infrastructure within them.', 'V47', 'INVENTED'),
('logistics',          'Logistics',          'Organised storage, carrying loadouts, and the routines that move material about a settlement.', 'V47', 'INVENTED')
ON CONFLICT (domain_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. The techniques themselves — 18 proven in the Wolf Kingdom playthrough.
-- ---------------------------------------------------------------------------
INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, principle) VALUES
('friction_fire',           'Primitive friction fire',        'fire',                'PRIMITIVE',    NULL,                 NULL,          'A spindle worked against a notched board raises an ember; the ember needs fine dry tinder or it dies on the board.'),
('stone_knife_knapping',    'Stone knife knapping',           'tools',               'PRIMITIVE',    'stone_knife',        NULL,          'Precision tool stone holds an edge that construction stone will not.'),
('stone_hatchet_hafting',   'Stone hatchet hafting',          'tools',               'PRIMITIVE',    'stone_hatchet',      'CUTTING',     'A hafted edge multiplies force; binding quality decides whether the head stays on.'),
('stone_hammer_hafting',    'Stone hammer hafting',           'tools',               'PRIMITIVE',    'stone_hammer',       'CUTTING',     'Mass at the end of a lever does work an unaided hand cannot.'),
('stone_pickaxe_hafting',   'Stone pickaxe hafting',          'tools',               'PRIMITIVE',    'primitive_pickaxe',  'CUTTING',     'A narrow hardened point concentrates force enough to break stone free.'),
('basket_weaving',          'Woven resource basket',          'textiles',            'PRIMITIVE',    'woven_basket',       NULL,          'Baskets multiply what one trip can carry; transport is a bottleneck before storage is.'),
('backpack_basket_weaving', 'Backpack basket weaving',        'textiles',            'INTERMEDIATE', 'backpack_basket',    'CUTTING',     'Load carried on the back leaves the hands free and spreads weight across the frame.'),
('utility_belt_making',     'Utility belt making',            'textiles',            'PRIMITIVE',    'utility_belt',       'CUTTING',     'Modular tool carrying keeps what is used most within reach; consistent placement cuts preparation time.'),
('fiber_cordage',           'Fiber cordage processing',       'textiles',            'PRIMITIVE',    'fiber_cordage',      NULL,          'Twisted fiber is stronger than the sum of its strands.'),
('stone_fire_pit',          'Stone-walled fire pit',          'construction',        'INTERMEDIATE', NULL,                 NULL,          'Stone walls improve fuel efficiency and concentrate heat; air vents lengthen the burn.'),
('wood_furniture',          'Wood furniture construction',    'woodworking',         'INTERMEDIATE', 'wooden_desk',        'CUTTING',     'A flat worked surface at working height changes what fine work is possible.'),
('woodworking_table',       'Woodworking table construction', 'woodworking',         'INTERMEDIATE', 'woodworking_table',  'CUTTING',     'Dedicated workstations improve specialised crafting efficiency.'),
('stoneworking_table',      'Stoneworking table construction','stoneworking',        'INTERMEDIATE', 'stoneworking_table', 'CUTTING',     'Working stone on stone gives a surface that will not deform under percussion.'),
('weaving_table',           'Weaving table construction',     'textiles',            'INTERMEDIATE', 'weaving_table',      'CUTTING',     'Separated manufacturing zones improve settlement workflow.'),
('sewing_table',            'Sewing table construction',      'textiles',            'INTERMEDIATE', 'sewing_table',       'CUTTING',     'Textile finishing needs a different surface and different light than weaving.'),
('tool_shed_construction',  'Tool shed construction',         'construction',        'INTERMEDIATE', 'tool_shed',          'CUTTING',     'Organised tool storage reduces preparation time and loss.'),
('stone_slab_documentation','Stone slab documentation',       'documentation',       'PRIMITIVE',    'stone_slab',         'CUTTING',     'Stone slabs provide durable permanent documentation; bark and hide do not outlast weather.'),
('regional_cartography',    'Regional map drawing',           'cartography',         'INTERMEDIATE', NULL,                 NULL,          'A map redrawn against fresh observation converges on the true shape of the country.')
ON CONFLICT (technique_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. Construction kinds proven in Phase 0.
-- ---------------------------------------------------------------------------
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('LEAN_TO',            'Lean-to shelter',      'construction',         TRUE,  FALSE, TRUE,  'PREBUILT'),
('STONE_FIRE_PIT',     'Stone-walled fire pit','construction',         FALSE, FALSE, TRUE,  'PREBUILT'),
('ARCHIVE_SHELF',      'Stone archive',        'documentation',        FALSE, FALSE, TRUE,  'PHASE_0'),
('KNOWLEDGE_STATION',  'Knowledge workstation','documentation',        FALSE, TRUE,  TRUE,  'PHASE_0'),
('WOODWORKING_TABLE',  'Woodworking table',    'woodworking',          FALSE, TRUE,  TRUE,  'PHASE_0'),
('STONEWORKING_TABLE', 'Stoneworking table',   'stoneworking',         FALSE, TRUE,  TRUE,  'PHASE_0'),
('WEAVING_TABLE',      'Weaving work table',   'textiles',             FALSE, TRUE,  TRUE,  'PHASE_0'),
('SEWING_TABLE',       'Sewing work table',    'textiles',             FALSE, TRUE,  TRUE,  'PHASE_0'),
('TOOL_SHED',          'Tool shed',            'construction',         FALSE, FALSE, TRUE,  'PHASE_0'),
('STORAGE_AREA',       'Resource storage area','logistics',            FALSE, FALSE, TRUE,  'PHASE_0')
ON CONFLICT (project_kind) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 10. District purposes. chronicle_named_location.purpose_tag is free text; these
-- are the purposes the Wolf Kingdom actually used, so a future chronicle's
-- settlement planning has the same vocabulary available without re-deriving it.
-- ---------------------------------------------------------------------------
CREATE TABLE district_purpose (
    purpose_tag  VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    description  TEXT NOT NULL
);

INSERT INTO district_purpose (purpose_tag, display_name, description) VALUES
('HEAVY_MANUFACTURING','Heavy Manufacturing','Woodworking and stoneworking stations; noisy, heavy, material-hungry work.'),
('TEXTILE',            'Textile Manufacturing','Weaving and sewing; fiber processing and finished textile work.'),
('ARSENAL',            'Arsenal',              'Tool storage and maintenance — where the settlement keeps what it works with.'),
('LIBRARY',            'Library',              'Archives and documentation; where knowledge is kept against weather and time.'),
('WAREHOUSE',          'Warehouse',            'Bulk material storage sorted by resource category.'),
('RESIDENTIAL',        'Residential',          'Sleeping and shelter ground.'),
('PARK',               'Park',                 'Open ground deliberately left unbuilt.'),
('DRINKING',           'Drinking Area',        'A designated clean water draw, kept upstream of everything else.'),
('SANITATION',         'Sanitation Area',      'A designated waste area, sited away from water and food.')
ON CONFLICT (purpose_tag) DO NOTHING;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('phase0_heritage','Phase 0 Heritage','The Wolf Kingdom prototype technology catalogue — techniques, construction kinds, and district purposes proven across Days 1-10, folded into the schema so they are never re-invented.','V47','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
