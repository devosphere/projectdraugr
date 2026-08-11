-- V109: bare-hand carrying objects (M1 #194, EPIC #191).
--
-- The first modest containers a Chronicle can make with only their hands — no blade, no tool. Every process here
-- is tool_class NULL (bare hands and body weight); the cutting/knapping boundary is enforced elsewhere by tool
-- requirements. Results are improvised and low-capacity, as hand-made carriers are. Two hand-gathered materials
-- (big burdock leaves, dry grass) join the reeds/fibre/bark already gatherable.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('big_leaf',            'Big leaf',            'MATERIAL',  15,  60,   TRUE,  FALSE, 0),
('dry_grass_bundle',    'Dry grass bundle',    'MATERIAL',  60,  400,  TRUE,  FALSE, 0),
('leaf_wrap',           'Leaf wrap',           'CONTAINER', 40,  120,  FALSE, TRUE,  0),
('folded_bark_cup',     'Folded bark cup',     'CONTAINER', 60,  110,  FALSE, TRUE,  0),
('bark_fold_container', 'Bark fold container', 'CONTAINER', 200, 700,  FALSE, TRUE,  0),
('grass_bundle_sling',  'Grass bundle sling',  'CONTAINER', 250, 900,  FALSE, TRUE,  0),
('reed_pouch',          'Handwoven reed pouch','CONTAINER', 180, 500,  FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- Improvised, low capacity.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('leaf_wrap', 300, 300),('folded_bark_cup', 250, 300),('bark_fold_container', 800, 900),
('grass_bundle_sling', 1500, 3500),('reed_pouch', 1000, 1200)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('burdock',      'big_leaf',         2, 5, NULL),
('meadow_grass', 'dry_grass_bundle', 2, 4, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('big_leaf',            'FLORA_DROP', 'pick broad burdock leaves by hand'),
('dry_grass_bundle',    'FLORA_DROP', 'pull dry grass by hand'),
('leaf_wrap',           'TECHNIQUE',  'folded from big leaves by hand'),
('folded_bark_cup',     'TECHNIQUE',  'folded from a strip of bark by hand'),
('bark_fold_container', 'TECHNIQUE',  'folded and tied from bark by hand'),
('grass_bundle_sling',  'TECHNIQUE',  'bundled and tied from dry grass by hand'),
('reed_pouch',          'TECHNIQUE',  'plaited from reed by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- All bare-hand (tool_class NULL). CRAFT category ("make/fold/plait a X"), keywords carry the object name.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_leaf_wrap',     'Fold a leaf wrap',        'leaf_wrap',          1,1,NULL,FALSE,FALSE,8, 'items','CRAFT','leaf wrap,make a leaf wrap,fold a leaf wrap,wrap it in leaves,leaf parcel', 'You fold the broad leaves around what you mean to carry and tie the parcel off with a twist of fibre.', 'VERIFIED', now()),
('fold_bark_cup',      'Fold a bark cup',         'folded_bark_cup',    1,1,NULL,FALSE,FALSE,10,'items','CRAFT','bark cup,fold a bark cup,make a bark cup,folded bark cup,birch bark cup', 'You crease a strip of bark into a cup, folding and tucking the corners so it will hold water for a while.', 'VERIFIED', now()),
('fold_bark_container','Fold a bark container',   'bark_fold_container',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','bark container,fold a bark container,make a bark box,bark fold container,bark box', 'You fold the bark into a deep open box and lash the corners with fibre — rough, but it will carry.', 'VERIFIED', now()),
('make_grass_sling',   'Twist a grass sling',     'grass_bundle_sling', 1,1,NULL,FALSE,FALSE,15,'items','CRAFT','grass sling,make a grass sling,grass bundle sling,twist a carrying sling,carry sling', 'You bundle the dry grass and bind it into a loose sling you can shoulder a light, bulky load in.', 'VERIFIED', now()),
('weave_reed_pouch',   'Plait a reed pouch',      'reed_pouch',         1,1,NULL,FALSE,FALSE,25,'items','CRAFT','reed pouch,make a reed pouch,plait a reed pouch,handwoven reed pouch,weave a reed pouch', 'You plait the reeds by hand into a small close pouch and work a fibre drawstring through its mouth.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_leaf_wrap','big_leaf',2),('make_leaf_wrap','plant_fiber',1),
('fold_bark_cup','bark_sheet',1),
('fold_bark_container','bark_sheet',2),('fold_bark_container','plant_fiber',1),
('make_grass_sling','dry_grass_bundle',3),('make_grass_sling','plant_fiber',1),
('weave_reed_pouch','reed_bundle',2),('weave_reed_pouch','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_leaf_wrap','wrap'),('make_leaf_wrap','leaf'),
('fold_bark_cup','cup'),('fold_bark_cup','bark cup'),
('fold_bark_container','container'),('fold_bark_container','bark box'),
('make_grass_sling','sling'),
('weave_reed_pouch','pouch')
ON CONFLICT DO NOTHING;
