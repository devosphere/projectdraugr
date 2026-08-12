-- V122: stone-boiling — treat water without a fireproof pot (M1 #125 / #123 cat.2 water, EPIC #123).
--
-- Boiling was permissive: any water vessel over a fire "boiled", including a waterskin or wooden bowl that would
-- in fact char or melt on the flame. V122 makes it real (see ChronicleActionService BOIL_WATER):
--   * direct boil over the fire needs a FIREPROOF vessel — fired clay or soapstone;
--   * otherwise a boiling_stone_set: stones heated in the fire and dropped into a vessel of water, the classic way
--     to boil in a wooden or hide container without a pot.
-- The stone set is the day-one path — gather cobbles, no firing chain first — so first-hours safe water is easier,
-- not harder; raw water is still drinkable (at the usual illness risk), so hydration is never blocked.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('boiling_stone_set', 'Boiling stone set', 'TOOL', 3000, 2400, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('boiling_stone_set', 'TECHNIQUE', 'a set of sound cobbles chosen for heating and dropping into water')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Bare-hand CRAFT: select and set aside sound stones. Any one hard cobble stock will do.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_boiling_stones','Set aside boiling stones','boiling_stone_set',1,1,NULL,FALSE,FALSE,10,'items','CRAFT','boiling stones,boiling stone set,make boiling stones,hot stones for boiling,pot boiler stones', 'You choose a set of sound, close-grained cobbles that will take the fire''s heat without shattering, and set them by for boiling.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('make_boiling_stones','stone','granite_cobble',3),('make_boiling_stones','stone','basalt_cobble',3)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_boiling_stones','boiling stones'),('make_boiling_stones','boiling stone'),('make_boiling_stones','hot stones')
ON CONFLICT DO NOTHING;
