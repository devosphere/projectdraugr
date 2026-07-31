-- V51: Every item must be obtainable.
--
-- Twice now a migration has defined items with no way to acquire them: the V48
-- hide garments were insulating but unsewable, and V49 added flint, pyrite and
-- crystal as fire kit with no place to find them. Both were caught by hand,
-- which is not a system. A definition nobody can obtain is scenery wearing the
-- costume of a mechanic.
--
-- item_source declares how each item enters the world. Most sources are already
-- declarative and are derived here; the rest are produced by service code and
-- are listed explicitly, so adding a hardcoded craft means declaring it. The
-- Auditor then holds the invariant: no item_definition without a source.

CREATE TABLE item_source (
    item_key    VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    source_kind VARCHAR(30)  NOT NULL,
    detail      VARCHAR(120),
    PRIMARY KEY (item_key, source_kind),
    CONSTRAINT item_source_kind_check CHECK (source_kind IN
        ('FLORA_DROP','WILDLIFE_DROP','INSECT_PRODUCT','MINERAL','TECHNIQUE','TAMED_YIELD','CODE','STARTING_KIT'))
);

-- Declarative sources, derived so they stay true as those tables grow.
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'FLORA_DROP', 'flora_drop' FROM flora_drop ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'WILDLIFE_DROP', 'wildlife_drop' FROM wildlife_drop ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'INSECT_PRODUCT', 'insect_colony_product' FROM insect_colony_product ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT mineral_key, 'MINERAL', 'mineral_definition' FROM mineral_definition ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT produces_item, 'TECHNIQUE', 'technique_definition' FROM technique_definition WHERE produces_item IS NOT NULL ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'TAMED_YIELD', 'tamed_yield' FROM tamed_yield ON CONFLICT DO NOTHING;

-- Produced by service code rather than a table. Adding a hardcoded craft means
-- adding a row here, which is the point: the declaration is the contract.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
('plant_fiber',      'CODE','PhysicalItemService.gatherPlantFiber'),
('field_stone',      'CODE','PhysicalItemService.gatherFieldStones'),
('wild_berries',     'CODE','PhysicalItemService.gatherWildBerries'),
('dry_branch',       'CODE','PhysicalItemService.gatherDryBranches'),
('clay_lump',        'CODE','PhysicalItemService.gatherClay'),
('stone_slab',       'CODE','PhysicalItemService.gatherStoneSlab'),
('bark_sheet',       'CODE','PhysicalItemService.stripBark'),
('charcoal',         'CODE','PhysicalItemService.makeCharcoal'),
('woven_basket',     'CODE','PhysicalItemService.craftBasket'),
('primitive_spear',  'CODE','PhysicalItemService.craftPrimitiveSpear'),
('stone_knife',      'CODE','PhysicalItemService.craftPrimitiveTool'),
('stone_hammer',     'CODE','PhysicalItemService.craftPrimitiveTool'),
('primitive_pickaxe','CODE','PhysicalItemService.craftPrimitiveTool'),
('hearth_board',     'CODE','PhysicalItemService.craftFireKit'),
('fire_spindle',     'CODE','PhysicalItemService.craftFireKit'),
('tinder_nest',      'CODE','PhysicalItemService.craftTinder'),
('wooden_desk',      'CODE','PhysicalItemService.craftFurniture'),
('wooden_chair',     'CODE','PhysicalItemService.craftFurniture'),
('stone_shelf',      'CODE','PhysicalItemService.craftFurniture'),
('raw_game_meat',    'CODE','WildlifeEncounterService.harvest / snare / checkTrap'),
('animal_hide',      'CODE','WildlifeEncounterService.harvest fallback'),
('raw_fish',         'CODE','WildlifeEncounterService.fish / checkTrap'),
('cooked_game_meat', 'CODE','FireService.cookGameMeat'),
('hide_coat',        'CODE','PhysicalItemService.craftGarment'),
('fur_cloak',        'CODE','PhysicalItemService.craftGarment'),
('hide_leggings',    'CODE','PhysicalItemService.craftGarment'),
('hide_boots',       'CODE','PhysicalItemService.craftGarment'),
('fiber_tunic',      'CODE','PhysicalItemService.craftGarment'),
('fire_bow',         'CODE','PhysicalItemService.craftFireTool'),
('fire_socket',      'CODE','PhysicalItemService.craftFireTool'),
('plough_board',     'CODE','PhysicalItemService.craftFireTool'),
('fire_saw_set',     'CODE','PhysicalItemService.craftFireTool'),
('char_tinder',      'CODE','PhysicalItemService.craftFireTool'),
('ember_bundle',     'CODE','PhysicalItemService.craftFireTool'),
('arrival_shirt',     'STARTING_KIT','ChronicleService.awaken'),
('arrival_trousers',  'STARTING_KIT','ChronicleService.awaken'),
('arrival_left_shoe', 'STARTING_KIT','ChronicleService.awaken'),
('arrival_right_shoe','STARTING_KIT','ChronicleService.awaken')
ON CONFLICT DO NOTHING;

-- fish_bone is a real butchery yield and simply had no drop row.
INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max)
SELECT species_key, 'fish_bone', 1, 2 FROM wildlife_species WHERE kingdom_class='PISCES'
ON CONFLICT DO NOTHING;

-- Re-derive after the drops above, since the declarative pass ran before them.
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'WILDLIFE_DROP', 'wildlife_drop' FROM wildlife_drop ON CONFLICT DO NOTHING;

-- Items that are deliberately not yet obtainable, recorded so the invariant
-- reports a known gap rather than a surprise. Each is a real piece of work
-- someone has to finish, not a mistake to be silenced.
CREATE TABLE item_unreachable_known (
    item_key VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    reason   TEXT NOT NULL
);

INSERT INTO item_unreachable_known (item_key, reason) VALUES
('field_journal',   'Orphaned since V1; superseded by literature documents created from surfaces.'),
('hand_drawn_map',  'Orphaned since V1; superseded by SKETCH_MAP writing onto a real surface.'),
-- V47 folded the Phase 0 material vocabulary into the schema as a record of what
-- that settlement worked with. Several of those names duplicate mechanics the
-- current world already has under different keys, so they stay catalogued but
-- unobtainable rather than being given a second, redundant acquisition path.
('timber_log',        'Superseded by species-specific logs (oak_log, birch_log, ...) from FELL_TREE.'),
('foundation_stone',  'Superseded by field_stone and the mineral_definition grades.'),
('construction_stone','Superseded by field_stone and the mineral_definition grades.'),
('wooden_component',  'Phase 0 vocabulary; no processing step distinguishes it from dry_branch yet.'),
('structural_timber', 'Phase 0 vocabulary; awaits a timber-processing step.'),
('textile_material',  'Phase 0 vocabulary; awaits a fiber-processing step beyond fiber_cordage.'),
('vine',              'Phase 0 vocabulary; plant_fiber currently covers every binding use.'),
('large_basket',      'Phase 0 vocabulary; woven_basket and backpack_basket cover carrying for now.'),
-- Genuinely later technology, recorded so its absence is a decision rather than an oversight.
('steel_striker',   'Iron-age kit. No smelting exists yet, so flint_and_steel stays unreachable until it does.'),
('stone_axe',       'Felling is served by stone_hatchet; a separate two-handed axe awaits its own craft.')
ON CONFLICT (item_key) DO NOTHING;
