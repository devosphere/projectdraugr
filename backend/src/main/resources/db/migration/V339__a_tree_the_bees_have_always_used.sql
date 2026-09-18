-- #224/#157 — a tree the bees have always used, and one rule for what a concentration IS.
--
-- THE EXISTING RULE, AND WHY IT WANTED GENERALISING. #157 gave the mussel bed a real advantage: a dense mat
-- cemented to itself and the rock reseeds from its own population, so it comes back in half the time a thin
-- scatter does. That was written as `shellfish AND a site whose kind contains 'shell bed'` — a boolean on the
-- colony and a string spelled out in Java, which is two ways of saying one thing: **this colony is concentrated
-- where the world has put its place.**
--
-- A wild bee tree is the same fact about a different animal. A hollow tree a swarm has occupied for years holds a
-- far larger colony than a chance hive in a fence post, and it is refilled from a population that never left.
--
-- WHAT THIS ADDS. `insect_colony_kind.concentrated_at` — the site kind, if any, where this colony is a
-- concentration rather than a scatter. The shellfish special case becomes one row of it, so the rule has a single
-- definition and a third colony can be added as data.
--
-- WHAT IT DOES NOT DO. It does not change a single yield. A concentration comes back sooner; it does not give
-- more in the hand, because what a person can carry off a hive in one robbing is set by the comb and not by how
-- much is behind it. That keeps this change to the one claim it can defend.

ALTER TABLE insect_colony_kind ADD COLUMN IF NOT EXISTS concentrated_at VARCHAR(60);

COMMENT ON COLUMN insect_colony_kind.concentrated_at IS
  'The ecology-site kind, matched as a fragment, where this colony is a concentration rather than a scatter: it '
  'recovers in half the time there. NULL for a colony the world places no such site for.';

UPDATE insect_colony_kind SET concentrated_at = 'shell bed'  WHERE shellfish;
UPDATE insect_colony_kind SET concentrated_at = 'bee tree'   WHERE colony_kind = 'honeybee_hive';

DO $$
DECLARE n int; bad text;
BEGIN
  -- The shellfish rule must be carried over exactly, or this migration has quietly dropped #157's mechanic.
  SELECT string_agg(colony_kind, ', ') INTO bad FROM insect_colony_kind WHERE shellfish AND concentrated_at IS NULL;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V339: a shellfish bed lost its concentration: %', bad; END IF;

  SELECT count(*) INTO n FROM insect_colony_kind WHERE concentrated_at IS NOT NULL;
  IF n < 3 THEN RAISE EXCEPTION 'V339: expected the two beds and the bee tree, found %', n; END IF;

  -- A fragment nobody can match is a rule that never fires. The site kinds themselves live in Java (the marker
  -- catalogue), so the pairing is asserted by ConcentrationSiteInvariantTest rather than here — what this can
  -- check is that nothing was left blank or spelled as a pattern the matcher would not take literally.
  SELECT string_agg(colony_kind, ', ') INTO bad FROM insect_colony_kind
   WHERE concentrated_at IS NOT NULL AND (trim(concentrated_at) = '' OR concentrated_at LIKE '%\%%');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V339: a concentration site that cannot be matched: %', bad; END IF;
END $$;
