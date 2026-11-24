-- =====================================================================================================
-- #962 — WHAT A FOOD IS WORTH.
--
-- #959 gave the body a stomach, a store, a nourishment level and starvation damage. But every food in
-- the game still went into that stomach as the same thing: ChroniclePhysiologyService.eat() put in a
-- flat 320 ml whatever had been eaten, and digestion turned bulk straight into reserve at one fixed
-- rate. Measured against the catalogue as it stands, that means:
--
--   ONE BILBERRY (3 g, 4 ml)  filled a quarter of a stomach and fed as well as
--   A ROAST HAUNCH (540 g)    -- the only food in the game that differed was cooked_game_meat, by a
--                                hard-coded 700 ml on one branch of one method
--   A SKIN OF BOILED WATER    nourished exactly as much as the haunch, because `eat` would take it
--                                (clean_water is category FOOD) and bulk was all digestion could see
--   A HANDFUL OF WATERCRESS   (11 kcal/100 g) was worth a handful of PEMMICAN (580 kcal/100 g)
--
-- A food has to carry its own worth, so this is the catalogue saying it: for each of the 118 foods,
-- what a unit of it is made of. Bulk is NOT declared here -- it is item_definition.unit_volume_ml,
-- which the catalogue has always had and which the carry weight already reads. One definition of how
-- big a thing is.
--
--   kcal_per_100g         real nourishment by mass. A number anyone can check against the world.
--   water_percent         how much of its mass is water, so a food can slake thirst as well as hunger
--   digestion_ml_per_hour how fast this food leaves a stomach. Water is gone in minutes; pemmican sits.
--   settles_the_nerves    a hot prepared dish is a comfort and raw scraps are not, which the game said
--                         in code on one branch for one food. Now it is a property OF THE FOOD.
--
-- WHY kcal AND NOT A GAME NUMBER: because then the data can be WRONG, and anyone can say so. A game
-- number ("nourishment: 7") can only be argued about.
--
-- THE SCALE IS NOT NEW. A reserve point costs 0.85 per hour to live, so a day is 20.4 points. At
-- 110 kcal to the point, a stomachful (1200 ml) of cooked game meat is 1,758 kcal = 16.0 points --
-- and the old flat model gave a stomachful 15.6. So the staple diet is worth what it was worth
-- yesterday, and the change bites exactly where it should: on the berry, the leaf, and the water.
-- =====================================================================================================

CREATE TABLE food_nourishment (
    item_key              VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    food_family           VARCHAR(40)  NOT NULL,
    kcal_per_100g         SMALLINT     NOT NULL CHECK (kcal_per_100g >= 0 AND kcal_per_100g <= 900),
    water_percent         SMALLINT     NOT NULL CHECK (water_percent >= 0 AND water_percent <= 100),
    digestion_ml_per_hour SMALLINT     NOT NULL CHECK (digestion_ml_per_hour BETWEEN 20 AND 600),
    settles_the_nerves    SMALLINT     NOT NULL DEFAULT 0 CHECK (settles_the_nerves BETWEEN 0 AND 10)
);
COMMENT ON TABLE  food_nourishment IS
    '#962: what one unit of a food is made of. Bulk is item_definition.unit_volume_ml, not repeated here.';
COMMENT ON COLUMN food_nourishment.digestion_ml_per_hour IS
    'How fast this food leaves the stomach. A mixture digests at the bulk-weighted mean of what is in there.';

INSERT INTO food_nourishment (item_key, food_family, kcal_per_100g, water_percent, digestion_ml_per_hour,
                              settles_the_nerves) VALUES
  ('acorn_flatbread', 'BREAD', 280, 25, 80, 1),
  ('acorn_flour', 'FLOUR', 387, 8, 80, 0),
  ('arrowhead_tuber', 'ROOT', 97, 70, 85, 0),
  ('bait_pouch', 'BAIT', 100, 65, 80, 0),
  ('beech_mast', 'NUT', 576, 7, 70, 0),
  ('berry_compote', 'BROTH', 90, 72, 130, 2),
  ('bilberry', 'BERRY', 57, 85, 110, 0),
  ('bird_egg', 'EGG', 143, 76, 90, 0),
  ('blackberry', 'BERRY', 43, 88, 110, 0),
  ('bulrush_root', 'ROOT', 90, 72, 85, 0),
  ('burdock_root', 'ROOT', 72, 80, 85, 0),
  ('caddis_grub', 'INSECT', 120, 70, 90, 0),
  ('cattail_rhizome', 'ROOT', 90, 72, 85, 0),
  ('cave_mushroom', 'MUSHROOM', 22, 92, 95, 0),
  ('cep_porcini', 'MUSHROOM', 34, 89, 95, 0),
  ('chanterelle', 'MUSHROOM', 38, 89, 95, 0),
  ('chestnut', 'NUT', 196, 52, 70, 0),
  ('chickweed', 'GREENS', 25, 91, 110, 0),
  ('clean_water', 'WATER', 0, 100, 400, 0),
  ('cloudberry', 'BERRY', 51, 85, 110, 0),
  ('cooked_fish', 'FISH', 145, 66, 85, 2),
  ('cooked_fowl_meat', 'MEAT', 190, 62, 80, 2),
  ('cooked_game_meat', 'MEAT', 190, 60, 80, 2),
  ('cooked_greens', 'GREENS', 35, 88, 110, 1),
  ('cooked_mushrooms', 'MUSHROOM', 30, 88, 95, 1),
  ('crab_apple', 'FRUIT', 52, 85, 100, 0),
  ('cranberry', 'BERRY', 46, 87, 110, 0),
  ('crayfish_meat', 'SHELLFISH', 77, 81, 90, 0),
  ('curly_dock_leaf', 'GREENS', 22, 92, 110, 0),
  ('dandelion', 'GREENS', 45, 86, 110, 0),
  ('dandelion_leaf', 'GREENS', 45, 86, 110, 0),
  ('death_cap', 'MUSHROOM', 25, 91, 95, 0),
  ('dried_cricket', 'INSECT', 450, 8, 90, 0),
  ('dried_fish', 'CURED_FISH', 290, 18, 70, 0),
  ('dried_grasshopper', 'INSECT', 450, 8, 90, 0),
  ('dried_meat', 'CURED_MEAT', 410, 12, 65, 0),
  ('dried_mushroom', 'DRIED_PLANT', 280, 12, 95, 0),
  ('elderberry', 'BERRY', 73, 80, 110, 0),
  ('fat_hen_greens', 'GREENS', 43, 87, 110, 0),
  ('fermented_vegetables', 'FERMENT', 20, 90, 110, 1),
  ('field_mushroom', 'MUSHROOM', 22, 92, 95, 0),
  ('filtered_water', 'WATER', 0, 100, 400, 0),
  ('fish_fillet', 'FISH', 110, 76, 85, 0),
  ('fish_side', 'FISH', 110, 76, 85, 0),
  ('fly_agaric', 'MUSHROOM', 25, 91, 95, 0),
  ('fowl_egg', 'EGG', 143, 76, 90, 0),
  ('freshwater_mussel', 'SHELLFISH', 86, 80, 90, 0),
  ('garlic_clove_wild', 'ROOT', 149, 59, 85, 0),
  ('giant_puffball', 'MUSHROOM', 20, 93, 95, 0),
  ('goat_milk', 'BEVERAGE', 69, 88, 300, 2),
  ('good_king_henry_greens', 'GREENS', 43, 87, 110, 0),
  ('grain_flatbread', 'BREAD', 270, 25, 80, 1),
  ('grain_flour', 'FLOUR', 364, 12, 80, 0),
  ('grain_porridge', 'BROTH', 70, 80, 130, 3),
  ('ground_elder_leaf', 'GREENS', 28, 90, 110, 0),
  ('gutted_fish', 'FISH', 110, 76, 85, 0),
  ('hawthorn_berry', 'BERRY', 52, 82, 110, 0),
  ('hawthorn_haw', 'BERRY', 52, 82, 110, 0),
  ('hazelnut', 'NUT', 419, 4, 70, 0),
  ('hazelnut_kernel', 'NUT', 628, 5, 70, 0),
  ('hedgehog_mushroom', 'MUSHROOM', 22, 92, 95, 0),
  ('herbal_infusion', 'BEVERAGE', 2, 99, 300, 6),
  ('juniper_berry', 'BERRY', 60, 70, 110, 0),
  ('lingonberry', 'BERRY', 46, 87, 110, 0),
  ('lions_mane', 'MUSHROOM', 35, 90, 95, 0),
  ('maple_sap', 'BEVERAGE', 26, 96, 300, 1),
  ('marsh_samphire', 'GREENS', 25, 90, 110, 0),
  ('medlar', 'FRUIT', 47, 85, 100, 0),
  ('mint_sprig', 'GREENS', 44, 78, 110, 0),
  ('morel', 'MUSHROOM', 31, 90, 95, 0),
  ('nettle_leaf', 'GREENS', 42, 82, 110, 0),
  ('oyster_mushroom', 'MUSHROOM', 33, 89, 95, 0),
  ('peeled_root', 'ROOT', 80, 76, 85, 0),
  ('pemmican', 'CURED_MEAT', 580, 8, 65, 1),
  ('pignut_tuber', 'ROOT', 95, 70, 85, 0),
  ('pine_nut', 'NUT', 673, 2, 70, 0),
  ('porcini', 'MUSHROOM', 34, 89, 95, 0),
  ('preserved_berries', 'DRIED_PLANT', 150, 45, 95, 0),
  ('rampion_root', 'ROOT', 70, 80, 85, 0),
  ('raw_fish', 'FISH', 110, 76, 85, 0),
  ('raw_fowl_meat', 'MEAT', 150, 70, 80, 0),
  ('raw_game_meat', 'MEAT', 140, 72, 80, 0),
  ('raw_honey', 'SWEET', 304, 17, 150, 2),
  ('raw_water', 'WATER', 0, 100, 400, 0),
  ('river_snail', 'SHELLFISH', 90, 79, 90, 0),
  ('root_vegetable_stew', 'BROTH', 55, 84, 130, 3),
  ('rose_hip', 'BERRY', 162, 59, 110, 0),
  ('rowan_berry', 'BERRY', 49, 84, 110, 0),
  ('salted_fish', 'CURED_FISH', 180, 50, 70, 0),
  ('salted_meat', 'CURED_MEAT', 230, 45, 65, 0),
  ('sea_beet_leaf', 'GREENS', 22, 91, 110, 0),
  ('sea_buckthorn_berry', 'BERRY', 82, 75, 110, 0),
  ('silverweed_root', 'ROOT', 85, 74, 85, 0),
  ('sloe', 'BERRY', 50, 83, 110, 0),
  ('smoked_fish', 'CURED_FISH', 200, 45, 70, 0),
  ('smoked_fowl', 'CURED_MEAT', 210, 48, 65, 0),
  ('smoked_meat', 'CURED_MEAT', 250, 40, 65, 0),
  ('sorrel_leaf', 'GREENS', 22, 92, 110, 0),
  ('sow_thistle_leaf', 'GREENS', 24, 91, 110, 0),
  ('sweet_chestnut', 'NUT', 196, 52, 70, 0),
  ('trail_cake', 'SWEET', 450, 12, 150, 3),
  ('walnut', 'NUT', 436, 3, 70, 0),
  ('walnut_kernel', 'NUT', 654, 4, 70, 0),
  ('washed_root', 'ROOT', 80, 76, 85, 0),
  ('water_chestnut', 'ROOT', 97, 74, 85, 0),
  ('watercress', 'GREENS', 11, 95, 110, 0),
  ('watercress_bundle', 'GREENS', 11, 95, 110, 0),
  ('wild_berries', 'BERRY', 50, 85, 110, 0),
  ('wild_carrot_root', 'ROOT', 41, 88, 85, 0),
  ('wild_cherry', 'BERRY', 63, 82, 110, 0),
  ('wild_garlic_bulb', 'ROOT', 149, 59, 85, 0),
  ('wild_garlic_leaf', 'GREENS', 30, 89, 110, 0),
  ('wild_grain', 'FLOUR', 340, 11, 80, 0),
  ('wild_onion_bulb', 'ROOT', 40, 89, 85, 0),
  ('wild_pear', 'FRUIT', 57, 84, 100, 0),
  ('wild_rice_grain', 'FLOUR', 357, 12, 80, 0),
  ('wild_rose_hip', 'BERRY', 162, 59, 110, 0),
  ('wild_strawberry', 'BERRY', 32, 91, 110, 0);;

-- EVERY FOOD, OR THIS MIGRATION DOES NOT APPLY. A food with no row would fall back to a middling
-- default in the reader and nobody would ever find out; the guard means a future migration that adds a
-- food and forgets its worth fails here, loudly, instead of quietly feeding the player a guess.
-- (A matching test holds the same line for the suite, so the rule survives a hand-patched database.)
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(d.item_key, ', ' ORDER BY d.item_key) INTO missing
      FROM item_definition d
      LEFT JOIN food_nourishment f ON f.item_key = d.item_key
     WHERE d.category = 'FOOD' AND f.item_key IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V482: these foods have no declared worth: %', missing;
    END IF;
    -- Bulk comes from the catalogue, so a food with no volume would go into the stomach as nothing.
    SELECT string_agg(d.item_key, ', ' ORDER BY d.item_key) INTO missing
      FROM item_definition d JOIN food_nourishment f ON f.item_key = d.item_key
     WHERE d.unit_volume_ml <= 0;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V482: these foods have no bulk to fill a stomach with: %', missing;
    END IF;
END $$;

-- =====================================================================================================
-- AND THE STOMACH HAS TO REMEMBER WHAT WENT IN.
--
-- stomach_fullness_ml alone cannot answer "how much good is this doing", because the answer depends on
-- what the bulk was. Two running totals, both emptied together as digestion proceeds, so a mixed
-- stomach -- which is the normal case -- is accounted for correctly without tracking every item in it.
-- =====================================================================================================
ALTER TABLE chronicle_physiology
    ADD COLUMN stomach_kcal              INTEGER  NOT NULL DEFAULT 0,
    ADD COLUMN stomach_digest_ml_per_hour SMALLINT NOT NULL DEFAULT 90;

COMMENT ON COLUMN chronicle_physiology.stomach_kcal IS
    '#962: usable nourishment still in the stomach. Released to energy_reserves in step with the bulk.';
COMMENT ON COLUMN chronicle_physiology.stomach_digest_ml_per_hour IS
    '#962: this stomach''s current emptying rate — the bulk-weighted mean of the foods in it. 90 is the
     rate the flat model used, so an untouched row behaves exactly as it did before.';

-- A CHRONICLE WHO WAS MID-MEAL WHEN THIS RAN KEEPS THAT MEAL. 1.58 kcal/ml is the density at which the
-- flat model's 1200 ml stomachful was worth its 15.6 reserve points, so their existing stomachful is
-- worth to them tomorrow precisely what it was worth yesterday — a deliberate transition rather than
-- a reset, which is what #962 asks for.
UPDATE chronicle_physiology
   SET stomach_kcal = ROUND(stomach_fullness_ml * 1.58)
 WHERE stomach_fullness_ml > 0;

-- =====================================================================================================
-- AND NOW THE CATALOGUE CAN BE HELD TO ACCOUNT FOR IT (#962: "Account for recipe inputs and output
-- servings so processing or splitting food does not create unlimited nourishment").
--
-- With a kilocalorie on every food, every recipe that turns food into food can be asked whether it adds
-- up -- and asked in SQL, which is how the mass-conservation gate already works. Measured the moment the
-- numbers existed, and the answer was no:
--
--   bake_grain_flatbread   109 kcal in  ->    972 out    (x8.9)   two PINCHES of flour, 360 g of bread
--   bake_flatbread         225          ->   1008        (x4.5)
--   dry_meat               910          ->   2132        (x2.3)   one haunch, TWO pieces of dried meat
--   brine_fish             836          ->   1620        (x1.9)
--   cook_greens             26          ->     53        (x2.0)
--   preserve_berries       635          ->    900        (x1.4)
--
-- None of them was ever noticed, because until this migration nothing could say what a food was worth.
-- Mass balanced in every case -- the Auditor's gate was satisfied throughout -- because the missing mass
-- came in as WATER, and water carries no nourishment. That is the whole defect in one line.
--
-- TWO SHAPES OF ERROR, and they want different fixes:
--
--   a yield that outran its input -> the YIELD is wrong. Preserving food shrinks it; one haunch makes one
--   piece of dried meat, not two. Corrected on the recipe, leaving every food its real density.
--
--   an input too small to make the output at all -> the INPUT is wrong. Thirty grams of flour cannot
--   become three hundred and sixty grams of bread however much water is added to it.
-- =====================================================================================================

-- PRESERVING FOOD SHRINKS IT. One unit out, not two or three.
UPDATE material_process SET output_max = 1
 WHERE process_key IN ('dry_meat', 'dry_fish', 'salt_fish', 'smoke_fish', 'preserve_berries');
-- A brine takes twice the salt of a dry-salting, so it keeps more of the fish: two, not three.
-- (Missed on the first pass, and the gate at the foot of this file is what found it.)
UPDATE material_process SET output_max = 2 WHERE process_key = 'brine_fish';

-- BREAD IS MADE OF FLOUR. A unit of flour is 15 g, so two of them were a dusting.
UPDATE material_process_input SET quantity = 14
 WHERE process_key = 'bake_grain_flatbread' AND item_key = 'grain_flour';
UPDATE material_process_input_group SET quantity = 14
 WHERE process_key = 'bake_flatbread' AND group_name = 'flour';
-- And porridge of grain, and a dish of greens of greens.
UPDATE material_process_input_group SET quantity = 12
 WHERE process_key = 'cook_porridge' AND group_name = 'grain';
UPDATE material_process_input_group SET quantity = 12
 WHERE process_key = 'cook_greens' AND group_name = 'greens';

-- A BAIT POUCH IS WORMS AND WINDFALL BERRIES, not a meal that outdoes what went into it.
UPDATE food_nourishment SET kcal_per_100g = 60 WHERE item_key = 'bait_pouch';

-- THE LARGEST ENERGY IN THE GAME WAS INVISIBLE. Rendered tallow is category MATERIAL -- correctly, it is
-- a making material -- but it is also three hundred and forty grams of fat, and it is the main thing in
-- pemmican. Without a row for it, the audit above reported pemmican as creating nourishment when in
-- truth it is the one recipe that conserves it best. Declaring what a material is worth as food does not
-- make it food: `eat` and the drink fallback both read category='FOOD', and tallow is not one.
INSERT INTO food_nourishment (item_key, food_family, kcal_per_100g, water_percent, digestion_ml_per_hour,
                              settles_the_nerves) VALUES
  ('rendered_tallow', 'FAT', 900, 1, 60, 0);

-- A GROUP IS A CHOICE, NOT A SUM. The first version of this gate added every alternative in a group
-- together, which overstates what a making costs and therefore UNDERSTATES what it creates -- and it
-- hid two of the worst cases in the catalogue behind the sum of options nobody would use:
--
--   dry_mushrooms   79 kcal of the cheapest mushrooms -> 448 of dried mushroom   (x5.7)
--   cook_root_stew  63 kcal of one rhizome and a clove -> 248 of stew            (x3.9)
--
-- The cheapest way to make a thing is the one that has to add up, because that is the one a player will
-- find. So: a root stew wants five roots, a dried mushroom eight fresh ones, a trail cake a dozen nuts.
UPDATE material_process_input_group SET quantity = 5
 WHERE process_key = 'cook_root_stew' AND group_name = 'root';
UPDATE material_process_input_group SET quantity = 8
 WHERE process_key = 'dry_mushrooms' AND group_name = 'mushroom';
-- Eight fresh mushrooms are 480 g and dry to about forty: ONE dried mushroom, not four.
UPDATE material_process SET output_min = 1, output_max = 1 WHERE process_key = 'dry_mushrooms';
UPDATE material_process_input_group SET quantity = 12
 WHERE process_key = 'bake_trail_cake' AND group_name = 'nut';
-- And an infusion is hot water with a scent in it: one kilocalorie in a hundred millilitres, not two.
UPDATE food_nourishment SET kcal_per_100g = 1 WHERE item_key = 'herbal_infusion';

-- AND THE GATE ITSELF. A third more usable energy than went in is allowed, because cooking, drying and
-- smoking genuinely make food more digestible -- that is what they are for, and #962 asks that the
-- raw/cooked difference be preserved. Beyond a third it is not digestibility, it is matter from nothing.
DO $$
DECLARE made_from_nothing TEXT;
BEGIN
    WITH kcal AS (
        SELECT d.item_key, d.unit_mass_grams * f.kcal_per_100g / 100.0 AS k
          FROM item_definition d JOIN food_nourishment f USING (item_key)),
    plain AS (
        SELECT i.process_key, SUM(COALESCE(i.quantity, 1) * k) AS kcal
          FROM material_process_input i JOIN kcal USING (item_key) GROUP BY i.process_key),
    cheapest_of_each_group AS (
        SELECT g.process_key, g.group_name, MIN(COALESCE(g.quantity, 1) * k) AS kcal
          FROM material_process_input_group g JOIN kcal USING (item_key)
         GROUP BY g.process_key, g.group_name),
    grouped AS (
        SELECT process_key, SUM(kcal) AS kcal FROM cheapest_of_each_group GROUP BY process_key),
    ins AS (
        SELECT process_key, SUM(kcal) AS kcal
          FROM (SELECT process_key, kcal FROM plain
                UNION ALL
                SELECT process_key, kcal FROM grouped) y
         GROUP BY process_key)
    SELECT string_agg(p.process_key || ' (' || ROUND(i.kcal) || ' -> '
                      || ROUND(COALESCE(p.output_max, p.output_min, 1) * k.k) || ')', ', ' ORDER BY p.process_key)
      INTO made_from_nothing
      FROM material_process p
      JOIN kcal k ON k.item_key = p.output_item_key
      JOIN ins  i ON i.process_key = p.process_key
     WHERE i.kcal > 0
       AND COALESCE(p.output_max, p.output_min, 1) * k.k > i.kcal * 1.35;
    IF made_from_nothing IS NOT NULL THEN
        RAISE EXCEPTION 'V482: these recipes make nourishment out of nothing: %', made_from_nothing;
    END IF;
END $$;
