-- V139: item wear (EPIC #215 story #220 — the item side of rust/rot/weathering).
--
-- condition_state (SOUND/WORN/BROKEN/DESTROYED) existed and could be mended (whetstone/repair) and examined
-- ("It shows honest wear, but holds"), but NOTHING ever degraded a tool through use — an axe felled forever and
-- never dulled. use_count paces that wear: a tool accrues uses on the work it does, and at thresholds its
-- condition steps down (SOUND -> WORN -> BROKEN); mending it resets the counter. Kept general on item_instance so
-- other tools can wear on their own use paths later; the first driver is felling (see PhysicalItemService.fellTree).
ALTER TABLE item_instance ADD COLUMN use_count INTEGER NOT NULL DEFAULT 0;
