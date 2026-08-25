-- V170: the lime kiln — dedicated infrastructure for burning lime (EPIC #180 heavy industry / #182 fuel/#183 kiln).
--
-- Calcining limestone over an open charge of charcoal (V164) works, but it is wasteful — much of the heat escapes,
-- and the burn gives up only what a bonfire can. A lime kiln is a built shaft of stone and clay that holds the heat
-- close around the stone, so the same charge yields more quicklime. It is the lime counterpart to the bloomery
-- furnace: a workstation that only EASES the burn (finer, fuller yield), never gates it — calcining still works
-- without one, just poorer. Completes the fuel/lime/masonry industry with its own dedicated furnace.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('lime_kiln', 'Lime kiln', 'FURNITURE', 70000, 90000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('lime_kiln', 'TECHNIQUE', 'a stone-and-clay burning shaft raised and packed to hold a calcining heat')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- 'kiln' is a PROCESS term (V57), so a kiln-raising sentence classifies to PROCESS; the kiln subject keeps it distinct.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_lime_kiln', 'Raise a lime kiln', 'lime_kiln', 1,1, NULL, FALSE, FALSE, 200, 'construction', 'PROCESS',
 'make a lime kiln,build a lime kiln,raise a lime kiln,lay up a lime kiln,lime kiln',
 'You raise a squat shaft of stone and pack it inside and out with clay, leaving a mouth to charge it and a draught hole below — a kiln that will hold a calcining heat close around the stone, far hotter and thriftier than an open burn.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_lime_kiln', 'clay_lump', 5), ('make_lime_kiln', 'field_stone', 6)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_lime_kiln','kiln'), ('make_lime_kiln','lime')
ON CONFLICT DO NOTHING;

-- A kiln is rammed up in place from clay and earth dug at the site, so its mass legitimately exceeds the carried
-- clay lumps and facing stones — the same unmodelled-source exemption as the bloomery furnace (V152).
UPDATE material_process SET conservation_exempt = TRUE,
    exempt_reason = 'A lime kiln is rammed up in place from clay and earth dug at the site; its mass legitimately exceeds the carried clay lumps and facing stones.'
WHERE process_key = 'make_lime_kiln';

-- The kiln holds the calcining heat close, so a burn in one gives a fuller yield: calcining gains a yield range, and
-- the kiln is its workstation, biasing that yield up. Without a kiln, calcining still works — it just yields the low end.
UPDATE material_process SET output_max = 2, station_kind = 'lime_kiln' WHERE process_key = 'calcine_quicklime';
