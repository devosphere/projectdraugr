-- #155 / #74 — a carcass is more than meat.
--
-- THE GAP. Twelve mammals from a goat's size to a buffalo's had no rows in wildlife_drop, so butchering one gave the
-- meat and then the one legacy stand-in harvest() keeps for uncatalogued species — a generic hide — and nothing
-- else: no sinew, no bone, no fat. An ibex brought down on a crag, a wild ass on the dry grassland, an ox slaughtered
-- in the byre each came apart into less than a badger does. Sinew is a bowstring and a sewing thread; bone is an awl,
-- a needle, a fish hook; fat is a lamp and a waterproofing. The animals were in the world (ambient, hunted, kept) and
-- the parts that make a carcass worth working were not.
--
-- WHAT IT ADDS. Drops in the idiom the catalogue already uses, from items that already have consumers, sized by the
-- animal: the goat-sized grazers give a hide, some sinew and some bone; the sheep and the equids more; the ox and the
-- buffalo a larger hide and fat as well. The porcupine gives fat, for which it has always been hunted, and not a hide
-- nobody would wear.
--
-- WHAT IT DOES NOT DO. No horns: only the aurochs has a horn item, and an ibex horn nothing reads would be a token.
-- The small and tiny mammals are left as they are — a vole is meat and nothing else, which is true.

INSERT INTO wildlife_drop (species_key, item_key, yield_min, yield_max, rarity)
SELECT v.species_key, v.item_key, v.lo, v.hi, v.rarity
  FROM (VALUES
    -- goat-sized grazers of the crags and the open range
    ('ibex','animal_hide',1,1,1.00), ('ibex','animal_sinew',1,2,0.60), ('ibex','animal_bone',1,2,0.70),
    ('chamois','animal_hide',1,1,1.00), ('chamois','animal_sinew',1,2,0.60), ('chamois','animal_bone',1,2,0.70),
    ('gazelle','animal_hide',1,1,1.00), ('gazelle','animal_sinew',1,2,0.60), ('gazelle','animal_bone',1,2,0.70),
    ('antelope','animal_hide',1,1,1.00), ('antelope','animal_sinew',1,2,0.60), ('antelope','animal_bone',1,2,0.70),
    ('saiga_antelope','animal_hide',1,1,1.00), ('saiga_antelope','animal_sinew',1,2,0.60), ('saiga_antelope','animal_bone',1,2,0.70),
    -- the wild sheep, larger
    ('bighorn_sheep','animal_hide',1,1,1.00), ('bighorn_sheep','animal_sinew',1,3,0.80), ('bighorn_sheep','animal_bone',2,3,0.80),
    -- the equids
    ('wild_donkey','animal_hide',1,1,1.00), ('wild_donkey','animal_sinew',2,3,0.80), ('wild_donkey','animal_bone',2,4,0.80),
    ('donkey','animal_hide',1,1,1.00), ('donkey','animal_sinew',2,3,0.80), ('donkey','animal_bone',2,4,0.80),
    ('horse','animal_hide',1,1,1.00), ('horse','animal_sinew',2,3,0.80), ('horse','animal_bone',2,4,0.80),
    -- the great cattle
    ('ox','animal_hide',1,2,1.00), ('ox','animal_sinew',2,4,0.85), ('ox','animal_bone',3,5,0.85), ('ox','animal_fat',1,3,0.80),
    ('water_buffalo','animal_hide',1,2,1.00), ('water_buffalo','animal_sinew',2,4,0.85), ('water_buffalo','animal_bone',3,5,0.85), ('water_buffalo','animal_fat',1,3,0.80),
    -- hunted for its fat
    ('porcupine','animal_fat',1,2,0.80), ('porcupine','animal_bone',1,1,0.50)
  ) AS v(species_key, item_key, lo, hi, rarity)
 WHERE NOT EXISTS (SELECT 1 FROM wildlife_drop d WHERE d.species_key = v.species_key AND d.item_key = v.item_key);

DO $$
DECLARE bad text;
BEGIN
  -- Every mammal of a goat's size or more comes apart into something besides meat.
  SELECT string_agg(species_key, ', ' ORDER BY species_key) INTO bad
    FROM wildlife_species s
   WHERE kingdom_class = 'MAMMALIA' AND size_tier IN ('MEDIUM','LARGE','HUGE')
     AND NOT EXISTS (SELECT 1 FROM wildlife_drop d WHERE d.species_key = s.species_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V343: these carcasses still give nothing but meat: %', bad; END IF;

  -- And what they give is something the world can use.
  SELECT string_agg(DISTINCT d.item_key, ', ') INTO bad
    FROM wildlife_drop d
   WHERE d.species_key IN ('ibex','chamois','gazelle','antelope','saiga_antelope','bighorn_sheep','wild_donkey',
                           'donkey','horse','ox','water_buffalo','porcupine')
     AND NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.item_key = d.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V343: these parts are read by nothing: %', bad; END IF;
END $$;
