-- V216 — story #93 catalogue batch 9: deadfall trap components. Three pre-made parts that build a deadfall trap
-- without gathering raw stone and branch on the spot — wired into WildlifeEncounterService.setTrap as an
-- alternative DEADFALL build (a weight stone paired with either a plain trigger or a figure-four trigger). Setting a
-- trap is deterministic (it succeeds when the parts are in hand), so the test proves it end to end.
--   deadfall_weight_stone — a heavy stone shaped to drop flat and kill clean
--   deadfall_trigger      — a simple prop-and-bait trigger stick
--   figure_four_trigger   — the classic figure-four trigger, more sensitive
-- Craft verbs ('shape'/'carve') dodge the SET_TRAP intent (which needs build/set/make/place/construct/lay), and
-- 'deadfall'/'trigger' carry no category weight, so each routes to its own recipe. Read only by setTrap (a code
-- consumer), so they are registered in DeadEndOutputInvariantTest.CODE_TERMINAL. Routing/matter/probe verified locally.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('deadfall_weight_stone', 'Deadfall weight stone', 'MATERIAL', 600, 300, TRUE, FALSE, 0),
('deadfall_trigger',      'Deadfall trigger',      'MATERIAL', 200, 400, TRUE, FALSE, 0),
('figure_four_trigger',   'Figure-four trigger',   'MATERIAL', 180, 400, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('deadfall_weight_stone', 'TECHNIQUE', 'a heavy stone shaped to drop flat'),
('deadfall_trigger',      'TECHNIQUE', 'a propped trigger stick for a deadfall'),
('figure_four_trigger',   'TECHNIQUE', 'a three-stick figure-four release')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('shape_deadfall_weight', 'Shape a deadfall weight stone', 'deadfall_weight_stone', 1,1,NULL,FALSE,FALSE,25,'tools','PROCESS',
 'shape a deadfall weight stone,shape a deadfall stone,deadfall weight stone,deadfall stone',
 'You work a heavy slab flat and square so it will drop true and pin whatever is beneath it.','VERIFIED',now()),
('carve_deadfall_trigger','Carve a deadfall trigger',     'deadfall_trigger',      1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a deadfall trigger,whittle a deadfall trigger,deadfall trigger',
 'You cut a notched prop and bait stick — set under the weight, a touch brings it down.','VERIFIED',now()),
('carve_figure_four',     'Carve a figure-four trigger',  'figure_four_trigger',   1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'carve a figure four trigger,whittle a figure four trigger,figure four trigger,figure four',
 'You cut and notch three sticks into a figure-four release — sensitive enough to spring at a light pull on the bait.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('shape_deadfall_weight', 'field_stone',1),
('carve_deadfall_trigger','dry_branch',1),
('carve_figure_four',     'dry_branch',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('shape_deadfall_weight','deadfall weight stone'),('shape_deadfall_weight','deadfall stone'),
('carve_deadfall_trigger','deadfall trigger'),
('carve_figure_four','figure four trigger'),('carve_figure_four','figure four')
ON CONFLICT DO NOTHING;
