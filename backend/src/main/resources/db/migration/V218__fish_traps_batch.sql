-- V218 — story #93 catalogue batch 11: woven fish traps. Two carried traps that make fish() choose the TRAP method
-- (its best odds) when one is in hand, previously only a generic fish_trap did:
--   crayfish_trap     — a small funnel trap for crayfish and small fish
--   basket_fish_trap  — a woven creel that funnels and holds fish
-- Craft phrasing threads the hard-intent minefield: 'carve a crayfish trap' is CRAFT (carve 2 beats trap's HUNT 1,
-- and 'carve' is not a SET_TRAP verb), while a plain 'fish trap' phrase is HUNT-dominated, so the creel is made as
-- 'carve a wicker creel' (a creel IS a fish basket, and 'creel'/'wicker' carry no category weight). Read only by
-- fish() (a code consumer), so both are registered in DeadEndOutputInvariantTest.CODE_TERMINAL. Routing/matter/probe
-- verified locally against the material matcher, the hard-intent classifier, and precedence.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('crayfish_trap',    'Crayfish trap',    'MATERIAL', 600, 2500, FALSE, FALSE, 0),
('basket_fish_trap', 'Woven fish creel', 'MATERIAL', 700, 3000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('crayfish_trap',    'TECHNIQUE', 'a small woven funnel trap for crayfish'),
('basket_fish_trap', 'TECHNIQUE', 'a woven creel that funnels and holds fish')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_crayfish_trap', 'Carve a crayfish trap', 'crayfish_trap',    1,1,NULL,FALSE,FALSE,40,'tools','CRAFT',
 'carve a crayfish trap,weave a crayfish trap,crayfish trap',
 'You weave a small withy funnel trap — dropped in a stream with bait, crayfish and minnows crawl in and cannot back out.','VERIFIED',now()),
('carve_fish_creel',    'Carve a wicker creel',  'basket_fish_trap', 1,1,NULL,FALSE,FALSE,50,'tools','CRAFT',
 'carve a wicker creel,weave a wicker creel,wicker creel,fish creel',
 'You weave a long withy creel with an inward funnel mouth — fish swim in with the current and are held.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_crayfish_trap', 'hazel_rod',2),
('carve_fish_creel',    'hazel_rod',2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_crayfish_trap','crayfish trap'),('carve_crayfish_trap','crayfish'),
('carve_fish_creel','wicker creel'),('carve_fish_creel','fish creel'),('carve_fish_creel','creel')
ON CONFLICT DO NOTHING;
