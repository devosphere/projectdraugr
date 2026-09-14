-- #79 — a mouser for the stores: the site, guard and pest-control role.
--
-- #79 names a "site/guard/pest-control" role and its candidates: `domestic_cat_candidate` and `ferret_candidate`.
-- Neither needs a new animal. The wild lines both are bred from already live in the world and can already be tamed:
-- the European wildcat, whose kind the house cat descends from, and the polecat, which a ferret simply is once kept.
-- What neither had was a job. A tamed wildcat on a camp was a pet with nothing to do.
--
-- The job was already waiting for them. #218 made a fouled camp (refuse at 50 or more) draw vermin that gnaw at a
-- keeper's food stores, docking two hours of shelf life for every hour the keeper stands on that ground. Rats and
-- mice in a larder are the oldest reason anyone kept a cat, and ratting is what ferrets were kept for.
--
-- So: a tamed pest hunter living on the keeper's ground keeps the vermin off the stores. The refuse still costs
-- everything else it costs — illness, predators drawn in, fouled water — because a cat does not clean a camp; it only
-- keeps rats out of the food. What counts as a pest hunter is declared here and read by
-- FoodPreservationService.advanceTo, not listed in Java.

CREATE TABLE pest_hunter (
    species_key VARCHAR(100) PRIMARY KEY REFERENCES wildlife_species(species_key),
    notes       TEXT NOT NULL
);

COMMENT ON TABLE pest_hunter IS
  'Kept animals that hunt the vermin a fouled camp draws (#79, V327). A tamed one living on the keeper''s ground spares '
  'the food stores the shelf life pests would dock. Read by FoodPreservationService.advanceTo.';

INSERT INTO pest_hunter (species_key, notes) VALUES
  ('european_wildcat', 'The wild line of the house cat. Mice and rats in a store are why anybody kept one.'),
  ('polecat',          'A ferret is a kept polecat. Sent down a rat run, it clears what a cat cannot reach.');

DO $$
DECLARE bad text;
BEGIN
  -- A pest hunter must be something a keeper can actually keep.
  SELECT string_agg(ph.species_key, ', ') INTO bad FROM pest_hunter ph JOIN wildlife_species ws USING (species_key)
   WHERE ws.tamability <= 0 OR ws.kingdom_class = 'MONSTRUM';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V327: nobody can keep these: %', bad; END IF;

  -- And a hunter of vermin: a small carnivore, not a grazer and not something that would take the stock.
  SELECT string_agg(ph.species_key, ', ') INTO bad FROM pest_hunter ph JOIN wildlife_species ws USING (species_key)
   WHERE ws.ecological_role <> 'CARNIVORE' OR ws.size_tier NOT IN ('TINY','SMALL');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V327: these do not hunt vermin: %', bad; END IF;
END $$;
