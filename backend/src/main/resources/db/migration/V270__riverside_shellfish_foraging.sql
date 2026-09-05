-- V270 — shellfish exist (#37/#74/#156).
--
-- V222 gave the world a shellfish pry. Its own crafting narration says what it is for: "a pry for levering open a
-- mussel or a clam without spilling it." There were no mussels and no clams. There was no shellfish of any kind in
-- the catalogue — the tool survived the dead-end guard only because a CUTTING edge is useful for other things, so
-- its named purpose was never noticed to be missing.
--
-- The same hole runs wider. wildlife_species carries freshwater_mussel and freshwater_snail as observable animals;
-- neither could be taken. Water's-edge foraging — the most reliable food a person on a riverbank actually has —
-- did not exist: the whole COLLECT_INSECTS registry offered a wetland nothing but an earthworm patch, and offered
-- a river bank nothing at all.
--
-- Also fixes the altitude half of the same gap: HIGHLAND and MOUNTAIN carried ZERO invertebrate species, although
-- the colony registry already lets you work a cricket colony, an earthworm patch and a spider den up there. You
-- could harvest small life on a mountain and never see any.

-- 1. A colony may require a tool to work. Prying a mussel off a stone without a blade loses the meat with the shell.
ALTER TABLE insect_colony_kind ADD COLUMN IF NOT EXISTS requires_tool_class varchar(32);
COMMENT ON COLUMN insect_colony_kind.requires_tool_class IS
    'Tool class needed to work this colony at all (tool_profile.tool_class), or NULL when bare hands will do.';

-- 2. The shellfish themselves, and the grub that comes off the same shallows.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('freshwater_mussel', 'Freshwater mussel', 'FOOD', 45, 35, TRUE, FALSE, 0),
('river_snail',       'River snail',       'FOOD', 14, 12, TRUE, FALSE, 0),
('caddis_grub',       'Caddis grub',       'FOOD',  3,  4, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('freshwater_mussel', 'INSECT_PRODUCT', 'insect_colony_product'),
('river_snail',       'INSECT_PRODUCT', 'insect_colony_product'),
('caddis_grub',       'INSECT_PRODUCT', 'insect_colony_product')
ON CONFLICT DO NOTHING;

-- 3. The beds and shallows they come off. A mussel bed wants a blade; snails and grubs are picked by hand.
INSERT INTO insect_colony_kind (colony_kind, biome_affinity, activity_cycle, season_active, harvest_intent, hazard_kind, hazard_min, hazard_max, smoke_suppresses, pollination_bonus, regrowth_days, requires_tool_class) VALUES
('mussel_bed',      'RIVER_BANK,WETLAND', 'ALL', 'ALL',                   'COLLECT_INSECTS', NULL, 0, 0, FALSE, 0, 21, 'CUTTING'),
('river_snail_bed', 'RIVER_BANK,WETLAND', 'ALL', 'ALL',                   'COLLECT_INSECTS', NULL, 0, 0, FALSE, 0, 14, NULL),
('caddis_shallows', 'RIVER_BANK',         'ALL', 'SPRING,SUMMER,AUTUMN',  'COLLECT_INSECTS', NULL, 0, 0, FALSE, 0, 10, NULL)
ON CONFLICT (colony_kind) DO NOTHING;

INSERT INTO insect_colony_product (colony_kind, item_key, yield_min, yield_max, rarity) VALUES
('mussel_bed',      'freshwater_mussel', 2, 6, 1.00),
('river_snail_bed', 'river_snail',       3, 8, 1.00),
('caddis_shallows', 'caddis_grub',       4, 10, 1.00)
ON CONFLICT DO NOTHING;

-- A worm patch runs along a river bank as readily as anywhere else damp.
UPDATE insect_colony_kind SET biome_affinity = biome_affinity || ',RIVER_BANK'
WHERE colony_kind = 'earthworm_patch' AND biome_affinity NOT ILIKE '%RIVER_BANK%';

-- 4. Grubs and mussels are what a line is baited with. Fishing with what the same water gave you.
INSERT INTO bait_profile (item_key, draws_role, potency, hours_active) VALUES
('caddis_grub',       'OMNIVORE', 16, 5),
('freshwater_mussel', 'OMNIVORE', 14, 5)
ON CONFLICT DO NOTHING;

-- 5. Small life at altitude. These are the hardy ones that genuinely live high, and they match the colonies the
--    harvest registry already places on that ground.
UPDATE wildlife_species SET biome_affinity = biome_affinity || ',HIGHLAND'
WHERE species_key IN ('grasshopper','cricket','orb_weaver_spider','earthworm','dung_beetle')
  AND biome_affinity NOT ILIKE '%HIGHLAND%';

UPDATE wildlife_species SET biome_affinity = biome_affinity || ',MOUNTAIN'
WHERE species_key IN ('cricket','orb_weaver_spider','earthworm')
  AND biome_affinity NOT ILIKE '%MOUNTAIN%';
