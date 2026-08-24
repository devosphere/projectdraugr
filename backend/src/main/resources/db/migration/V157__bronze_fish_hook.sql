-- V157: the bronze fish hook (EPIC #180 heavy industry / #184 copper objects, and #36 fishing).
--
-- A bone hook turns bare grabbing into angling, but it is soft and blunt and bends or snaps on a good fish. A metal
-- hook — forged from a little bronze, filed to a keen barb — holds a fish that a bone hook would lose, so a line
-- fishes better with one. One ingot casts a whole handful of hooks. Completes the angling tackle beside the lead
-- sinker (V154): a metal hook catches, a lead sinker carries it deep. (The line read is in WildlifeEncounterService.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bronze_fish_hook', 'Bronze fish hook', 'TOOL', 40, 15, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bronze_fish_hook', 'TECHNIQUE', 'forged and filed from a little bronze into a keen barbed hook')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Forge a handful of hooks from one ingot ('forge'->CRAFT, V144). The hook/fish subject keeps it distinct.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_bronze_fish_hook', 'Forge bronze fish hooks', 'bronze_fish_hook', 3,6, 'STRIKING', TRUE, FALSE, 40, 'tools', 'CRAFT',
 'forge a bronze fish hook,forge bronze fish hooks,make bronze fish hooks,hammer out fish hooks,file bronze fish hooks,bronze fish hook',
 'You draw the bronze into fine wire, cut and bend it, and file each to a keen barbed point — a handful of hooks that will hold a fish where a bone one would straighten and lose it.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_bronze_fish_hook', 'bronze_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_bronze_fish_hook','hook'), ('forge_bronze_fish_hook','hooks'), ('forge_bronze_fish_hook','fish')
ON CONFLICT DO NOTHING;
