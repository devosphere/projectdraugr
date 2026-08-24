-- V160: reforging worn iron — closing the iron wear-out end (EPIC #180 heavy industry / #186 iron).
--
-- Bronze objects already melt back to ingots (V148/V156); iron had no such path, so a worn-out iron axe or a
-- battered iron cuirass was a dead end — heavy good iron with nowhere to go. Iron does not pour like bronze (a
-- bloomery never reaches its melting point), so scrap iron is not melted but REFORGED: the old pieces are heated
-- and hammered back together, free of their rust and scale, into a fresh bloom to draw new work from. Reforging is
-- lossy — scale flakes away with every heat — so it takes two worn axe-heads, or one whole cuirass, to make one
-- bloom. This mirrors the bronze recycling loop and means no smelted iron object is a terminal dead end.
--
-- Modelled like melt_down_bronze: a PROCESS over a hot fire (requires_fire), an either/or input group so any worn
-- iron object feeds it, output a single iron_bloom (the same stock smelt_iron yields).

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('reforge_iron_scrap', 'Reforge iron scrap', 'iron_bloom', 1,1, NULL, TRUE, FALSE, 90, 'items', 'PROCESS',
 'reforge the iron scrap,reforge iron scrap,reforge the scrap iron,reforge the worn iron,work the old iron down,recover the iron,reforge the iron',
 'You heat the worn iron and hammer the pieces together again and again, driving off the rust and scale, and consolidate them into a fresh spongy bloom to draw new work from — less than went in, for scale flakes away with every heat.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Either/or: two worn axe-heads, or one whole cuirass, reforge into one bloom. Both mass-conserve into a 1400 g
-- bloom (2 axes = 2100 g; a cuirass = 2600 g) — reforging only ever loses mass to scale, never makes it.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('reforge_iron_scrap','iron_object','iron_axe',2),
('reforge_iron_scrap','iron_object','iron_cuirass',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('reforge_iron_scrap','iron'), ('reforge_iron_scrap','scrap')
ON CONFLICT DO NOTHING;
