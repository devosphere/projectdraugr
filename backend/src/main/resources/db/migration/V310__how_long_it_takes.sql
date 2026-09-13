-- #216 (EPIC #215) — the last of the three switches: how long an act takes.
--
-- V307 put the FOOTPRINT on the card, V309 the LABOUR and the CAPABILITY FAMILY. `durationFor` is the fourth
-- and last switch over the Intent enum in ChronicleActionService, and #216's specification names "time/neglect"
-- among the things a complete impact card carries. It is also the biggest: 90 case arms covering 115 intents,
-- with the remaining 13 falling to a five-minute default.
--
-- WHAT STAYS IN JAVA. Only the per-intent default moves. The rule above it does not: a player who writes an
-- explicit span — "rest for two hours", "work for 40 minutes" — gets that span, clamped to a day, and the card
-- is consulted only when they did not say. That belongs in code because it is parsing, not catalogue.
--
-- NOTHING CHANGES. The 128 values were extracted from the switch mechanically rather than retyped, and the
-- guard asserts the resulting distribution against the one the switch produced — fourteen distinct durations,
-- each covering the same number of intents as before. A slip moves one of those counts.
--
-- AND THE CARD BECOMES ANSWERABLE AS A WHOLE. With time on the same row as the effort and the mark, the three
-- can finally be asked about together, and the guard at the foot asks the obvious question: does anything take
-- real time and cost the body nothing while changing the world? That is the question that found COPPICE in
-- V309, and it is worth keeping asked as each new column arrives.

ALTER TABLE activity_impact ADD COLUMN duration_minutes SMALLINT;

COMMENT ON COLUMN activity_impact.duration_minutes IS
  'How long the act takes when the player does not say. An explicit span in the action text wins — that is '
  'parsing, and stays in ChronicleActionService. Five minutes is the figure for acts that are over as soon as '
  'they are begun; 480 is a night''s sleep.';

UPDATE activity_impact a SET duration_minutes = c.minutes
  FROM (VALUES
  ('ABANDON_LEAN_TO', 5),
  ('ADVANCE_ASSEMBLY', 45),
  ('AGGRESSION_INANIMATE', 1),
  ('AGGRESSION_WILDLIFE', 5),
  ('ANALYZE', 10),
  ('BANK_FIRE', 10),
  ('BOIL_WATER', 20),
  ('BUILD_ALARM', 25),
  ('BUILD_FENCE', 40),
  ('BUILD_FIRE_PIT', 30),
  ('BUILD_FUEL_RACK', 35),
  ('BUILD_LATRINE', 40),
  ('BUILD_LOOKOUT', 45),
  ('BUILD_PEN', 50),
  ('BUILD_SMOKE_VENT', 30),
  ('BUILD_STORAGE_AREA', 50),
  ('BUILD_TOOL_SHED', 60),
  ('CHECK_TRAP', 10),
  ('CLEAR_LAND', 60),
  ('CLOSE_CONTAINER', 5),
  ('COLLECT_INSECTS', 20),
  ('COLLECT_WATER', 10),
  ('CONFRONT_WILDLIFE', 10),
  ('COOK_MEAT', 10),
  ('COOL_BODY', 10),
  ('COPPICE', 35),
  ('CRAFT_BASKET', 45),
  ('CRAFT_BELT', 40),
  ('CRAFT_CHAIR', 40),
  ('CRAFT_DESK', 60),
  ('CRAFT_FIRE_KIT', 25),
  ('CRAFT_FIRE_TOOL', 30),
  ('CRAFT_GARMENT', 90),
  ('CRAFT_HAMMER', 5),
  ('CRAFT_HATCHET', 35),
  ('CRAFT_KNIFE', 5),
  ('CRAFT_NET', 90),
  ('CRAFT_PICKAXE', 5),
  ('CRAFT_SHELF', 50),
  ('CRAFT_SPEAR', 35),
  ('CRAFT_TINDER', 10),
  ('CRAFT_WORKSTATION', 60),
  ('DEFECATE', 5),
  ('DESIGNATE', 10),
  ('DISENGAGE', 10),
  ('DISMANTLE', 45),
  ('DRINK', 5),
  ('DROP', 5),
  ('DRY_BODY', 10),
  ('EAT', 5),
  ('EDIT_DOCUMENT', 15),
  ('EQUIP', 5),
  ('EXAMINE', 5),
  ('EXTINGUISH_FIRE', 5),
  ('FEED_ANIMAL', 10),
  ('FEED_FIRE', 5),
  ('FEEL', 5),
  ('FELL_TREE', 60),
  ('FILTER_WATER', 15),
  ('FISH', 45),
  ('FORAGE_GROUND', 20),
  ('GATHER_BERRIES', 25),
  ('GATHER_BRANCHES', 25),
  ('GATHER_CLAY', 20),
  ('GATHER_FIBER', 25),
  ('GATHER_MINERAL', 40),
  ('GATHER_PLANT', 20),
  ('GATHER_STONE', 25),
  ('GATHER_STONE_SLAB', 30),
  ('HARVEST_CARCASS', 10),
  ('HARVEST_CROP', 45),
  ('INSPECT', 5),
  ('INVESTIGATE', 15),
  ('LIGHT_FIRE', 20),
  ('LISTEN', 5),
  ('LURE', 10),
  ('MAINTAIN_CAMP', 25),
  ('MAKE_BED', 20),
  ('MAKE_CHARCOAL', 10),
  ('MARK', 15),
  ('MEASURE', 10),
  ('MOVE', 30),
  ('OBSERVE', 10),
  ('OPEN_CONTAINER', 5),
  ('PERSONAL_ACT', 20),
  ('PICK_UP', 5),
  ('PLACE_COVER', 20),
  ('PLACE_WINDBREAK', 20),
  ('PLANT_TREE', 25),
  ('PROCESS_MATERIAL', 45),
  ('RAID_HIVE', 15),
  ('READ', 15),
  ('REFINE', 30),
  ('REPAIR_ITEM', 20),
  -- 35, not the 5 the switch gave it. durationFor had no arm for REPAIR_LEAN_TO at all, so mending a shelter
  -- fell to the five-minute default — while laborOf had it in the HEAVY tier at 12 energy and 8 hygiene. Heavy,
  -- dirty labour done in five minutes. 35 is what REPAIR_STRUCTURE takes, which is the same act on a bigger
  -- building. See the note at the foot.
  ('REPAIR_LEAN_TO', 35),
  ('REPAIR_STRUCTURE', 35),
  ('REST', 60),
  ('RESTORE_HABITAT', 45),
  ('RESUME_LEAN_TO', 5),
  ('REWORK', 30),
  ('SCOUT', 20),
  ('SEARCH', 10),
  ('SET_TRAP', 35),
  ('SHELTER_BODY', 10),
  ('SKETCH_MAP', 30),
  ('SLEEP', 480),
  ('SMELL', 5),
  ('SNARE', 25),
  ('SOW', 40),
  ('START_LEAN_TO', 30),
  ('STORE', 5),
  ('STRETCH', 5),
  ('STRIP_BARK', 15),
  ('TAKE_ANIMAL_YIELD', 15),
  ('TAME', 30),
  ('TEND_ANIMAL', 20),
  ('TILL_GROUND', 50),
  ('TRACK', 25),
  ('TRAVEL', 5),
  ('TREAT_WOUND', 10),
  ('UNEQUIP', 5),
  ('UNKNOWN', 5),
  ('URINATE', 5),
  ('WARM_BODY', 10),
  ('WASH', 5),
  ('WEED_CROP', 35),
  ('WORK_LEAN_TO', 45),
  ('WRITE', 15)
  ) AS c(intent_key, minutes)
 WHERE a.intent_key = c.intent_key;

ALTER TABLE activity_impact
    ALTER COLUMN duration_minutes SET NOT NULL,
    ADD CONSTRAINT activity_impact_duration_is_sane CHECK (duration_minutes BETWEEN 1 AND 1440);

DO $$
DECLARE bad text; n int;
BEGIN
  -- Transcription asserted by shape. This is the distribution durationFor produced over the 128 intents, the
  -- 13 default arms included. A slip in extracting 90 case arms moves one of these counts, and a hand-retyped
  -- table would have moved several.
  SELECT string_agg(duration_minutes || 'min=' || cnt, ', ' ORDER BY duration_minutes) INTO bad
    FROM (SELECT duration_minutes, count(*) AS cnt FROM activity_impact GROUP BY 1) d
   WHERE (duration_minutes, cnt) NOT IN
  -- 5min=28 and 35min=8, where the switch gave 29 and 7. The difference is REPAIR_LEAN_TO, moved deliberately;
  -- see the note at the foot of this file.
     ((1,1),(5,28),(10,21),(15,9),(20,13),(25,10),(30,10),(35,8),(40,6),(45,9),(50,4),(60,6),(90,2),(480,1));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V310: these durations do not match what the switch produced: %', bad; END IF;
  SELECT count(DISTINCT duration_minutes) INTO n FROM activity_impact;
  IF n <> 14 THEN RAISE EXCEPTION 'V310: the switch produced 14 distinct durations, the table has %', n; END IF;

  -- The question the whole card exists to make askable, asked again now that time is on it: an act that takes
  -- real time and changes the world must cost the body something. AGGRESSION_WILDLIFE is the standing exception
  -- and the reason is in V309 — a fight runs its own physiology and would otherwise be charged twice.
  SELECT string_agg(intent_key, ', ') INTO bad FROM activity_impact
   WHERE footprint_kind IS NOT NULL AND labor_energy = 0 AND duration_minutes >= 10
     AND intent_key <> 'AGGRESSION_WILDLIFE';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V310: these take real time, mark the land, and tire nobody: %', bad; END IF;

  -- Heavy labour is not instant. An act costing 12 energy and 8 hygiene is swinging, hauling or breaking ground,
  -- and none of that is done in five minutes — a heavy card with a trivial duration is one of the two numbers
  -- being wrong, and until now they lived too far apart to be compared.
  --
  -- This guard fired on its first run, on REPAIR_LEAN_TO, and it is the same defect V309 found in COPPICE
  -- wearing different clothes: durationFor had no arm for it, so mending a shelter fell into the five-minute
  -- default while laborOf had it in the HEAVY tier. Twelve energy and eight hygiene spent in five minutes. It is
  -- 35 above, which is what REPAIR_STRUCTURE takes — the same act on a bigger building.
  --
  -- Both defects are the same shape and it is worth naming: a `default` arm makes forgetting SILENT, and four
  -- switches over one enum means four chances to forget an intent independently. Neither COPPICE nor
  -- REPAIR_LEAN_TO could be seen from inside the switch that was wrong about them — only from the other one.
  SELECT string_agg(intent_key || ' (' || duration_minutes || 'min)', ', ') INTO bad
    FROM activity_impact WHERE labor_energy >= 12 AND duration_minutes < 10;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V310: these are heavy labour done in under ten minutes: %', bad; END IF;

  -- And rest is the one thing that must take real time, or a Chronicle recovers a night's sleep in a moment.
  SELECT string_agg(intent_key || ' (' || duration_minutes || 'min)', ', ') INTO bad
    FROM activity_impact WHERE capability_domain = 'RECOVERY' AND duration_minutes < 5;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V310: recovery cannot be instant: %', bad; END IF;
END $$;
