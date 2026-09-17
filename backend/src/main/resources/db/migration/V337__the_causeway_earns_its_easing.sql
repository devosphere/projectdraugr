-- #77 — the causeway earns its easing.
--
-- V333 gave the fen causeway its first effect: a laden walker gets across soft ground that would otherwise turn
-- them back. V336 gave laid ways a second kind of worth — minutes off the going of the chunk they lie on — and
-- deliberately left the causeway out of it, because Flyway runs OUT OF ORDER in this project: had V336 landed
-- before V333, an `UPDATE ... WHERE project_kind='FEN_CAUSEWAY'` inside it would have matched nothing and the
-- causeway would have stayed un-eased for good, silently. Both are now in the same tree, so it can be set safely.
--
-- THE NUMBER. A fen costs 36 minutes a chunk: wading, and every step found before it is taken. A pegged timber way
-- takes 14 of those off, bringing it to 22 — about the pace of woodland. That is what such a road actually bought:
-- not a highway, but firm footing where there was none, on a way narrow enough that you still watch your feet. It
-- cannot go lower, because V336's floor holds a laid way to the going of open grass and its guard refuses any
-- easing that would reach it.
--
-- WHY THIS IS NOT THE SAME EFFECT TWICE. Crossing and pace are different questions, and a Chronicle can meet
-- either alone: an unladen walker crosses the bog today and is still slowed to a wade by it, and a laid path on dry
-- ground quickens a walk that was never in doubt. The causeway answers both because a plank road over peat really
-- does both — which is the test of whether a second effect is earned or invented.

UPDATE construction_kind SET eases_going = 14 WHERE project_kind = 'FEN_CAUSEWAY';

DO $$
DECLARE eased int; fen int; floor_min int;
BEGIN
  SELECT eases_going INTO eased FROM construction_kind WHERE project_kind = 'FEN_CAUSEWAY';
  IF eased IS NULL THEN RAISE EXCEPTION 'V337: there is no fen causeway to ease'; END IF;
  IF eased = 0 THEN RAISE EXCEPTION 'V337: the causeway was not eased'; END IF;

  SELECT minutes_per_chunk INTO fen FROM terrain_going WHERE biome = 'WETLAND';
  SELECT MIN(minutes_per_chunk) INTO floor_min FROM terrain_going;
  IF fen IS NULL OR floor_min IS NULL THEN RAISE EXCEPTION 'V337: the going of the ground is not recorded'; END IF;

  -- A way over a bog must leave it slower than open grass, and must not be so slight that laying it changes
  -- nothing a player would notice.
  IF fen - eased <= floor_min THEN
    RAISE EXCEPTION 'V337: a plank road over peat is not a meadow (fen % - easing % would reach the floor %)',
      fen, eased, floor_min;
  END IF;
  IF eased < 5 THEN RAISE EXCEPTION 'V337: an easing nobody would feel is not worth the timber: %', eased; END IF;

  -- The crossing effect is the causeway's first and must survive this one untouched.
  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='FEN_CAUSEWAY' AND crosses_soft_ground) THEN
    RAISE EXCEPTION 'V337: the causeway has lost the crossing it was built for';
  END IF;
END $$;
