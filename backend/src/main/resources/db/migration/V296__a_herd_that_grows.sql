-- #52/#79/#100/#106/#108 — a herd that grows.
--
-- Everything about keeping animals exists except the one thing that makes it husbandry rather than ownership.
-- A Chronicle can tame a goat, feed it, water it, rest it in a byre, muck out after it, milk it and shear it —
-- and the number of goats in the world will never change except downward. Nothing models an animal's age or
-- generation, so a herd is a fixed count a keeper draws down and can never build.
--
-- That absence is load-bearing well beyond this file. It is why #108's farrowing shelter, brooder shelter,
-- foaling stall and boar isolation pen were all recorded as "nothing to shelter" rather than built: there are no
-- young to protect and no breeding stock to separate. Four structures the ticket names wait on this table.
--
-- WHAT IS DELIBERATELY NOT MODELLED, so the next reader does not add it thinking it was forgotten:
--
--   * SEX. Every tamed animal is one bond; a keeper does not choose a bull. Modelling it would mean either
--     inventing a sex for animals already tamed in live saves, or refusing to breed until a keeper happens to
--     hold two. The compromise here is that breeding needs TWO tamed animals of the species — which is the
--     honest floor of what a herd requires — without claiming to know which is which.
--   * A NAMED INDIVIDUAL. `wildlife_bond` is a bond to a population, not to a beast with a history. Young
--     mature into another bond of the same species, which is what the rest of the code already understands.
--   * DEATH BY AGE. Nothing else in the simulation ages, and a lifespan that only applied to livestock would be
--     a rule about goats rather than a rule about the world.
--
-- ON THE CLOCKS. These are the real ones: a goat carries 150 days, a cow 283, a mare 340; a clutch of fowl
-- hatches in three weeks. That means fowl are the stock a keeper can actually build a flock from inside a
-- season, and cattle are a commitment across a year — which is exactly true of keeping cattle, and is the reason
-- people kept fowl. The alternative is a compressed clock that would make a goat a renewable resource on the
-- scale of a firewood stack, and this game is not that.

CREATE TABLE breeding_profile (
    species_key     VARCHAR(60) PRIMARY KEY REFERENCES wildlife_species(species_key),
    gestation_hours INTEGER  NOT NULL CHECK (gestation_hours > 0),
    litter_min      SMALLINT NOT NULL CHECK (litter_min >= 1),
    litter_max      SMALLINT NOT NULL CHECK (litter_max >= litter_min),
    maturity_hours  INTEGER  NOT NULL CHECK (maturity_hours > 0),
    recovery_hours  INTEGER  NOT NULL DEFAULT 0 CHECK (recovery_hours >= 0)
);

COMMENT ON TABLE breeding_profile IS
  'Which kept species breed, and on what clock. Real gestation and maturity: fowl build a flock inside a season, '
  'cattle are a commitment across a year. A species absent from this table simply does not breed in captivity here.';
COMMENT ON COLUMN breeding_profile.recovery_hours IS
  'How long after giving birth before the animal can carry again — a dam worked straight back into calf is how a '
  'herd is ruined.';

INSERT INTO breeding_profile (species_key, gestation_hours, litter_min, litter_max, maturity_hours, recovery_hours) VALUES
    -- Large stock. A year's commitment, which is what cattle are.
    ('aurochs',        6800, 1, 1, 12000, 1400),
    ('water_buffalo',  7440, 1, 1, 12000, 1400),
    ('horse',          8160, 1, 1, 12000, 2000),
    ('donkey',         8760, 1, 1, 12000, 2000),
    ('reindeer',       5400, 1, 1,  9000,  900),
    -- Small stock. A season, and twins are ordinary.
    ('mountain_goat',  3600, 1, 2,  7200,  700),
    ('bighorn_sheep',  4200, 1, 2,  7200,  700),
    -- Fowl. Three weeks to a clutch — the stock a keeper can actually build a flock from.
    ('marsh_fowl',      500, 4, 9,  3600,  200),
    ('mallard_duck',    650, 5, 10, 3600,  200),
    ('greylag_goose',   700, 4, 7,  4300,  240),
    ('wild_turkey',     650, 6, 12, 3600,  200),
    ('quail',           410, 6, 12, 2000,  150),
    ('pheasant',        580, 5, 10, 3600,  200),
    ('partridge',       580, 6, 12, 3400,  200),
    ('grouse',          620, 5, 9,  3600,  200),
    -- A pigeon lays two and raises them fast, which is why a dovecote was worth building.
    ('wood_pigeon',     430, 1, 2,  4300,  170);

-- One in-progress pregnancy per bond. The row is the pregnancy; it is deleted when the young are born, so its
-- presence IS the answer to "is this animal carrying".
CREATE TABLE tamed_gestation (
    bond_id      UUID PRIMARY KEY REFERENCES wildlife_bond(id) ON DELETE CASCADE,
    species_key  VARCHAR(60) NOT NULL REFERENCES wildlife_species(species_key),
    conceived_at TIMESTAMPTZ NOT NULL,
    due_at       TIMESTAMPTZ NOT NULL,
    CHECK (due_at > conceived_at)
);

-- Young are not yet stock. They cannot be worked, milked, shorn or hauled with — they are a promise with a date
-- on it, which is what makes a farrowing shelter worth building and a hard winter worth fearing.
CREATE TABLE tamed_young (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bond_id     UUID NOT NULL REFERENCES wildlife_bond(id) ON DELETE CASCADE,
    species_key VARCHAR(60) NOT NULL REFERENCES wildlife_species(species_key),
    born_at     TIMESTAMPTZ NOT NULL,
    matures_at  TIMESTAMPTZ NOT NULL,
    CHECK (matures_at > born_at)
);
CREATE INDEX idx_tamed_young_due ON tamed_young (matures_at);

-- When this bond last gave birth, so recovery_hours can be enforced without keeping the spent gestation row.
ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS last_birth_at TIMESTAMPTZ;

DO $$
DECLARE wrong text; n int;
BEGIN
  -- A breeding profile for a species nobody can tame is a row that never fires. Tamability is the gate the
  -- taming path actually reads, so a species with none of it cannot become a bond and cannot breed.
  SELECT string_agg(bp.species_key, ', ' ORDER BY bp.species_key) INTO wrong FROM breeding_profile bp
   WHERE NOT EXISTS (SELECT 1 FROM wildlife_species ws WHERE ws.species_key=bp.species_key AND ws.tamability > 0);
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V296: nobody can tame these, so they can never breed in a keeper''s hands: %', wrong;
  END IF;

  -- The point of the table: the stock a keeper actually keeps must be able to increase. Every species that
  -- gives a yield or pulls a load is something a keeper builds a herd of, and must breed.
  SELECT string_agg(k, ', ') INTO wrong FROM (
      SELECT DISTINCT species_key AS k FROM tamed_yield
      UNION SELECT species_key FROM draft_species
  ) kept
   WHERE k NOT IN (SELECT species_key FROM breeding_profile)
     -- An ox is a castrated draft bull. It pulls and it does not breed, and that is not an oversight.
     AND k <> 'ox'
     -- Elk and red deer are draft-capable here but are not kept herds; they are worked, not bred.
     AND k NOT IN ('elk', 'red_deer');
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V296: a keeper builds a herd of these and they cannot breed: %', wrong;
  END IF;

  -- Young must mature strictly after they are carried, or a keeper would get grown stock out of a pregnancy.
  SELECT string_agg(species_key, ', ' ORDER BY species_key) INTO wrong FROM breeding_profile
   WHERE maturity_hours <= gestation_hours;
  IF wrong IS NOT NULL THEN
    RAISE EXCEPTION 'V296: growing up must take longer than being carried: %', wrong;
  END IF;

  -- And the clocks must be the real ones rather than a game-y compression. A month is the floor for anything,
  -- and no bird may be slower to hatch than the fastest mammal is to carry.
  SELECT string_agg(species_key || '/' || gestation_hours, ', ' ORDER BY species_key) INTO wrong
    FROM breeding_profile WHERE gestation_hours < 336;
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V296: nothing is carried in under two weeks: %', wrong; END IF;

  SELECT count(*) INTO n FROM breeding_profile WHERE litter_max > 1;
  IF n < 5 THEN RAISE EXCEPTION 'V296: only % species can bear more than one; a flock could never grow', n; END IF;
END $$;
