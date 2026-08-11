-- V103: wood shavings and fatwood — real tinder (M1 #75, EPIC #45/#54).
--
-- Net-new breadth (fuel/tinder family): a feather-stick of wood shavings and a sliver of resin-soaked fatwood
-- both catch a spark far better than plain fibre. Wood shavings are whittled from a dry branch (needs a blade);
-- fatwood is split from the resinous heart of a pine. Both feed craftTinder into a tinder nest (code).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('wood_shaving',  'Wood shavings', 'MATERIAL', 30, 120, TRUE, FALSE, 0),
('fatwood_stick', 'Fatwood stick', 'MATERIAL', 60, 90,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season) VALUES
('pine', 'fatwood_stick', 1, 2, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('wood_shaving',  'TECHNIQUE',  'whittled from a dry branch'),
('fatwood_stick', 'FLORA_DROP', 'split from the resinous heart of a pine')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Whittle a feather-stick of shavings (a blade, no fire).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('whittle_shavings','Whittle wood shavings','wood_shaving',1,2,'CUTTING',FALSE,FALSE,15,'items','CRAFT','wood shavings,whittle shavings,shave a stick,feather stick,make shavings,whittle a feather stick,curl shavings', 'You draw the blade down a dry stick again and again, curling off fine shavings that will catch a spark.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('whittle_shavings','dry_branch',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('whittle_shavings','shavings'),('whittle_shavings','feather stick')
ON CONFLICT DO NOTHING;
