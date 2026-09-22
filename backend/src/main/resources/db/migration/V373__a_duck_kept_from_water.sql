-- #108 / #122 — a duck kept from water does not lay.
--
-- #108 asks for a `duck_house` and a `waterfowl_run`, and says what it wants of them: "wetland-compatible
-- shelter/access structure, **not a dry coop copy**". The honest objection to building one today is that the
-- world has no idea what makes a duck different from a hen, so a duck house would be a poultry coop with
-- another name on it — a catalogue token, which is the defect this project exists to avoid.
--
-- THIS IS THE MISSING FACT, not the structure. Waterfowl are kept differently because they need open water to
-- be well: to feed, to preen, to keep their plumage in the state that keeps them alive. A mallard on dry
-- grassland with a trough to drink from is watered and is still not a duck being kept properly, and the first
-- thing that goes is the lay.
--
-- WHY A TROUGH IS NOT ENOUGH, DELIBERATELY. V694's thirst tick counts a watering station or a rainwater
-- catchment as water, and it is right to: a beast drinks from a trough. This rule counts only OPEN water — the
-- wet biomes and the freshwater sites — because what a duck is missing on dry ground is not a drink. Keeping
-- the two predicates different is the point of writing it down rather than reusing the nearest one.
--
-- WHAT IT COSTS A KEEPER. Siting. Ducks and geese belong near the marsh, the river bank or a pond, and a keeper
-- who wants eggs from them has to keep them there. A hen on the same dry ground is unaffected, which is the
-- asymmetry that makes this a rule about ducks rather than a rule against poultry.
--
-- AND IT IS WHAT A DUCK HOUSE WILL BE FOR. Once a structure can answer "there is water here" — a dug and lined
-- pool beside the run — the building has something to do that a coop cannot. That is the next change; this is
-- the fact it would rest on, and neither is worth having without the other.

ALTER TABLE wildlife_species ADD COLUMN needs_open_water BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wildlife_species.needs_open_water IS
  '#108: this species must have open water on its ground to be kept well — water to swim and feed in, not water to drink. Read by the laying rule; a trough does not answer it. False for everything that is merely found near water.';

-- The three laying species whose whole living is made on water. Every one of them already says so in its own
-- biome affinity; this states the consequence rather than leaving it to be inferred from where they are found.
UPDATE wildlife_species SET needs_open_water = TRUE
 WHERE species_key IN ('mallard_duck', 'greylag_goose', 'marsh_fowl');

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM wildlife_species WHERE needs_open_water;
    IF n <> 3 THEN RAISE EXCEPTION 'V373: expected the duck, the goose and the marsh fowl, found %', n; END IF;
    -- A species that needs open water and is not found near any would be unkeepable anywhere, which would be a
    -- mistake in the data rather than a hard life.
    SELECT COUNT(*) INTO n FROM wildlife_species
     WHERE needs_open_water
       AND biome_affinity NOT ILIKE '%WETLAND%'
       AND biome_affinity NOT ILIKE '%RIVER_BANK%'
       AND biome_affinity NOT ILIKE '%COAST%';
    IF n > 0 THEN RAISE EXCEPTION 'V373: % species need open water and live nowhere near any', n; END IF;
END $$;
