-- V220 — story #93 catalogue batch 13: snare components. Three pre-made parts that build a snare without twisting
-- fresh cordage on the spot, wired into WildlifeEncounterService.setTrap's SNARE build (a running loop alone, or a
-- wire cord paired with a trigger). Setting a trap is deterministic, so the test proves it end to end.
--   snare_loop          — a finished running noose ready to set on a run
--   snare_trigger_stick — a bent-stick spring trigger that yanks the loop tight
--   snare_wire_fibre     — a length of tough twisted-fibre snare cord
-- NOTE: 'snare' is a strong HUNT term AND triggers the SNARE intent, so a craft phrase containing it cannot reach a
-- recipe. These are therefore crafted by their real synonyms — 'twist a running loop', 'carve a spring trigger',
-- 'twist a wire cord' — which route cleanly (verified against the material matcher, hard-intent classifier, and
-- precedence). 'snare loop' etc. remain subject terms so the parts are still recognised by name. Read only by
-- setTrap (a code consumer), so registered in DeadEndOutputInvariantTest.CODE_TERMINAL.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('snare_loop',          'Snare running loop',  'MATERIAL', 40,  60,  TRUE, FALSE, 0),
('snare_trigger_stick', 'Snare trigger stick', 'MATERIAL', 150, 300, TRUE, FALSE, 0),
('snare_wire_fibre',    'Snare wire cord',     'MATERIAL', 30,  40,  TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('snare_loop',          'TECHNIQUE', 'a finished running noose'),
('snare_trigger_stick', 'TECHNIQUE', 'a bent-stick spring trigger'),
('snare_wire_fibre',    'TECHNIQUE', 'a length of tough twisted snare cord')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('twist_snare_loop',    'Twist a running loop',   'snare_loop',          1,1,NULL,FALSE,FALSE,15,'tools','PROCESS',
 'twist a running loop,tie a running loop,running loop,running noose',
 'You twist and tie a smooth running loop that closes hard when it is pulled — the business end of a snare, ready to set.','VERIFIED',now()),
('carve_snare_trigger', 'Carve a spring trigger', 'snare_trigger_stick', 1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a spring trigger,whittle a spring trigger,spring trigger,bent stick trigger',
 'You cut and bend a springy stick into a trigger that yanks the loop tight the instant it is disturbed.','VERIFIED',now()),
('twist_snare_wire',    'Twist a wire cord',      'snare_wire_fibre',    1,1,NULL,FALSE,FALSE,20,'tools','PROCESS',
 'twist a wire cord,twist a snaring cord,wire cord,snaring cord',
 'You lay up a length of tough, thin fibre cord — strong enough to hold a struggling animal on a run.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('twist_snare_loop',    'plant_fiber',2),
('carve_snare_trigger', 'dry_branch',1),
('twist_snare_wire',    'plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('twist_snare_loop','running loop'),('twist_snare_loop','snare loop'),('twist_snare_loop','running noose'),
('carve_snare_trigger','spring trigger'),('carve_snare_trigger','snare trigger'),
('twist_snare_wire','wire cord'),('twist_snare_wire','snaring cord'),('twist_snare_wire','snare wire')
ON CONFLICT DO NOTHING;
