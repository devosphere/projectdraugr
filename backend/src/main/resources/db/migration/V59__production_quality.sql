-- V59: Production quality -- grade, inspection, and rework (M3b).
--
-- Every real workflow has a quality dimension and the foundation had none: a
-- process or an assembly either produced its output or it did not, and a rushed
-- job was indistinguishable from a careful one. The leather-armour fixture named
-- the missing idea plainly -- a fine hide should yield fine armour -- and the bow
-- named the other half: a botched component should be caught and REWORKED, not
-- silently carried into a finished weapon.
--
-- So quality is an ordered grade -- defective < poor < sound < fine -- set when a
-- thing is made and flowing forward: an output is never better than the worst of
-- its inputs and the care of the attempt (Layer 2 of the success model). Defective
-- work is not produced silently; it is caught at the next step, which refuses to
-- consume it until it is reworked.
--
-- The grade lives on the item instance (a made thing carries its quality) and on
-- each assembly stage completion (so an in-progress build can be inspected stage by
-- stage, and rework can return to exactly the flawed step).

ALTER TABLE item_instance
    ADD COLUMN quality_grade VARCHAR(12) NOT NULL DEFAULT 'SOUND'
        CHECK (quality_grade IN ('DEFECTIVE','POOR','SOUND','FINE'));

ALTER TABLE assembly_stage_completion
    ADD COLUMN quality_grade VARCHAR(12) NOT NULL DEFAULT 'SOUND'
        CHECK (quality_grade IN ('DEFECTIVE','POOR','SOUND','FINE'));

-- Raw materials already in the world predate the idea of grade; they are sound by
-- default (the column default covers every row and every future gather). Nothing
-- else needs backfilling -- quality only becomes interesting once things are made
-- from other things, which the grade-flow logic now handles going forward.

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('production_quality','Production Quality','An ordered grade -- defective, poor, sound, fine -- carried by made items and by each stage of an assembly, flowing from a thing''s inputs and the care of the attempt. Defective work is gated at the next step and can be reworked rather than discarded.','V59','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
