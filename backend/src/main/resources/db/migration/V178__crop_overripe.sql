-- V178 — the harvest has a deadline (EPIC #162 agriculture): a ripe crop left un-reaped goes over and is lost.
--
-- The sow→grow→reap loop (V177) let a stand stand ripe forever, waiting patiently to be reaped. Grain does not wait:
-- left past its season the heads shatter, the birds and weather take it, and the crop is lost. This is what makes the
-- harvest a decision under pressure — bring it in on time, or lose the season's labour. This adds an outcome to the
-- stand so a reaped crop and a crop lost to neglect are distinguished (a reaping records REAPED; the world tick loses
-- an over-ripe stand as LOST), and PhysicalItemService.advanceCrops runs the deadline in the world tick.
ALTER TABLE crop_stand ADD COLUMN outcome VARCHAR(16);

COMMENT ON COLUMN crop_stand.outcome IS
    'How a finished stand ended: NULL while growing, REAPED when a Chronicle brought it in, LOST when it went over un-reaped (#162). Set alongside harvested.';
