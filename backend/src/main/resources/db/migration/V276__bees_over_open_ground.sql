-- V276 — bees work open ground too (#162/#74).
--
-- Numbered 276 rather than 271 on purpose. This PR was opened before #502/#503, and those merged first, so a
-- database that ran in between is already at V273. Flyway is not configured out-of-order, so a LOWER version
-- appearing after a higher one has been applied is a hard boot failure -- not a warning. Renumbering above the
-- current head costs a CI cycle and costs nobody a broken local database.
--
-- V270's companion. pollination_bonus is now read: a hive or a worm patch working a chunk fills out the stand
-- grown on it. But honeybee_hive was confined to TEMPERATE_FOREST and HIGHLAND, and crops are sown on tilled
-- open ground — so the strongest pollinator in the catalogue could never reach a field, and the mechanic would
-- have been live and unreachable, which is the same defect one layer up.
--
-- Bees forage flowering open grassland harder than they forage closed canopy. Wetland too: a marsh in flower is
-- prime forage. This is what actually puts a hive within reach of a plot.
UPDATE insect_colony_kind SET biome_affinity = biome_affinity || ',GRASSLAND'
WHERE colony_kind = 'honeybee_hive' AND biome_affinity NOT ILIKE '%GRASSLAND%';

-- The hornets that come with them: the same open ground, and a reason not to work a field carelessly.
UPDATE insect_colony_kind SET biome_affinity = biome_affinity || ',GRASSLAND'
WHERE colony_kind = 'hornet_nest' AND biome_affinity NOT ILIKE '%GRASSLAND%';
