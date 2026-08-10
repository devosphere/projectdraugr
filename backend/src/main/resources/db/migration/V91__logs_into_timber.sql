-- V91: felled logs actually become timber (M1 #75/#77, EPIC #45/#54).
--
-- Real-world-logic fix (orphan-materials audit, Vertical A1). Two breaks: (1) the six species logs a Chronicle
-- fells — oak/ash/pine/spruce/maple/birch — were consumed by nothing, so a felled tree just sat there; and
-- (2) split_planks, timber_from_log, and notch_log produced planks/timber/notched logs FROM NOTHING, so building
-- stock materialised out of thin air while the real logs rotted unused. Both are impossible in the world.
--
-- The fix wires each of those three milling processes to consume a log through an input GROUP (alternatives):
-- any one species log satisfies the step, and executeProcess fails grounded when no log is within reach — so a
-- plank now always comes off a log, and every species log has a use. Output stays below the log it takes
-- (split_planks makes at most 4 planks x1400g = 5600g < the lightest log, birch 6000g), so milling is
-- mass-honest. Pure data; no new items.

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
-- split a log into sawn planks
('split_planks','log','oak_log',1),('split_planks','log','ash_log',1),('split_planks','log','pine_log',1),
('split_planks','log','spruce_log',1),('split_planks','log','maple_log',1),('split_planks','log','birch_log',1),
-- hew a log into a squared timber
('timber_from_log','log','oak_log',1),('timber_from_log','log','ash_log',1),('timber_from_log','log','pine_log',1),
('timber_from_log','log','spruce_log',1),('timber_from_log','log','maple_log',1),('timber_from_log','log','birch_log',1),
-- notch a log for log-cabin cornering
('notch_log','log','oak_log',1),('notch_log','log','ash_log',1),('notch_log','log','pine_log',1),
('notch_log','log','spruce_log',1),('notch_log','log','maple_log',1),('notch_log','log','birch_log',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
