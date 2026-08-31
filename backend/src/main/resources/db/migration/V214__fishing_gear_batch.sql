-- V214 — story #93 catalogue batch 7: fishing spears and hook. Three tools that plug into
-- WildlifeEncounterService.fish()'s gear selection (each was a token until now), made functional by widening those
-- carried-gear reads — pure data plus a two-line Java widening (fish_spear/eel_spear -> SPEAR, thorn_fish_hook ->
-- LINE):
--   fish_spear      — a barbed spear for taking fish in the shallows
--   eel_spear       — a multi-pronged leister for eels
--   thorn_fish_hook — a small carved gorge hook for a hand-line
-- All equippable (dead-end-clean). Craft phrases dodge the hard-intent minefield: 'haft'/'carve' are CRAFT verbs
-- that outweigh the HUNT weight of 'spear'; the 'fish'/'eel' words are safe because the FISH intent defers to a
-- matched process (!actionMatchesProcess). Routing verified locally against BOTH the material matcher and the
-- hard-intent classifier. Fishing OUTCOMES are probabilistic, so the test asserts the craft, not the catch.
-- (The woven weir panel was held back — its phrasing collides with reinforce/weave processes and needs its own care.)

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('fish_spear',      'Fish spear',      'TOOL', 550, 1300, FALSE, TRUE, 0),
('eel_spear',       'Eel spear',       'TOOL', 550, 1300, FALSE, TRUE, 0),
('thorn_fish_hook', 'Thorn fish hook', 'TOOL', 10,  8,    TRUE,  TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('fish_spear',      'TECHNIQUE', 'a barbed point hafted for spearing fish in the shallows'),
('eel_spear',       'TECHNIQUE', 'a multi-pronged leister for pinning eels'),
('thorn_fish_hook', 'TECHNIQUE', 'a small gorge hook carved from bone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fish_spear','HAND_RIGHT','CARRIED'),('fish_spear','HAND_LEFT','CARRIED'),
('eel_spear','HAND_RIGHT','CARRIED'),('eel_spear','HAND_LEFT','CARRIED'),
('thorn_fish_hook','HAND_RIGHT','CARRIED'),('thorn_fish_hook','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_fish_spear', 'Haft a fish spear',    'fish_spear',      1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'haft and lash a fish spear,lash a fish spear,fish spear',
 'You barb a bone point and lash it to a light shaft — a spear balanced for driving down onto fish in clear shallows.','VERIFIED',now()),
('carve_eel_spear',  'Haft an eel spear',    'eel_spear',       1,1,NULL,FALSE,FALSE,40,'tools','CRAFT',
 'haft an eel spear,haft and lash an eel spear,eel spear,eel leister',
 'You bind several barbed prongs to a shaft — a leister that pins an eel to the mud where a single point would slip.','VERIFIED',now()),
('carve_thorn_hook', 'Carve a thorn fish hook','thorn_fish_hook',1,2,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a thorn fish hook,carve a gorge hook,thorn fish hook,gorge hook',
 'You carve a small double-pointed gorge from bone — swallowed with the bait, it turns crosswise and holds the fish.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_fish_spear', 'dry_branch',1),('carve_fish_spear','animal_bone',1),('carve_fish_spear','plant_fiber',1),
('carve_eel_spear',  'dry_branch',1),('carve_eel_spear','animal_bone',1),('carve_eel_spear','plant_fiber',1),
('carve_thorn_hook', 'animal_bone',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_fish_spear','fish spear'),
('carve_eel_spear','eel spear'),('carve_eel_spear','eel leister'),
('carve_thorn_hook','thorn fish hook'),('carve_thorn_hook','gorge hook')
ON CONFLICT DO NOTHING;
