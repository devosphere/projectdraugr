-- V233 — story #137 (first-era plant-fibre and natural-binding source chains). Fills the two named entries missing
-- after prior work delivered nettle (nettle_fiber), inner bark (strip_bark_cordage/soft_bast_strip), cattail
-- (cattail_leaf), flexible root (flexible_root) and reed bundle: long grass fibre and willow bark strip. Each is a
-- distinct fibre item obtained from a reachable plant and twisted into fiber_cordage (the terminal binding the whole
-- object graph consumes), mirroring the existing twist_*_cordage web. Pure data.
--   long_grass_fibre  <- strip_grass_fibre (green_grass_bundle) -> twist_grass_cordage  -> fiber_cordage
--   willow_bark_strip <- ret_willow_bark  (willow_branch)      -> twist_willow_cordage -> fiber_cordage
-- Willow processing uses ret/soak/twist verbs (never strip/peel/cut) so it is NOT stolen by the Java STRIP_BARK
-- intent (bark + strip/peel/gather/cut/collect/pull); the grass strip carries no 'bark' and no gather+fibre so it
-- misses STRIP_BARK and GATHER_FIBER; the twist keywords are distinctive multiword so they beat twist_cordage's
-- short 'cordage'/'twist' on longest-match. Masses balance (input sum >= output) at every step.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('long_grass_fibre',  'Long grass fibre',  'MATERIAL', 120, 450, TRUE, FALSE),
('willow_bark_strip', 'Willow bark strip', 'MATERIAL', 150, 500, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('long_grass_fibre','TECHNIQUE','combed and stripped from long meadow grass by hand'),
('willow_bark_strip','TECHNIQUE','retted from a willow branch to loosen the inner bast')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('strip_grass_fibre','Comb out long grass fibre','long_grass_fibre',1,1,NULL,FALSE,FALSE,20,'textiles','PROCESS','long grass fibre,strip the long grass,comb out grass fibre,grass fibre,dress the long grass', 'You comb and strip the tough length of the meadow grass down to its fibre, ready to twist.', 'VERIFIED', now()),
('twist_grass_cordage','Twist grass cordage','fiber_cordage',1,1,NULL,FALSE,FALSE,35,'textiles','PROCESS','grass cordage,twist grass cordage,twist grass into cordage,twist the grass fibre', 'You roll the grass fibre against your thigh two strands at a time until it lays up into a rough, serviceable cord.', 'VERIFIED', now()),
('ret_willow_bark','Ret willow bast','willow_bark_strip',1,1,NULL,FALSE,TRUE,40,'textiles','PROCESS','ret the willow bark,soak willow bark,ret willow bast,soak the willow bast,willow bast strip', 'You soak the willow branch until the inner bark slips free in long, supple bast strips.', 'VERIFIED', now()),
('twist_willow_cordage','Twist willow-bast cordage','fiber_cordage',1,1,NULL,FALSE,FALSE,35,'textiles','PROCESS','willow cordage,twist willow cordage,twist willow bast into cordage,willow bast cordage', 'You twist the supple willow bast into a strong, even cord that holds a knot well.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('strip_grass_fibre','green_grass_bundle',2),
('twist_grass_cordage','long_grass_fibre',1),
('ret_willow_bark','willow_branch',1),
('twist_willow_cordage','willow_bark_strip',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('strip_grass_fibre','grass fibre'),('strip_grass_fibre','long grass'),
('twist_grass_cordage','grass cordage'),('twist_grass_cordage','grass'),
('ret_willow_bark','willow bark'),('ret_willow_bark','willow bast'),
('twist_willow_cordage','willow cordage'),('twist_willow_cordage','willow bast')
ON CONFLICT DO NOTHING;
