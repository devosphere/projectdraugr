-- V96: tarred cordage lashes what gets wet, and the smoke hood completes the smoke rack (M1 #75).
--
-- Real-world-logic (misc audit). tarred_cordage (waterproof lashing) and smoke_hood (a smoking cover) were
-- orphans. Tarred cordage is simply better cordage where water is involved, so it becomes an alternative to
-- plain fibre cordage in the bindings that get wet or bear heavy wet loads — bucket, bark container, burden
-- frame, rope harness (input GROUP, either will do, keeping the original counts). The smoke hood is what turns
-- a bare rack into a working smoker, so raising a smoke rack now consumes one. Pure data.

-- Waterproof cordage as an alternative binder (either fibre or tarred cordage satisfies the slot).
DELETE FROM material_process_input WHERE item_key='fiber_cordage' AND process_key IN ('assemble_wooden_bucket','make_bark_container','make_rope_harness','lash_burden_frame');
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('assemble_wooden_bucket','cordage','fiber_cordage',1),('assemble_wooden_bucket','cordage','tarred_cordage',1),
('make_bark_container','cordage','fiber_cordage',1),('make_bark_container','cordage','tarred_cordage',1),
('lash_burden_frame','cordage','fiber_cordage',2),('lash_burden_frame','cordage','tarred_cordage',2),
('make_rope_harness','cordage','fiber_cordage',4),('make_rope_harness','cordage','tarred_cordage',4)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

-- The smoke hood completes the smoke rack into a working smoker.
INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('smoke_bars','smoke_hood',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;
