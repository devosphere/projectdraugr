-- V418 — what a trap was built from, so lifting one can give it back (#37)
--
-- MEASURED LIVE, with six plant fibre in hand so the chain could actually be judged rather than refused:
--
--   set a snare          -> "You build the set snare and set it across the run. It stands there after you
--                            have gone."                                    (two plant fibre consumed)
--   check the snare      -> "The trap stands as you left it."               (honest)
--   take up the snare    -> "The trap stands as you left it."               (WRONG — the player asked to
--                                                                            REMOVE it, and was told it is
--                                                                            still standing)
--
-- That last one is a recorded defect of this project's asking-is-not-doing family. It was once worse — it
-- WORKED the snare when the player asked to remove it — and the fix made it report instead of act. Reporting
-- is better than the wrong act and is still not the act: a trapper lifts a set to move the line, and there was
-- no way to do it at all.
--
-- WHY THIS NEEDS A COLUMN AND NOT JUST A BRANCH. Lifting a trap should give back what it was made of, and
-- `placed_trap` records only the KIND. A SNARE can be built three ways — two plant fibre, one pre-made snare
-- loop, or a wire fibre paired with a trigger stick — so returning "plant fibre" for a snare built from a loop
-- would quietly TRANSFORM one catalogue item into another. That is matter from nothing wearing a different hat,
-- and this project's conservation rule forbids it: a better process means better RECOVERY, never more matter.
--
-- So the components are written down when the trap is set, and the lift returns those, minus one for the wear
-- of cutting it out. Recovery is therefore always a net LOSS of material, which is both honest about frayed
-- cordage and incapable of breaking mass balance.
--
-- Nullable, because every trap standing in a world older than this migration was set without the record. The
-- lift path reads a null as "nothing recoverable" and says so rather than guessing.

ALTER TABLE placed_trap
  ADD COLUMN IF NOT EXISTS built_from TEXT[];

COMMENT ON COLUMN placed_trap.built_from IS
  'The item_keys consumed to set this trap, in the order they were taken. Written by WildlifeEncounterService.'
  'setTrap and read by the lift path, which returns all but one of them — the one being the wear of cutting the '
  'set out. NULL for traps set before V418: the lift reads that as nothing recoverable and says so rather than '
  'guessing, because a SNARE can be built from fibre, a pre-made loop, or wire and a trigger, and returning the '
  'wrong one would transform one catalogue item into another.';

DO $$
DECLARE n int;
BEGIN
  -- 1. THE COLUMN MUST BE NULLABLE AND HAVE NO DEFAULT. A default would claim every pre-existing trap was
  --    built from something it was not, which is the whole error this column exists to avoid.
  SELECT count(*) INTO n FROM information_schema.columns
   WHERE table_name='placed_trap' AND column_name='built_from'
     AND is_nullable='YES' AND column_default IS NULL;
  IF n <> 1 THEN
    RAISE EXCEPTION 'V418: built_from must be nullable with no default, so a trap set before this migration is '
      'not credited with components it never had';
  END IF;

  -- 2. AND NOTHING MAY HAVE BEEN BACKFILLED. Asserted rather than assumed, because a later edit adding a
  --    backfill would make the lift hand out items that were never spent.
  SELECT count(*) INTO n FROM placed_trap WHERE built_from IS NOT NULL;
  IF n > 0 THEN
    RAISE EXCEPTION 'V418: % traps already carry a built_from; a trap set before this migration has no record '
      'of its components and must not be given one', n;
  END IF;
END $$;
