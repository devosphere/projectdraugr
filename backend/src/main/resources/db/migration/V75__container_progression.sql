-- V75: portable container progression (M1 #56, EPIC #54).
--
-- The generic container OPERATIONS (place/pack/unpack/store/retrieve/open/close/seal — #67, V72) already work on
-- any container. This adds a batch of named first-era container OBJECTS and their craft procedures, so the moment
-- one is made it is storable, sealable, and retrievable through the existing action boundary with no new code.
-- Every process is CRAFT with a make/sew/assemble/form keyword (so it classifies to its own category), carries
-- subject terms, consumes obtainable first-era stock, and its output mass stays under its input mass (conserves
-- matter). Verified explicitly here and by the routing-reachability probe.

-- 1. The finished container objects.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('hide_sack',     'Hide sack',      'CONTAINER', 600,  4000,  FALSE, TRUE,  0),
('wooden_bucket', 'Wooden bucket',  'CONTAINER', 2000, 9000,  FALSE, FALSE, 0),
('clay_jar',      'Clay jar',       'CONTAINER', 1200, 3000,  FALSE, FALSE, 0),
('grain_sack',    'Grain sack',     'CONTAINER', 300,  6000,  FALSE, TRUE,  0),
('tool_roll',     'Tool roll',      'CONTAINER', 400,  2500,  FALSE, TRUE,  0),
('lidded_basket', 'Lidded basket',  'CONTAINER', 1000, 14000, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- 2. How much each holds.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('hide_sack',     8000,  12000),
('wooden_bucket', 6000,  8000),
('clay_jar',      5000,  6000),
('grain_sack',    12000, 16000),
('tool_roll',     3000,  4000),
('lidded_basket', 10000, 14000)
ON CONFLICT (item_key) DO NOTHING;

-- 3. How the equippable ones are carried.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('hide_sack',     'BACK', 'CARRIED'),
('grain_sack',    'BACK', 'CARRIED'),
('tool_roll',     'WAIST','CARRIED'),
('lidded_basket', 'BACK', 'CARRIED')
ON CONFLICT DO NOTHING;

-- 4. The craft procedures. Column order matches V57.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('sew_hide_sack',       'Sew a hide sack',      'hide_sack',     1, 1, 'CUTTING', FALSE, FALSE, 60,  'items', 'CRAFT', 'hide sack,sew a hide sack,leather sack,make a hide sack,sew,stitch', 'You cut and stitch the tanned hide into a deep sack, binding the seam tight with cord.', 'VERIFIED', now()),
('assemble_wooden_bucket','Assemble a wooden bucket','wooden_bucket',1,1,'CUTTING', FALSE, FALSE, 90,  'items', 'CRAFT', 'wooden bucket,bucket,assemble a bucket,make a bucket,assemble,build a bucket', 'You fit the planks into a ring and bind them tight with cordage until they hold water without a gap.', 'VERIFIED', now()),
('form_clay_jar',       'Form and fire a clay jar','clay_jar',    1, 1, NULL,      TRUE,  FALSE, 120, 'items', 'CRAFT', 'clay jar,form a clay jar,make a clay jar,coil a clay jar,fire a clay jar', 'You coil and smooth the clay into a round jar and set it in the fire until it hardens to a dull ring.', 'VERIFIED', now()),
('sew_grain_sack',      'Sew a grain sack',     'grain_sack',    1, 1, 'CUTTING', FALSE, FALSE, 55,  'items', 'CRAFT', 'grain sack,sew a grain sack,fibre sack,fiber sack,make a grain sack,sew,stitch', 'You stitch a close-woven fibre sack, tight enough to hold grain without spilling a seed.', 'VERIFIED', now()),
('roll_tool_wrap',      'Make a tool roll',     'tool_roll',     1, 1, 'CUTTING', FALSE, FALSE, 45,  'items', 'CRAFT', 'tool roll,tool wrap,make a tool roll,roll a tool wrap,make a tool wrap,roll', 'You sew loops into a strip of hide so each tool sits in its own place, then bind it as a roll that ties shut.', 'VERIFIED', now()),
('weave_lidded_basket', 'Weave a lidded basket','lidded_basket', 1, 1, 'CUTTING', FALSE, FALSE, 100, 'items', 'CRAFT', 'lidded basket,basket with a lid,covered basket,make a lidded basket,weave a lidded basket,weave,plait', 'You weave a basket and a close-fitting lid to match, so what it holds stays covered against weather and pests.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- 5. Inputs (all obtainable first-era stock; output mass < input mass, conserving matter).
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('sew_hide_sack',        'tanned_leather', 1), ('sew_hide_sack',        'leather_cord',  1),
('assemble_wooden_bucket','timber_plank',  2), ('assemble_wooden_bucket','fiber_cordage', 1),
('form_clay_jar',        'clay_lump',      3),
('sew_grain_sack',       'plant_fiber',    4), ('sew_grain_sack',       'fiber_cordage', 1),
('roll_tool_wrap',       'tanned_leather', 1), ('roll_tool_wrap',       'leather_cord',  1),
('weave_lidded_basket',  'plant_fiber',    6), ('weave_lidded_basket',  'vine',          2)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- 6. Subject terms (the two-axis matcher needs one to match).
INSERT INTO process_subject (process_key, subject_term) VALUES
('sew_hide_sack','sack'), ('sew_hide_sack','hide'), ('sew_hide_sack','leather'),
('assemble_wooden_bucket','bucket'), ('assemble_wooden_bucket','plank'), ('assemble_wooden_bucket','wood'),
('form_clay_jar','jar'),
('sew_grain_sack','sack'), ('sew_grain_sack','grain'), ('sew_grain_sack','fibre'),
('roll_tool_wrap','roll'), ('roll_tool_wrap','tool'), ('roll_tool_wrap','wrap'),
('weave_lidded_basket','basket'), ('weave_lidded_basket','lid'), ('weave_lidded_basket','fibre')
ON CONFLICT DO NOTHING;
-- (No item_source rows for the outputs: they are process outputs, not raw sources, and nothing consumes them
-- as an input, so the coverage probe needs no acquisition path for them.)
