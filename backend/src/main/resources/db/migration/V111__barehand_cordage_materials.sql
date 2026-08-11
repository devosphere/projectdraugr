-- V111: pliable root and soft bast twisted into cordage by hand (M1 #192/#193, EPIC #191).
--
-- Bare-hand handwork: two more soft materials a Chronicle can collect and work into cordage with only the hands
-- (tool_class NULL). Pliable roots are pulled from wet/forest ground; soft inner bast is peeled by hand from a
-- linden. Both twist into the fiber_cordage the game already uses everywhere. Keywords carry the material name so
-- each beats the generic twist_cordage and the bark-bast process.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('flexible_root',   'Flexible root',   'MATERIAL', 90,  400, TRUE, FALSE, 0),
('soft_bast_strip', 'Soft bast strip', 'MATERIAL', 110, 500, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous) VALUES
('linden', 'TREE', 'TEMPERATE_FOREST', NULL, 60, FALSE)
ON CONFLICT (flora_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('fibrous_roots', 'flexible_root',   1, 3, NULL),
('linden',        'soft_bast_strip', 2, 4, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('flexible_root',   'FLORA_DROP', 'pull pliable roots from wet or forest ground by hand'),
('soft_bast_strip', 'FLORA_DROP', 'peel soft inner bast from a linden by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('twist_flexible_root_cordage','Twist root cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','flexible root into cordage,flexible root cordage,twist the flexible root,flexible root,pliable root cord', 'You clean the pliable roots and twist them by hand into a springy, serviceable cord.', 'VERIFIED', now()),
('twist_bast_cordage','Twist bast cordage','fiber_cordage',1,2,NULL,FALSE,FALSE,40,'textiles','PROCESS','soft bast into cordage,soft bast cordage,twist the soft bast,soft bast strip,soft bast', 'You split the soft bast into fine ribbons and twist them by hand into a smooth, strong cord.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('twist_flexible_root_cordage','flexible_root',3),
('twist_bast_cordage','soft_bast_strip',3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('twist_flexible_root_cordage','flexible root'),
('twist_bast_cordage','soft bast')
ON CONFLICT DO NOTHING;
