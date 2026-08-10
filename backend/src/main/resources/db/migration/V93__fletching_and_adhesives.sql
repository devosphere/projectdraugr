-- V93: any feather fletches, any adhesive binds (M1 #75, EPIC #45/#54).
--
-- Real-world-logic ([[feedback_real_world_simulation]]), adhesives/animal-parts audit. The arrow chain already
-- fletches and assembles arrows, but it accepted only the generic `feather` and only `pine_pitch` — so the
-- specific feathers a Chronicle actually plucks (harpy, roc) could not fletch, and the other adhesives it renders
-- (birch tar, fish glue, propolis) could not bind, though every one of them does exactly that job in the real
-- world. This makes those slots interchangeable through input GROUPS, closing five orphan materials by letting
-- them do their true work.
--
-- Bigger feathers/weaker glues take fewer/more per job, as they should. Pure data — the recipes are unchanged
-- except that their fletching and binder slots now accept the real alternatives.

-- Fletching: replace the fixed generic feather with a group that takes any feather (a roc feather is huge, so
-- two suffice; a harpy feather large, three; ordinary feathers, eight).
DELETE FROM material_process_input WHERE process_key='fletch_arrows' AND item_key='feather';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('fletch_arrows','fletching','feather',8),
('fletch_arrows','fletching','harpy_feather',3),
('fletch_arrows','fletching','roc_feather',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

-- Arrow binder: replace the fixed pine pitch with a group of real adhesives (propolis is weaker, so two).
DELETE FROM material_process_input WHERE process_key='assemble_arrows' AND item_key='pine_pitch';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('assemble_arrows','binder','pine_pitch',1),
('assemble_arrows','binder','birch_tar',1),
('assemble_arrows','binder','fish_glue',1),
('assemble_arrows','binder','propolis',2)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

-- Tarred cordage: birch tar is literally tar, so it makes tarred cordage as well as pine pitch does.
DELETE FROM material_process_input WHERE process_key='tar_cordage' AND item_key='pine_pitch';
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('tar_cordage','tar','pine_pitch',1),
('tar_cordage','tar','birch_tar',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
