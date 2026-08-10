-- V90: timber buildings — the terminal consumers the carpentry chain never had (M1 #75/#77, EPIC #45/#54).
--
-- The game already lets a Chronicle saw and joint every building component — floorboards, ridge beams, joined
-- frames, dovetailed corners, door blanks, roof shakes, mortared courses — through a rich carpentry/masonry
-- process chain (V57/V60). But NOTHING consumed those components into an actual building, so every one of them
-- dead-ended in the inventory. That breaks the real-world line the whole game holds to: if you can rive a roof
-- shake and lay a floorboard, you must be able to raise a roof and lay a floor.
--
-- This adds the two buildings that close the chain, through the same staged-assembly engine (V58) that raised
-- the #61 shelters. Between them they consume all 24 finished carpentry/masonry components. The log cabin is an
-- enclosing, roofed dwelling and is wired into the exposure model (see ChroniclePhysiologyService / shelterInReach)
-- alongside the huts; the timber barn is an outbuilding. Every stage requirement is an already-obtainable
-- component, so the V58 review gate promotes both to VERIFIED.

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration) VALUES
('log_cabin','STRUCTURE','Log cabin',FALSE,NULL,'LOG_CABIN','construction',
 'build a log cabin,raise a log cabin,work on the log cabin,log cabin,build a timber cabin,timber cabin',
 'cabin,cabin wall,cabin roof',
 'The cabin stands finished and four-square — notched log walls chinked tight, a shake roof turned to the weather, a plank floor underfoot and a door hung true. It will keep a body warm and dry through any season.'),
('timber_barn','STRUCTURE','Timber-framed barn',FALSE,NULL,'TIMBER_BARN','construction',
 'build a timber barn,raise a barn,build a barn,work on the barn,timber barn,timber-framed barn,build an outbuilding',
 'barn,outbuilding',
 'The barn stands up on its charred posts, timber-framed and plank-walled with daub between the studs — a dry, roomy outbuilding to store and work under cover.');

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
-- log cabin
('cabin_foundation','log_cabin',1,'Lay the foundation',NULL,0,'STRIKING',FALSE,'You dress and course stone into a level foundation for the cabin to sit on, up off the damp ground.'),
('cabin_walls','log_cabin',2,'Raise the log walls','cabin_foundation',0,'AXE',FALSE,'You lift the notched logs into courses, seating each into the last, and drive moss into every seam until no daylight shows.'),
('cabin_frame','log_cabin',3,'Set the frame','cabin_walls',0,'CUTTING',FALSE,'You set the joined frames, scarf the plates together, and pin the sills down onto the walls.'),
('cabin_roof','log_cabin',4,'Raise the roof','cabin_frame',0,NULL,FALSE,'You set the ridge, lean the rafters up to it, and lay the shakes in overlapping courses from the eave, capped with bark.'),
('cabin_floor','log_cabin',5,'Lay the floor','cabin_roof',0,NULL,FALSE,'You lay the floorboards across the joists, wedge them tight, and level the seasoned timber underfoot.'),
('cabin_door','log_cabin',6,'Hang the door','cabin_floor',0,'CUTTING',FALSE,'You hang the door blank in its opening and fit the shutters, and the cabin closes up snug against the world.'),
-- timber barn
('barn_groundwork','timber_barn',1,'Set the groundwork',NULL,0,'STRIKING',FALSE,'You char and set the ground posts against rot, and pack a hard earth floor between them.'),
('barn_frame','timber_barn',2,'Raise the frame','barn_groundwork',0,'CUTTING',FALSE,'You pitch the timbers up, tie the corners in dovetails, and square the frame with a heavy log at each bent.'),
('barn_walls','timber_barn',3,'Plank the walls','barn_frame',0,NULL,FALSE,'You edge-join the board panels and lap the planks up the walls, weather-tight.'),
('barn_infill','timber_barn',4,'Infill and point','barn_walls',0,NULL,FALSE,'You set daubed panels between the studs and point the base in a mortared course, and the barn stands closed.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
-- log cabin
('cabin_foundation','foundation_stone',4),('cabin_foundation','stone_course',2),
('cabin_walls','notched_log',8),('cabin_walls','moss_chinking',4),
('cabin_frame','joined_frame',2),('cabin_frame','scarfed_beam',2),('cabin_frame','sill_plate',2),
('cabin_roof','ridge_beam',1),('cabin_roof','rafter_pole',6),('cabin_roof','roof_shake',20),('cabin_roof','bark_roofing',4),
('cabin_floor','floorboard',8),('cabin_floor','wooden_wedge',6),('cabin_floor','seasoned_timber',2),
('cabin_door','door_blank',1),('cabin_door','shutter_panel',2),
-- timber barn
('barn_groundwork','charred_post',4),('barn_groundwork','packed_floor',1),
('barn_frame','pitched_timber',2),('barn_frame','timber_log',2),('barn_frame','dovetailed_corner',4),
('barn_walls','board_panel',4),('barn_walls','lapped_plank',4),
('barn_infill','daubed_panel',2),('barn_infill','mortared_course',2);

-- Re-run the V58 review gate over the new DRAFT rows and promote what passes (same three blocking checks).
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'BAD_PREREQUISITE', 'BLOCKING',
       'Stage ' || s.stage_key || ' has a prerequisite that is not an earlier stage of the same assembly.'
FROM assembly_stage s JOIN assembly_stage p ON p.stage_key = s.prerequisite_stage_key
WHERE (p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order)
  AND s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT');
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT d.assembly_key, 'NO_STAGES', 'BLOCKING', 'Assembly declares no stages.'
FROM assembly_definition d
WHERE d.review_state='DRAFT' AND NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = d.assembly_key);
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT DISTINCT s.assembly_key, 'UNOBTAINABLE_REQUIREMENT', 'BLOCKING',
       'Stage ' || r.stage_key || ' requires ' || r.item_key || ', which has no acquisition path.'
FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
WHERE s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT')
  AND NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key = r.item_key);
INSERT INTO assembly_review (assembly_key, finding_kind, severity, detail)
SELECT s.assembly_key, 'EMPTY_WORK_STAGE', 'ADVISORY',
       'Stage ' || s.stage_key || ' is a work stage with no requirements.'
FROM assembly_stage s
WHERE s.assembly_key IN (SELECT assembly_key FROM assembly_definition WHERE review_state='DRAFT')
  AND s.cure_minutes = 0
  AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key);

UPDATE assembly_definition SET review_state='VERIFIED', reviewed_at=now()
WHERE review_state='DRAFT' AND NOT EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);
UPDATE assembly_definition SET review_state='NEEDS_REFINEMENT', reviewed_at=now()
WHERE review_state='DRAFT' AND EXISTS (
    SELECT 1 FROM assembly_review r WHERE r.assembly_key=assembly_definition.assembly_key
      AND r.severity='BLOCKING' AND r.resolved_at IS NULL);
