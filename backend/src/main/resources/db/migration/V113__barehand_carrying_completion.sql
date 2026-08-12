-- V113: complete the bare-hand carrying set (M1 #194, EPIC #191).
--
-- V109 delivered five hand-made carriers (leaf_wrap, folded_bark_cup, bark_fold_container, grass_bundle_sling,
-- reed_pouch). #194 names five more: a bark scoop for water and loose grain, a tied reed sheaf for long bulky
-- stalks, a cordage burden loop, a heaped grass carry-mat, and the generic tied forage bundle. Every process here
-- is tool_class NULL (bare hands and body weight only); the cutting/knapping boundary stays enforced by the tool
-- requirements on the input materials (bark_sheet, reed_bundle) that were themselves gathered upstream. Results are
-- improvised and low-capacity, with short service lives reflected in their narration.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bark_scoop',                'Bark scoop',                'CONTAINER',  50,  90,   FALSE, TRUE,  0),
('reed_bundle_tie',           'Tied reed sheaf',           'CONTAINER',  120, 600,  FALSE, TRUE,  0),
('simple_cordage_loop',       'Cordage carry loop',        'CONTAINER',  40,  70,   FALSE, TRUE,  0),
('temporary_grass_carry_mat', 'Grass carry-mat',           'CONTAINER',  200, 1500, FALSE, TRUE,  0),
('foraged_material_bundle',   'Tied forage bundle',        'CONTAINER',  100, 800,  FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- Improvised, low-to-bulky capacity. The scoop and loop are small; the sheaf and mat are bulky but light.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('bark_scoop', 200, 250),
('reed_bundle_tie', 4000, 12000),
('simple_cordage_loop', 3000, 6000),
('temporary_grass_carry_mat', 5000, 15000),
('foraged_material_bundle', 3500, 9000)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bark_scoop',                'TECHNIQUE', 'pinched and folded from a strip of bark by hand'),
('reed_bundle_tie',           'TECHNIQUE', 'a sheaf of reeds tied for carrying by hand'),
('simple_cordage_loop',       'TECHNIQUE', 'a burden loop knotted from cordage by hand'),
('temporary_grass_carry_mat', 'TECHNIQUE', 'grass heaped and loosely bound into a carry-mat by hand'),
('foraged_material_bundle',   'TECHNIQUE', 'forage gathered and tied into one bundle by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- All bare-hand (tool_class NULL). CRAFT category; keywords carry the object name, subjects kept distinct to avoid
-- matcher collisions with the V109 carriers ("bundle"/"loop"/"mat"/"scoop" are always qualified).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
-- NOTE ON KEYWORDS: ActivityClassifier reads category_term/activity_category. "scoop" and "forage" are ACQUIRE
-- verbs and "weave"/"twist"/"plait" are PROCESS verbs; on a tie ACQUIRE(prec 2) beats CRAFT(prec 4), so a keyword
-- carrying a bare ACQUIRE/PROCESS token would classify away from CRAFT and never resolve to these processes. Scoop
-- keywords are therefore gated behind a strong CRAFT verb (craft/carve/fashion/form, weight ≥2 > scoop's 1); the
-- forage bundle and grass mat avoid the "forage"/"weave" tokens entirely. dr0114 pins this reachability.
('fold_bark_scoop',   'Carve a bark scoop',  'bark_scoop',                1,1,NULL,FALSE,FALSE,6,  'items','CRAFT','carve a bark scoop,craft a bark scoop,fashion a bark scoop,form a bark scoop', 'You pinch and fold a short strip of bark into a small scoop — good for lifting water or a handful of grain, though it will soften and leak before long.', 'VERIFIED', now()),
('tie_reed_sheaf',    'Tie a reed sheaf',    'reed_bundle_tie',           1,1,NULL,FALSE,FALSE,10, 'items','CRAFT','reed sheaf,tie a reed sheaf,sheaf of reeds,tie the reeds into a bundle,bundle the reeds,tied reed sheaf', 'You gather the long reeds into a sheaf and bind it in two places with cordage, making a single bulky bundle you can shoulder.', 'VERIFIED', now()),
('knot_cordage_loop', 'Knot a carry loop',   'simple_cordage_loop',       1,1,NULL,FALSE,FALSE,5,  'items','CRAFT','carry loop,cordage loop,burden loop,shoulder loop,knot a carry loop,make a carry loop,carrying loop', 'You knot a length of cordage into a closed loop — a simple burden loop to sling a tied bundle over one shoulder.', 'VERIFIED', now()),
('weave_grass_mat',   'Heap a grass mat',    'temporary_grass_carry_mat', 1,1,NULL,FALSE,FALSE,18, 'items','CRAFT','grass mat,carry mat,carrying mat,heap a grass mat,make a grass mat,drag mat,coil a grass mat', 'You lay the dry grass into a loose mat and lash its edges, so you can heap forage onto it and gather the corners to drag or carry — it will not last many trips.', 'VERIFIED', now()),
('tie_forage_bundle', 'Tie a carry bundle',  'foraged_material_bundle',   1,1,NULL,FALSE,FALSE,6,  'items','CRAFT','tie a bundle,bind a bundle,material bundle,tie a carrying bundle,tie everything into a bundle,bundle it up', 'You draw your loose forage into a heap and bind it into one bundle with a twist of fibre — the quickest way to carry an armful.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('fold_bark_scoop','bark_sheet',1),
('tie_reed_sheaf','reed_bundle',2),('tie_reed_sheaf','fiber_cordage',1),
('knot_cordage_loop','fiber_cordage',1),
('weave_grass_mat','dry_grass_bundle',4),('weave_grass_mat','plant_fiber',1),
('tie_forage_bundle','plant_fiber',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('fold_bark_scoop','scoop'),('fold_bark_scoop','bark scoop'),
('tie_reed_sheaf','sheaf'),('tie_reed_sheaf','reeds'),
('knot_cordage_loop','loop'),('knot_cordage_loop','carry loop'),
('weave_grass_mat','mat'),('weave_grass_mat','carry mat'),
('tie_forage_bundle','bundle')
ON CONFLICT DO NOTHING;
