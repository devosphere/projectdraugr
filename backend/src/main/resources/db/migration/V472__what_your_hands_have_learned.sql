-- V472: SKILLS, AND THE APTITUDES THAT SUPPORT THEM (GitHub #954).
--
-- The world already adapts to practice, but at the wrong grain. There are eight capability columns in
-- chronicle_capability_adaptation and a hundred and ninety intents, so the measured truth today is:
--
--     FINE_MOTOR  67 intents      ATTENTION  47      LOAD  41      AIM  7
--     RECOVERY     7              LOCOMOTION  4      KNOWLEDGE  2  INSIGHT  1
--
-- One number stands for weaving, cooking, forging and writing alike; another for tracking, appraising a
-- crop and talking to a stranger. A chronicle who has drawn a bow a hundred times and never woven is,
-- to the simulation, equally practised at both, because both are AIM and FINE_MOTOR respectively and
-- nothing finer was ever recorded. That is the whole of #954's complaint and it is exactly right.
--
-- THE SHAPE OF THE ANSWER IS TWO LAYERS, NOT ONE. The eight existing columns become APTITUDES: broad
-- bodily and mental faculties that transfer between related work. On top of them sit SKILLS: experience
-- at one particular craft, quarry or weapon. An archer's practice makes them an archer; it makes their
-- aim a little better, and their aim helps them throw a javelin, but it never makes them a javelin
-- thrower. That asymmetry is the entire mechanic.
--
-- NOTHING HERE IS A NEW NUMBER FOR ITS OWN SAKE. Every skill in this catalogue is REACHED by something
-- a player can actually do — an intent, a catalogued process, or a weapon they can hold. A skill no act
-- reaches would be the catalogue token this project exists to refuse, and SkillsAreReachableTest fails
-- the build if one appears.
--
-- WHAT IS DELIBERATELY ABSENT: sword fighting. #954 asks for it, and there is no sword in the item
-- catalogue — not one row in item_definition matches sword, dagger or blade. A SWORD_FIGHTING skill
-- would therefore be unreachable by construction. It is left out until a sword exists to swing, and
-- said so on the ticket rather than catalogued as a token that looks like coverage.
--
-- WHAT IS NEWLY PRESENT: knives can be fought with. Seven knives are in the item catalogue and not one
-- had a weapon_profile row, so a knapped flint knife could not be raised against an animal at all —
-- the combat query joins weapon_profile and a knife simply was not there. #954 asks for knife combat
-- "distinct from carving and other utility knife work"; this gives the knives the profile that makes
-- the skill reachable and the weapon usable. That is a capability the player gains today, not only a
-- number that grows.

-- ---------------------------------------------------------------------------------------------------
-- APTITUDES — the broad layer, declared rather than hardcoded.
--
-- These are the columns V11 and V67 already wrote. Naming them in a table means the supporting weights
-- below can reference them, and a reader can ask the database which faculties exist instead of reading
-- a switch statement in Java. recovery_readiness is NOT an aptitude: it is a state of the body, not a
-- faculty that practice builds, and no skill draws on it.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE aptitude (
    aptitude_key      VARCHAR(30) PRIMARY KEY,
    display_name      VARCHAR(60) NOT NULL,
    adaptation_column VARCHAR(40) NOT NULL UNIQUE,
    evidence_domain   VARCHAR(20) NOT NULL UNIQUE,
    notes             TEXT NOT NULL,
    CONSTRAINT aptitude_needs_a_reason CHECK (length(btrim(notes)) >= 40)
);

INSERT INTO aptitude (aptitude_key, display_name, adaptation_column, evidence_domain, notes) VALUES
 ('AIM', 'Aim and coordination', 'visual_aim_familiarity', 'AIM',
  'Putting a thrown or loosed thing where the eye intends. Supports archery, the javelin, the sling, a thrown stone and spear fishing.'),
 ('DEXTERITY', 'Dexterity', 'fine_motor_familiarity', 'FINE_MOTOR',
  'Precise, controlled work of the hands. Supports weaving, carving, knotting, fine forging, the knife and the pen.'),
 ('AGILITY', 'Agility', 'locomotion_familiarity', 'LOCOMOTION',
  'Balance, footwork, changes of direction, climbing and movement over bad ground. Distinct from hand dexterity and from sustained endurance.'),
 ('PERCEPTION', 'Perception', 'attention_resilience', 'ATTENTION',
  'Noticing what is there: sign on the ground, a landmark, a flaw in a material, a change in the weather or a threat.'),
 ('STRENGTH_ENDURANCE', 'Strength and endurance', 'load_conditioning', 'LOAD',
  'Force and the ability to keep applying it. Supports hammering, digging, hauling, felling and sustained fighting; also carrying and lifting.'),
 ('REASONING', 'Reasoning', 'insight_familiarity', 'INSIGHT',
  'Working out how a thing functions and what follows from it: planning a build, judging a forge heat, reading a hunt, spatial reasoning.'),
 ('KNOWLEDGE', 'Knowledge', 'knowledge_familiarity', 'KNOWLEDGE',
  'What has been learned and can be recalled and applied: provenance, properties, seasons, precedent and the written record.');

-- ---------------------------------------------------------------------------------------------------
-- SKILLS — the specific layer.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE skill_definition (
    skill_key    VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(60) NOT NULL,
    family       VARCHAR(20) NOT NULL,
    notes        TEXT        NOT NULL,
    CONSTRAINT skill_family_check CHECK (family IN ('CRAFT','LAND','ANIMAL','COMBAT','MIND','BODY','SOCIAL')),
    CONSTRAINT skill_needs_a_reason CHECK (length(btrim(notes)) >= 30)
);

INSERT INTO skill_definition (skill_key, display_name, family, notes) VALUES
 -- CRAFT
 ('WEAVING','Weaving','CRAFT','Cordage, basketry, netting and cloth: anything built by passing one fibre through others.'),
 ('CARVING','Carving','CRAFT','Shaping wood, bone and antler with an edge — hafts, points, bowls, pegs and fittings.'),
 ('JOINERY','Joinery','CRAFT','Fitted woodwork that must stand and bear: benches, shelves, desks and workstations.'),
 ('KNAPPING','Knapping','CRAFT','Breaking stone where it is meant to break: flakes, blades, points, cores and ground edges.'),
 ('FORGING','Forging','CRAFT','Winning metal from ore and working it hot: the bloomery, the smelt, the alloy and the cast.'),
 ('TAILORING','Tailoring','CRAFT','Turning hide, fur and cloth into things worn — cut, fit, seam and lining.'),
 ('COOKING','Cooking','CRAFT','Making food edible and better than it was: heat, water, timing and seasoning.'),
 ('PRESERVING','Preserving','CRAFT','Keeping food past its own span by smoke, salt, drying or cold.'),
 ('BUTCHERY','Butchery','CRAFT','Taking a carcass apart for everything it holds — meat, fat, hide, sinew, bone and offal.'),
 ('FIRECRAFT','Firecraft','CRAFT','Getting fire, keeping it, banking it and putting it out; and the charcoal burn.'),
 ('MATERIAL_PROCESSING','Material processing','CRAFT','Rendering raw stuff workable: retting, tanning, rendering, leaching, filtering, grinding.'),
 ('CONSTRUCTION','Construction','CRAFT','Raising something that stands on the ground and stays there, and taking it down again.'),
 ('MENDING','Mending','CRAFT','Returning a worn or broken thing to use rather than making another.'),
 -- LAND
 ('FORAGING','Foraging','LAND','Finding and taking what grows or nests of its own accord, and knowing what is worth taking.'),
 ('LOGGING','Logging','LAND','Felling, coppicing and the taking of wood, and clearing the ground that holds it.'),
 ('QUARRYING','Quarrying','LAND','Winning stone, clay and mineral out of the ground, and judging where it lies.'),
 ('FARMING','Farming','LAND','Working ground to make it yield: tilling, sowing, tending, watering and harvest.'),
 ('HAULING','Hauling','LAND','Lifting, carrying, stowing and fetching — the discipline of moving weight without hurting yourself.'),
 ('WAYFARING','Wayfaring','LAND','Covering ground on foot, and judging what a crossing or a route will cost.'),
 -- ANIMAL
 ('TRACKING','Tracking','ANIMAL','Reading sign and movement, following it, and leaving as little of your own as possible.'),
 ('HUNTING','Hunting','ANIMAL','Closing with an animal and taking it: the stalk, the set trap and the kill itself.'),
 ('FISHING','Fishing','ANIMAL','Taking fish, by whatever means the water and the gear allow.'),
 ('HUSBANDRY','Husbandry','ANIMAL','Taming and keeping living stock: feeding, tending, grooming, breeding and the yield.'),
 -- COMBAT
 ('ARCHERY','Archery','COMBAT','The bow: draw, hold, loose and the arrow that suits the shot.'),
 ('SPEAR_COMBAT','Spear combat','COMBAT','The spear held and used in the hand — reach, thrust and recovery. Not spear fishing.'),
 ('KNIFE_COMBAT','Knife combat','COMBAT','The knife raised against something that can hurt back. Not carving or any utility knife work.'),
 ('AXE_COMBAT','Axe combat','COMBAT','The hafted edge swung in a fight: the axe and the cleaver, which cut rather than crush.'),
 ('CLUB_COMBAT','Club combat','COMBAT','The hafted or hand weight swung to crush: club, maul and hammer.'),
 ('JAVELIN_THROWING','Javelin throwing','COMBAT','Throwing a shafted weapon by hand or by thrower, for distance and for a hit that holds.'),
 ('SLING_USE','Sling use','COMBAT','The cord-and-pouch sling: whirl, release and the stone that fits it.'),
 ('STONE_THROWING','Stone throwing','COMBAT','Throwing a stone by hand — the oldest reach a person has, and a real one.'),
 -- MIND
 ('SCRIBING','Scribing','MIND','Writing, copying and keeping records that can be read again and relied on.'),
 ('CARTOGRAPHY','Cartography','MIND','Setting ground down as a map, and reading one: landmarks, distances, bearings and routes.'),
 ('LORE','Lore','MIND','Reasoning from what is known: reading, analysis, inference and what has been learned before.'),
 ('OBSERVATION','Observation','MIND','Looking, listening and handling in order to find out — appraisal rather than action.'),
 -- BODY / SOCIAL
 ('HEALING','Healing','BODY','Tending a hurt or soiled body: cleaning, dressing, binding and keeping a wound from turning.'),
 ('ATHLETICS','Athletics','BODY','Breaking away, going to ground, climbing and holding footing where it is bad.'),
 ('NEGOTIATION','Negotiation','SOCIAL','Dealing with other people: approach, bargain, conduct, agreement and the settling of a claim.');

-- ---------------------------------------------------------------------------------------------------
-- WHICH APTITUDES SUPPORT WHICH SKILL, and how much.
--
-- Weights are a share of the supporting contribution, read as such and normalised by the reader, so a
-- drift in the data cannot produce a number outside its band. Each skill's weights are written to sum
-- to 1.0 and SkillAptitudeSupportTest asserts it.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE skill_aptitude_support (
    skill_key    VARCHAR(40) NOT NULL REFERENCES skill_definition(skill_key),
    aptitude_key VARCHAR(30) NOT NULL REFERENCES aptitude(aptitude_key),
    weight       REAL        NOT NULL,
    PRIMARY KEY (skill_key, aptitude_key),
    CONSTRAINT support_weight_is_a_share CHECK (weight > 0 AND weight <= 1)
);

INSERT INTO skill_aptitude_support (skill_key, aptitude_key, weight) VALUES
 ('WEAVING','DEXTERITY',0.60),('WEAVING','PERCEPTION',0.20),('WEAVING','KNOWLEDGE',0.20),
 ('CARVING','DEXTERITY',0.60),('CARVING','PERCEPTION',0.20),('CARVING','KNOWLEDGE',0.20),
 ('JOINERY','DEXTERITY',0.40),('JOINERY','REASONING',0.25),('JOINERY','STRENGTH_ENDURANCE',0.20),('JOINERY','PERCEPTION',0.15),
 ('KNAPPING','DEXTERITY',0.45),('KNAPPING','PERCEPTION',0.25),('KNAPPING','KNOWLEDGE',0.20),('KNAPPING','STRENGTH_ENDURANCE',0.10),
 ('FORGING','STRENGTH_ENDURANCE',0.35),('FORGING','DEXTERITY',0.25),('FORGING','PERCEPTION',0.20),('FORGING','KNOWLEDGE',0.20),
 ('TAILORING','DEXTERITY',0.60),('TAILORING','PERCEPTION',0.20),('TAILORING','KNOWLEDGE',0.20),
 ('COOKING','DEXTERITY',0.35),('COOKING','PERCEPTION',0.30),('COOKING','KNOWLEDGE',0.35),
 ('PRESERVING','KNOWLEDGE',0.40),('PRESERVING','PERCEPTION',0.30),('PRESERVING','DEXTERITY',0.30),
 ('BUTCHERY','DEXTERITY',0.40),('BUTCHERY','KNOWLEDGE',0.30),('BUTCHERY','STRENGTH_ENDURANCE',0.30),
 ('FIRECRAFT','DEXTERITY',0.40),('FIRECRAFT','PERCEPTION',0.25),('FIRECRAFT','KNOWLEDGE',0.35),
 ('MATERIAL_PROCESSING','KNOWLEDGE',0.35),('MATERIAL_PROCESSING','DEXTERITY',0.30),('MATERIAL_PROCESSING','PERCEPTION',0.20),('MATERIAL_PROCESSING','STRENGTH_ENDURANCE',0.15),
 ('CONSTRUCTION','STRENGTH_ENDURANCE',0.40),('CONSTRUCTION','REASONING',0.25),('CONSTRUCTION','DEXTERITY',0.20),('CONSTRUCTION','PERCEPTION',0.15),
 ('MENDING','DEXTERITY',0.45),('MENDING','PERCEPTION',0.30),('MENDING','KNOWLEDGE',0.25),
 ('FORAGING','PERCEPTION',0.45),('FORAGING','KNOWLEDGE',0.35),('FORAGING','STRENGTH_ENDURANCE',0.20),
 ('LOGGING','STRENGTH_ENDURANCE',0.55),('LOGGING','PERCEPTION',0.20),('LOGGING','DEXTERITY',0.15),('LOGGING','KNOWLEDGE',0.10),
 ('QUARRYING','STRENGTH_ENDURANCE',0.50),('QUARRYING','PERCEPTION',0.25),('QUARRYING','KNOWLEDGE',0.25),
 ('FARMING','STRENGTH_ENDURANCE',0.35),('FARMING','KNOWLEDGE',0.30),('FARMING','PERCEPTION',0.20),('FARMING','DEXTERITY',0.15),
 ('HAULING','STRENGTH_ENDURANCE',0.70),('HAULING','AGILITY',0.15),('HAULING','REASONING',0.15),
 ('WAYFARING','AGILITY',0.45),('WAYFARING','STRENGTH_ENDURANCE',0.35),('WAYFARING','PERCEPTION',0.20),
 ('TRACKING','PERCEPTION',0.55),('TRACKING','KNOWLEDGE',0.25),('TRACKING','AGILITY',0.20),
 ('HUNTING','PERCEPTION',0.30),('HUNTING','AIM',0.25),('HUNTING','AGILITY',0.20),('HUNTING','KNOWLEDGE',0.25),
 ('FISHING','PERCEPTION',0.35),('FISHING','AIM',0.25),('FISHING','DEXTERITY',0.20),('FISHING','KNOWLEDGE',0.20),
 ('HUSBANDRY','PERCEPTION',0.30),('HUSBANDRY','KNOWLEDGE',0.35),('HUSBANDRY','DEXTERITY',0.20),('HUSBANDRY','STRENGTH_ENDURANCE',0.15),
 ('ARCHERY','AIM',0.55),('ARCHERY','STRENGTH_ENDURANCE',0.25),('ARCHERY','PERCEPTION',0.20),
 ('SPEAR_COMBAT','STRENGTH_ENDURANCE',0.35),('SPEAR_COMBAT','AGILITY',0.30),('SPEAR_COMBAT','DEXTERITY',0.20),('SPEAR_COMBAT','PERCEPTION',0.15),
 ('KNIFE_COMBAT','DEXTERITY',0.40),('KNIFE_COMBAT','AGILITY',0.35),('KNIFE_COMBAT','PERCEPTION',0.25),
 ('AXE_COMBAT','STRENGTH_ENDURANCE',0.45),('AXE_COMBAT','AGILITY',0.25),('AXE_COMBAT','DEXTERITY',0.15),('AXE_COMBAT','PERCEPTION',0.15),
 ('CLUB_COMBAT','STRENGTH_ENDURANCE',0.55),('CLUB_COMBAT','AGILITY',0.25),('CLUB_COMBAT','PERCEPTION',0.20),
 ('JAVELIN_THROWING','AIM',0.45),('JAVELIN_THROWING','STRENGTH_ENDURANCE',0.35),('JAVELIN_THROWING','AGILITY',0.20),
 ('SLING_USE','AIM',0.60),('SLING_USE','DEXTERITY',0.25),('SLING_USE','PERCEPTION',0.15),
 ('STONE_THROWING','AIM',0.60),('STONE_THROWING','STRENGTH_ENDURANCE',0.25),('STONE_THROWING','AGILITY',0.15),
 ('SCRIBING','DEXTERITY',0.40),('SCRIBING','PERCEPTION',0.25),('SCRIBING','KNOWLEDGE',0.35),
 ('CARTOGRAPHY','PERCEPTION',0.35),('CARTOGRAPHY','REASONING',0.35),('CARTOGRAPHY','DEXTERITY',0.30),
 ('LORE','KNOWLEDGE',0.45),('LORE','REASONING',0.40),('LORE','PERCEPTION',0.15),
 ('OBSERVATION','PERCEPTION',0.55),('OBSERVATION','REASONING',0.25),('OBSERVATION','KNOWLEDGE',0.20),
 ('HEALING','KNOWLEDGE',0.40),('HEALING','DEXTERITY',0.35),('HEALING','PERCEPTION',0.25),
 ('ATHLETICS','AGILITY',0.60),('ATHLETICS','STRENGTH_ENDURANCE',0.30),('ATHLETICS','PERCEPTION',0.10),
 ('NEGOTIATION','PERCEPTION',0.35),('NEGOTIATION','KNOWLEDGE',0.35),('NEGOTIATION','REASONING',0.30);

-- ---------------------------------------------------------------------------------------------------
-- WHAT ONE CHRONICLE HAS PRACTISED. Hidden, per-chronicle, and it dies with them, exactly as the
-- aptitudes already do. Nothing here is ever shown on the Body HUD — #954 asks for that explicitly and
-- SkillsStayHiddenIntegrationTest holds the line.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE chronicle_skill_practice (
    chronicle_id      UUID        NOT NULL REFERENCES chronicle(id),
    skill_key         VARCHAR(40) NOT NULL REFERENCES skill_definition(skill_key),
    proficiency       REAL        NOT NULL DEFAULT 0,
    practice_events   INTEGER     NOT NULL DEFAULT 0,
    practised_minutes INTEGER     NOT NULL DEFAULT 0,
    last_practised_at TIMESTAMPTZ,
    PRIMARY KEY (chronicle_id, skill_key),
    CONSTRAINT practice_proficiency_band CHECK (proficiency BETWEEN 0 AND 1),
    CONSTRAINT practice_counts_are_real  CHECK (practice_events >= 0 AND practised_minutes >= 0)
);

-- WHY THE PRACTICE EVENT IS A TABLE AND NOT A LOG LINE: #954 asks that a replayed request not award
-- practice twice. The code already returns early on a duplicate idempotency key, but code placement is
-- a promise and a UNIQUE index is a guarantee. One row per (action, skill) means a second award for the
-- same resolved action cannot be written even by a caller that tries.
--
-- It also carries the causal record #954 asks for — the discipline, the supporting aptitude shares, the
-- proficiency before and after, and the action it came from — which is the shape #953 generalises.
CREATE TABLE chronicle_skill_practice_event (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id       UUID        NOT NULL REFERENCES chronicle(id),
    action_id          UUID,
    occurred_at        TIMESTAMPTZ NOT NULL,
    skill_key          VARCHAR(40) NOT NULL REFERENCES skill_definition(skill_key),
    exposure_minutes   INTEGER     NOT NULL,
    difficulty         REAL        NOT NULL,
    proficiency_before REAL        NOT NULL,
    proficiency_after  REAL        NOT NULL,
    aptitude_shares    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT practice_event_has_a_span      CHECK (exposure_minutes > 0),
    CONSTRAINT practice_event_difficulty_band CHECK (difficulty BETWEEN 0 AND 1),
    CONSTRAINT practice_event_before_band     CHECK (proficiency_before BETWEEN 0 AND 1),
    CONSTRAINT practice_event_after_band      CHECK (proficiency_after  BETWEEN 0 AND 1),
    CONSTRAINT practice_never_goes_backwards  CHECK (proficiency_after >= proficiency_before),
    CONSTRAINT practice_shares_are_an_object  CHECK (jsonb_typeof(aptitude_shares) = 'object')
);
CREATE UNIQUE INDEX one_award_per_action_and_skill
    ON chronicle_skill_practice_event (action_id, skill_key) WHERE action_id IS NOT NULL;
CREATE INDEX idx_skill_practice_event_chronicle_time
    ON chronicle_skill_practice_event (chronicle_id, occurred_at);

CREATE OR REPLACE FUNCTION prevent_skill_practice_event_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'skill practice evidence is immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER skill_practice_event_is_immutable
    BEFORE UPDATE OR DELETE ON chronicle_skill_practice_event
    FOR EACH ROW EXECUTE FUNCTION prevent_skill_practice_event_mutation();

-- ---------------------------------------------------------------------------------------------------
-- WHICH SKILL AN ACT PRACTISES — first source: the intent's own impact card.
--
-- The card already carries the capability family, the labour and the footprint, so the discipline
-- belongs on it too: one statement about an act rather than four lists that can disagree. A card with
-- no skill must say why, the same way activity_impact_needs_a_reason already makes a card justify a
-- footprint of nothing.
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE activity_impact
    ADD COLUMN skill_key       VARCHAR(40) REFERENCES skill_definition(skill_key),
    ADD COLUMN no_skill_reason TEXT;

UPDATE activity_impact SET skill_key = 'WEAVING'  WHERE intent_key IN ('CRAFT_BASKET','CRAFT_NET','CRAFT_BELT');
UPDATE activity_impact SET skill_key = 'CARVING'  WHERE intent_key IN ('CRAFT_SPEAR','CRAFT_KNIFE','CRAFT_HAMMER','CRAFT_PICKAXE','CRAFT_HATCHET','CRAFT_FIRE_TOOL','CRAFT_TINDER','REFINE','REWORK');
UPDATE activity_impact SET skill_key = 'JOINERY'  WHERE intent_key IN ('CRAFT_DESK','CRAFT_CHAIR','CRAFT_SHELF','CRAFT_WORKSTATION');
UPDATE activity_impact SET skill_key = 'TAILORING' WHERE intent_key IN ('CRAFT_GARMENT','LINE_GARMENT');
UPDATE activity_impact SET skill_key = 'COOKING'  WHERE intent_key IN ('COOK_MEAT','BOIL_WATER');
UPDATE activity_impact SET skill_key = 'BUTCHERY' WHERE intent_key IN ('HARVEST_CARCASS','SLAUGHTER_STOCK');
UPDATE activity_impact SET skill_key = 'FIRECRAFT' WHERE intent_key IN ('LIGHT_FIRE','FEED_FIRE','BANK_FIRE','EXTINGUISH_FIRE','CHECK_FIRE','MAKE_CHARCOAL','CRAFT_FIRE_KIT');
UPDATE activity_impact SET skill_key = 'MATERIAL_PROCESSING' WHERE intent_key IN ('PROCESS_MATERIAL','STRIP_BARK','FILTER_WATER');
UPDATE activity_impact SET skill_key = 'CONSTRUCTION' WHERE intent_key IN ('BUILD_ALARM','BUILD_SMOKE_VENT','BUILD_FENCE','BUILD_FIRE_PIT','BUILD_FUEL_RACK','BUILD_LATRINE','BUILD_LOOKOUT','BUILD_PEN','BUILD_STORAGE_AREA','BUILD_TOOL_SHED','START_LEAN_TO','WORK_LEAN_TO','RESUME_LEAN_TO','ABANDON_LEAN_TO','ADVANCE_ASSEMBLY','PLACE_COVER','PLACE_WINDBREAK','DISMANTLE','MAKE_BED');
UPDATE activity_impact SET skill_key = 'MENDING'  WHERE intent_key IN ('REPAIR_ITEM','REPAIR_STRUCTURE','REPAIR_LEAN_TO');
UPDATE activity_impact SET skill_key = 'SCRIBING' WHERE intent_key IN ('WRITE','EDIT_DOCUMENT','MARK','DESIGNATE');
UPDATE activity_impact SET skill_key = 'CARTOGRAPHY' WHERE intent_key IN ('SKETCH_MAP','WHICH_WAY','JUDGE_WHERE_TO_GO');
UPDATE activity_impact SET skill_key = 'FORAGING' WHERE intent_key IN ('GATHER_BERRIES','GATHER_PLANT','FORAGE_GROUND','GATHER_FIBER','RAID_HIVE','RAID_NEST','COLLECT_INSECTS','JUDGE_FLORA');
UPDATE activity_impact SET skill_key = 'LOGGING'  WHERE intent_key IN ('FELL_TREE','COPPICE','GATHER_BRANCHES','CLEAR_LAND');
UPDATE activity_impact SET skill_key = 'QUARRYING' WHERE intent_key IN ('GATHER_STONE','GATHER_STONE_SLAB','GATHER_MINERAL','GATHER_CLAY');
UPDATE activity_impact SET skill_key = 'FARMING'  WHERE intent_key IN ('TILL_GROUND','SOW','HARVEST_CROP','WEED_CROP','WATER_CROP','PLANT_TREE','SCARE_BIRDS','CHECK_CROP','JUDGE_RAISING','RESTORE_HABITAT');
UPDATE activity_impact SET skill_key = 'HAULING'  WHERE intent_key IN ('PICK_UP','DROP','STORE','EQUIP','UNEQUIP','OPEN_CONTAINER','CLOSE_CONTAINER','EMPTY_CONTAINER','JUDGE_HAULAGE','COLLECT_WATER');
UPDATE activity_impact SET skill_key = 'WAYFARING' WHERE intent_key IN ('MOVE','TRAVEL','JUDGE_CROSSING');
UPDATE activity_impact SET skill_key = 'TRACKING' WHERE intent_key IN ('TRACK','SCOUT','HIDE_TRAIL','JUDGE_UNSEEN');
UPDATE activity_impact SET skill_key = 'HUNTING'  WHERE intent_key IN ('CONFRONT_WILDLIFE','SNARE','SET_TRAP','CHECK_TRAP','LURE');
UPDATE activity_impact SET skill_key = 'FISHING'  WHERE intent_key = 'FISH';
UPDATE activity_impact SET skill_key = 'HUSBANDRY' WHERE intent_key IN ('TAME','FEED_ANIMAL','TEND_ANIMAL','GROOM_ANIMAL','CHECK_STOCK','TURN_OUT_STOCK','BRING_IN_STOCK','PARE_HOOVES','TAKE_ANIMAL_YIELD','BREEDING_PROSPECTS');
UPDATE activity_impact SET skill_key = 'OBSERVATION' WHERE intent_key IN ('OBSERVE','INSPECT','EXAMINE','SEARCH','LISTEN','SMELL','FEEL','READ_THE_SKY','SENSE_BODY','MEASURE','CHECK_STANDING','TAKE_STOCK_OF_CAMP','TAKE_STOCK_OF_FOOD','TAKE_STOCK_OF_GEAR','MAINTAIN_CAMP','TAKE_UP_AGAIN','JUDGE_CLOTHING','JUDGE_FOOD','JUDGE_LIGHT','JUDGE_MAKING','JUDGE_PLACE','JUDGE_TOOLS','JUDGE_WATER','JUDGE_WORK_IN_HAND','JUDGE_CAMP_GROUND');
UPDATE activity_impact SET skill_key = 'LORE'     WHERE intent_key IN ('READ','ANALYZE','INVESTIGATE','JUDGE_LEARNING');
UPDATE activity_impact SET skill_key = 'HEALING'  WHERE intent_key IN ('TREAT_WOUND','WASH');
UPDATE activity_impact SET skill_key = 'ATHLETICS' WHERE intent_key = 'DISENGAGE';
UPDATE activity_impact SET skill_key = 'NEGOTIATION' WHERE intent_key IN ('CONTACT_PEOPLE','TRADE_WITH_PEOPLE','CONDUCT_TOWARD_PEOPLE','AGREE_WITH_PEOPLE','WORK_FOR_PEOPLE','COMPANION_PEOPLE','ADDRESS_PEOPLE','JOIN_PEOPLE','SETTLE_CLAIM');

-- And the acts in which there is genuinely no discipline to acquire. Each says so in its own words,
-- because "no skill" is a decision somebody has to have made, not an omission.
UPDATE activity_impact SET no_skill_reason =
    'Lying down and recovering is a state of the body, not a craft. It moves recovery_readiness and nothing else; there is no mastery in sleeping better.'
    WHERE intent_key IN ('REST','SLEEP','STRETCH');
UPDATE activity_impact SET no_skill_reason =
    'Getting warm, dry or cool is the body being looked after rather than a technique rehearsed. What skill there is lives in the shelter or the fire that does it.'
    WHERE intent_key IN ('WARM_BODY','DRY_BODY','COOL_BODY','SHELTER_BODY');
UPDATE activity_impact SET no_skill_reason =
    'Eating and drinking are not practised. The skill was in the cooking, the preserving or the finding, and was credited there.'
    WHERE intent_key IN ('EAT','DRINK');
UPDATE activity_impact SET no_skill_reason =
    'A bodily necessity, carried out. No discipline attaches to it and none ever will.'
    WHERE intent_key IN ('URINATE','DEFECATE','PERSONAL_ACT');
UPDATE activity_impact SET no_skill_reason =
    'Lashing out is venting, not fighting. The combat skills are reached through the weapon actually used in a real confrontation; rage at a tree teaches nothing.'
    WHERE intent_key IN ('AGGRESSION_WILDLIFE','AGGRESSION_INANIMATE');
UPDATE activity_impact SET no_skill_reason =
    'The sentence was not understood, so there is no act to have practised. Awarding anything here would pay for a misunderstanding.'
    WHERE intent_key = 'UNKNOWN';

-- A card now carries a skill or the reason it carries none — never both, and never neither. A new
-- intent added without deciding which fails here, loudly, at migration time.
ALTER TABLE activity_impact ADD CONSTRAINT activity_impact_decides_the_skill CHECK (
    (skill_key IS NOT NULL AND no_skill_reason IS NULL)
    OR (skill_key IS NULL AND length(btrim(no_skill_reason)) >= 40));

-- ---------------------------------------------------------------------------------------------------
-- SECOND SOURCE: the process actually run.
--
-- One intent, PROCESS_MATERIAL, stands behind a hundred and ninety-three catalogued processes, and the
-- CRAFT_* intents behind two hundred and ninety-seven more. Were the intent the only source, forging
-- and retting and knapping would share one number — the same flattening #954 objects to, one level
-- down. The catalogue already knows better: material_process.domain_key says textiles, stoneworking,
-- woodworking, food_preservation, fire, butchery, construction, logistics and taming.
--
-- Only the domains that genuinely name a discipline are mapped, plus forging, which the smelt/forge/
-- alloy/cast keys name directly while their domain_key says only 'tools' or 'items'. The remaining
-- processes are left NULL on purpose and fall through to their intent's skill — a guess dressed as
-- data would be worse than the honest coarser answer.
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE material_process ADD COLUMN skill_key VARCHAR(40) REFERENCES skill_definition(skill_key);

UPDATE material_process SET skill_key = 'FORGING'
    WHERE process_key ~ '^(smelt|forge|reforge|alloy|cast)_' OR process_key LIKE '%bloomery%';
UPDATE material_process SET skill_key = 'WEAVING'      WHERE skill_key IS NULL AND domain_key = 'textiles';
UPDATE material_process SET skill_key = 'KNAPPING'     WHERE skill_key IS NULL AND domain_key = 'stoneworking';
UPDATE material_process SET skill_key = 'CARVING'      WHERE skill_key IS NULL AND domain_key = 'woodworking';
UPDATE material_process SET skill_key = 'PRESERVING'   WHERE skill_key IS NULL AND domain_key = 'food_preservation';
UPDATE material_process SET skill_key = 'FIRECRAFT'    WHERE skill_key IS NULL AND domain_key = 'fire';
UPDATE material_process SET skill_key = 'BUTCHERY'     WHERE skill_key IS NULL AND domain_key = 'butchery';
UPDATE material_process SET skill_key = 'CONSTRUCTION' WHERE skill_key IS NULL AND domain_key = 'construction';
UPDATE material_process SET skill_key = 'HAULING'      WHERE skill_key IS NULL AND domain_key = 'logistics';
UPDATE material_process SET skill_key = 'HUSBANDRY'    WHERE skill_key IS NULL AND domain_key = 'taming';

-- ---------------------------------------------------------------------------------------------------
-- THIRD SOURCE: the weapon in your hands.
--
-- #954 asks that the combat disciplines be kept distinct — the spear in the hand from the spear in the
-- water, the knife in a fight from the knife on a haft, hand-throwing from the sling. weapon_profile
-- already answers which weapon a thing is; this says which discipline using it rehearses. Per item and
-- not per role, because combat_role HAND holds both the spears and the axes, which are not one skill.
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE weapon_profile ADD COLUMN combat_skill_key VARCHAR(40) REFERENCES skill_definition(skill_key);

-- SEVEN KNIVES AND NOT ONE OF THEM A WEAPON. The combat query joins weapon_profile, so a knapped flint
-- knife — a thing whose entire point is a cutting edge — could not be raised against an animal at all.
-- Declared in the item catalogue, ignored by the fight. HAND role, since a knife is used in the hand,
-- and the edge tier follows the material as the axes' do. The knife sheath is not a weapon.
INSERT INTO weapon_profile (item_key, combat_role, edge_tier) VALUES
 ('stone_knife',   'HAND', 'PLAIN'),
 ('flint_knife',   'HAND', 'HARDENED'),
 ('chert_knife',   'HAND', 'HARDENED'),
 ('obsidian_knife','HAND', 'HARDENED'),
 ('flake_knife',   'HAND', 'PLAIN'),
 ('bone_knife',    'HAND', 'PLAIN'),
 ('bronze_knife',  'HAND', 'BRONZE')
ON CONFLICT (item_key) DO NOTHING;

UPDATE weapon_profile SET combat_skill_key = 'ARCHERY'          WHERE combat_role IN ('BOW','ARROW');
UPDATE weapon_profile SET combat_skill_key = 'JAVELIN_THROWING' WHERE combat_role = 'JAVELIN';
UPDATE weapon_profile SET combat_skill_key = 'SLING_USE'        WHERE combat_role = 'SLING';
UPDATE weapon_profile SET combat_skill_key = 'STONE_THROWING'   WHERE combat_role = 'THROWN_STONE';
UPDATE weapon_profile SET combat_skill_key = 'CLUB_COMBAT'      WHERE combat_role = 'BLUNT';
UPDATE weapon_profile SET combat_skill_key = 'KNIFE_COMBAT'     WHERE combat_role = 'HAND' AND item_key LIKE '%knife%';
UPDATE weapon_profile SET combat_skill_key = 'SPEAR_COMBAT'     WHERE combat_role = 'HAND' AND item_key LIKE '%spear%';
UPDATE weapon_profile SET combat_skill_key = 'AXE_COMBAT'
    WHERE combat_role = 'HAND' AND (item_key LIKE '%axe%' OR item_key LIKE '%cleaver%');

-- Every weapon names its discipline, so a weapon added without one fails here rather than quietly
-- practising nothing when it is swung.
ALTER TABLE weapon_profile ALTER COLUMN combat_skill_key SET NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- EXISTING CHRONICLES. #954: "Define compatibility for existing Chronicles instead of fabricating
-- unsupported past proficiency."
--
-- So nothing is fabricated. A chronicle who has been alive for weeks starts every skill at zero, and
-- the aptitudes they have genuinely earned keep the values they earned — which is what carries their
-- past forward, since those aptitudes support the skills. A practised hand is therefore not reduced to
-- a beginner: their dexterity still helps the first weave. They simply have not yet been credited with
-- weaving they were never recorded as having done. No row is pre-created; the first practice makes it.
-- ---------------------------------------------------------------------------------------------------
