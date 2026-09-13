-- #216 — building is heard: the card's most arguable silence, settled.
--
-- The building intents left the land unmarked, and the decision was recorded on #216 as open for one reason: "no
-- behaviour in the world currently distinguishes a worked camp from an unworked one closely enough to test it." That
-- is no longer true of the one footprint building would leave. chunk_disturbance is read by WildlifeSimulationService:
-- wildlife on ground at 40 or more is pushed onto quieter neighbouring ground, at 70 it flees outright, and an empty
-- site only refills below 40. Disturbance decays by four an hour.
--
-- So the question is answerable, and the answer is yes. Driving posts, lashing a frame and splitting timber for an
-- hour is loud, sustained, strange work, and animals keep away from a camp where it is going on. They do not flee one
-- afternoon's fence — a single job fades inside two hours — but a camp being built up day after day holds its
-- disturbance, and the deer stop coming to the water beside it. That is what happens.
--
-- COMMOTION, on the scale already set by a confrontation with an animal (20, drifting):
--   8  heavy building: a fence, pen, pit, rack, lookout, store, tool shed; and every assembly stage
--   6  a lean-to begun or worked
--   4  light building: a camp alarm line, a smoke vent
-- None drifts: the noise is on the ground being worked, not carried downwind like smoke.
--
-- Deliberately still silent, with their notes unchanged: propping a windbreak, throwing a cover over, making a bed,
-- abandoning or resuming a lean-to, and REPAIR_LEAN_TO — mending is quieter than raising, and its card says so.

UPDATE activity_impact SET footprint_kind = 'COMMOTION', footprint_amount = 8,
       notes = 'Building is heard (#216, V324). Posts driven, frames lashed, timber split for an hour: animals keep off ground where that goes on. One job fades in two hours; a camp built up day after day holds its disturbance.'
 WHERE intent_key IN ('BUILD_FENCE','BUILD_PEN','BUILD_FIRE_PIT','BUILD_FUEL_RACK','BUILD_LOOKOUT','BUILD_STORAGE_AREA','BUILD_TOOL_SHED','ADVANCE_ASSEMBLY')
   AND footprint_kind IS NULL;

UPDATE activity_impact SET footprint_kind = 'COMMOTION', footprint_amount = 6,
       notes = 'Building is heard (#216, V324). Cutting and lashing a lean-to frame is lighter than raising a structure, but it is still an hour of noise on the ground.'
 WHERE intent_key IN ('START_LEAN_TO','WORK_LEAN_TO') AND footprint_kind IS NULL;

UPDATE activity_impact SET footprint_kind = 'COMMOTION', footprint_amount = 4,
       notes = 'Building is heard (#216, V324). Stringing an alarm line or opening a smoke vent is short, light work, and leaves only a little noise behind.'
 WHERE intent_key IN ('BUILD_ALARM','BUILD_SMOKE_VENT') AND footprint_kind IS NULL;

DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM activity_impact WHERE footprint_kind = 'COMMOTION' AND intent_key ~ '^(BUILD_|ADVANCE_ASSEMBLY|START_LEAN_TO|WORK_LEAN_TO)';
  IF n <> 12 THEN RAISE EXCEPTION 'V324: expected 12 building cards to be heard, found %', n; END IF;

  -- The latrine keeps its excavation footprint; it was already heard, as a pit.
  IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'BUILD_LATRINE' AND footprint_kind = 'EXCAVATION') THEN
    RAISE EXCEPTION 'V324: the latrine''s excavation footprint must be untouched';
  END IF;

  -- No single building job may displace wildlife by itself: that takes 40, and one job must stay well under it.
  IF EXISTS (SELECT 1 FROM activity_impact WHERE intent_key ~ '^(BUILD_|ADVANCE_ASSEMBLY|START_LEAN_TO|WORK_LEAN_TO)'
             AND footprint_kind = 'COMMOTION' AND footprint_amount >= 20) THEN
    RAISE EXCEPTION 'V324: a building job as loud as a fight with an animal would clear the ground by itself';
  END IF;
END $$;
