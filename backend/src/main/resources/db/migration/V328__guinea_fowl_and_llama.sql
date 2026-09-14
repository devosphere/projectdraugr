-- #79 — the guinea fowl and the llama.
--
-- Of #79's species list, most names are animals the world already keeps under another key, and V326 added the yak and
-- the musk ox. Two more are genuinely absent and have a working role nothing else fills:
--
--   GUINEA FOWL  a hardy ground bird of open country, kept for eggs and meat. It forages its own food across a
--                farmstead and lays through the warm half of the year. Grassland and wood edge.
--   LLAMA        the pack animal of high ground: it carries a load where a horse cannot climb and is sheared for its
--                fleece, but it is not ridden. Highland and mountain, beside the reindeer, yak and musk ox.
--
-- Recorded, NOT added, so a later reading of #79 does not re-derive it: `musk_duck` and `mule_deer` are regional
-- species whose roles this world already fills — the mallard and marsh fowl for a kept duck, the red deer for a deer.
-- A second deer that behaves exactly like the first would be a name, not an animal.
--
-- Both are written into every table a kept animal lives in, reusing the items those tables already use: `fowl_egg`,
-- `raw_fowl_meat` and `feather` for the bird; `wool_tuft`, `animal_hide` and `animal_bone` for the llama. Seasons follow
-- V323.

INSERT INTO wildlife_species
  (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance,
   ambush_hunter, pack_hunter, territorial, tamability, biome_affinity, temperament)
VALUES
  ('guinea_fowl', 'AVES',     'OMNIVORE',  'DIURNAL', 'TERRESTRIAL', 'SMALL', 16, FALSE, FALSE, FALSE, 55, 'GRASSLAND,TEMPERATE_FOREST', 'BIDDABLE'),
  ('llama',       'MAMMALIA', 'HERBIVORE', 'DIURNAL', 'TERRESTRIAL', 'LARGE', 40, FALSE, FALSE, FALSE, 65, 'HIGHLAND,MOUNTAIN',          'WARY')
ON CONFLICT (species_key) DO NOTHING;

-- A llama carries a load and is not ridden: less than a yak, more than a goat could.
INSERT INTO draft_species (species_key, haul_bonus_grams, bulk_bonus_ml, rideable) VALUES
  ('llama', 60000, 90000, FALSE)
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO tamed_yield (species_key, item_key, interval_hours, yield_kind, available_months) VALUES
  ('guinea_fowl', 'fowl_egg',  36,  'EGG',  '{4,5,6,7,8,9}'),
  ('llama',       'wool_tuft', 720, 'WOOL', '{5,6}')
ON CONFLICT DO NOTHING;

-- Guinea fowl sit on a clutch for about four weeks; a llama carries for about eleven and a half months.
INSERT INTO breeding_profile (species_key, gestation_hours, litter_min, litter_max, maturity_hours, recovery_hours, birth_loss_percent) VALUES
  ('guinea_fowl',  650, 6, 12,  3600,  300, 20),
  ('llama',       8300, 1,  1, 12000, 1400, 10)
ON CONFLICT (species_key) DO NOTHING;

INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max, rarity) VALUES
  ('guinea_fowl', 'raw_fowl_meat', 1, 1, 1.00), ('guinea_fowl', 'feather', 1, 3, 0.90),
  ('llama', 'animal_hide', 1, 1, 1.00), ('llama', 'animal_bone', 1, 3, 0.80);

-- Llamas foul one shared dung heap rather than the whole pasture, so their scat is the sign that lasts.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours) VALUES
  ('guinea_fowl', 'PRINTS', 12), ('guinea_fowl', 'FEATHERS', 24),
  ('llama', 'PRINTS', 48), ('llama', 'SCAT', 72), ('llama', 'DISTURBED_GROUND', 24);

DO $$
DECLARE bad text;
BEGIN
  SELECT string_agg(k, ', ') INTO bad FROM (VALUES ('guinea_fowl'),('llama')) s(k)
   WHERE NOT EXISTS (SELECT 1 FROM wildlife_species ws WHERE ws.species_key = k AND ws.tamability > 0)
      OR NOT EXISTS (SELECT 1 FROM tamed_yield t WHERE t.species_key = k)
      OR NOT EXISTS (SELECT 1 FROM breeding_profile b WHERE b.species_key = k AND b.birth_loss_percent > 0
                                                         AND b.maturity_hours > b.gestation_hours AND b.gestation_hours >= 336)
      OR NOT EXISTS (SELECT 1 FROM wildlife_drop w WHERE w.species_key = k)
      OR (SELECT count(*) FROM wildlife_sign g WHERE g.species_key = k) < 2;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V328: these are not whole animals: %', bad; END IF;

  IF EXISTS (SELECT 1 FROM draft_species WHERE species_key = 'llama' AND rideable) THEN
    RAISE EXCEPTION 'V328: a llama carries a pack, not a person';
  END IF;

  SELECT string_agg(DISTINCT w.item_key, ', ') INTO bad FROM wildlife_drop w
   WHERE w.species_key IN ('guinea_fowl','llama') AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = w.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V328: drops name items that do not exist: %', bad; END IF;
END $$;
