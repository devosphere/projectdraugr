-- V52: Material processing — the transformations that turn raw stock into usable material.
--
-- The world had raw materials and finished goods but almost nothing in between.
-- A log could be felled and a desk could be built, yet nothing turned one into
-- the other; hide came off animals and garments were sewn, but no tanning stood
-- between them. V51's reachability invariant exposed the result as a list of
-- "unreachable" items, and the first response was to declare them superseded.
-- That was wrong. They were not superseded, they were unfinished.
--
-- This makes processing declarative. A process names its inputs, its output, the
-- class of tool it needs, and whether it needs fire or water. Adding a material
-- chain from here is data, not Java — which matters beyond tidiness: every gap in
-- this table is a moment the simulation cannot resolve deterministically and has
-- to fall back on an AI call. A complete material foundation is what keeps
-- narration cheap.

CREATE TABLE material_process (
    process_key      VARCHAR(60)  PRIMARY KEY,
    display_name     VARCHAR(120) NOT NULL,
    output_item_key  VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    output_min       SMALLINT     NOT NULL DEFAULT 1,
    output_max       SMALLINT     NOT NULL DEFAULT 1,
    tool_class       VARCHAR(20),            -- CUTTING, STRIKING, AXE; NULL = bare hands
    requires_fire    BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_water   BOOLEAN      NOT NULL DEFAULT FALSE,
    duration_minutes SMALLINT     NOT NULL DEFAULT 30,
    domain_key       VARCHAR(60)  NOT NULL REFERENCES domain_registry(domain_key),
    keywords         VARCHAR(200) NOT NULL,  -- comma-separated, matched against action text
    narration        TEXT         NOT NULL,
    CONSTRAINT material_process_tool_check CHECK (tool_class IS NULL OR tool_class IN ('CUTTING','STRIKING','AXE'))
);

CREATE TABLE material_process_input (
    process_key VARCHAR(60)  NOT NULL REFERENCES material_process(process_key),
    item_key    VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    quantity    SMALLINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (process_key, item_key)
);
-- Some processes accept any of several stocks (any log, any hide). A group lets
-- one input slot be satisfied by whichever of them the chronicle actually carries.
CREATE TABLE material_process_input_group (
    process_key VARCHAR(60)  NOT NULL REFERENCES material_process(process_key),
    group_name  VARCHAR(40)  NOT NULL,
    item_key    VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    quantity    SMALLINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (process_key, group_name, item_key)
);

-- New intermediate and finished stock the chains produce.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('timber_plank',   'Timber plank',    'MATERIAL', 2600, 9000,  FALSE, FALSE, 0),
('tanned_leather', 'Tanned leather',  'MATERIAL',  800, 2400,  TRUE,  FALSE, 0),
('pine_pitch',     'Pine pitch',      'MATERIAL',  180,  160,  TRUE,  FALSE, 0),
('wood_ash',       'Wood ash',        'MATERIAL',  120,  400,  TRUE,  FALSE, 0),
('lye_solution',   'Lye solution',    'MATERIAL',  600,  600,  TRUE,  FALSE, 0),
('bone_needle',    'Bone needle',     'TOOL',       12,   20,  TRUE,  FALSE, 0),
('unfired_vessel', 'Unfired vessel',  'MATERIAL', 1400, 2600,  FALSE, FALSE, 0),
('clay_pot',       'Clay pot',        'CONTAINER',1200, 2400,  FALSE, TRUE,  0),
('leather_cord',   'Leather cord',    'MATERIAL',   60,  120,  TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('clay_pot','HAND_LEFT','CARRIED'), ('clay_pot','HAND_RIGHT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('clay_pot', 4000, 3000) ON CONFLICT (item_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- WOODWORKING: log -> plank -> component / structural timber
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('split_planks',      'Split planks from a log',   'timber_plank',     2, 4, 'AXE',      FALSE, FALSE, 60, 'woodworking', 'split,plank,saw the log,cut the log,hew,rive',
 'You drive wedges along the grain until the log opens with a crack, and again, until you have flat stock instead of a tree.'),
('shape_components',  'Shape wooden components',   'wooden_component', 2, 3, 'CUTTING',  FALSE, FALSE, 45, 'woodworking', 'shape,carve component,whittle,dress the plank,trim the plank',
 'You work the plank down to clean, squared pieces — the parts a thing is made of rather than the wood it came from.'),
('reinforce_timber',  'Reinforce structural timber','structural_timber',1, 1, 'CUTTING', FALSE, FALSE, 50, 'woodworking', 'reinforce,structural,beam,lash the planks,bind the planks',
 'You bind planks face to face until the piece stops flexing under your weight. It will carry a roof now.'),
('timber_from_log',   'Square a timber baulk',     'timber_log',       1, 1, 'AXE',      FALSE, FALSE, 40, 'woodworking', 'square the log,baulk,timber baulk,dress the log',
 'You square the log off, taking the round away until it will sit flat against another.');

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('split_planks','log','oak_log',1),('split_planks','log','birch_log',1),('split_planks','log','pine_log',1),
('split_planks','log','ash_log',1),('split_planks','log','maple_log',1),('split_planks','log','spruce_log',1),
('timber_from_log','log','oak_log',1),('timber_from_log','log','birch_log',1),('timber_from_log','log','pine_log',1),
('timber_from_log','log','ash_log',1),('timber_from_log','log','maple_log',1),('timber_from_log','log','spruce_log',1);

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('shape_components','timber_plank',1),
('reinforce_timber','timber_plank',2),
('reinforce_timber','fiber_cordage',1);

-- ---------------------------------------------------------------------------
-- STONEWORKING: field stone -> dressed grades and tool stock
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('dress_foundation', 'Dress a foundation stone',  'foundation_stone',    1, 1, 'STRIKING', FALSE, FALSE, 55, 'stoneworking', 'foundation stone,dress stone,square the stone,block',
 'You strike the stone square, knocking off what does not sit flat, until it will bear weight without rocking.'),
('dress_construction','Dress construction stone', 'construction_stone',  2, 3, 'STRIKING', FALSE, FALSE, 40, 'stoneworking', 'construction stone,break the stone,dress rubble,coursing stone',
 'You break the stone down into pieces of a size a wall can be built from.'),
('knap_tool_stone',  'Knap precision tool stone', 'precision_tool_stone',1, 2, 'STRIKING', FALSE, FALSE, 35, 'stoneworking', 'knap,tool stone,flake,strike flakes,work the flint',
 'You strike flakes away in turn, watching how the stone answers, until what is left will take and hold an edge.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dress_foundation','field_stone',3),
('dress_construction','field_stone',2),
('knap_tool_stone','field_stone',2);

-- ---------------------------------------------------------------------------
-- FIBER AND TEXTILE: fiber -> cordage -> woven cloth
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('twist_cordage',  'Twist fiber cordage',   'fiber_cordage',    1, 2, NULL, FALSE, FALSE, 30, 'textiles', 'cordage,twist,twine,rope,cord,spin fiber',
 'You roll the fibers against your thigh, feeding in more as you go, until what was loose plant matter pulls hard and does not part.'),
('weave_textile',  'Weave textile material','textile_material', 1, 2, NULL, FALSE, FALSE, 60, 'textiles', 'textile,weave cloth,weaving,cloth,fabric',
 'You work the cordage over and under itself until the weave closes up and holds its own shape.'),
('ret_nettle',     'Ret nettle fiber',      'fiber_cordage',    2, 3, NULL, FALSE, TRUE,  45, 'textiles', 'ret,soak nettle,nettle cordage,strip nettle',
 'You soak the stems until the soft matter rots away from the fiber, then strip out what is left. Nettle makes better cord than it has any right to.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('twist_cordage','plant_fiber',3),
('weave_textile','fiber_cordage',4),
('ret_nettle','nettle_fiber',3);

-- ---------------------------------------------------------------------------
-- HIDE AND LEATHER: oak bark is the tannin source, which is why it is gatherable
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('tan_hide',      'Tan a hide',        'tanned_leather', 1, 2, 'CUTTING', FALSE, TRUE, 180, 'textiles', 'tan,tanning,bark tan,cure the hide,soak the hide',
 'You scrape the hide clean and lay it in bark liquor. It will take days to strike through, and it will not rot afterward.'),
('cut_leather_cord','Cut leather cord', 'leather_cord',   2, 4, 'CUTTING', FALSE, FALSE, 20, 'textiles', 'leather cord,thong,lace,cut leather',
 'You cut a long spiral from the edge of the hide, one continuous thong.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('tan_hide','oak_bark',2),
('cut_leather_cord','tanned_leather',1);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('tan_hide','hide','animal_hide',1),('tan_hide','hide','deer_hide',1),('tan_hide','hide','boar_hide',1);

-- ---------------------------------------------------------------------------
-- FIRE BYPRODUCTS AND CHEMISTRY: ash, lye, pitch
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('gather_ash',   'Gather wood ash',   'wood_ash',     1, 3, NULL, FALSE, FALSE, 10, 'fire',     'ash,gather ash,scoop ash,rake the ashes',
 'You scoop the cold ash out of the hearth and keep it. It is worth more than it looks.'),
('leach_lye',    'Leach lye from ash','lye_solution', 1, 1, NULL, FALSE, TRUE,  60, 'fire',     'lye,leach,leaching,ash water',
 'You pour water slowly through the ash and catch what runs out. The liquid bites at your fingers.'),
('render_pitch', 'Render pine pitch', 'pine_pitch',   1, 2, NULL, TRUE,  FALSE, 50, 'fire',     'pitch,tar,render resin,melt resin,glue',
 'You melt the resin down and work charcoal dust through it until it cools hard but not brittle. It will hold stone to wood.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('leach_lye','wood_ash',2),
('render_pitch','pine_resin',2),
('render_pitch','charcoal',1);

-- ---------------------------------------------------------------------------
-- BONE
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('carve_needle', 'Carve a bone needle', 'bone_needle', 1, 2, 'CUTTING', FALSE, FALSE, 40, 'tools', 'needle,awl,bone needle,carve bone',
 'You split the bone and grind a sliver down to a point, then bore an eye through the thick end. Small work, and slow.');

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('carve_needle','bone','animal_bone',1),('carve_needle','bone','fish_bone',2);

-- ---------------------------------------------------------------------------
-- POTTERY: the first domain that genuinely needs fire to finish a thing
-- ---------------------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('form_vessel', 'Form a clay vessel', 'unfired_vessel', 1, 1, NULL, FALSE, TRUE,  50, 'stoneworking', 'form pot,shape clay,coil pot,mould,mold,make a vessel,shape a pot',
 'You coil the clay up on itself and smooth the joins away until it holds the shape of a vessel. It is still soft enough to ruin.'),
('fire_vessel', 'Fire a clay vessel', 'clay_pot',       1, 1, NULL, TRUE,  FALSE, 120, 'stoneworking', 'fire the pot,fire the clay,bake the pot,bake the clay,fire the vessel,fire pottery,harden the pot,kiln',
 'You bank the fire around it and keep it fed. When it cools the clay rings instead of thudding, and water will no longer soften it.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('form_vessel','clay_lump',2),
('fire_vessel','unfired_vessel',1);

-- ---------------------------------------------------------------------------
-- Vines are gathered, not made. They were catalogued with no source at all.
-- ---------------------------------------------------------------------------
INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous)
VALUES ('climbing_vine','SHRUB','TEMPERATE_FOREST,WETLAND',NULL,14,FALSE)
ON CONFLICT (flora_key) DO NOTHING;
INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season, tool_condition)
VALUES ('climbing_vine','vine',2,5,NULL,NULL) ON CONFLICT DO NOTHING;

-- A large basket is simply more of the same weaving.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('weave_large_basket','Weave a large basket','large_basket',1,1,NULL,FALSE,FALSE,75,'textiles','large basket,big basket,pannier,carrying basket',
 'You work the withies into something far bigger than a hand basket, wide enough to be awkward and deep enough to be worth it.');
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('weave_large_basket','vine',4),
('weave_large_basket','plant_fiber',4);

-- A two-handed axe, so felling is not forever hostage to a hatchet.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, keywords, narration) VALUES
('haft_stone_axe','Haft a stone axe','stone_axe',1,1,'CUTTING',FALSE,FALSE,70,'tools','stone axe,felling axe,haft an axe,make an axe',
 'You groove the head, seat it in the split of the haft, and bind it down hard. Two hands will swing this.');
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('haft_stone_axe','precision_tool_stone',1),
('haft_stone_axe','wooden_component',1),
('haft_stone_axe','fiber_cordage',2);

-- ---------------------------------------------------------------------------
-- Register the processes as sources, and retire the "superseded" excuses. Every
-- item below now has a real chain behind it.
-- ---------------------------------------------------------------------------
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT output_item_key, 'TECHNIQUE', 'material_process:' || process_key FROM material_process
ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'FLORA_DROP', 'flora_drop' FROM flora_drop ON CONFLICT DO NOTHING;

DELETE FROM item_unreachable_known WHERE item_key IN
    ('timber_log','foundation_stone','construction_stone','wooden_component','structural_timber',
     'textile_material','vine','large_basket','stone_axe');

INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, proven_in, principle)
SELECT 'process_' || process_key, display_name, domain_key,
       CASE WHEN duration_minutes >= 90 THEN 'ADVANCED' WHEN duration_minutes >= 45 THEN 'INTERMEDIATE' ELSE 'PRIMITIVE' END,
       output_item_key, tool_class, 'V52', narration
FROM material_process
ON CONFLICT (technique_key) DO NOTHING;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('material_processing','Material Processing','The transformations between raw stock and usable material: splitting, dressing, knapping, retting, tanning, leaching, rendering, and firing.','V52','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
