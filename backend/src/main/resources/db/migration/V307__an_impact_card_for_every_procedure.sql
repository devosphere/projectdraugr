-- #216 (EPIC #215) — an impact card for every procedure, and the gate that makes it mandatory.
--
-- The acceptance criterion is a single sentence: "No procedure can be activated without a complete impact card."
-- Today there is no card and no gate. What a procedure does to the land is decided in four places in Java — a
-- switch on the intent, and three recordings written inline into the dispatch lines for felling, coppicing and
-- clearing — and the ONLY procedures that mark the ground at all are these seven:
--
--     FELL_TREE          CANOPY_LOSS 25, drifting     CLEAR_LAND     CANOPY_LOSS 25, drifting
--     COPPICE            CANOPY_LOSS  8, at the point GATHER_MINERAL EXCAVATION  30, at the point
--     GATHER_STONE / GATHER_STONE_SLAB / GATHER_CLAY  EXCAVATION  15, at the point
--     MAKE_CHARCOAL      SMOKE       20, drifting     PROCESS_MATERIAL SMOKE 15, drifting, fire workings only
--
-- The other hundred and twenty-one procedures leave nothing, and not one of them ever said so. That is the
-- difference this table makes: not that the seven change (six of them do not), but that silence stops being the
-- default. A procedure with no row cannot be added, and a row cannot be written without saying, in words, what
-- the act does to the ground it is done on.
--
-- HOW THE CARD DRIVES THE WORLD, rather than describing it. ChronicleActionService reads this table once per
-- successful action and records whatever it says. There is no second list in Java to drift out of agreement with
-- it — the switch and the three inline recordings are deleted in the same commit, and
-- ActivityImpactCardIntegrationTest asserts Intent.values() and this table are the same set in both directions.
-- A capability declared in data and named as literals in code is this project's most-repeated defect; a card that
-- only documented the literals would be a fresh instance of it.
--
-- WHAT CHANGES IN PLAY. Six of the seven are preserved to the number. Three acts gain a footprint they always
-- should have had:
--
--   * TILL_GROUND breaks ground with a hoe for the better part of an hour. Scraping clay off a bank is already
--     EXCAVATION 15; turning a whole plot is not less than that.
--   * BUILD_LATRINE digs a pit. Same act, same mark.
--   * CONFRONT_WILDLIFE and AGGRESSION_WILDLIFE are a fight: shouting, a struggle, and usually blood. The
--     existing code comment claims industrial sources mark the land "beyond a fight or a felled tree" — but a
--     fight was never recorded anywhere. It is now, as COMMOTION, and it carries the way noise carries.
--
-- WHAT IS STILL SILENT AND WHY. Every silent card below says what the act physically is, and the silence has to
-- be defensible on those words: perception alters nothing it touches, bench work happens on a bench, handling
-- your own gear moves nothing but your own gear. Where the silence is genuinely arguable — a fence line, a
-- lean-to, an assembly stage — the card says so plainly rather than pretending the question was never asked.
-- Those are a later slice's argument to have, and they can now be had by reading one table instead of grepping
-- four places in Java for what is missing.

CREATE TABLE activity_impact (
    intent_key       VARCHAR(40) PRIMARY KEY,
    footprint_kind   VARCHAR(30),
    footprint_amount SMALLINT NOT NULL DEFAULT 0,
    drifts           BOOLEAN  NOT NULL DEFAULT FALSE,
    only_with_fire   BOOLEAN  NOT NULL DEFAULT FALSE,
    notes            TEXT     NOT NULL,
    CONSTRAINT activity_impact_amount_agrees CHECK (
        (footprint_kind IS NULL AND footprint_amount = 0 AND NOT drifts)
        OR (footprint_kind IS NOT NULL AND footprint_amount > 0)),
    CONSTRAINT activity_impact_needs_a_reason CHECK (length(btrim(notes)) >= 40)
);

COMMENT ON TABLE activity_impact IS
  'The impact card #216 requires: what each procedure does to the ground it is done on. Read by '
  'ChronicleActionService after a successful action; it is the only place a footprint is decided. Every value of '
  'the Intent enum must have exactly one row, including the ones that leave no mark — the notes must say why.';
COMMENT ON COLUMN activity_impact.drifts IS
  'TRUE records through recordEmissionDrift, so the mark carries onto neighbouring ground: smoke, noise and a '
  'felled canopy are all felt past the chunk they happen in. FALSE is a point source, felt only here.';
COMMENT ON COLUMN activity_impact.only_with_fire IS
  'PROCESS_MATERIAL covers every material process there is. Only the ones that burn — a smelt, a kiln firing, a '
  'forge, a charcoal char — lay a plume; cold bench work leaves nothing.';

-- ---------------------------------------------------------------------------------------------------------
-- The acts that mark the land.
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes) VALUES
  ('FELL_TREE', 'CANOPY_LOSS', 25, TRUE, FALSE,
   'A tree comes down and the hole it leaves is felt well past where it stood: light, wind and shelter all change for the ground around it.'),
  ('CLEAR_LAND', 'CANOPY_LOSS', 25, TRUE, FALSE,
   'Clearing takes the wood off a whole piece of ground at once, which is the same loss as felling and over a wider area.'),
  ('COPPICE', 'CANOPY_LOSS', 8, FALSE, FALSE,
   'Cutting a stool back is a fraction of felling and the stump lives to grow again, so the loss is small and stays where it is made.'),
  ('GATHER_MINERAL', 'EXCAVATION', 30, FALSE, FALSE,
   'Breaking a seam open is the heaviest ground-work a Chronicle does by hand, and it leaves the rock turned over where it was worked.'),
  ('GATHER_STONE', 'EXCAVATION', 15, FALSE, FALSE,
   'Lifting loose stone turns the ground over without opening it, which is half the mark that digging a seam leaves.'),
  ('GATHER_STONE_SLAB', 'EXCAVATION', 15, FALSE, FALSE,
   'Levering a slab up is the same work as lifting stone, done to one larger piece.'),
  ('GATHER_CLAY', 'EXCAVATION', 15, FALSE, FALSE,
   'Cutting clay out of a bank leaves the bank cut, and the mark is the digging rather than the clay.'),
  ('MAKE_CHARCOAL', 'SMOKE', 20, TRUE, FALSE,
   'A smothered burn smokes for hours and the plume goes where the wind takes it, which is why a charcoal camp is smelled before it is seen.'),
  ('PROCESS_MATERIAL', 'SMOKE', 15, TRUE, TRUE,
   'A smelt, a kiln firing or a forge burns hard enough to lay a plume; the cold bench work that shares this intent leaves nothing at all.'),
  -- The three that should always have had one.
  ('TILL_GROUND', 'EXCAVATION', 15, FALSE, FALSE,
   'Turning a plot over is an hour of breaking ground open. Scraping clay off a bank already counts for this much and this is not less.'),
  ('BUILD_LATRINE', 'EXCAVATION', 15, FALSE, FALSE,
   'A latrine is a pit dug and screened. The digging is the same act as cutting clay, and the ground carries it the same way.'),
  ('CONFRONT_WILDLIFE', 'COMMOTION', 20, TRUE, FALSE,
   'A fight is shouting, a struggle and usually blood, and none of it stays quiet: everything within earshot knows where it happened.'),
  ('AGGRESSION_WILDLIFE', 'COMMOTION', 20, TRUE, FALSE,
   'Going at an animal is the same commotion whether it was provoked or answered.');

-- ---------------------------------------------------------------------------------------------------------
-- The acts that leave the ground as they found it. Grouped by what they physically are, because that is what
-- has to justify the silence — one shared sentence per group, and a group only exists where the sentence is
-- true of every act in it.
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes)
SELECT k, NULL, 0, FALSE, FALSE, n FROM (VALUES

  ('Perception alters nothing it touches. Looking, listening, smelling, reading, measuring and following sign all take the world exactly as it is and put nothing back.',
   ARRAY['OBSERVE','SCOUT','INSPECT','EXAMINE','ANALYZE','INVESTIGATE','SEARCH','LISTEN','SMELL','FEEL','READ','MEASURE','TRACK','SKETCH_MAP']),

  ('Handling what is already yours moves nothing but your own gear: taking it up, setting it down, putting it on, opening the box it is in.',
   ARRAY['EQUIP','UNEQUIP','DROP','PICK_UP','STORE','OPEN_CONTAINER','CLOSE_CONTAINER']),

  ('Naming and writing happen on a surface, not on the ground. A blaze cut into bark and a name given to a place are marks a person reads, not ones the land feels.',
   ARRAY['MARK','DESIGNATE','WRITE','EDIT_DOCUMENT']),

  ('The body''s own business. Eating, drinking, resting, warming, drying, washing and dressing a wound are done to a person and cost the ground nothing. What relieving yourself leaves behind is refuse, and chunk_refuse already carries it — a second reckoning here would count the same filth twice.',
   ARRAY['EAT','DRINK','REST','SLEEP','STRETCH','WASH','WARM_BODY','DRY_BODY','COOL_BODY','SHELTER_BODY','TREAT_WOUND','URINATE','DEFECATE','PERSONAL_ACT']),

  ('Walking through country is what every animal in it also does. Crossing ground, travelling between places and breaking off contact leave a passing disturbance at most, and the passive-encounter rule already answers for being seen while you do it.',
   ARRAY['MOVE','TRAVEL','DISENGAGE']),

  ('Bench work happens on a bench. Carving, sewing, weaving, hafting, knapping, fitting and mending are quiet work done with what is already carried, and the shavings are the only thing they put down.',
   ARRAY['CRAFT_BASKET','CRAFT_SPEAR','CRAFT_KNIFE','CRAFT_HAMMER','CRAFT_PICKAXE','CRAFT_HATCHET','CRAFT_FIRE_KIT','CRAFT_TINDER','CRAFT_DESK','CRAFT_CHAIR','CRAFT_SHELF','CRAFT_WORKSTATION','CRAFT_NET','CRAFT_BELT','CRAFT_GARMENT','CRAFT_FIRE_TOOL','REFINE','REWORK','REPAIR_ITEM','MAKE_BED']),

  ('Taking what grows where it grows. Picking, cutting fibre, pulling roots, stripping bark, lifting a comb and scooping water all take a season''s surplus off ground that keeps standing — the depletion is the plant''s to bear, and flora regrowth already carries it.',
   ARRAY['GATHER_FIBER','GATHER_BERRIES','GATHER_BRANCHES','GATHER_PLANT','FORAGE_GROUND','STRIP_BARK','RAID_HIVE','COLLECT_INSECTS','COLLECT_WATER','FILTER_WATER']),

  ('Working a plot that is already broken. Sowing, weeding, reaping and setting a sapling are done to ground that has had the ground-breaking done to it, and the tilling is where that reckoning belongs.',
   ARRAY['SOW','HARVEST_CROP','WEED_CROP','PLANT_TREE']),

  ('Habitat work is the opposite of a footprint. Restoring ground is already handled as a reduction of what was done to it; recording a fresh disturbance for it would have a Chronicle damage the place by mending it.',
   ARRAY['RESTORE_HABITAT']),

  ('A hearth already lit. Feeding, banking and putting out a fire tend a burn that is already there, and cooking a meal or boiling a pot is that hearth doing its work — the fire is the source, not the hand that keeps it going.',
   ARRAY['LIGHT_FIRE','FEED_FIRE','BANK_FIRE','EXTINGUISH_FIRE','COOK_MEAT','BOIL_WATER']),

  ('Quiet dealings with animals. Feeding, gentling, luring, milking and dosing a beast are done close and slow, because none of them work if they are not — a struggle would not be this intent.',
   ARRAY['FEED_ANIMAL','TAME','LURE','TAKE_ANIMAL_YIELD','TEND_ANIMAL']),

  ('Taking by patience rather than by force. A line, a snare and a set trap all work because nothing about them announces itself, and walking a trapline is the quietest round a Chronicle makes.',
   ARRAY['FISH','SNARE','SET_TRAP','CHECK_TRAP']),

  ('Butchering is done over a kill that is already down, and its own consequence is the smell of it — carcass scent draws what it draws, and that is a stronger and more specific answer than a disturbance figure.',
   ARRAY['HARVEST_CARCASS']),

  ('Camp fabric: a windbreak propped up, a cover thrown over, a camp tidied, a shelter left or taken up again. These are hours of quiet arranging on ground a Chronicle is already living on, and the ground was marked when the camp was made.',
   ARRAY['PLACE_WINDBREAK','PLACE_COVER','MAINTAIN_CAMP','ABANDON_LEAN_TO','RESUME_LEAN_TO','REPAIR_LEAN_TO','REPAIR_STRUCTURE']),

  ('Building in a camp already in use. Setting posts, lashing a frame, raising a rack or a screen and taking one down again are slow, mostly quiet work at a place whose ground is already worked. This is the most arguable silence on this table and it is written as an argument rather than an oversight: a fence line and an assembly stage may well deserve a mark, and the case can now be made by editing one row.',
   ARRAY['BUILD_FIRE_PIT','BUILD_ALARM','BUILD_FENCE','BUILD_PEN','BUILD_LOOKOUT','BUILD_FUEL_RACK','BUILD_TOOL_SHED','BUILD_SMOKE_VENT','BUILD_STORAGE_AREA','START_LEAN_TO','WORK_LEAN_TO','DISMANTLE','ADVANCE_ASSEMBLY']),

  ('Striking at a thing rather than a creature. It hurts the thing and the person swinging, and the ground is not party to it.',
   ARRAY['AGGRESSION_INANIMATE']),

  ('Not a procedure at all. UNKNOWN is what the classifier returns when it recognises nothing, and an act the world could not understand cannot have done anything to it.',
   ARRAY['UNKNOWN'])

) AS grouped(n, keys), LATERAL unnest(grouped.keys) k;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Behaviour preservation, asserted rather than trusted: the six cards that must be identical to what the Java
  -- switch and the inline recordings did before this migration. If any of these drifts, a world that has been
  -- played for months starts marking its ground differently and nothing else would say so.
  SELECT string_agg(intent_key, ', ') INTO bad FROM (VALUES
      ('FELL_TREE','CANOPY_LOSS',25,TRUE), ('CLEAR_LAND','CANOPY_LOSS',25,TRUE), ('COPPICE','CANOPY_LOSS',8,FALSE),
      ('GATHER_MINERAL','EXCAVATION',30,FALSE), ('MAKE_CHARCOAL','SMOKE',20,TRUE), ('PROCESS_MATERIAL','SMOKE',15,TRUE)
    ) AS was(intent_key, kind, amount, drifts)
   WHERE NOT EXISTS (SELECT 1 FROM activity_impact a WHERE a.intent_key = was.intent_key
                       AND a.footprint_kind = was.kind AND a.footprint_amount = was.amount AND a.drifts = was.drifts);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V307: these cards do not match what the code did before: %', bad; END IF;

  SELECT count(*) INTO n FROM activity_impact
   WHERE intent_key IN ('GATHER_STONE','GATHER_STONE_SLAB','GATHER_CLAY')
     AND footprint_kind = 'EXCAVATION' AND footprint_amount = 15 AND NOT drifts;
  IF n <> 3 THEN RAISE EXCEPTION 'V307: the three hand-gathering cards must stay at EXCAVATION 15, point source'; END IF;

  -- only_with_fire belongs to PROCESS_MATERIAL alone. On any other card it would be a condition nothing checks,
  -- which is the declared-but-ignored shape this table exists to stop.
  SELECT string_agg(intent_key, ', ') INTO bad FROM activity_impact WHERE only_with_fire AND intent_key <> 'PROCESS_MATERIAL';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V307: only_with_fire is only ever checked for PROCESS_MATERIAL: %', bad; END IF;

  -- A source kind has to fit the column it is written into, or the recording fails at the moment it matters —
  -- and drift appends "_DRIFT" to it, so that is the length that has to fit.
  SELECT string_agg(DISTINCT footprint_kind, ', ') INTO bad FROM activity_impact
   WHERE footprint_kind IS NOT NULL AND length(footprint_kind) + 6 > 40;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V307: these source kinds are too long once _DRIFT is appended: %', bad; END IF;

  -- The gate itself is in Java, where the enum can be read: ActivityImpactCardIntegrationTest asserts that
  -- Intent.values() and this table are the same set, in both directions. What can be checked here is that the
  -- count is the one that test expects to find, so a row lost to a bad edit is caught before the suite runs.
  SELECT count(*) INTO n FROM activity_impact;
  IF n <> 128 THEN RAISE EXCEPTION 'V307: expected a card for each of the 128 intents, found %', n; END IF;
END $$;
