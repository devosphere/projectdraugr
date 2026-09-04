-- V259 — story #59: complete the "viable clay / sand / grog" temper path. Clay was temperable only with silt; sand
-- and grog, the two classic tempers the story names, had no route. Sand became gatherable in V258 (river_sand) but
-- nothing consumed it — this gives it its purpose, closing the chain gather sand -> temper clay -> fire a vessel.
--
-- Grog is crushed fired ceramic returned to the clay body. Making it consumes a fired vessel: an old or cracked pot
-- is broken up and pounded down, which is exactly how grog is won, and it gives ceramic_grog a real consumer
-- (temper_clay_with_grog) so it is no dead-end stock.
--
-- Routing: each new keyword is strictly LONGER than the incumbent 'temper the clay' that temper_clay owns, so the
-- longest-keyword rule routes these phrases here while plain "temper the clay" still reaches temper_clay and
-- "...with silt" still reaches temper_clay_with_silt. 'temper' is not a gather verb, so neither GATHER_CLAY (clay +
-- gather/dig/collect) nor GATHER_MINERAL (gather verb + river sand/sand bar/gravel) can steal these.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('ceramic_grog', 'Ceramic grog', 'MATERIAL', 400, 300, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('ceramic_grog', 'TECHNIQUE', 'crushed down from a fired vessel by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('temper_clay_with_sand','Temper clay with sand','tempered_clay',1,1,NULL,FALSE,FALSE,20,'items','PROCESS',
 'temper the clay with sand,temper clay with sand,work sand into the clay,mix sand into the clay',
 'You work washed river sand through the clay, wedging until the grit is evenly through the body — tempered so it will dry and fire without cracking.',
 'VERIFIED', now()),
('crush_ceramic_grog','Crush ceramic grog','ceramic_grog',2,2,'STRIKING',FALSE,FALSE,25,'items','PROCESS',
 'crush ceramic grog,crush the sherds for grog,pound the sherds to grog,ceramic grog',
 'You break up an old fired vessel and pound the sherds down to a coarse grit — grog, ready to go back into fresh clay.',
 'VERIFIED', now()),
('temper_clay_with_grog','Temper clay with grog','tempered_clay',1,1,NULL,FALSE,FALSE,20,'items','PROCESS',
 'temper the clay with grog,temper clay with grog,work grog into the clay,mix grog into the clay',
 'You work the ground grog through the clay — old fired body returned to new, which lets the pot take the fire without splitting.',
 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('temper_clay_with_sand','clay_lump',1),('temper_clay_with_sand','river_sand',1),
('crush_ceramic_grog','fired_bowl',1),
('temper_clay_with_grog','clay_lump',1),('temper_clay_with_grog','ceramic_grog',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('temper_clay_with_sand','clay'),('temper_clay_with_sand','sand'),
('crush_ceramic_grog','grog'),('crush_ceramic_grog','sherds'),
('temper_clay_with_grog','clay'),('temper_clay_with_grog','grog')
ON CONFLICT DO NOTHING;
