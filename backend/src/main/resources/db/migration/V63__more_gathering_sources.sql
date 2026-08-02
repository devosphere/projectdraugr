-- V63: More places to find the raw materials a playthrough leans on.
--
-- Salt was only in highland/mountain rock, which is a long way to go for the base of
-- the whole preservation chain. Flint and pyrite were likewise inland-only. And the
-- things that wash up or erode out along water — flint nodules, pyrite, clay — belong
-- on a stream bank as much as a hillside.

-- Salt: rock salt in highland/mountain seams, sea salt at the oceanside, and a
-- dedicated salt deposit (a saline spring/flat, its own biome like CLAY_DEPOSIT).
-- Deliberately NOT in rivers or streams — those are drinkable freshwater, not brine.
-- SALT_DEPOSIT is listed here so salt is gatherable there once the world generator
-- places such chunks (that placement, mirroring clay deposits, is a follow-up).
UPDATE mineral_definition SET biome_affinity = 'MOUNTAIN,HIGHLAND,OCEAN,SALT_DEPOSIT'
WHERE mineral_key = 'rock_salt';

-- Flint and pyrite also erode out along stream banks.
UPDATE mineral_definition SET biome_affinity = 'HIGHLAND,GRASSLAND,MOUNTAIN,RIVER_BANK'
WHERE mineral_key = 'flint_stone';
UPDATE mineral_definition SET biome_affinity = 'MOUNTAIN,HIGHLAND,RIVER_BANK'
WHERE mineral_key = 'iron_pyrite';

-- Clay is already gatherable on river banks (GATHER_CLAY: CLAY_DEPOSIT/RIVER_BANK/
-- WETLAND) — no change needed there.

-- Vines hang from the trees of the forest; make sure the shrub that drops them is
-- present wherever there are trees to carry them.
UPDATE flora_definition SET biome_affinity = 'TEMPERATE_FOREST,WETLAND,HIGHLAND'
WHERE flora_key = 'climbing_vine';
