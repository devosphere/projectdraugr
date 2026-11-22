-- V477: A BRONZE SHORT SWORD (GitHub #966, under EPIC #964).
--
-- #965 gave the epic its first two swords, carved from wood and edged with stone. This is the first sword of
-- METAL, and the ticket is explicit that it must not be what every earlier bronze object was: one ingot in,
-- one finished thing out, "forge a bronze knife" in a single 45-minute act. A sword is not made that way and
-- never was. It is cast in a mould, its casting is worked into a blade, and the blade is hilted. Each of those
-- is its own act here, each with its own inputs, tool, heat and time, so a Chronicle making one walks the
-- same road a Bronze Age smith walked.
--
-- THE CHAIN, from what the world already gives:
--
--   tin_ore (MINERAL, scarce, highland rock) --smelt_tin------\
--   copper_ore (MINERAL)                     --smelt_copper---+--alloy_bronze--> bronze_ingot (900 g)
--   charcoal (V163 clamp)                    -----------------/
--
--   clay_lump x2  --make_sword_mould (fire, CUTTING, 90 min)-->  sword_mould          1900 g
--   bronze_ingot + sword_mould + charcoal x2
--                 --cast_bronze_sword (fire, 60 min)--------->  bronze_sword_casting  760 g
--   bronze_sword_casting
--                 --forge_sword_edges (STRIKING, 150 min)---->  bronze_sword_blade    640 g
--   bronze_sword_blade + hilt (branch | bone | antler) + grip (sinew | cordage | bast)
--                 --hilt_bronze_sword (CUTTING, 75 min)------>  bronze_short_sword    760 g
--
-- Every upstream link already existed and was already reachable: tin_ore and copper_ore are MINERAL sources
-- (V306 binds them to their geology), charcoal has its clamp (V163), clay and dry branches are gathered in
-- code, and alloy_bronze (V146) is the ~2:1-ingot alloying. Nothing in the chain waits on anything new except
-- the four steps below. The ingot is the one scarce input, and it is scarce for the right reason: tin.
--
-- THE MOULD IS CONSUMED, on purpose. A clay piece-mould is broken open to free the casting -- that is how the
-- casting is got out of it -- so one mould makes one sword. A reusable two-piece stone mould is the other
-- historical answer and would be a separate, later recipe; it is not pretended here.
--
-- THE FURNACE. #966 asks for "the required furnace". Casting is marked with the bloomery furnace as its
-- station, the same way V152 marked every smelt: a fire with charcoal will melt bronze (V146 already alloys it
-- over one), and a furnace's steadier heat gives a cleaner pour -- one grade, bounded, never a gate. Making the
-- furnace a hard gate would strand every Chronicle who alloyed their ingot over a fire, which the world allows.
--
-- FINISHING is cold work: hammering the cast edges thins and hardens them, which is what makes bronze hold an
-- edge at all, and it wants a STRIKING tool. The grinding and polishing that follows is read in
-- PhysicalItemService.executeProcess: an abrasive stone in reach lifts the finish one grade. That is not
-- written into a keyword, because "sharpen" and "grind ... blade" already belong to mending (REPAIR_ITEM),
-- and a recipe that answered to them would steal "sharpen my bronze sword" from the whetstone it was meant for.
--
-- CONSERVATION, with process_mass_balance's own arithmetic (cheapest option per group):
--   make_sword_mould    2 x 1200 = 2400  ->  1900   (water driven off in the firing)
--   cast_bronze_sword   900 + 1900 + 2 x 30 = 2860  ->  760   (sprue, flash and the broken mould)
--   forge_sword_edges   760  ->  640                (flash cut off, edges ground)
--   hilt_bronze_sword   640 + 260 (bone) + 40 (sinew) = 940  ->  760
-- Nothing is created anywhere in the chain. A Bronze Age short sword of this length weighs about this.
--
-- NOT DONE, AND WHY: the sword is not added to melt_down_bronze. That recipe returns a whole 900 g ingot, and a
-- 760 g sword melted into a 900 g ingot creates 140 g of bronze from nothing. Recovering it needs a recipe whose
-- yield is honest about the loss, which is a separate change.

-- ---------------------------------------------------------------------------------------------------
-- THE FOUR THINGS.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
 ('sword_mould',          'Sword mould',          'MATERIAL', 1900, 1400, FALSE, FALSE, 0),
 ('bronze_sword_casting', 'Bronze sword casting', 'MATERIAL',  760,  100, FALSE, FALSE, 0),
 ('bronze_sword_blade',   'Bronze sword blade',   'MATERIAL',  640,   90, FALSE, FALSE, 0),
 ('bronze_short_sword',   'Bronze short sword',   'WEAPON',    760,  400, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
 ('bronze_short_sword', 'HAND_RIGHT', 'CARRIED'),
 ('bronze_short_sword', 'HAND_LEFT',  'CARRIED')
ON CONFLICT DO NOTHING;

-- A material_process output is not a source; the launch audit refuses a world with an item nobody can obtain
-- until item_source says so (V475 hit this; it is the guard doing its job).
INSERT INTO item_source (item_key, source_kind, detail) VALUES
 ('sword_mould',          'TECHNIQUE', 'material_process:make_sword_mould'),
 ('bronze_sword_casting', 'TECHNIQUE', 'material_process:cast_bronze_sword'),
 ('bronze_sword_blade',   'TECHNIQUE', 'material_process:forge_sword_edges'),
 ('bronze_short_sword',   'TECHNIQUE', 'material_process:hilt_bronze_sword')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- HOW THEY ARE MADE.
--
-- All four are CRAFT, and every one carries a keyword that classifies to CRAFT (mould, cast, forge, haft,
-- fit), which the launch audit requires. Keywords are short phrases found INSIDE what a player writes.
--
-- "bronze sword" sits on the cast, the edging and the hilting alike. That is a tie, and it is meant to be:
-- V318 settles it by what is in reach, so "forge a bronze sword" pours the casting for a Chronicle holding an
-- ingot and a mould, works the edges for one holding the casting, and hilts the blade for one holding the
-- blade -- the next step of the road, whatever step they are on -- and asks when it cannot tell.
--
-- review_state VERIFIED explicitly: the column defaults to DRAFT and every reader filters on VERIFIED (V475).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO material_process
 (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
  duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
 ('make_sword_mould', 'Make a sword mould', 'sword_mould', 1, 1, 'CUTTING', TRUE, FALSE,
  90, 'items', 'CRAFT',
  'sword mould,sword mold,clay sword mould,clay sword mold,mould for a sword,mold for a sword,make a sword mould,make a sword mold,carve a sword mould,carve a sword mold',
  'You knead the clay smooth, split it into two thick slabs, and cut the shape of a blade into their faces: a long leaf with a ridge down its middle, a tang at one end, a cup at the other for the metal to go in by. The halves are bound face to face and set in the fire until they ring when tapped. It will hold one pour.', 'VERIFIED', now()),
 ('cast_bronze_sword', 'Cast a bronze sword', 'bronze_sword_casting', 1, 1, NULL, TRUE, FALSE,
  60, 'items', 'CRAFT',
  'cast a sword,cast the sword,cast a bronze sword,cast the bronze sword,cast a sword blade,cast the sword blade,pour the sword,pour a sword,bronze sword',
  'You bed the mould upright in hot sand and bring the ingot to the run in the heart of the coals, feeding them charcoal and air until the bronze moves like water. It goes into the cup in one pour. When it has gone dull you break the mould open and the sword comes out of it grey and rough-edged, with a stub of metal where the cup was and a thin fin of bronze along every seam.', 'VERIFIED', now()),
 ('forge_sword_edges', 'Forge the edges of a sword', 'bronze_sword_blade', 1, 1, 'STRIKING', FALSE, FALSE,
  150, 'items', 'CRAFT',
  'forge the edges,forge the sword edges,forge the edge of the sword,forge the sword blade,forge the bronze blade,hammer out the edges,hammer the sword edges,finish the sword casting,finish the casting,sword casting,bronze sword',
  'You saw off the stub where the pour went in and file away the fins along the seams, then lay the blade on a flat stone and work the edges cold, a hand-width at a time, light blows drawing the metal thin. Bronze stiffens as it is worked, and by the end the edges are harder than the casting ever was. It is a blade now. It still wants a hilt.', 'VERIFIED', now()),
 ('hilt_bronze_sword', 'Hilt a bronze sword', 'bronze_short_sword', 1, 1, 'CUTTING', FALSE, FALSE,
  75, 'items', 'CRAFT',
  'hilt the sword,hilt the blade,fit a hilt,fit the hilt,haft the sword,haft the sword blade,bronze short sword,short sword,bronze sword',
  'You split the hilt piece along its length, cut a bed for the tang in each half, and close them on it. The grip is bound over, turn on turn, wet so that it shrinks tight as it dries. When you take it up the balance sits a finger past the guard, and the point goes where you look.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- The furnace eases the pour, as V152 has it ease every smelt. Never a gate.
UPDATE material_process SET station_kind = 'bloomery_furnace' WHERE process_key = 'cast_bronze_sword' AND station_kind IS NULL;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
 ('make_sword_mould',  'clay_lump', 2),
 ('cast_bronze_sword', 'bronze_ingot', 1),
 ('cast_bronze_sword', 'sword_mould', 1),
 ('cast_bronze_sword', 'charcoal', 2),
 ('forge_sword_edges', 'bronze_sword_casting', 1),
 ('hilt_bronze_sword', 'bronze_sword_blade', 1)
ON CONFLICT DO NOTHING;

-- THE HILT: wood, bone or antler, whichever is to hand -- all three were hilt materials. THE GRIP: the same
-- three bindings V475 trusted to hold stone into a sword against a blow; loose plant fibre is left out again.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
 ('hilt_bronze_sword', 'hilt', 'dry_branch', 1),
 ('hilt_bronze_sword', 'hilt', 'animal_bone', 1),
 ('hilt_bronze_sword', 'hilt', 'deer_antler', 1),
 ('hilt_bronze_sword', 'grip', 'animal_sinew', 1),
 ('hilt_bronze_sword', 'grip', 'fiber_cordage', 1),
 ('hilt_bronze_sword', 'grip', 'soft_bast_strip', 1)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT THE PLAYER IS TALKING ABOUT. The matcher wants a keyword AND a subject term, whole-word, on the same
-- process, and EveryPhraseKeywordNamesItsOwnMaterialTest holds every keyword to having its own noun as a
-- subject -- so each keyword's content nouns are here.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO process_subject (process_key, subject_term) VALUES
 ('make_sword_mould', 'sword'), ('make_sword_mould', 'mould'), ('make_sword_mould', 'mold'),
 ('make_sword_mould', 'sword mould'), ('make_sword_mould', 'sword mold'), ('make_sword_mould', 'clay'),
 ('cast_bronze_sword', 'sword'), ('cast_bronze_sword', 'bronze'), ('cast_bronze_sword', 'bronze sword'),
 ('cast_bronze_sword', 'blade'), ('cast_bronze_sword', 'sword blade'),
 ('forge_sword_edges', 'sword'), ('forge_sword_edges', 'edges'), ('forge_sword_edges', 'edge'),
 ('forge_sword_edges', 'blade'), ('forge_sword_edges', 'bronze'), ('forge_sword_edges', 'casting'),
 ('forge_sword_edges', 'sword casting'), ('forge_sword_edges', 'bronze sword'), ('forge_sword_edges', 'sword blade'),
 ('hilt_bronze_sword', 'sword'), ('hilt_bronze_sword', 'hilt'), ('hilt_bronze_sword', 'blade'),
 ('hilt_bronze_sword', 'bronze'), ('hilt_bronze_sword', 'bronze sword'), ('hilt_bronze_sword', 'short sword'),
 ('hilt_bronze_sword', 'sword blade'), ('hilt_bronze_sword', 'bronze short sword')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT IT IS IN A FIGHT. HAND role like every edged weapon; BRONZE edge tier, the same as the bronze spear
-- and axe, above the stone-edged sword's HARDENED. It draws on SWORD_FIGHTING (V475), so the discipline a
-- Chronicle built with the wooden sword carries to this one, and practising with it (PRACTISE_WEAPON) trains it.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, combat_skill_key) VALUES
 ('bronze_short_sword', 'HAND', 'BRONZE', 'SWORD_FIGHTING')
ON CONFLICT (item_key) DO NOTHING;
