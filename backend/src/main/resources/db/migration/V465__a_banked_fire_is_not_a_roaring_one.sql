-- V465: banking a fire is the opposite of letting it roar, and one number said both (#890).
--
-- `fire_state.fuel_minutes` is burning time remaining. It is also, and only because nothing else existed,
-- what the ember hazard reads as INTENSITY: scorchNearbyFlammables bites above 120 fuel-minutes and
-- fireReading warns at exactly the same figure.
--
-- Banking RAISES fuel_minutes — that is the whole point of raking the coals tight and covering them with
-- ash, and it is why bank() adds 90 (130 with a poker). So a keeper who did the careful, correct thing on a
-- low fire was immediately told their hearth was throwing embers and would char the hut and the smoke vent
-- by the hour. Live, Chronicle 17: bank a fire at about 50 minutes, then check it, and the answer contradicts
-- the act that produced it. The simulation had it exactly backwards: covered coals throw LESS than an open
-- flame, not more.
--
-- DURATION AND FEROCITY ARE TWO FACTS AND NOW HAVE TWO COLUMNS. A banked fire keeps its long burn and stops
-- throwing embers; feeding it or lighting it again opens the flame and clears the flag, because a fire you
-- have just put a branch on is not banked any more whatever it was a minute ago.
ALTER TABLE fire_state ADD COLUMN IF NOT EXISTS banked BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN fire_state.banked IS
  'The coals are raked tight and covered: the fire holds low and long and throws no embers. Set by '
  'FireService.bank, cleared by light() and feed(), read by scorchNearbyFlammables and fireReading. Never '
  'true of a fire that is out — extinguish clears it with the flame.';

-- A dead fire is not a banked one, and a fire with no fuel left is dead.
UPDATE fire_state SET banked = FALSE WHERE NOT active OR fuel_minutes = 0;
