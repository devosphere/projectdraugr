-- #79 — the cold-country pack animals: yak and musk ox.
--
-- #79's species list names draft and pack animals for every kind of country. Most of what it names the world already
-- has under another key — its `wild_ox` is the ox and aurochs, `wild_goat` the mountain goat, `mouflon_sheep` the
-- bighorn, `wild_pig` the boar, `wild_goose` the greylag, `wild_horse` the horse. Two are genuinely absent, and #100
-- names both as the cold-country half of animal logistics: the YAK, highland pack and light draft, and the MUSK OX,
-- cold-region pack and light drag. The high ground had one working animal, the reindeer, and nothing heavier.
--
-- Each is written into every table a working animal lives in, so it is a whole animal from the first day — found,
-- tamed, bred, worked, milked or plucked, hunted, tracked — and not a name in a registry:
--
--   YAK      wary rather than dangerous, carries a person as readily as a load, gives milk from spring into autumn
--            and moults its coat in late spring. Ridden and packed across the high passes wherever it is kept.
--   MUSK OX  DANGEROUS: a herd stands shoulder to shoulder and the bulls charge, which is how it faces wolves and how
--            it faces a keeper who comes in carelessly. Not ridden. Pulls a light sledge. Gives qiviut, the underwool
--            it sheds in spring and that is plucked, not shorn. Calves only every other year.
--
-- Milk and wool reuse `goat_milk` and `wool_tuft`, which have stood for milk and fleece generally since V45, and the
-- seasons follow V323. Feeding and tending them by name works through PhysicalItemService.namesAKeptAnimal, which
-- reads draft_species and tamed_yield rather than a list of animal names in Java.

INSERT INTO wildlife_species
  (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance,
   ambush_hunter, pack_hunter, territorial, tamability, biome_affinity, temperament)
VALUES
  ('yak',     'MAMMALIA', 'HERBIVORE', 'DIURNAL', 'TERRESTRIAL', 'LARGE', 55, FALSE, FALSE, FALSE, 60, 'MOUNTAIN,HIGHLAND', 'WARY'),
  ('musk_ox', 'MAMMALIA', 'HERBIVORE', 'DIURNAL', 'TERRESTRIAL', 'LARGE', 70, FALSE, FALSE, TRUE,  30, 'MOUNTAIN,HIGHLAND', 'DANGEROUS')
ON CONFLICT (species_key) DO NOTHING;

-- The yak out-hauls a reindeer; the musk ox pulls a light sledge and no more.
INSERT INTO draft_species (species_key, haul_bonus_grams, bulk_bonus_ml, rideable) VALUES
  ('yak',     140000, 180000, TRUE),
  ('musk_ox',  80000, 100000, FALSE)
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO tamed_yield (species_key, item_key, interval_hours, yield_kind, available_months) VALUES
  ('yak',     'goat_milk', 36,  'MILK', '{4,5,6,7,8,9,10}'),
  ('yak',     'wool_tuft', 720, 'WOOL', '{5,6}'),
  ('musk_ox', 'wool_tuft', 720, 'WOOL', '{5,6}')
ON CONFLICT DO NOTHING;

-- A yak carries about eight and a half months; a musk ox a little less, and calves only every other year, which is
-- the long recovery.
INSERT INTO breeding_profile (species_key, gestation_hours, litter_min, litter_max, maturity_hours, recovery_hours, birth_loss_percent) VALUES
  ('yak',     6300, 1, 1, 12000, 1400, 12),
  ('musk_ox', 5900, 1, 1, 14000, 8000, 15)
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max, rarity) VALUES
  ('yak',     'animal_hide', 1, 2, 1.00), ('yak',     'animal_bone', 2, 4, 0.80),
  ('musk_ox', 'animal_hide', 1, 2, 1.00), ('musk_ox', 'animal_bone', 2, 4, 0.80);

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours) VALUES
  ('yak',     'PRINTS', 48), ('yak',     'SCAT', 36), ('yak',     'DISTURBED_GROUND', 24),
  ('musk_ox', 'PRINTS', 48), ('musk_ox', 'SCAT', 36), ('musk_ox', 'DISTURBED_GROUND', 24);

DO $$
DECLARE bad text; n int;
BEGIN
  -- Whole animals: every working table holds both.
  SELECT string_agg(k, ', ') INTO bad FROM (VALUES ('yak'),('musk_ox')) s(k)
   WHERE NOT EXISTS (SELECT 1 FROM wildlife_species ws WHERE ws.species_key = k AND ws.tamability > 0)
      OR NOT EXISTS (SELECT 1 FROM draft_species d WHERE d.species_key = k)
      OR NOT EXISTS (SELECT 1 FROM tamed_yield t WHERE t.species_key = k)
      OR NOT EXISTS (SELECT 1 FROM breeding_profile b WHERE b.species_key = k AND b.birth_loss_percent > 0 AND b.maturity_hours > b.gestation_hours)
      OR NOT EXISTS (SELECT 1 FROM wildlife_drop w WHERE w.species_key = k)
      OR (SELECT count(*) FROM wildlife_sign g WHERE g.species_key = k) < 2;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V326: these are not whole animals: %', bad; END IF;

  -- A yak is ridden; a musk ox is not.
  IF NOT EXISTS (SELECT 1 FROM draft_species WHERE species_key = 'yak' AND rideable) THEN RAISE EXCEPTION 'V326: yaks are ridden'; END IF;
  IF EXISTS (SELECT 1 FROM draft_species WHERE species_key = 'musk_ox' AND rideable) THEN RAISE EXCEPTION 'V326: nobody rides a musk ox'; END IF;

  -- Every drop item exists.
  SELECT string_agg(DISTINCT w.item_key, ', ') INTO bad FROM wildlife_drop w
   WHERE w.species_key IN ('yak','musk_ox') AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = w.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V326: drops name items that do not exist: %', bad; END IF;
  -- Whether they live on ground the world actually makes is not asked here: a migration can run against a partly built
  -- world. CatalogueWorldSeedCompatibilityIntegrationTest asks it of every catalogue entry against a generated world.
END $$;
