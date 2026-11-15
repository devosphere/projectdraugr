-- V467: which way the sickness is going (#909).
--
-- V465/V466 are taken by the fire work in flight; this is the next free number.
--
-- Illness now falls as well as rises: every source of it is answerable, and answering them all used to leave
-- the Chronicle exactly as sick as before, with 80 meaning Critical and 100 meaning Systemic Illness. A
-- Chronicle who had put right everything the simulation held against them still had nowhere to go but down.
--
-- But a NUMBER is not an answer to "am I getting better?". The body reading can say how sick you are; it
-- cannot say which way it is moving without being told, and working it out a second time in the reader is
-- how this codebase gets two readers of one question that disagree. The physiology tick already computes
-- exactly this — pressure minus mending — against the full environment: the filth, the wet and the cold, the
-- wound that will not close, the unvented hearth, the refuse piled at the camp. It writes it down here, and
-- the reading repeats it rather than guessing at it.
--
-- Positive is getting worse, negative is mending, zero is holding. Zero for a Chronicle that has not been
-- ticked since this migration, which reads as "holding" and is the right thing to say about a body nothing
-- is known to have happened to.
ALTER TABLE chronicle_physiology ADD COLUMN IF NOT EXISTS illness_trend SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN chronicle_physiology.illness_trend IS
  'Points of illness the last physiology tick added (positive) or threw off (negative), written by '
  'ChroniclePhysiologyService.advanceTo and read by the body reading so the player is told which way their '
  'sickness is going by the one piece of code that knows.';
