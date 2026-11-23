-- V480: WHAT A WEAPON IS IN THE HAND (GitHub #968, closing EPIC #964).
--
-- #968 asks for "profiles for reach, damage, defensive handling, wear, and hand requirements using the
-- game's existing combat abstractions". Measured against the live catalogue, the weapon side of the fight
-- knew exactly two things about a weapon -- its combat_role and its edge_tier -- and the hunt turned those
-- into one number. So:
--
--   * DAMAGE is already there and already data: the edge ladder runs stone(+35) < copper/hardened(+45)
--     < bronze(+53) < iron(+60) < steel(+70), and the five swords inherit it. Nothing to add.
--   * REACH, DEFENSIVE HANDLING and HAND REQUIREMENTS did not exist at all. A knife and a two-handed spear
--     were the same weapon to the simulation but for the edge on them.
--   * WEAR did not exist FOR THE WEAPON, and this is the asymmetry worth naming: armour and shields take
--     part of every blow and are graded by armour_protection, a restraint strains SOUND -> WORN -> BROKEN
--     when an animal pulls against it, a tool wears by use_count -- and the thing you actually swing was the
--     one piece of equipment in the game that a fight never touched. A steel sword and a bone knife came out
--     of a hundred fights in identical condition.
--
-- Every column here is NOT NULL for the roles it applies to and NULL for the roles it does not, by CHECK.
-- Reach, a parry and wear are facts about a weapon used IN THE HAND; a loosed arrow has no parry, and
-- writing 0 there would be a number pretending to be a measurement. Hand requirements apply to everything,
-- because a bow needs both hands as surely as a maul does.

ALTER TABLE weapon_profile
    ADD COLUMN reach_cm              SMALLINT,
    ADD COLUMN parry                 SMALLINT,
    ADD COLUMN hands_required        SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN fights_per_wear_step  SMALLINT;

-- ---------------------------------------------------------------------------------------------------
-- THE MELEE WEAPONS: reach in centimetres, a parry out of ten, and how many fights it takes to go a step
-- worse. The wear numbers are the material's own story -- obsidian is the keenest edge in the catalogue and
-- the most brittle, bone and flake chip, bronze holds, iron holds longer, steel longest; and a club has
-- nothing on it to blunt, so it outlasts every edge in the game.
-- ---------------------------------------------------------------------------------------------------

-- Knives: short, almost no parry, and the edge goes quickly.
UPDATE weapon_profile SET reach_cm = 18, parry = 1, hands_required = 1, fights_per_wear_step =  2 WHERE item_key = 'flake_knife';
UPDATE weapon_profile SET reach_cm = 22, parry = 1, hands_required = 1, fights_per_wear_step =  3 WHERE item_key IN ('bone_knife','stone_knife');
UPDATE weapon_profile SET reach_cm = 24, parry = 1, hands_required = 1, fights_per_wear_step =  4 WHERE item_key IN ('flint_knife','chert_knife');
UPDATE weapon_profile SET reach_cm = 24, parry = 1, hands_required = 1, fights_per_wear_step =  2 WHERE item_key = 'obsidian_knife';
UPDATE weapon_profile SET reach_cm = 26, parry = 2, hands_required = 1, fights_per_wear_step =  8 WHERE item_key = 'bronze_knife';

-- Hafted edges: more reach, still a poor guard -- a heavy head does not turn a blow, it answers one.
UPDATE weapon_profile SET reach_cm = 32, parry = 2, hands_required = 1, fights_per_wear_step =  4 WHERE item_key = 'stone_hand_axe';
UPDATE weapon_profile SET reach_cm = 40, parry = 2, hands_required = 1, fights_per_wear_step =  3 WHERE item_key = 'stone_cleaver';
UPDATE weapon_profile SET reach_cm = 55, parry = 2, hands_required = 1, fights_per_wear_step =  4 WHERE item_key = 'stone_axe';
UPDATE weapon_profile SET reach_cm = 58, parry = 2, hands_required = 1, fights_per_wear_step =  6 WHERE item_key = 'copper_axe';
UPDATE weapon_profile SET reach_cm = 58, parry = 3, hands_required = 1, fights_per_wear_step =  9 WHERE item_key = 'bronze_axe';
UPDATE weapon_profile SET reach_cm = 60, parry = 3, hands_required = 1, fights_per_wear_step = 14 WHERE item_key = 'iron_axe';
UPDATE weapon_profile SET reach_cm = 60, parry = 3, hands_required = 1, fights_per_wear_step = 22 WHERE item_key = 'steel_axe';

-- Spears: the reach weapons, two hands, and a real guard -- the point keeps a thing off you.
UPDATE weapon_profile SET reach_cm = 190, parry = 4, hands_required = 2, fights_per_wear_step =  3 WHERE item_key = 'primitive_spear';
UPDATE weapon_profile SET reach_cm = 195, parry = 4, hands_required = 2, fights_per_wear_step =  3 WHERE item_key IN ('bone_tipped_spear','barbed_bone_spear','poisoned_spear');
UPDATE weapon_profile SET reach_cm = 190, parry = 4, hands_required = 2, fights_per_wear_step =  5 WHERE item_key = 'fire_hardened_spear';
UPDATE weapon_profile SET reach_cm = 200, parry = 4, hands_required = 2, fights_per_wear_step =  4 WHERE item_key = 'flint_tipped_spear';
UPDATE weapon_profile SET reach_cm = 205, parry = 5, hands_required = 2, fights_per_wear_step =  9 WHERE item_key = 'bronze_spear';

-- SWORDS: the parry weapons, and the reason "defensive handling" is in this ticket at all. One hand, so a
-- shield can go in the other; and the best guard in the catalogue, which is what a sword is FOR. A spear
-- outreaches every one of them and none of them can be thrown -- that is the trade.
UPDATE weapon_profile SET reach_cm = 78, parry = 5, hands_required = 1, fights_per_wear_step =  6 WHERE item_key = 'wooden_practice_sword';
UPDATE weapon_profile SET reach_cm = 80, parry = 6, hands_required = 1, fights_per_wear_step =  4 WHERE item_key = 'stone_edged_wooden_sword';
UPDATE weapon_profile SET reach_cm = 62, parry = 7, hands_required = 1, fights_per_wear_step =  9 WHERE item_key = 'bronze_short_sword';
UPDATE weapon_profile SET reach_cm = 88, parry = 8, hands_required = 1, fights_per_wear_step = 14 WHERE item_key = 'iron_sword';
UPDATE weapon_profile SET reach_cm = 90, parry = 8, hands_required = 1, fights_per_wear_step = 22 WHERE item_key = 'steel_sword';

-- Clubs and mauls: nothing on them to blunt, so they outlast every edge. The maul wants both hands.
UPDATE weapon_profile SET reach_cm = 55, parry = 2, hands_required = 1, fights_per_wear_step = 18 WHERE item_key = 'wooden_club';
UPDATE weapon_profile SET reach_cm = 55, parry = 2, hands_required = 1, fights_per_wear_step = 20 WHERE item_key = 'root_club';
UPDATE weapon_profile SET reach_cm = 50, parry = 2, hands_required = 1, fights_per_wear_step = 22 WHERE item_key = 'stone_club';
UPDATE weapon_profile SET reach_cm = 48, parry = 2, hands_required = 1, fights_per_wear_step = 25 WHERE item_key = 'stone_hammer';
UPDATE weapon_profile SET reach_cm = 70, parry = 2, hands_required = 2, fights_per_wear_step = 25 WHERE item_key = 'stone_maul';

-- ---------------------------------------------------------------------------------------------------
-- THE RANGED WEAPONS keep only their hand requirement. A bow needs both hands; a javelin, a sling and a
-- stone are thrown from one. Reach, a parry and fighting wear are left NULL on purpose: they are facts
-- about a weapon used in the hand, and a loosed arrow has none of them. Ammunition is already consumed
-- rather than worn, which is the right model for it.
-- ---------------------------------------------------------------------------------------------------
UPDATE weapon_profile SET hands_required = 2 WHERE combat_role = 'BOW';
UPDATE weapon_profile SET hands_required = 1 WHERE combat_role IN ('ARROW','JAVELIN','SLING','THROWN_STONE');

-- Every melee weapon answers all four, every ranged one answers exactly the hand count, and nothing is
-- left to a default nobody decided. A weapon added without these fails here rather than fighting as a
-- reachless, guardless, imperishable thing.
ALTER TABLE weapon_profile ADD CONSTRAINT weapon_melee_facts_are_complete CHECK (
    (combat_role IN ('HAND','BLUNT')
        AND reach_cm IS NOT NULL AND parry IS NOT NULL AND fights_per_wear_step IS NOT NULL)
    OR (combat_role NOT IN ('HAND','BLUNT')
        AND reach_cm IS NULL AND parry IS NULL AND fights_per_wear_step IS NULL));
ALTER TABLE weapon_profile ADD CONSTRAINT weapon_reach_is_a_length    CHECK (reach_cm IS NULL OR reach_cm BETWEEN 1 AND 400);
ALTER TABLE weapon_profile ADD CONSTRAINT weapon_parry_is_out_of_ten  CHECK (parry IS NULL OR parry BETWEEN 0 AND 10);
ALTER TABLE weapon_profile ADD CONSTRAINT weapon_wear_is_real         CHECK (fights_per_wear_step IS NULL OR fights_per_wear_step BETWEEN 1 AND 60);
ALTER TABLE weapon_profile ADD CONSTRAINT weapon_hands_are_one_or_two CHECK (hands_required IN (1, 2));

-- ---------------------------------------------------------------------------------------------------
-- AND THE APTITUDE #968 NAMES THAT V475 LEFT OUT.
--
-- "supported by relevant Dexterity, Agility, Strength/Endurance, Perception, and Reasoning/Knowledge."
-- V475 gave SWORD_FIGHTING the first four and not the fifth, and the fifth is the one that distinguishes
-- swordplay from swinging an axe: a guard answered before it lands is judgement, not reflex. The other four
-- are scaled down proportionally so the weights still sum to one, which SkillAptitudeSupportTest asserts.
-- ---------------------------------------------------------------------------------------------------
UPDATE skill_aptitude_support SET weight = 0.30 WHERE skill_key = 'SWORD_FIGHTING' AND aptitude_key = 'DEXTERITY';
UPDATE skill_aptitude_support SET weight = 0.26 WHERE skill_key = 'SWORD_FIGHTING' AND aptitude_key = 'AGILITY';
UPDATE skill_aptitude_support SET weight = 0.17 WHERE skill_key = 'SWORD_FIGHTING' AND aptitude_key = 'STRENGTH_ENDURANCE';
UPDATE skill_aptitude_support SET weight = 0.12 WHERE skill_key = 'SWORD_FIGHTING' AND aptitude_key = 'PERCEPTION';
INSERT INTO skill_aptitude_support (skill_key, aptitude_key, weight) VALUES
 ('SWORD_FIGHTING', 'REASONING', 0.15)
ON CONFLICT (skill_key, aptitude_key) DO UPDATE SET weight = 0.15;
