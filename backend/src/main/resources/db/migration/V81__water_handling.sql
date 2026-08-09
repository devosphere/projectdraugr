-- V81: water collection, boiling, and drinking safety (M1 #71 collect/boil water; #59/#60 safety intent).
--
-- Water becomes a persistent carriable object with a safety state, so water handling "cannot operate by intent
-- alone". You COLLECT raw water into a carried vessel, then either drink it raw (a gut-illness risk from a
-- standing source), FILTER it (clearer, some risk removed — not pathogen-safe), or BOIL it (safe). DRINK prefers
-- the safest water you carry. This is deliberately NOT a full contamination model — it is the one axis (raw vs
-- filtered vs boiled) that makes the filter and the fire matter without a per-pathogen simulation.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('raw_water',      'Raw water',      'FOOD', 500, 500, TRUE, FALSE, 0),
('filtered_water', 'Filtered water', 'FOOD', 500, 500, TRUE, FALSE, 0),
('clean_water',    'Boiled water',   'FOOD', 500, 500, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Catalogued as obtained through code (the collect/boil/filter action handlers), like the other gathered stock.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
('raw_water',      'CODE', 'collect water into a carried vessel'),
('filtered_water', 'CODE', 'pour raw water through a clay water filter'),
('clean_water',    'CODE', 'boil raw water or spring water over a fire')
ON CONFLICT (item_key, source_kind) DO NOTHING;
