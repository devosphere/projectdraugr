-- Regression: first-day multi-biome survival SCENARIO reachability (M1 #129, EPIC #123). Read-only.
--
-- dr0113 pins that survival CONTENT exists per biome. #129 promises the survival SCENARIOS are playable:
-- a careful Chronicle can make fire/water/shelter/carry, AVOID a hostile boundary, and RETREAT with a
-- ranged/escape option — while reckless play still risks harm. This pins the scenario-critical pieces
-- dr0113 does not: (1) real threats exist to avoid; (2) a non-melee escape/ranged option is buildable
-- and VERIFIED; (3) the water-treatment axis is complete; (4) defensible-site + first-week-progression
-- infrastructure is buildable; (5) every land start biome can reach fire fuel from its own ground.
-- A gap here means a #129 scenario is unplayable (no threat to avoid, no way to retreat, no potable
-- water, or a biome that cannot make fire) — a release-blocking survival defect.

BEGIN;

-- (1) Avoidance scenarios (#129 S1 forest/goblin-cave, S3 grassland/centaur, S4 highland monster, S6 hostile)
--     need real predators/monsters seeded so "observe signs, avoid the boundary" is a genuine choice.
DO $$
DECLARE threats int;
BEGIN
    SELECT count(*) INTO threats FROM wildlife_species
     WHERE species_key IN ('gray_wolf','brown_bear','dire_wolf','constrictor_snake','crocodilian',
                            'cave_troll','bog_wraith','harpy','giant_bat_swarm');
    IF threats < 5 THEN RAISE EXCEPTION 'SCENARIO: too few predator/monster species (%s) — nothing to avoid', threats; END IF;
    -- A Chronicle must never be FORCED to confront: confrontation is opt-in (CONFRONT_WILDLIFE), so a
    -- monster species existing is a hazard to route around, not an unwinnable wall. Pin that at least one
    -- fabular monster is present (S4 "no forced monster confrontation" has a real subject).
    IF NOT EXISTS (SELECT 1 FROM wildlife_species WHERE species_key IN ('cave_troll','bog_wraith','harpy')) THEN
        RAISE EXCEPTION 'SCENARIO: no fabular monster present for the avoid-not-fight scenarios'; END IF;
END $$;

-- (2) Retreat/escape (#129 S6): a Chronicle must be able to keep distance, not only trade blows in melee.
--     At least one ranged option AND one shield must exist with a VERIFIED maker (buildable, not injected).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM material_process WHERE review_state='VERIFIED'
          AND output_item_key IN ('sling','javelin','hunting_bow')) THEN
        RAISE EXCEPTION 'SCENARIO: no VERIFIED maker of a ranged escape weapon (sling/javelin/bow)'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM material_process WHERE review_state='VERIFIED'
          AND output_item_key IN ('bark_shield','woven_reed_shield','rawhide_shield')) THEN
        RAISE EXCEPTION 'SCENARIO: no VERIFIED maker of a shield for covered retreat'; END IF;
    -- Thrown stones are the zero-craft fallback ranged option — field_stone must be a real gatherable item.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='field_stone') THEN
        RAISE EXCEPTION 'SCENARIO: field_stone (fallback thrown ranged option) missing'; END IF;
END $$;

-- (3) Potable water (#129 S2 wetland cold "safe water treatment"): the raw -> filtered -> clean axis must
--     be complete so untreated water is a real waterborne risk and treatment is a real remedy.
DO $$
BEGIN
    IF NOT (EXISTS(SELECT 1 FROM item_definition WHERE item_key='raw_water')
            AND EXISTS(SELECT 1 FROM item_definition WHERE item_key='filtered_water')
            AND EXISTS(SELECT 1 FROM item_definition WHERE item_key='clean_water')) THEN
        RAISE EXCEPTION 'SCENARIO: water-treatment axis incomplete (need raw+filtered+clean)'; END IF;
    -- Rainwater catchment is the passive water-security build for the first-week progression (S8).
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE construction_kind='RAINWATER_CATCHMENT') THEN
        RAISE EXCEPTION 'SCENARIO: RAINWATER_CATCHMENT (passive water security) missing'; END IF;
END $$;

-- (4) Defensible site + first-week progression (#129 S6 camp warning, S8 tools->storage->fire->shelter->defence).
DO $$
BEGIN
    -- A perimeter must be buildable (fence/gate/screen) so a camp can be made defensible.
    IF (SELECT count(DISTINCT construction_kind) FROM assembly_definition
         WHERE construction_kind IN ('WATTLE_FENCE','SPLIT_RAIL_FENCE','REED_SCREEN','SIMPLE_GATE')) < 2 THEN
        RAISE EXCEPTION 'SCENARIO: too little perimeter infrastructure for a defensible camp'; END IF;
    -- The first-week chain's fixed points must each be buildable: fire pit, a shelter, storage, food rack.
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE construction_kind='STONE_FIRE_PIT') THEN
        RAISE EXCEPTION 'SCENARIO: STONE_FIRE_PIT (fire) missing'; END IF;
    IF (SELECT count(*) FROM assembly_definition
         WHERE construction_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN')
           AND review_state='VERIFIED') < 2 THEN
        RAISE EXCEPTION 'SCENARIO: fewer than two VERIFIED shelters for the progression'; END IF;
    IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE construction_kind='WOOD_STORE') THEN
        RAISE EXCEPTION 'SCENARIO: WOOD_STORE (fuel storage) missing'; END IF;
    IF (SELECT count(DISTINCT construction_kind) FROM assembly_definition
         WHERE construction_kind IN ('SMOKE_RACK','DRYING_RACK')) < 1 THEN
        RAISE EXCEPTION 'SCENARIO: no food-preservation rack for durable provisioning'; END IF;
END $$;

-- (5) Every VEGETATED land start biome can make FIRE from its own ground: an in-biome flora yields a
--     burnable material (wood/bark/reed OR dried grass/straw/thatch — grassland burns grass, not wood),
--     so a Chronicle is never stranded cold there before reaching the arrival envelope. MOUNTAIN is
--     deliberately excluded: bare rock has no fuel and relies on the arrival envelope (adjacent chunks),
--     the same way dr0113 lets tool-stone and water come from the envelope arrival_viability guarantees.
DO $$
DECLARE b text;
BEGIN
    FOREACH b IN ARRAY ARRAY['TEMPERATE_FOREST','WETLAND','GRASSLAND','HIGHLAND'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM flora_definition f
              JOIN flora_drop fd ON fd.flora_key=f.flora_key
             WHERE f.biome_affinity ILIKE '%'||b||'%'
               AND fd.item_key IN ('dry_branch','dry_twig','fallen_leaf_litter','loose_bark_strip',
                                   'straight_reed','dry_grass_bundle','straw_bundle','thatch_bundle')) THEN
            RAISE EXCEPTION 'SCENARIO: vegetated start biome % cannot gather fire fuel/tinder in-biome', b; END IF;
    END LOOP;
    RAISE NOTICE 'PASS: first-day survival scenarios reachable — threats to avoid, ranged/shield retreat, potable-water axis, defensible-site + first-week builds, and in-biome fire from every vegetated land start (#129)';
END $$;

ROLLBACK;
