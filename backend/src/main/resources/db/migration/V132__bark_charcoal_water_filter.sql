-- V132 — an improvised, fire-free water filter for the first hours (#141, EPIC #123).
--
-- Every existing water-treatment path leans on fired clay: BOIL_WATER needs a fireproof vessel and
-- FILTER_WATER accepted only the clay_water_filter, whose own process (make_clay_water_filter) requires
-- firing. So a newly arrived Chronicle standing at murky, standing water with no clean stream had NO way to
-- improve it before building the whole pottery-and-kiln chain — yet a bark cone packed with charcoal and
-- grass is exactly the kind of thing bare hands make on day one. Add it: a bare-hand CRAFT (no tool, no
-- fire) from a bark sheet, a lump of charcoal, and a grass bundle, and let FILTER_WATER accept it. It yields
-- filtered_water (clarified, charcoal-adsorbed) — not as safe as boiling, but a real first-hours option.
--
-- Mass conserves: output 200 g <= 120 (bark) + 30 (charcoal) + 60 (grass) = 210 g of input.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
    ('bark_water_filter', 'Bark-and-charcoal water filter', 'TOOL', 200, 800, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
    ('bark_water_filter', 'TECHNIQUE', 'make_bark_water_filter')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
    ('make_bark_water_filter', 'Make a bark-and-charcoal water filter', 'bark_water_filter', 1, 1, NULL, FALSE, FALSE, 20, 'items', 'CRAFT',
     'bark water filter,charcoal water filter,make a bark and charcoal filter,pack a bark filter with charcoal,improvised water filter',
     'You fold a bark sheet into a deep cone, pack it in layers with crushed charcoal and grass, and set it to drip — rough, but it will clear and sweeten what runs through it.',
     'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
    ('make_bark_water_filter', 'bark_sheet', 1),
    ('make_bark_water_filter', 'charcoal', 1),
    ('make_bark_water_filter', 'dry_grass_bundle', 1)
ON CONFLICT DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
    ('make_bark_water_filter', 'filter')
ON CONFLICT DO NOTHING;
