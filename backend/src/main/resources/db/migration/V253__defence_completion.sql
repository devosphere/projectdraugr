-- V253 — story #126 (early defence/concealment/escape catalogue): the genuinely-new named entries not yet built.
-- Concealment screens (brush/reed) are category PROCESS ('screen' is a PROCESS term); the rest are CRAFT. All are
-- equippable carried gear (dead-end-exempt), each a bounded physical aid (concealment, noise/light control, signal,
-- travel/route, cache). Many other #126 entries already exist under their own keys and are mapped at close (bracers,
-- rain capes, sling_stone, self bows, fletched/flint arrows, camp alarms, sledge, wound_wrap, splint_set, etc.).
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('brush_screen',           'Brush screen',           'TOOL', 400, 1600, FALSE, TRUE, 1, 2),
('reed_screen',            'Reed screen',            'TOOL', 350, 1400, FALSE, TRUE, 1, 3),
('noise_dampening_wrap',   'Noise-dampening wrap',   'TOOL', 150, 400,  FALSE, TRUE, 3, 2),
('firebrand',              'Firebrand',              'TOOL', 300, 700,  FALSE, TRUE, 0, 0),
('stone_lantern_cover',    'Stone lantern cover',    'TOOL', 250, 500,  FALSE, TRUE, 0, 3),
('warning_rattle',         'Warning rattle',         'TOOL', 120, 350,  FALSE, TRUE, 0, 0),
('whistle',                'Whistle',                'TOOL', 40,  80,   FALSE, TRUE, 0, 0),
('smoke_signal_bundle',    'Smoke signal bundle',    'TOOL', 200, 800,  FALSE, TRUE, 0, 0),
('fire_poker',             'Fire poker',             'TOOL', 300, 700,  FALSE, TRUE, 0, 0),
('walking_staff',          'Walking staff',          'TOOL', 300, 1400, FALSE, TRUE, 0, 2),
('climbing_rope',          'Climbing rope',          'TOOL', 400, 1200, FALSE, TRUE, 0, 2),
('simple_grapnel',         'Simple grapnel',         'TOOL', 350, 800,  FALSE, TRUE, 0, 2),
('emergency_cache_bundle', 'Emergency cache bundle', 'TOOL', 150, 900,  FALSE, TRUE, 1, 3),
('waterproof_fire_cache',  'Waterproof fire cache',  'TOOL', 200, 600,  FALSE, TRUE, 0, 10)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('brush_screen','TORSO','CARRIED'),('reed_screen','TORSO','CARRIED'),
('noise_dampening_wrap','WAIST','ATTACHED'),
('firebrand','HAND_RIGHT','ATTACHED'),('fire_poker','HAND_RIGHT','ATTACHED'),
('stone_lantern_cover','WAIST','ATTACHED'),('warning_rattle','WAIST','ATTACHED'),
('whistle','NECK','ATTACHED'),('smoke_signal_bundle','WAIST','CARRIED'),
('walking_staff','HAND_RIGHT','ATTACHED'),('climbing_rope','TORSO','CARRIED'),
('simple_grapnel','WAIST','ATTACHED'),('emergency_cache_bundle','WAIST','CARRIED'),
('waterproof_fire_cache','WAIST','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('brush_screen','TECHNIQUE','a portable brush screen to hide behind'),
('reed_screen','TECHNIQUE','a portable reed screen to hide behind'),
('noise_dampening_wrap','TECHNIQUE','a fibre wrap to muffle the noise of gear and step'),
('firebrand','TECHNIQUE','a wrapped brand to carry fire and ward off beasts'),
('stone_lantern_cover','TECHNIQUE','a stone cover to hood a flame and control its light'),
('warning_rattle','TECHNIQUE','a rattle strung to warn of approach'),
('whistle','TECHNIQUE','a whistle cut to carry a signal far'),
('smoke_signal_bundle','TECHNIQUE','a bundle of green stuff to raise a smoke signal'),
('fire_poker','TECHNIQUE','a poker to tend a fire and reach hot coals'),
('walking_staff','TECHNIQUE','a staff cut for travel and rough ground'),
('climbing_rope','TECHNIQUE','a rope laid up long enough to climb or lower by'),
('simple_grapnel','TECHNIQUE','a hooked grapnel bound to a rope'),
('emergency_cache_bundle','TECHNIQUE','a bundle of emergency supplies wrapped to cache'),
('waterproof_fire_cache','TECHNIQUE','a waterproofed cache of dry fire-making stuff')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_brush_screen','Weave a brush screen','brush_screen',1,1,NULL,FALSE,FALSE,25,'items','PROCESS','brush screen,make a brush screen', 'You weave cut brush into a light screen to crouch behind, unseen.', 'VERIFIED', now()),
('make_reed_screen','Weave a reed screen','reed_screen',1,1,NULL,FALSE,FALSE,25,'items','PROCESS','reed screen,make a reed screen', 'You weave reed into a light screen to crouch behind, unseen.', 'VERIFIED', now()),
('make_noise_dampening_wrap','Wind a noise-dampening wrap','noise_dampening_wrap',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','noise dampening wrap,make a noise dampening wrap,noise wrap', 'You wind a fibre wrap round gear and step to muffle the noise you make.', 'VERIFIED', now()),
('make_firebrand','Bind a firebrand','firebrand',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','firebrand,make a firebrand,bind a firebrand', 'You wrap a stout brand to carry fire and ward beasts back from the dark.', 'VERIFIED', now()),
('make_stone_lantern_cover','Shape a stone lantern cover','stone_lantern_cover',1,1,NULL,FALSE,FALSE,20,'items','CRAFT','stone lantern cover,make a stone lantern cover,lantern cover', 'You shape a stone hood to cover a flame and hide or aim its light.', 'VERIFIED', now()),
('make_warning_rattle','String a warning rattle','warning_rattle',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','warning rattle,make a warning rattle', 'You string a rattle to hang across an approach and sound a warning.', 'VERIFIED', now()),
('make_whistle','Cut a whistle','whistle',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','whistle,make a whistle,cut a whistle', 'You cut a whistle that carries a signal far across the ground.', 'VERIFIED', now()),
('make_smoke_signal_bundle','Bundle a smoke signal','smoke_signal_bundle',1,1,NULL,FALSE,FALSE,12,'items','PROCESS','smoke signal bundle,make a smoke signal bundle', 'You bundle green stuff to throw a column of smoke as a signal.', 'VERIFIED', now()),
('make_fire_poker','Cut a fire poker','fire_poker',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','fire poker,make a fire poker,cut a fire poker', 'You cut a poker to tend the fire and reach the hot coals.', 'VERIFIED', now()),
('make_walking_staff','Cut a walking staff','walking_staff',1,1,NULL,FALSE,FALSE,12,'items','CRAFT','walking staff,make a walking staff,cut a walking staff', 'You cut and trim a staff for travel and rough ground.', 'VERIFIED', now()),
('make_climbing_rope','Lay up a climbing rope','climbing_rope',1,1,NULL,FALSE,FALSE,30,'items','CRAFT','climbing rope,make a climbing rope,lay up a climbing rope', 'You lay up cordage long and strong enough to climb or lower by.', 'VERIFIED', now()),
('make_simple_grapnel','Bind a simple grapnel','simple_grapnel',1,1,NULL,FALSE,FALSE,22,'items','CRAFT','simple grapnel,make a simple grapnel,grapnel', 'You bind a hooked grapnel to a rope to catch and hold.', 'VERIFIED', now()),
('make_emergency_cache_bundle','Wrap an emergency cache bundle','emergency_cache_bundle',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','emergency cache bundle,make an emergency cache bundle,cache bundle', 'You wrap a bundle of emergency stuff to cache against hard need.', 'VERIFIED', now()),
('make_waterproof_fire_cache','Wrap a waterproof fire cache','waterproof_fire_cache',1,1,NULL,FALSE,FALSE,18,'items','CRAFT','waterproof fire cache,make a waterproof fire cache,fire cache', 'You waterproof a cache of dry tinder and fire-making stuff against the wet.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_brush_screen','dry_branch',2),
('make_reed_screen','reed_bundle',2),
('make_noise_dampening_wrap','plant_fiber',2),
('make_firebrand','dry_branch',1),('make_firebrand','plant_fiber',1),
('make_stone_lantern_cover','clay_lump',2),
('make_warning_rattle','dry_branch',1),('make_warning_rattle','plant_fiber',1),
('make_whistle','dry_branch',1),
('make_smoke_signal_bundle','dry_grass_bundle',2),('make_smoke_signal_bundle','plant_fiber',1),
('make_fire_poker','dry_branch',1),
('make_walking_staff','dry_branch',1),
('make_climbing_rope','fiber_cordage',4),
('make_simple_grapnel','dry_branch',1),('make_simple_grapnel','fiber_cordage',1),
('make_emergency_cache_bundle','plant_fiber',2),
('make_waterproof_fire_cache','bark_sheet',1),('make_waterproof_fire_cache','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_brush_screen','brush screen'),('make_brush_screen','screen'),
('make_reed_screen','reed screen'),('make_reed_screen','screen'),
('make_noise_dampening_wrap','noise wrap'),('make_noise_dampening_wrap','dampening wrap'),
('make_firebrand','firebrand'),
('make_stone_lantern_cover','lantern cover'),
('make_warning_rattle','rattle'),('make_warning_rattle','warning rattle'),
('make_whistle','whistle'),
('make_smoke_signal_bundle','smoke signal'),('make_smoke_signal_bundle','signal bundle'),
('make_fire_poker','poker'),('make_fire_poker','fire poker'),
('make_walking_staff','staff'),('make_walking_staff','walking staff'),
('make_climbing_rope','climbing rope'),
('make_simple_grapnel','grapnel'),
('make_emergency_cache_bundle','cache bundle'),
('make_waterproof_fire_cache','fire cache')
ON CONFLICT DO NOTHING;
