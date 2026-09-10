-- #52/#79/#108 — a hard winter takes the young.
--
-- V296 gave the world young animals: carried, born, and grown on the real clock. Nothing could interrupt them.
-- A kid born into a January night on open ground reached maturity as reliably as one born in a byre in May, so
-- the young were a timer rather than a thing a keeper keeps alive.
--
-- That absence is the reason #108's farrowing shelter, brooder shelter and foaling stall could not honestly be
-- built yet: a structure that reduces losses is meaningless while there are no losses, and adding one would have
-- been a bonus applied to a process that cannot fail. The loss has to exist first.
--
-- WHAT DECIDES IT IS ALREADY IN THE CATALOGUE. `construction_kind` has carried `encloses` since V280 — "can a
-- Chronicle be inside this, out of the weather" — and the stock shelters split cleanly along it:
--
--     roofed  : CATTLE_BYRE, POULTRY_COOP, TIMBER_BARN
--     unroofed: ANIMAL_PEN, GOAT_FOLD, PIG_STY, HITCHING_POST, TETHER_LINE
--
-- So a byre and a fold have until now been identical to a keeper — both rest a beast, both stand in a wolf's way
-- — and the difference between a walled, roofed building and a ring of hurdles has meant nothing. It means
-- something now: what is under a roof lives through the cold, and what is behind a fence does not. No new
-- capability column, because the catalogue already answered this question for the Chronicle's own shelter.
--
-- ON THE SHAPE OF THE LOSS. Not a die roll per tick, and not instant death at the first frost. `cold_since` marks
-- when hard cold began for this animal and is cleared the moment it is sheltered or the weather turns, so the
-- rule is "three days of unbroken hard cold with no roof". A keeper who sees the frost coming and raises a byre
-- saves them; a keeper away for a week loses them. That is a decision with a deadline rather than a dice game,
-- which is the only kind of loss worth having.
--
-- Deliberately NOT modelled here, so the next reader does not think it was forgotten: predation of young (the
-- barrier layer already exists and wants its own slice), and loss to a dam in poor condition (condition already
-- gates conception, and stacking it here would punish the same neglect twice).

ALTER TABLE tamed_young ADD COLUMN IF NOT EXISTS cold_since TIMESTAMPTZ;

COMMENT ON COLUMN tamed_young.cold_since IS
  'When unbroken hard cold began for this animal with no roof over it, or NULL when sheltered or mild. Cleared '
  'the moment either changes, so it measures an unbroken spell rather than a total.';

CREATE INDEX IF NOT EXISTS idx_tamed_young_cold ON tamed_young (cold_since) WHERE cold_since IS NOT NULL;

DO $$
DECLARE n int; wrong text;
BEGIN
  -- The protection must be reachable: something a keeper can build has to have a roof over stock.
  SELECT count(*) INTO n FROM construction_kind WHERE shelters_stock AND encloses;
  IF n = 0 THEN
    RAISE EXCEPTION 'V297: nothing a keeper builds puts a roof over stock, so the young could never be saved';
  END IF;

  -- And it must be possible to get it wrong, or the rule never bites and is decoration.
  SELECT count(*) INTO n FROM construction_kind WHERE shelters_stock AND NOT encloses;
  IF n = 0 THEN
    RAISE EXCEPTION 'V297: every stock shelter has a roof, so exposure could never happen';
  END IF;

  -- The three the migration comment names as roofed must actually be roofed, or the distinction it rests on has
  -- drifted since it was written.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['CATTLE_BYRE','POULTRY_COOP','TIMBER_BARN']) k
   WHERE k NOT IN (SELECT project_kind FROM construction_kind WHERE shelters_stock AND encloses);
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V297: these are buildings and must keep the weather off what is in them: %', wrong;
  END IF;

  -- And the open ones must stay open, or a ring of hurdles quietly becomes a barn.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO wrong FROM construction_kind
   WHERE encloses AND project_kind IN ('ANIMAL_PEN','GOAT_FOLD','PIG_STY','HITCHING_POST','TETHER_LINE');
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V297: a fence is not a roof: %', wrong;
  END IF;

  -- Young must exist as a concept at all — this migration is meaningless without V296.
  IF NOT EXISTS (SELECT 1 FROM breeding_profile) THEN
    RAISE EXCEPTION 'V297: nothing breeds, so there are no young to lose';
  END IF;
END $$;
