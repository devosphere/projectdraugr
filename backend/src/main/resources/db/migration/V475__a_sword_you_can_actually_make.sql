-- V475: A SWORD YOU CAN ACTUALLY MAKE (GitHub #965, under EPIC #964).
--
-- V472 catalogued thirty-eight skills and deliberately left SWORD_FIGHTING out, because not one row in
-- item_definition matched sword, dagger or blade. A skill nothing can ever practise is the catalogue token
-- this project refuses to ship, so it was omitted and said so on the ticket. #964 is the answer to that,
-- and this is its first story: the two swords a Chronicle can make before they have ever smelted anything.
--
-- THE WOODEN PRACTICE SWORD is a seasoned plank carved down with a cutting edge. It is a real weapon in the
-- sense that it can be swung and will hurt, and a poor one: PLAIN edge tier, the lowest the combat model
-- has, so it bruises where a bronze sword would cut. What it is genuinely for is practice -- and because
-- V472 draws the combat discipline from the weapon in hand, swinging it develops SWORD_FIGHTING exactly as
-- a steel sword would. That is #965's "appropriate training effects and bounded combat capability", and it
-- needs no separate machinery: the training IS the skill system already in place.
--
-- THE STONE-EDGED WOODEN SWORD is that blank with knapped stone set into its sides and lashed fast -- the
-- macuahuitl pattern, and a serious weapon. HARDENED edge tier, between a plain wooden club and bronze.
--
-- THE EDGE MAY BE ANY OF THREE STONES. #965 asks to "review chert as an eligible edge material", so:
-- obsidian takes the keenest edge and is the scarcest; flint is the common answer; and chert is eligible,
-- which it ought to be -- it is a microcrystalline silica that knaps and holds an edge, and the catalogue
-- already makes a chert_knife out of it. All three are one input group, so the recipe runs on whichever the
-- Chronicle has.
--
-- CONSERVATION. process_mass_balance takes the CHEAPEST option in each group, so the binding group's
-- lightest member (animal_sinew, 40 g) and the edge group's lightest (flint_stone, 190 g) decide the floor:
-- 700 + 190 + 40 = 930 g of input at worst. The sword weighs 880 g, so matter is never created even by the
-- thriftiest route, and the 50 g difference is the shavings and the stone that broke wrong.

-- ---------------------------------------------------------------------------------------------------
-- THE TWO WEAPONS.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
 ('wooden_practice_sword',    'Wooden practice sword',    'WEAPON', 700, 1100, FALSE, TRUE, 0),
 ('stone_edged_wooden_sword', 'Stone-edged wooden sword', 'WEAPON', 880, 1200, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- Held in either hand, carried like every other hand weapon.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
 ('wooden_practice_sword',    'HAND_RIGHT', 'CARRIED'),
 ('wooden_practice_sword',    'HAND_LEFT',  'CARRIED'),
 ('stone_edged_wooden_sword', 'HAND_RIGHT', 'CARRIED'),
 ('stone_edged_wooden_sword', 'HAND_LEFT',  'CARRIED')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- HOW THEY ARE MADE.
--
-- Both are bare-handed work with a CUTTING tool, which is the honest requirement: you cannot carve a sword
-- out of a plank with your fingers, and you do not need a forge. The plank itself is reachable today --
-- fell a tree, split_planks, season_plank -- so neither recipe waits on anything that does not exist.
-- ---------------------------------------------------------------------------------------------------
-- review_state VERIFIED, explicitly. The column defaults to DRAFT and every reader -- the matcher, the
-- discovery read, the AI procedure interpreter -- filters on VERIFIED, so a recipe left at the default is
-- invisible to the game. Found the hard way: both of these were catalogued, conserved and audited, and
-- "carve a wooden practice sword" still answered "those words fit more than one piece of work" because the
-- matcher could not see them. DRAFT is for what RuntimeAuthoringService writes at play time; a hand-authored
-- recipe that has been checked is VERIFIED, as all 519 of the others are.
INSERT INTO material_process
 (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire,
  duration_minutes, domain_key, category_key, keywords, narration, review_state) VALUES
 ('carve_practice_sword', 'Carve a wooden practice sword', 'wooden_practice_sword', 1, 1, 'CUTTING', FALSE,
  120, 'woodworking', 'CRAFT',
  -- Short noun phrases first, longest-match-wins, which is the idiom every other recipe uses:
  -- carve_wooden_mallet reads "wooden mallet,mallet,beetle,carve a mallet,carve". Written as full commands
  -- ("carve a wooden practice sword") these matched NOTHING, because the matcher looks for a keyword
  -- INSIDE what the player wrote and no such phrase is a substring of a real sentence.
  -- "wooden sword" is deliberately NOT here. Both of these ARE wooden swords, and at twelve characters it
  -- out-matched the edging recipe's own "stone edges" by one -- so "set stone edges into a wooden sword"
  -- carved a fresh blank instead. Each keyword either belongs to one recipe alone or is left to the tie.
  'wooden practice sword,practice sword,training sword,carve a wooden sword,carve a sword,whittle a sword,sword,carve',
  'You set the seasoned plank on your knee and work it down: a blade as long as your arm, a shoulder where the grip begins, the edges taken off square and then rounded. It is heavy for its length and it will not cut anything. Swung at a post it lands with a sound like a dropped beam, and your hands learn where the weight wants to go.', 'VERIFIED'),
 ('edge_wooden_sword', 'Set stone edges into a wooden sword', 'stone_edged_wooden_sword', 1, 1, 'CUTTING', FALSE,
  150, 'woodworking', 'CRAFT',
  -- "haft", "lash" and "bind" are CRAFT terms, and the audit requires a process to carry at least one
  -- keyword that classifies to its own category -- it refused to start a world without one:
  --   Launch audit found 1 invariant violation(s): [1 process(es) have no keyword that classifies to
  --   their own category.]
  -- They are also the right words for what this is: stone set in a groove and lashed fast.
  'stone edged wooden sword,stone-edged wooden sword,stone edged sword,stone-edged sword,set stone edges,haft stone edges,lash stone edges,bind stone edges,edge a wooden sword,edge the sword,stone edges,inset stone,sword',
  'You cut a shallow groove down each side of the wooden blade and knap the stone into short straight-backed flakes, seating them edge-out with their backs bedded in the wood. Then the lashing, turn over turn, tight enough that the stone cannot rock and close enough that it cannot catch. What comes off your knee is no longer a practice weapon: it will open a hide and keep doing it.', 'VERIFIED')
ON CONFLICT (process_key) DO NOTHING;

-- Carving the blank: one seasoned plank, nothing else.
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
 ('carve_practice_sword', 'seasoned_plank', 1),
 ('edge_wooden_sword', 'wooden_practice_sword', 1)
ON CONFLICT DO NOTHING;

-- THE EDGE: whichever knappable stone is to hand. Obsidian is keenest, flint is commonest, and chert is
-- eligible -- #965 asked that it be reviewed, and it knaps and holds an edge as the chert_knife attests.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
 ('edge_wooden_sword', 'edge stone', 'obsidian_shard', 1),
 ('edge_wooden_sword', 'edge stone', 'flint_stone', 1),
 ('edge_wooden_sword', 'edge stone', 'chert_nodule', 1),
 -- AND THE LASHING, any of the three that hold fast enough for a struck edge. The loose-fibre end of
 -- binding_material is left out on purpose: plant fibre will not hold stone against the shock of a blow.
 ('edge_wooden_sword', 'lashing', 'fiber_cordage', 1),
 ('edge_wooden_sword', 'lashing', 'animal_sinew', 1),
 ('edge_wooden_sword', 'lashing', 'soft_bast_strip', 1)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- AND THE SKILL V472 WOULD NOT INVENT.
--
-- It was left out because nothing could practise it. Two things can now, so it exists -- and it exists on
-- exactly the terms the other combat disciplines do, reached through the weapon in hand.
--
-- The weights are the sword's own: more dexterity than an axe wants and less raw strength, because a sword
-- is used with the point and the edge rather than swung through. SkillAptitudeSupportTest asserts the
-- weights of every skill sum to one, so this had to be written as a share like the rest.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO skill_definition (skill_key, display_name, family, notes) VALUES
 ('SWORD_FIGHTING', 'Sword fighting', 'COMBAT',
  'A long two-edged blade used with the point as much as the edge: guard, step, cut and recover. Distinct from the axe, which is swung through, and from the knife, which is close work.')
ON CONFLICT (skill_key) DO NOTHING;

INSERT INTO skill_aptitude_support (skill_key, aptitude_key, weight) VALUES
 ('SWORD_FIGHTING', 'DEXTERITY', 0.35),
 ('SWORD_FIGHTING', 'AGILITY', 0.30),
 ('SWORD_FIGHTING', 'STRENGTH_ENDURANCE', 0.20),
 ('SWORD_FIGHTING', 'PERCEPTION', 0.15)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT THEY ARE IN A FIGHT.
--
-- HAND role, like the spears and the axes, so the existing combat query sees them without changing. The
-- practice sword is PLAIN -- the floor of the edge model -- and the stone-edged one HARDENED, which puts it
-- above a club and below bronze, where a glass edge on a wooden back belongs.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, combat_skill_key) VALUES
 ('wooden_practice_sword',    'HAND', 'PLAIN',    'SWORD_FIGHTING'),
 ('stone_edged_wooden_sword', 'HAND', 'HARDENED', 'SWORD_FIGHTING')
ON CONFLICT (item_key) DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- WHAT THE PLAYER IS TALKING ABOUT.
--
-- 519 of the 521 catalogued processes carry process_subject rows, and the matcher wants BOTH a keyword and
-- a subject on the same process before it will run it. A recipe with keywords and no subject is a recipe
-- nobody can ask for -- which would have made the whole of this migration unreachable prose.
--
-- Both swords answer to the bare word "sword" on purpose. That is a tie, and V318 already settles ties by
-- the output a player named, then by which inputs are in reach, then by asking: a Chronicle holding a plank
-- gets the carving and one holding the blank and a flint gets the edging, without either recipe having to
-- guess.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO process_subject (process_key, subject_term) VALUES
 ('carve_practice_sword', 'sword'),
 ('carve_practice_sword', 'wooden sword'),
 ('carve_practice_sword', 'practice sword'),
 ('carve_practice_sword', 'training sword'),
 ('carve_practice_sword', 'wooden practice sword'),
 ('carve_practice_sword', 'plank'),
 ('edge_wooden_sword', 'sword'),
 ('edge_wooden_sword', 'wooden sword'),
 ('edge_wooden_sword', 'stone edged sword'),
 ('edge_wooden_sword', 'stone-edged sword'),
 ('edge_wooden_sword', 'stone edged wooden sword'),
 ('edge_wooden_sword', 'stone-edged wooden sword'),
 ('edge_wooden_sword', 'edge stone'),
 ('edge_wooden_sword', 'stone edges')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- HOW THEY ENTER THE WORLD.
--
-- item_source (V51) is the separate declaration the Persistent State Auditor reads, and a material_process
-- that outputs an item is NOT enough on its own: the launch audit refuses to start a world containing an
-- item nobody can obtain, and it counted these two before this block existed --
--
--   Launch audit found 1 invariant violation(s): [2 item definition(s) have no way to be obtained.]
--
-- which is the guard doing exactly its job. The comment on that check names the two times it happened
-- before: V48's insulating hide garments that could not be sewn, and V49's fire-kit flint with nowhere to
-- find it. This is the third, caught by the system rather than by hand.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO item_source (item_key, source_kind, detail) VALUES
 ('wooden_practice_sword',    'TECHNIQUE', 'material_process:carve_practice_sword'),
 ('stone_edged_wooden_sword', 'TECHNIQUE', 'material_process:edge_wooden_sword')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- AND A WAY TO PRACTISE, WITHOUT WHICH THE PRACTICE SWORD IS A TOKEN.
--
-- #965 asks for "appropriate training effects". V472 credits a discipline only on a SUCCEEDED act, and the
-- only act that exercises a sword was CONFRONT_WILDLIFE -- so the training effect was unreachable by
-- construction, and measurably so. Live, a fresh Chronicle holding the stone-edged sword, hunting red deer
-- on ground that held twenty-four of them, energy and body reset before each attempt:
--
--     ten hunts, ten PARTIAL, zero SUCCEEDED, SWORD_FIGHTING still absent
--
-- You cannot practise because you cannot win, and you cannot win because you have not practised. A weapon
-- whose whole purpose is training, with no way to train, is the catalogue token this project refuses --
-- strictly worse than a club and compensated by nothing.
--
-- So: an act for drilling. Swing at a post, work the guards, drill the footwork. It costs time and sweat
-- and makes noise, it produces nothing, and it teaches the discipline of whatever is in your hands -- or
-- ATHLETICS bare-handed, because shadow drill is footwork and that is a real thing to get better at.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, labor_energy, labor_hygiene,
  capability_domain, duration_minutes, skill_key, notes) VALUES
 ('PRACTISE_WEAPON', 'COMMOTION', 8, TRUE, FALSE, 8, 4, 'FINE_MOTOR', 30, 'ATHLETICS',
  'Drilling with a weapon against a post or at nothing: repetition, not a fight. It carries as noise because a person working a blade for half an hour is not quiet, and it leaves the ground as it found it because nothing is taken and nothing is built. The skill it teaches is whatever is actually in the hands, which the weapon decides; bare-handed it is footwork, and ATHLETICS is the honest answer rather than a fallback nobody reaches.')
ON CONFLICT (intent_key) DO NOTHING;
