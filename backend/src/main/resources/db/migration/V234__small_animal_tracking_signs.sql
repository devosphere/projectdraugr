-- V234 — story #152 (small-animal tracking signs and trail-age procedures). The wildlife_sign table drives
-- WildlifeEncounterService.track(): tracking a chunk reads a sign left by a resident population, ages it by
-- readable_hours, and (with a practised eye) names the species and what it was doing. The bulk PRINTS seed predated
-- the #74 100-species roster, so the small mammals and small birds added there left NO sign and were untrackable.
-- This gives the small-fauna roster species-appropriate signs so every one is trackable, and adds the three sign
-- kinds #152 names beyond prints/scat/feathers: FEEDING_SIGN (gnawed cones/husks), GRASS_RUNWAY (broken-grass
-- trail), BURROW (a dug mouth). Sign visibility ages by readable_hours; the species link is plausible per animal.
ALTER TABLE wildlife_sign DROP CONSTRAINT wildlife_sign_kind_check;
ALTER TABLE wildlife_sign ADD CONSTRAINT wildlife_sign_kind_check CHECK (sign_kind IN (
  'PRINTS','SCAT','FEATHERS','DISTURBED_GROUND','CARCASS_SCRAPS','DEN_MARKS','TERRITORIAL_SCRATCH',
  'FEEDING_SIGN','GRASS_RUNWAY','BURROW'));

-- Base prints for the small mammals that had no sign at all (skip the four that already carry PRINTS:
-- hare, rabbit, red_squirrel, field_mouse). readable_hours 12 = a small print fades within half a day.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'PRINTS', 12
FROM wildlife_species s
WHERE s.species_key IN (
  'bank_vole','common_mole','desert_hare','flying_squirrel','forest_dormouse','forest_rat','gopher',
  'ground_squirrel','ironmaw_mole','kangaroo_rat','marsh_rabbit','marsh_shrew','muskrat','pine_vole','shrew',
  'snowshoe_hare','striped_field_mouse','water_shrew','water_vole','wood_mouse','yellow_necked_mouse')
AND NOT EXISTS (SELECT 1 FROM wildlife_sign w WHERE w.species_key=s.species_key AND w.sign_kind='PRINTS');

-- Droppings (rat droppings, hare pellets). readable_hours 36.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'SCAT', 36 FROM wildlife_species s
WHERE s.species_key IN ('forest_rat','kangaroo_rat','muskrat','hare','rabbit','snowshoe_hare','desert_hare',
  'marsh_rabbit','wood_mouse','bank_vole','water_vole')
AND NOT EXISTS (SELECT 1 FROM wildlife_sign w WHERE w.species_key=s.species_key AND w.sign_kind='SCAT');

-- Squirrel feeding sign (gnawed cones and husks). readable_hours 24.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'FEEDING_SIGN', 24 FROM wildlife_species s
WHERE s.species_key IN ('red_squirrel','ground_squirrel','flying_squirrel','forest_dormouse');

-- Burrows (gopher burrow, warren mouths). readable_hours 168 — a dug mouth persists for days.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'BURROW', 168 FROM wildlife_species s
WHERE s.species_key IN ('gopher','ground_squirrel','kangaroo_rat','rabbit','marsh_rabbit','muskrat');

-- Turned earth (molehills, gopher spoil). readable_hours 24.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'DISTURBED_GROUND', 24 FROM wildlife_species s
WHERE s.species_key IN ('common_mole','ironmaw_mole','gopher')
AND NOT EXISTS (SELECT 1 FROM wildlife_sign w WHERE w.species_key=s.species_key AND w.sign_kind='DISTURBED_GROUND');

-- Broken-grass trail (vole/mouse/shrew runways). readable_hours 18.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'GRASS_RUNWAY', 18 FROM wildlife_species s
WHERE s.species_key IN ('bank_vole','pine_vole','water_vole','field_mouse','wood_mouse','striped_field_mouse',
  'yellow_necked_mouse','shrew','marsh_shrew','water_shrew');

-- Bird feather sign. readable_hours 24.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT s.species_key, 'FEATHERS', 24 FROM wildlife_species s
WHERE s.species_key IN ('sparrow','chaffinch','goldfinch','wren','robin','skylark','meadowlark','song_thrush','mistle_thrush')
AND NOT EXISTS (SELECT 1 FROM wildlife_sign w WHERE w.species_key=s.species_key AND w.sign_kind='FEATHERS');
