-- V116: bare-hand raw materials (M1 #192, EPIC #191).
--
-- Ten more materials a Chronicle can collect with bare hands and prepare into stock the existing chains already
-- consume — no orphan is created: every material below is an input to a process here or to craftTinder. Each is
-- gathered without a tool (its flora is a NULL-tool shrub/herb/grass, or its mineral row carries tool_required
-- NULL), and the cut/fell boundary is untouched: the AXE_CLASS trees still need an axe; these come from what sheds,
-- snags, or lies loose. Risk/limits (why bare hands are safe, and when a tool is instead mandatory) are noted per
-- material in its item_source detail and here:
--   green_grass_bundle  – pull fresh grass; safe; a sickle only speeds a bulk cut.
--   straight_reed       – snap a straight reed; safe; edges can nick, so handle by the stem.
--   loose_bark_strip    – peel bark that is already lifting from a hazel; live bark off a standing tree needs a blade (STRIP_BARK).
--   birch_bark_shed     – gather curls of shed bark; safe; taking bark from a live birch needs a blade.
--   fallen_leaf_litter  – rake up dry leaves by hand; safe; damp litter rots and is discarded.
--   dry_twig            – pick up dry twigs; safe; snapping green wood needs a tool.
--   shed_feather        – lift feathers dropped among the reeds; safe; wash before use (contamination).
--   shed_fur_tuft       – pull fur snagged on thorns; safe; wash before use (contamination).
--   surface clay/silt   – scrape by hand from soft ground/river margin; safe; a proper deposit needs digging.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('green_grass_bundle', 'Green grass bundle', 'MATERIAL', 70, 420, TRUE,  FALSE, 0),
('straight_reed',      'Straight reed',      'MATERIAL', 30, 200, TRUE,  FALSE, 0),
('loose_bark_strip',   'Loose bark strip',   'MATERIAL', 40, 150, TRUE,  FALSE, 0),
('birch_bark_shed',    'Shed birch bark',    'MATERIAL', 25, 120, TRUE,  FALSE, 0),
('fallen_leaf_litter', 'Fallen leaf litter', 'MATERIAL', 20, 300, TRUE,  FALSE, 0),
('dry_twig',           'Dry twig',           'MATERIAL', 30, 180, TRUE,  FALSE, 0),
('shed_feather',       'Shed feather',       'MATERIAL', 2,  20,  TRUE,  FALSE, 0),
('shed_fur_tuft',      'Shed fur tuft',      'MATERIAL', 5,  40,  TRUE,  FALSE, 0),
('clay_lump_surface',  'Surface clay',       'MATERIAL', 900,600, TRUE,  FALSE, 0),
('silt_bundle',        'River silt',         'MATERIAL', 700,400, TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Obtainability. Eight come off bare-hand flora (NULL-tool shrubs/herbs/grasses); shed feather/fur are found among
-- the reeds and snagged on thorns. Surface clay and river silt are hand-scraped, so they are minerals with a NULL
-- tool_required (a proper clay/deposit dig is the tool path, unchanged).
INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('meadow_grass', 'green_grass_bundle', 2, 4, NULL),
('bulrush',      'straight_reed',      2, 3, NULL),
('hazel',        'loose_bark_strip',   1, 2, NULL),
('hazel',        'birch_bark_shed',    1, 2, NULL),
('hazel',        'fallen_leaf_litter', 2, 4, NULL),
('hazel',        'dry_twig',           2, 3, NULL),
('cattail',      'shed_feather',       1, 2, NULL),
('blackthorn',   'shed_fur_tuft',      1, 2, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('clay_lump_surface', 'Surface clay', 'GRASSLAND,HIGHLAND,TEMPERATE_FOREST', 0.5, NULL, 1, 2, 'Clay scraped by hand from soft surface ground; grittier than a dug deposit.'),
('silt_bundle',       'River silt',   'WETLAND,RIVER_BANK',                  0.5, NULL, 1, 2, 'Fine silt scooped by hand from a river margin or marsh.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('green_grass_bundle', 'FLORA_DROP', 'pull fresh green grass by hand'),
('straight_reed',      'FLORA_DROP', 'snap a straight reed from the stand by hand'),
('loose_bark_strip',   'FLORA_DROP', 'peel bark already lifting from a hazel by hand (live bark needs a blade)'),
('birch_bark_shed',    'FLORA_DROP', 'gather curls of shed bark by hand'),
('fallen_leaf_litter', 'FLORA_DROP', 'rake up dry fallen leaves by hand'),
('dry_twig',           'FLORA_DROP', 'pick up dry twigs by hand'),
('shed_feather',       'FLORA_DROP', 'lift feathers dropped among the reeds (wash before use)'),
('shed_fur_tuft',      'FLORA_DROP', 'pull fur snagged on thorns by hand (wash before use)'),
('clay_lump_surface',  'MINERAL',    'scrape surface clay by hand'),
('silt_bundle',        'MINERAL',    'scoop river silt by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Feeder processes: each new raw material is prepared by hand into stock an existing chain consumes. All tool_class
-- NULL. Keywords stay on-category (shaping/prep = PROCESS, making = CRAFT) and avoid ACQUIRE tokens and the
-- STRIP_BARK "strip" collision. ret is PROCESS(3), temper PROCESS(2), sort PROCESS(1), press PROCESS(2).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('dry_grass',            'Dry the grass',        'dry_grass_bundle', 2,2,NULL,FALSE,FALSE,30,'items','PROCESS','dry the grass,dry the green grass,cure the grass,dry grass in the sun', 'You spread the green grass in the sun and turn it until it is dry and light — ready to bundle or bed down with.', 'VERIFIED', now()),
('ready_reed_shaft',     'Ready a reed shaft',   'arrow_shaft',      1,1,NULL,FALSE,FALSE,15,'items','CRAFT','ready a reed shaft,straighten a reed shaft,trim a reed shaft,make a reed shaft,a reed shaft', 'You pick a straight reed, snap it to length and true it by hand into a light shaft.', 'VERIFIED', now()),
('ret_bark_strip',       'Ret bark strips',      'soft_bast_strip',  1,1,NULL,FALSE,TRUE, 40,'items','PROCESS','ret the bark,ret the loose bark,rett the loose bark,soak the bark to loosen', 'You soak the loose bark until the fibres part, and work the soft bast free of the outer rind.', 'VERIFIED', now()),
('flatten_bark',         'Flatten shed bark',    'bark_sheet',       1,1,NULL,FALSE,FALSE,12,'items','PROCESS','flatten the bark,flatten the shed bark,press the bark flat,press the shed bark', 'You weight the curled shed bark flat and press it into a workable sheet.', 'VERIFIED', now()),
('dress_feathers',       'Dress feathers',       'feather',          1,1,NULL,FALSE,FALSE,10,'items','PROCESS','sort the feathers,ready the feathers,trim the feathers,prepare the feathers', 'You sort the shed feathers, trim the quills and lay by the sound ones fit for fletching.', 'VERIFIED', now()),
('clean_surface_clay',   'Clean surface clay',   'clay_lump',        1,1,NULL,FALSE,FALSE,15,'items','PROCESS','clean the surface clay,clean the clay of grit,wash the clay of grit,pick grit from the clay', 'You pick and wash the grit from the scraped surface clay until it is clean enough to work.', 'VERIFIED', now()),
('temper_clay_with_silt','Temper clay with silt','tempered_clay',    1,1,NULL,FALSE,FALSE,40,'items','PROCESS','temper the clay with silt,temper clay with silt,work silt into the clay,mix silt into the clay', 'You work fine river silt through the clay until it is even and less apt to crack in the firing.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dry_grass','green_grass_bundle',2),
('ready_reed_shaft','straight_reed',2),
('ret_bark_strip','loose_bark_strip',2),
('flatten_bark','birch_bark_shed',1),
('dress_feathers','shed_feather',2),
('clean_surface_clay','clay_lump_surface',2),
('temper_clay_with_silt','clay_lump',1),('temper_clay_with_silt','silt_bundle',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('dry_grass','grass'),('dry_grass','green grass'),
('ready_reed_shaft','shaft'),('ready_reed_shaft','reed shaft'),
('ret_bark_strip','bark'),
('flatten_bark','bark'),
('dress_feathers','feathers'),('dress_feathers','feather'),
('clean_surface_clay','clay'),('clean_surface_clay','surface clay'),
('temper_clay_with_silt','silt'),('temper_clay_with_silt','clay')
ON CONFLICT DO NOTHING;
