-- V478: IRON AND STEEL SWORDS (GitHub #967, under EPIC #964).
--
-- V477 gave the epic its first metal sword, and its rule: a sword is not one ingot in and one object out. It is
-- the same here, and more so, because iron does not pour. A bloomery never melts iron; it leaves a spongy bloom
-- shot through with slag (V150). Nothing can be cast from that. The bloom has to be hammered hot until the slag
-- runs out of it and the iron closes into a bar, and the blade is then forged out of the bar, a heat at a time.
--
-- THE IRON ROAD, from what the world already gives:
--
--   iron_ore x2 + charcoal x3 --smelt_iron (V150)-->  iron_bloom 1400 g     (or smelt_iron_sand, smelt_iron_hot)
--
--   iron_bloom + charcoal x2   --consolidate_iron_bloom   (fire, STRIKING, 120 min)-->  iron_billet       1000 g
--   iron_billet + charcoal x2  --forge_iron_sword_blade   (fire, STRIKING, 180 min)-->  iron_sword_blade   850 g
--   iron_sword_blade + hilt (branch | bone | antler) + grip (sinew | cordage | bast)
--                              --hilt_iron_sword          (CUTTING, 75 min)--------->  iron_sword        1050 g
--
-- THE STEEL ROAD branches from the unhilted iron blade, because the heat treatment has to be done to bare metal:
--
--   iron_sword_blade + charcoal x4   --carburise_iron_sword_blade (fire, 240 min)----->  steel_sword_blank   840 g
--   steel_sword_blank + charcoal x1  --harden_steel_sword_blade   (fire AND water, 90)->  steel_sword_blade   830 g
--   steel_sword_blade + hilt + grip  --hilt_steel_sword           (CUTTING, 75 min)--->  steel_sword        1030 g
--
-- THE HEAT TREATMENT IS MATERIAL-APPROPRIATE, which #967 asks for by name. Bloomery iron has almost no carbon, so
-- heating it red and plunging it in water does nothing useful: there is nothing in it to harden. The iron sword
-- therefore has NO quench. The edge is all it gets from the hammer, and that is the honest iron sword. Steel is
-- different. Packed in charcoal and held red for hours, carbon creeps into the blade (V151 does the same to an
-- axe). Plunged hot into water, that carburised skin goes glass-hard and brittle, and it is then let back down
-- over a gentle heat until it is springy instead of brittle. That last pair is one act here, hardening and
-- tempering, and it wants water at hand (requires_water) as well as a fire, because the plunge is the point.
--
-- THE WORD "QUENCH" IS DELIBERATELY ABSENT FROM EVERY KEYWORD. ChronicleActionService reads any sentence with
-- "quench" in it as DRINK ("quench your thirst"). A keyword containing it would be a keyword nobody can reach.
-- "Harden" and "temper" are both PROCESS category terms already, and both are the smith's own words.
-- "Draw" is avoided for the same reason: "draw the ..." is a haul intent.
--
-- NOT EVERY LATER SWORD IS SIMPLY BETTER (#967's explicit ask). What each one actually costs and gives, using
-- rules the world already enforces:
--   bronze_short_sword  760 g   edge BRONZE  wear 16/32  does NOT rust     needs tin: one scarce exposure
--   iron_sword         1050 g   edge IRON    wear 24/48  RUSTS when left   ore is common, 4 hours of hammering
--   steel_sword        1030 g   edge STEEL   wear 32/64  RUSTS when left   five and a half hours more, plus water
-- The rust and the wear ladder both come from the key prefix (PhysicalItemService.weatherExposedMetal and
-- toolWearThresholds read iron_% and steel_%), which is why the keys are named as they are. The iron sword is the
-- heaviest of the three. A bronze sword left lying in a bog is still a sword a season later, and an iron one is not.
--
-- CONSERVATION, with process_mass_balance's own arithmetic (cheapest option per group, charcoal 30 g):
--   consolidate_iron_bloom      1400 + 60            = 1460  ->  1000   (slag beaten out, scale)
--   forge_iron_sword_blade      1000 + 60            = 1060  ->   850   (scale, the tang trimmed)
--   hilt_iron_sword              850 + 260 + 40      = 1150  ->  1050
--   carburise_iron_sword_blade   850 + 120           =  970  ->   840   (scale; the carbon taken up is grams)
--   harden_steel_sword_blade     840 + 30            =  870  ->   830   (scale, the grinding after)
--   hilt_steel_sword             830 + 260 + 40      = 1130  ->  1030
-- Nothing is created anywhere. And no sword is a dead end: two worn swords of either metal reforge into a bloom.

-- ---------------------------------------------------------------------------------------------------
-- THE THINGS.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
 ('iron_billet',       'Iron billet',            'MATERIAL', 1000, 130, TRUE,  FALSE, 0),
 ('iron_sword_blade',  'Iron sword blade',       'MATERIAL',  850, 110, FALSE, FALSE, 0),
 ('iron_sword',        'Iron sword',             'WEAPON',   1050, 450, FALSE, TRUE,  0),
 ('steel_sword_blank', 'Carburised sword blade', 'MATERIAL',  840, 110, FALSE, FALSE, 0),
 ('steel_sword_blade', 'Steel sword blade',      'MATERIAL',  830, 110, FALSE, FALSE, 0),
 ('steel_sword',       'Steel sword',            'WEAPON',   1030, 450, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
 ('iron_sword',  'HAND_RIGHT', 'CARRIED'),
 ('iron_sword',  'HAND_LEFT',  'CARRIED'),
 ('steel_sword', 'HAND_RIGHT', 'CARRIED'),
 ('steel_sword', 'HAND_LEFT',  'CARRIED')
ON CONFLICT DO NOTHING;

-- The launch audit refuses an item nobody can obtain until item_source says how (V475, V477).
INSERT INTO item_source (item_key, source_kind, detail) VALUES
 ('iron_billet',       'TECHNIQUE', 'material_process:consolidate_iron_bloom'),
 ('iron_sword_blade',  'TECHNIQUE', 'material_process:forge_iron_sword_blade'),
 ('iron_sword',        'TECHNIQUE', 'material_process:hilt_iron_sword'),
 ('steel_sword_blank', 'TECHNIQUE', 'material_process:carburise_iron_sword_blade'),
 ('steel_sword_blade', 'TECHNIQUE', 'material_process:harden_steel_sword_blade'),
 ('steel_sword',       'TECHNIQUE', 'material_process:hilt_steel_sword')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- HOW THEY ARE MADE.
--
-- The SHARED words are shared on purpose, as "bronze sword" is in V477: V318 settles a tie by what is in reach, so
-- "forge an iron sword" forges the blade for a Chronicle holding a billet and hilts it for one holding the blade,
-- and asks when it cannot tell. The BLOOM step does not answer to "iron sword": ProcessMatcher scores a tie by how
-- many words of each OUTPUT KEY the sentence names, and "iron sword" names one word of iron_billet against two of
-- iron_sword_blade and iron_sword, so the bloom step would lose every such tie and the keyword would be a promise
-- nothing keeps. It answers to the bar and the bloom.
--
-- "steel sword" is NOT shared down the steel road, and that was measured, not assumed. Carburising and hardening
-- are PROCESS work and the category is a GATE: "make a steel sword" classifies CRAFT, so the matcher never even
-- considers the two PROCESS steps, and a shared "steel sword" on them only ever reached the hilting (live: an iron
-- blade and charcoal in hand, "make a steel sword" -> "You have not got enough steel sword blade"). Making them CRAFT
-- would gate their own verbs instead, since carburise, harden and temper all classify PROCESS. So the steel steps
-- answer to the smith's own verbs, and "steel sword" belongs to the hilting alone.
--
-- The plain hilting words ("hilt the sword", "fit a hilt", "haft the sword blade") and "forge the
-- sword blade" were V477's alone, and would otherwise send a Chronicle holding an iron blade to the bronze recipe;
-- each now sits on every recipe it truthfully describes and the tie picks the one whose blade is in hand.
--
-- review_state VERIFIED explicitly: the column defaults to DRAFT and every reader filters on VERIFIED.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO material_process
 (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
  duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
 ('consolidate_iron_bloom', 'Forge an iron bloom into a billet', 'iron_billet', 1, 1, 'STRIKING', TRUE, FALSE,
  120, 'items', 'CRAFT',
  'forge the bloom,forge the iron bloom,forge the bloom into a bar,forge an iron bar,forge a billet,forge an iron billet,iron billet,iron bar',
  'You bring the bloom up to a white heat and strike it, lightly at first, because it is still more sponge than metal and the first blows would break it apart. Slag runs out of it with every heat, spitting. Heat by heat the pores close and the lump stops crumbling and starts to ring, and by the end what lies on the stone is a bar of grey iron about the length of your forearm, lighter by a third than the bloom it came from.', 'VERIFIED', now()),
 ('forge_iron_sword_blade', 'Forge an iron sword blade', 'iron_sword_blade', 1, 1, 'STRIKING', TRUE, FALSE,
  180, 'items', 'CRAFT',
  'forge an iron blade,forge the iron blade,forge an iron sword blade,forge the iron sword blade,forge the sword blade,iron sword blade,iron blade,iron sword',
  'You work the billet out a hand at a time: a heat, a dozen blows, back into the fire. It lengthens and flattens into a blade with a ridge down its middle and a narrow tang at one end, and you bevel the edges with the hammer, then file and grind them true. The iron is soft enough to take a nick and tough enough not to snap. It still wants a hilt.', 'VERIFIED', now()),
 ('hilt_iron_sword', 'Hilt an iron sword', 'iron_sword', 1, 1, 'CUTTING', FALSE, FALSE,
  75, 'items', 'CRAFT',
  'hilt the iron sword,hilt the iron blade,haft the iron sword,fit a hilt to the iron blade,iron sword,hilt the sword,hilt the blade,fit a hilt,fit the hilt,haft the sword,haft the sword blade',
  'You split the hilt piece, cut a bed for the tang in each half and close them on it, then bind the grip over, wet so it shrinks tight as it dries. It is heavier in the hand than any blade you have held, and the weight sits further out. It will cut through what a bronze edge would only bruise, and it will rust if you leave it wet.', 'VERIFIED', now()),
 ('carburise_iron_sword_blade', 'Carburise a sword blade', 'steel_sword_blank', 1, 1, NULL, TRUE, FALSE,
  240, 'items', 'PROCESS',
  'carburise the sword blade,carburize the sword blade,carburise the iron blade,carburize the iron blade,carburise the sword,carburize the sword,case-harden the sword blade,case harden the sword blade,steel the sword blade,steel the iron blade',
  'You pack the bare blade in a box of crushed charcoal, seal it with clay, and keep it at a steady red heat for hours, feeding the fire and never letting it flare. When you break the clay away the blade looks no different. But a file skates on the edge where before it bit: carbon has gone into the skin of the iron, and it is steel there now, though soft until it is hardened.', 'VERIFIED', now()),
 ('harden_steel_sword_blade', 'Harden and temper a steel sword blade', 'steel_sword_blade', 1, 1, NULL, TRUE, TRUE,
  90, 'items', 'PROCESS',
  'harden the steel blade,harden the steel sword,harden the sword blade,harden the blade,temper the steel blade,temper the steel sword,temper the sword blade,temper the blade,harden and temper the blade',
  'You bring the blade up evenly until it is the colour of a summer sunset, edge and spine together, and plunge it point-first into the water. It hisses and shudders. A file will not touch it now, and dropped on stone it might break. So you clean a stretch bright and hold it over a gentle fire, watching the colours run along it, straw, then bronze, then blue at the spine, and take it off at the moment they are right. What you hold is hard at the edge and springy in the body.', 'VERIFIED', now()),
 ('hilt_steel_sword', 'Hilt a steel sword', 'steel_sword', 1, 1, 'CUTTING', FALSE, FALSE,
  75, 'items', 'CRAFT',
  'hilt the steel sword,hilt the steel blade,haft the steel sword,fit a hilt to the steel blade,steel sword,hilt the sword,hilt the blade,fit a hilt,fit the hilt,haft the sword,haft the sword blade',
  'You fit the hilt to the tang and bind the grip tight. It weighs about as much as the iron sword did, but it does not feel like it: the blade flexes and comes back, and the edge stays keen through work that would have dulled iron. It is the best sword you know how to make, and it will rust as readily as any iron if you neglect it.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- The bloom is consolidated where it was won: the furnace's steadier heat welds it cleaner. One grade, never a gate,
-- the same way V152 has the bloomery ease every smelt and V477 the bronze pour.
UPDATE material_process SET station_kind = 'bloomery_furnace' WHERE process_key = 'consolidate_iron_bloom' AND station_kind IS NULL;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
 ('consolidate_iron_bloom',     'iron_bloom', 1),
 ('consolidate_iron_bloom',     'charcoal', 2),
 ('forge_iron_sword_blade',     'iron_billet', 1),
 ('forge_iron_sword_blade',     'charcoal', 2),
 ('hilt_iron_sword',            'iron_sword_blade', 1),
 ('carburise_iron_sword_blade', 'iron_sword_blade', 1),
 ('carburise_iron_sword_blade', 'charcoal', 4),
 ('harden_steel_sword_blade',   'steel_sword_blank', 1),
 ('harden_steel_sword_blade',   'charcoal', 1),
 ('hilt_steel_sword',           'steel_sword_blade', 1)
ON CONFLICT DO NOTHING;

-- THE HILT AND GRIP: the same three of each V477 trusted for the bronze sword.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
 ('hilt_iron_sword',  'hilt', 'dry_branch', 1),
 ('hilt_iron_sword',  'hilt', 'animal_bone', 1),
 ('hilt_iron_sword',  'hilt', 'deer_antler', 1),
 ('hilt_iron_sword',  'grip', 'animal_sinew', 1),
 ('hilt_iron_sword',  'grip', 'fiber_cordage', 1),
 ('hilt_iron_sword',  'grip', 'soft_bast_strip', 1),
 ('hilt_steel_sword', 'hilt', 'dry_branch', 1),
 ('hilt_steel_sword', 'hilt', 'animal_bone', 1),
 ('hilt_steel_sword', 'hilt', 'deer_antler', 1),
 ('hilt_steel_sword', 'grip', 'animal_sinew', 1),
 ('hilt_steel_sword', 'grip', 'fiber_cordage', 1),
 ('hilt_steel_sword', 'grip', 'soft_bast_strip', 1)
ON CONFLICT DO NOTHING;

-- NO SWORD IS A DEAD END. V160 reforges worn iron back into a bloom; two worn swords of either metal now feed it.
-- Mass: two of the lighter sword (2 x 1030 = 2060) into a 1400 g bloom only loses, the hilts burning away with the scale.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
 ('reforge_iron_scrap', 'iron_object', 'iron_sword', 2),
 ('reforge_iron_scrap', 'iron_object', 'steel_sword', 2)
ON CONFLICT DO NOTHING;
INSERT INTO process_subject (process_key, subject_term) VALUES
 ('reforge_iron_scrap', 'sword'), ('reforge_iron_scrap', 'swords')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT THE PLAYER IS TALKING ABOUT: every keyword's content nouns, whole-word, on the same process
-- (EveryPhraseKeywordNamesItsOwnMaterialTest).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO process_subject (process_key, subject_term) VALUES
 ('consolidate_iron_bloom', 'iron'), ('consolidate_iron_bloom', 'bloom'), ('consolidate_iron_bloom', 'iron bloom'),
 ('consolidate_iron_bloom', 'bar'), ('consolidate_iron_bloom', 'iron bar'), ('consolidate_iron_bloom', 'billet'),
 ('consolidate_iron_bloom', 'iron billet'),
 ('forge_iron_sword_blade', 'iron'), ('forge_iron_sword_blade', 'blade'), ('forge_iron_sword_blade', 'sword'),
 ('forge_iron_sword_blade', 'iron blade'), ('forge_iron_sword_blade', 'sword blade'), ('forge_iron_sword_blade', 'iron sword'),
 ('forge_iron_sword_blade', 'iron sword blade'), ('forge_iron_sword_blade', 'billet'),
 ('hilt_iron_sword', 'sword'), ('hilt_iron_sword', 'hilt'), ('hilt_iron_sword', 'blade'), ('hilt_iron_sword', 'iron'),
 ('hilt_iron_sword', 'iron sword'), ('hilt_iron_sword', 'iron blade'), ('hilt_iron_sword', 'sword blade'),
 ('carburise_iron_sword_blade', 'sword'), ('carburise_iron_sword_blade', 'blade'), ('carburise_iron_sword_blade', 'sword blade'),
 ('carburise_iron_sword_blade', 'iron'), ('carburise_iron_sword_blade', 'iron blade'), ('carburise_iron_sword_blade', 'steel'),
 ('carburise_iron_sword_blade', 'steel blade'),
 ('harden_steel_sword_blade', 'steel'), ('harden_steel_sword_blade', 'blade'), ('harden_steel_sword_blade', 'sword'),
 ('harden_steel_sword_blade', 'steel blade'), ('harden_steel_sword_blade', 'steel sword'), ('harden_steel_sword_blade', 'sword blade'),
 ('hilt_steel_sword', 'sword'), ('hilt_steel_sword', 'hilt'), ('hilt_steel_sword', 'blade'), ('hilt_steel_sword', 'steel'),
 ('hilt_steel_sword', 'steel sword'), ('hilt_steel_sword', 'steel blade'), ('hilt_steel_sword', 'sword blade')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT THEY ARE IN A FIGHT: HAND role, the IRON and STEEL edge tiers the axes already use, and the same
-- SWORD_FIGHTING discipline the wooden sword began, so practice carries from one sword to the next.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, combat_skill_key) VALUES
 ('iron_sword',  'HAND', 'IRON',  'SWORD_FIGHTING'),
 ('steel_sword', 'HAND', 'STEEL', 'SWORD_FIGHTING')
ON CONFLICT (item_key) DO NOTHING;
