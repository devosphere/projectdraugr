-- #216 (EPIC #215) — the impact card carries what the act costs the actor, not only what it costs the ground.
--
-- V307 put a card against every procedure for its FOOTPRINT and built the gate that makes a card mandatory. The
-- ticket's specification names more than that: "actor state, inputs, process footprint, outputs/waste, affected
-- world state, time/neglect, and recovery". Two of those were already decided, and decided the same way the
-- footprint was — by a switch over the same enum, in the same file, that nothing outside that file could read:
--
--   * laborOf(intent)            what the act costs in energy and hygiene beyond the passive tick (#27)
--   * capabilityDomainOf(intent) which mastery repeating it builds (#26): LOAD, AIM, ATTENTION, FINE_MOTOR, ...
--
-- Both move onto the card. Nothing about play changes — the values below were extracted from those two switches
-- mechanically rather than retyped, and the guard at the foot asserts the resulting tier and family counts
-- against the ones the switches produced, so a transcription slip could not have passed quietly.
--
-- WHY THIS IS WORTH DOING when the switches worked. Three things the switch shape cannot do and the card can:
--
--   1. A new intent could be added to any ONE of the three and forgotten by the other two. The switches all end
--      in a `default`, so forgetting is silent and looks deliberate — an act that costs nothing, builds nothing
--      and marks nothing reads exactly like an act that was decided to be free. The gate makes forgetting loud.
--   2. Nothing outside ChronicleActionService could ask what an act costs. Every question about the shape of a
--      Chronicle's work — which acts are heavy, which build which mastery — meant reading a switch.
--   3. The card can be READ ALONGSIDE ITSELF. "This act breaks ground, costs 12 energy, and builds LOAD" is one
--      row now; before, it was three switches that could disagree with each other and never be caught at it.
--
-- The `default` arms are preserved exactly and written out per intent rather than left implicit, because that
-- is the whole point: 22 intents cost the body nothing and 62 build FINE_MOTOR, and each of those is now a
-- recorded decision instead of the absence of one.

ALTER TABLE activity_impact
    ADD COLUMN labor_energy      SMALLINT,
    ADD COLUMN labor_hygiene     SMALLINT,
    ADD COLUMN capability_domain VARCHAR(20);

COMMENT ON COLUMN activity_impact.labor_energy IS
  'Energy spent by the act itself, beyond what the passive metabolic tick takes (#27). Zero for the acts that '
  'run their own physiology — eating, drinking, sleeping, relieving yourself, fighting.';
COMMENT ON COLUMN activity_impact.labor_hygiene IS
  'Cleanliness lost to the act. Heavy dirty labour costs 8, steady work 3, light handling none.';
COMMENT ON COLUMN activity_impact.capability_domain IS
  'The mastery repeating this act builds (#26): LOAD, AIM, ATTENTION, LOCOMOTION, RECOVERY, KNOWLEDGE, INSIGHT '
  'or FINE_MOTOR. One family per act; the growth is slow, hidden and lifelong.';

UPDATE activity_impact a SET labor_energy = c.energy, labor_hygiene = c.hygiene, capability_domain = c.domain
  FROM (VALUES
  ('ABANDON_LEAN_TO', 0, 0, 'FINE_MOTOR'),
  ('ADVANCE_ASSEMBLY', 12, 8, 'FINE_MOTOR'),
  ('AGGRESSION_INANIMATE', 0, 0, 'FINE_MOTOR'),
  ('AGGRESSION_WILDLIFE', 0, 0, 'FINE_MOTOR'),
  ('ANALYZE', 2, 0, 'INSIGHT'),
  ('BANK_FIRE', 2, 0, 'FINE_MOTOR'),
  ('BOIL_WATER', 2, 0, 'FINE_MOTOR'),
  ('BUILD_ALARM', 6, 3, 'FINE_MOTOR'),
  ('BUILD_FENCE', 12, 8, 'LOAD'),
  ('BUILD_FIRE_PIT', 12, 8, 'LOAD'),
  ('BUILD_FUEL_RACK', 12, 8, 'LOAD'),
  ('BUILD_LATRINE', 12, 8, 'LOAD'),
  ('BUILD_LOOKOUT', 12, 8, 'LOAD'),
  ('BUILD_PEN', 12, 8, 'LOAD'),
  ('BUILD_SMOKE_VENT', 6, 3, 'FINE_MOTOR'),
  ('BUILD_STORAGE_AREA', 12, 8, 'LOAD'),
  ('BUILD_TOOL_SHED', 12, 8, 'LOAD'),
  ('CHECK_TRAP', 6, 3, 'AIM'),
  ('CLEAR_LAND', 12, 8, 'LOAD'),
  ('CLOSE_CONTAINER', 2, 0, 'FINE_MOTOR'),
  ('COLLECT_INSECTS', 6, 3, 'LOAD'),
  ('COLLECT_WATER', 2, 0, 'FINE_MOTOR'),
  ('CONFRONT_WILDLIFE', 12, 8, 'AIM'),
  ('COOK_MEAT', 6, 3, 'FINE_MOTOR'),
  ('COOL_BODY', 0, 0, 'RECOVERY'),
  -- 6/3, not the 0/0 the switch gave it. See the note at the foot: coppicing was in none of laborOf's three
  -- tiers, so cutting a stool back with an axe cost a Chronicle nothing at all. The guard below found it.
  ('COPPICE', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_BASKET', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_BELT', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_CHAIR', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_DESK', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_FIRE_KIT', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_FIRE_TOOL', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_GARMENT', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_HAMMER', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_HATCHET', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_KNIFE', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_NET', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_PICKAXE', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_SHELF', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_SPEAR', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_TINDER', 6, 3, 'FINE_MOTOR'),
  ('CRAFT_WORKSTATION', 6, 3, 'FINE_MOTOR'),
  ('DEFECATE', 0, 0, 'FINE_MOTOR'),
  ('DESIGNATE', 2, 0, 'FINE_MOTOR'),
  ('DISENGAGE', 0, 0, 'LOCOMOTION'),
  ('DISMANTLE', 12, 8, 'LOAD'),
  ('DRINK', 0, 0, 'FINE_MOTOR'),
  ('DROP', 2, 0, 'FINE_MOTOR'),
  ('DRY_BODY', 0, 0, 'RECOVERY'),
  ('EAT', 0, 0, 'FINE_MOTOR'),
  ('EDIT_DOCUMENT', 2, 0, 'FINE_MOTOR'),
  ('EQUIP', 2, 0, 'FINE_MOTOR'),
  ('EXAMINE', 2, 0, 'ATTENTION'),
  ('EXTINGUISH_FIRE', 2, 0, 'FINE_MOTOR'),
  ('FEED_ANIMAL', 6, 3, 'FINE_MOTOR'),
  ('FEED_FIRE', 2, 0, 'FINE_MOTOR'),
  ('FEEL', 2, 0, 'ATTENTION'),
  ('FELL_TREE', 12, 8, 'LOAD'),
  ('FILTER_WATER', 2, 0, 'FINE_MOTOR'),
  ('FISH', 6, 3, 'AIM'),
  ('FORAGE_GROUND', 6, 3, 'LOAD'),
  ('GATHER_BERRIES', 6, 3, 'LOAD'),
  ('GATHER_BRANCHES', 6, 3, 'LOAD'),
  ('GATHER_CLAY', 12, 8, 'LOAD'),
  ('GATHER_FIBER', 6, 3, 'LOAD'),
  ('GATHER_MINERAL', 12, 8, 'LOAD'),
  ('GATHER_PLANT', 6, 3, 'LOAD'),
  ('GATHER_STONE', 12, 8, 'LOAD'),
  ('GATHER_STONE_SLAB', 12, 8, 'LOAD'),
  ('HARVEST_CARCASS', 12, 8, 'LOAD'),
  ('HARVEST_CROP', 6, 3, 'LOAD'),
  ('INSPECT', 2, 0, 'ATTENTION'),
  ('INVESTIGATE', 2, 0, 'KNOWLEDGE'),
  ('LIGHT_FIRE', 6, 3, 'FINE_MOTOR'),
  ('LISTEN', 2, 0, 'ATTENTION'),
  ('LURE', 6, 3, 'AIM'),
  ('MAINTAIN_CAMP', 6, 3, 'ATTENTION'),
  ('MAKE_BED', 6, 3, 'FINE_MOTOR'),
  ('MAKE_CHARCOAL', 6, 3, 'FINE_MOTOR'),
  ('MARK', 2, 0, 'FINE_MOTOR'),
  ('MEASURE', 2, 0, 'ATTENTION'),
  ('MOVE', 6, 3, 'LOCOMOTION'),
  ('OBSERVE', 2, 0, 'ATTENTION'),
  ('OPEN_CONTAINER', 2, 0, 'FINE_MOTOR'),
  ('PERSONAL_ACT', 0, 0, 'FINE_MOTOR'),
  ('PICK_UP', 2, 0, 'FINE_MOTOR'),
  ('PLACE_COVER', 6, 3, 'LOAD'),
  ('PLACE_WINDBREAK', 6, 3, 'LOAD'),
  ('PLANT_TREE', 6, 3, 'LOAD'),
  ('PROCESS_MATERIAL', 12, 8, 'FINE_MOTOR'),
  ('RAID_HIVE', 6, 3, 'LOAD'),
  ('READ', 2, 0, 'KNOWLEDGE'),
  ('REFINE', 6, 3, 'FINE_MOTOR'),
  ('REPAIR_ITEM', 6, 3, 'FINE_MOTOR'),
  ('REPAIR_LEAN_TO', 12, 8, 'LOAD'),
  ('REPAIR_STRUCTURE', 12, 8, 'LOAD'),
  ('REST', 0, 0, 'RECOVERY'),
  ('RESTORE_HABITAT', 6, 3, 'LOAD'),
  ('RESUME_LEAN_TO', 0, 0, 'FINE_MOTOR'),
  ('REWORK', 6, 3, 'FINE_MOTOR'),
  ('SCOUT', 2, 0, 'ATTENTION'),
  ('SEARCH', 2, 0, 'ATTENTION'),
  ('SET_TRAP', 6, 3, 'AIM'),
  ('SHELTER_BODY', 0, 0, 'RECOVERY'),
  ('SKETCH_MAP', 2, 0, 'FINE_MOTOR'),
  ('SLEEP', 0, 0, 'RECOVERY'),
  ('SMELL', 2, 0, 'ATTENTION'),
  ('SNARE', 6, 3, 'AIM'),
  ('SOW', 6, 3, 'LOAD'),
  ('START_LEAN_TO', 12, 8, 'LOAD'),
  ('STORE', 2, 0, 'FINE_MOTOR'),
  ('STRETCH', 0, 0, 'RECOVERY'),
  ('STRIP_BARK', 6, 3, 'FINE_MOTOR'),
  ('TAKE_ANIMAL_YIELD', 0, 0, 'FINE_MOTOR'),
  ('TAME', 6, 3, 'AIM'),
  ('TEND_ANIMAL', 0, 0, 'FINE_MOTOR'),
  ('TILL_GROUND', 12, 8, 'LOAD'),
  ('TRACK', 6, 3, 'ATTENTION'),
  ('TRAVEL', 6, 3, 'LOCOMOTION'),
  ('TREAT_WOUND', 2, 0, 'FINE_MOTOR'),
  ('UNEQUIP', 2, 0, 'FINE_MOTOR'),
  ('UNKNOWN', 0, 0, 'FINE_MOTOR'),
  ('URINATE', 0, 0, 'FINE_MOTOR'),
  ('WARM_BODY', 0, 0, 'RECOVERY'),
  ('WASH', 0, 0, 'FINE_MOTOR'),
  ('WEED_CROP', 6, 3, 'LOAD'),
  ('WORK_LEAN_TO', 12, 8, 'LOAD'),
  ('WRITE', 2, 0, 'FINE_MOTOR')
  ) AS c(intent_key, energy, hygiene, domain)
 WHERE a.intent_key = c.intent_key;

ALTER TABLE activity_impact
    ALTER COLUMN labor_energy      SET NOT NULL,
    ALTER COLUMN labor_hygiene     SET NOT NULL,
    ALTER COLUMN capability_domain SET NOT NULL,
    ADD CONSTRAINT activity_impact_labor_is_sane CHECK (labor_energy BETWEEN 0 AND 40 AND labor_hygiene BETWEEN 0 AND 40),
    ADD CONSTRAINT activity_impact_domain_is_known CHECK (capability_domain IN
        ('LOAD','AIM','ATTENTION','LOCOMOTION','RECOVERY','KNOWLEDGE','INSIGHT','FINE_MOTOR'));

DO $$
DECLARE n int; bad text;
BEGIN
  -- Transcription, asserted by shape rather than by trust. These four counts are what laborOf produced for the
  -- 128 intents, and these eight are what capabilityDomainOf produced. A slip in extracting either switch moves
  -- one of these numbers, and a hand-retyped table would have moved several.
  SELECT count(*) INTO n FROM activity_impact WHERE labor_energy = 12 AND labor_hygiene = 8;
  IF n <> 24 THEN RAISE EXCEPTION 'V309: heavy labour should cover 24 intents, covers %', n; END IF;
  -- 52 and 21, where the switch produced 51 and 22. The difference is COPPICE, moved deliberately; see the note
  -- at the foot of this file.
  SELECT count(*) INTO n FROM activity_impact WHERE labor_energy = 6 AND labor_hygiene = 3;
  IF n <> 52 THEN RAISE EXCEPTION 'V309: steady work should cover 52 intents, covers %', n; END IF;
  SELECT count(*) INTO n FROM activity_impact WHERE labor_energy = 2 AND labor_hygiene = 0;
  IF n <> 31 THEN RAISE EXCEPTION 'V309: light acts should cover 31 intents, covers %', n; END IF;
  SELECT count(*) INTO n FROM activity_impact WHERE labor_energy = 0 AND labor_hygiene = 0;
  IF n <> 21 THEN RAISE EXCEPTION 'V309: the acts that cost the body nothing should number 21, number %', n; END IF;

  SELECT string_agg(capability_domain || '=' || cnt, ', ' ORDER BY capability_domain) INTO bad
    FROM (SELECT capability_domain, count(*) AS cnt FROM activity_impact GROUP BY 1) d
   WHERE (capability_domain, cnt) NOT IN
     (('LOAD',35),('AIM',7),('ATTENTION',11),('LOCOMOTION',3),('RECOVERY',7),('KNOWLEDGE',2),('INSIGHT',1),('FINE_MOTOR',62));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V309: these capability families do not match what the switch produced: %', bad; END IF;

  -- Dirty work is heavy work. Hygiene is lost to the kind of labour that puts you in the mud, and a card that
  -- costs cleanliness without costing effort would be describing an act nobody performs.
  SELECT string_agg(intent_key, ', ') INTO bad FROM activity_impact WHERE labor_hygiene > 0 AND labor_energy = 0;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V309: these cost cleanliness without costing effort: %', bad; END IF;

  -- An act that marks the land must cost the body something, or the world is being changed by work nobody is
  -- doing. This is the check that made putting the two switches on one card worth the trouble: it can only be
  -- asked when the footprint and the labour are the same row, and it found two answers the moment it was asked.
  --
  -- COPPICE was in none of laborOf's three tiers. It fell to the default and cutting a stool back with an axe
  -- cost a Chronicle nothing — not tiring, not dirtying — while still taking the canopy down by 8. That is a
  -- real defect, not a decision, and nothing could have seen it while the footprint and the labour lived in
  -- different switches in different parts of the file. It is 6/3 above, the steady-work tier that already holds
  -- STRIP_BARK and MAKE_CHARCOAL, which is the company coppicing belongs in — lighter than felling, not free.
  -- Its capability family is deliberately NOT touched: which mastery axe work builds is a separate judgement
  -- with no defect proving it, and the family counts above still match the switch exactly.
  --
  -- AGGRESSION_WILDLIFE is the real exception and is exempted by name. laborOf's own comment says the aggression
  -- acts "run their own physiology" — a fight resolves its own exhaustion and its own injuries — so a labour
  -- figure here would be charging the body twice for one struggle. The footprint it gained in V307 is about the
  -- noise, which is a different thing again.
  SELECT string_agg(intent_key, ', ') INTO bad FROM activity_impact
   WHERE footprint_kind IS NOT NULL AND labor_energy = 0 AND intent_key <> 'AGGRESSION_WILDLIFE';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V309: these mark the land while costing the body nothing: %', bad; END IF;
END $$;
