-- V164: the lime chain — limestone burned to quicklime, slaked into a proper mortar (EPIC #180 / #182 fuel chains,
-- feeding #183 masonry). This is the LIME chain that V57 and the clay-ceramic work explicitly deferred ("needs a
-- gatherable limestone... deferred with the lime chain").
--
-- Mortar until now was a stopgap: wood ash and clay (mix_mortar). Real mortar is lime. Limestone is quarried, burned
-- in a hot charcoal fire until it gives up its carbon dioxide and becomes quicklime, then slaked with water — a
-- fierce, heat-throwing reaction — into a lime putty that sets hard and weatherproof. It binds a wall far better than
-- ash ever did. The burn eats charcoal, so it leans on the fuel supply the charcoal clamp (V163) now provides: the
-- fuel chain, the lime chain, and the wall are one connected industry.
--
-- Terminal use: lime mortar is an either/or alternative to the ash-and-clay mortar in lay_mortared_course, so a
-- Chronicle can bed a mortared course with whichever they have made.

-- Limestone is quarried from chalk-and-limestone country, like the flint that weathers out of it.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('limestone_chunk', 'Limestone', 'MATERIAL', 1100, 440, TRUE,  FALSE, 0),
('quicklime',       'Quicklime', 'MATERIAL',  900, 500, TRUE,  FALSE, 0),
('lime_mortar',     'Lime mortar','MATERIAL', 1600, 900, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('limestone_chunk', 'MINERAL',   'quarried from chalk and limestone country in the highlands and hills'),
('quicklime',       'TECHNIQUE', 'limestone calcined in a hot charcoal fire until it gives up its carbon'),
('lime_mortar',     'TECHNIQUE', 'quicklime slaked with water and tempered with clay into a setting mortar')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('limestone_chunk', 'Limestone', 'HIGHLAND,MOUNTAIN,GRASSLAND', 0.50, 'STRIKING', 1, 2,
 'Pale beds of limestone and chalk break out of the higher ground; a hammer frees workable chunks. Burned hot, it yields the lime that makes real mortar.')
ON CONFLICT (mineral_key) DO NOTHING;

-- 'calcine' is decisive PROCESS work (driving the carbon out of limestone with heat); a new, rare word, no ripple.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','calcine',3)
ON CONFLICT (category_key, term) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('calcine_quicklime', 'Calcine limestone into quicklime', 'quicklime', 1,1, NULL, TRUE, FALSE, 240, 'construction', 'PROCESS',
 'calcine the limestone,calcine limestone,burn the limestone into lime,burn limestone for lime,fire the limestone for lime,burn quicklime',
 'You stack the limestone in among a hot charge of charcoal and hold the fire fierce for the better part of a day; the stone spits and crumbles as it gives up its carbon, leaving light, caustic quicklime that hisses at a drop of water.', 'VERIFIED', now()),
('slake_lime_mortar', 'Slake lime into mortar', 'lime_mortar', 1,1, NULL, FALSE, TRUE, 60, 'construction', 'CONSTRUCT',
 'mix lime mortar,slake the lime into mortar,slake the quicklime,make lime mortar,lime mortar',
 'You tip water onto the quicklime and stand back as it boils and steams itself apart, then work in clay and more water until it comes together as a smooth grey lime mortar that will set hard and hold a wall against the weather.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('calcine_quicklime', 'limestone_chunk', 2), ('calcine_quicklime', 'charcoal', 2),
('slake_lime_mortar', 'quicklime', 1), ('slake_lime_mortar', 'clay_lump', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('calcine_quicklime','limestone'), ('calcine_quicklime','lime'), ('calcine_quicklime','quicklime'),
('slake_lime_mortar','lime'), ('slake_lime_mortar','mortar'), ('slake_lime_mortar','quicklime')
ON CONFLICT DO NOTHING;

-- Terminal use: let lime mortar stand in for the ash-and-clay mortar_mix when bedding a mortared course. Convert the
-- fixed mortar_mix input into an either/or group so either mortar works.
DELETE FROM material_process_input WHERE process_key='lay_mortared_course' AND item_key='mortar_mix';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('lay_mortared_course','mortar','mortar_mix',1),
('lay_mortared_course','mortar','lime_mortar',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
