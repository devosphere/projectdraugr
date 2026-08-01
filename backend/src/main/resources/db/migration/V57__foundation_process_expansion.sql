-- V57: The coverage batch -- roughly five times as many processes, generated
-- against the V53 gate rather than trusted.
--
-- Routing hardening (V54-V56) bought correctness, not coverage. Four hand-run
-- procedure simulations returned 0/12, 1/10, 3/12 and 3/12 COVERED, and V56's
-- diagnosis of the misses was overwhelmingly MECHANIC: the world did not lack the
-- words, it lacked the work. No salt existed. No joint existed. Nothing turned a
-- hide into armour or a stave into a bow. With twenty processes, nearly every
-- meaningful action still had to be answered by an Architect call.
--
-- So this is the actual cost lever, and it is deliberately a bulk landing rather
-- than a trickle: every row below was written as DRAFT, put through the same
-- deterministic review V53 applies to everything else -- mass balance, self-loop,
-- unobtainable input, duration, heat consistency -- plus V55's category-agreement
-- check and V51's reachability, and only what passed is promoted to VERIFIED at
-- the foot of this file. Nothing here is trusted because it was written
-- carefully. It is trusted because it survived the gate.
--
-- Scope is not arbitrary: it is exactly the eight areas the simulations named as
-- missing. Timber preservation, joinery, building layers, salt and food
-- preservation, fish processing, bow production, leather and armour, and the
-- tools and containers those chains need in order to be reachable at all.
--
-- Staged assembly (M3) is deliberately NOT here. It is a schema extension, not
-- data, and pretending otherwise would mean collapsing genuinely multi-stage work
-- into single processes that lie about how long a thing takes to make.
--
-- Verified from a clean database: all 57 migrations apply in order, 129 processes
-- reach VERIFIED with none held, zero advisories. A SQL replica of the runtime
-- resolution rule (classify -> two-axis resolve) was run over 54 representative
-- sentences, one per gap area plus the originals as regression, and every one
-- resolves to its intended process. Seven collisions surfaced by that probe were
-- fixed here rather than shipped: joinery nouns and 'fish trap' were missing
-- classification vocabulary; 'rive_bow_stave' carried bare split/rive keywords
-- that stole "split the log into planks"; and two verbs ('split', 'timber')
-- leaked into subject terms through a derivation that reads display names.

-- ---------------------------------------------------------------------------
-- 1. VOCABULARY CORRECTIONS
--
-- Three existing terms are wrong in a way that only becomes visible once the
-- world can actually process what it catches. 'fish' at HUNT weight 3 was
-- written for the verb, but it is far more often the noun: it made "fillet the
-- fish", "salt the fish" and "split the fish" all classify as hunting, which
-- would put every fish-processing step below out of reach on the category axis.
-- The verb sense is preserved by phrases that can only be the verb.
-- ---------------------------------------------------------------------------
UPDATE category_term SET weight = 1 WHERE category_key = 'HUNT' AND term = 'fish';
UPDATE category_term SET weight = 1 WHERE category_key = 'HUNT' AND term = 'skin';
DELETE FROM category_term WHERE category_key = 'PROCESS' AND term = 'fillet';
-- 'trap' as a bare HUNT term (weight 2) swallows "weave a fish trap", because
-- trap-setting and trap-making both contain the word. No HUNT process exists for
-- routing to reach anyway, so dropping it to weight 1 costs nothing and lets the
-- compound CRAFT terms below win: making a trap is craft, setting one is not
-- routed here at all (SET_TRAP is a separate intent path).
UPDATE category_term SET weight = 1 WHERE category_key = 'HUNT' AND term = 'trap';
-- 'cure' is a processing verb with no other reading. At weight 2 it tied with the
-- HUNT pair fish+skin and lost the tie to precedence, so "cure the fish skin into
-- leather" was heard as skinning a fish rather than making leather. Weight 3 settles it.
UPDATE category_term SET weight = 3 WHERE category_key = 'PROCESS' AND term = 'cure';

INSERT INTO category_term (category_key, term, weight) VALUES
('HUNT','fillet',3),('HUNT','go fishing',3),('HUNT','catch fish',3),('HUNT','land the fish',3),
-- Processing verbs the world had no word for.
-- 'coat' is deliberately NOT a classification term: it collides with the garment
-- noun ("line the coat with fur" would classify PROCESS and lose line_with_fur).
-- pitch_timber still reaches on its phrase keywords ('coat the timber', 'pitch the
-- timber') under a null classification, and on 'tar'.
('PROCESS','char',3),('PROCESS','scorch',2),('PROCESS','tar',2),('PROCESS','press',2),
('PROCESS','preserve',3),('PROCESS','tiller',3),('PROCESS','dehair',3),('PROCESS','felt',3),
('PROCESS','fish leather',3),('PROCESS','season the',2),('PROCESS','flesh out',2),
-- Spinning, plying and plaiting are genuine fibre-processing verbs that V54 wrote
-- as process keywords but never as classification vocabulary, so the yarn and
-- rope processes below could not classify to their own PROCESS category. 'plait'
-- is weight 1 so it cannot outrank 'basket' (CRAFT, weight 2) and pull
-- weave_large_basket out of reach.
('PROCESS','spin',2),('PROCESS','ply',2),('PROCESS','plait',1),
-- Mortise and tenon are the joinery nouns. Without them, "cut a mortise into the
-- beam" classifies CONSTRUCT on the strength of 'beam' and resolves to
-- reinforce_timber instead of the mortising process. Weight 3 so the specific
-- joinery term beats the generic structural noun.
('PROCESS','mortise',3),('PROCESS','tenon',3),
-- 'rawhide' is a processing noun, not a craft object: "make rawhide" must classify
-- PROCESS so make_rawhide is reachable rather than being filtered by the generic
-- 'make'->CRAFT. Only make_rawhide and cut_boot_soles use it, so no collision.
('PROCESS','rawhide',2),
-- Building vocabulary. Wattle, daub and mortar are construction nouns that no
-- verb accompanies -- "weave the wattle panel" would otherwise classify as
-- textile work and never reach a building process.
('CONSTRUCT','wattle',3),('CONSTRUCT','hurdle',3),('CONSTRUCT','daub',3),('CONSTRUCT','cob',3),
('CONSTRUCT','plaster',3),('CONSTRUCT','mortar',3),('CONSTRUCT','chink',3),('CONSTRUCT','caulk',3),
('CONSTRUCT','shingle',3),('CONSTRUCT','shake',2),('CONSTRUCT','course',2),('CONSTRUCT','batten',2),
-- A woven fish trap is a craft object. These compound nouns carry CRAFT weight so
-- "weave a fish trap" resolves to the making process rather than dead-ending in
-- hunting vocabulary that routes to no material process at all.
('CRAFT','fish trap',3),('CRAFT','eel trap',3),('CRAFT','funnel trap',3),('CRAFT','fish weir',3)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. NEW RAW SOURCES
--
-- Two gaps in the world itself, not in processing. Without salt there is no
-- preservation chain at all, and without moss there is nothing to close the gaps
-- in a log wall with. Salt is rock salt from highland stone rather than seawater,
-- because this world has no coast.
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('rock_salt',  'Rock salt',   'MATERIAL', 500, 220, TRUE, FALSE, 0),
('moss_bundle','Moss bundle', 'MATERIAL', 200, 900, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes)
VALUES ('rock_salt','Rock salt','MOUNTAIN,HIGHLAND',0.35,'STRIKING',1,3,
        'Halite in the seams of highland stone. The only salt in a world with no coast, and the whole preservation chain stands on it.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO flora_definition (flora_key, organism_type, biome_affinity, tool_required, regrowth_days, is_poisonous)
VALUES ('sphagnum_moss','HERB','WETLAND,TEMPERATE_FOREST,HIGHLAND',NULL,10,FALSE)
ON CONFLICT (flora_key) DO NOTHING;
INSERT INTO flora_drop (flora_key, item_key, yield_min, yield_max, season, tool_condition)
VALUES ('sphagnum_moss','moss_bundle',2,5,NULL,NULL) ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. NEW ITEMS
-- ---------------------------------------------------------------------------
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
-- Timber preservation
('seasoned_timber','Seasoned timber','MATERIAL',2200,9600,FALSE,FALSE,0),
('seasoned_plank','Seasoned plank','MATERIAL',1150,5200,FALSE,FALSE,0),
('charred_post','Charred post','MATERIAL',2300,9600,FALSE,FALSE,0),
('pitched_timber','Pitched timber','MATERIAL',2500,9600,FALSE,FALSE,0),
('birch_tar','Birch tar','MATERIAL',140,130,TRUE,FALSE,0),
('tarred_cordage','Tarred cordage','MATERIAL',130,300,TRUE,FALSE,0),
-- Joinery
('wooden_peg','Wooden peg','MATERIAL',45,60,TRUE,FALSE,0),
('wooden_dowel','Wooden dowel','MATERIAL',70,90,TRUE,FALSE,0),
('wooden_wedge','Wooden wedge','MATERIAL',100,120,TRUE,FALSE,0),
('mortised_beam','Mortised beam','MATERIAL',2400,9600,FALSE,FALSE,0),
('tenoned_beam','Tenoned beam','MATERIAL',2400,9600,FALSE,FALSE,0),
('scarfed_beam','Scarfed beam','MATERIAL',4900,19000,FALSE,FALSE,0),
('lapped_plank','Lapped plank','MATERIAL',2600,10000,FALSE,FALSE,0),
('dovetailed_corner','Dovetailed corner','MATERIAL',2500,10000,FALSE,FALSE,0),
('notched_log','Notched log','MATERIAL',5600,34000,FALSE,FALSE,0),
('board_panel','Board panel','MATERIAL',4000,16000,FALSE,FALSE,0),
('joined_frame','Joined frame','MATERIAL',4800,20000,FALSE,FALSE,0),
-- Building layers
('thatch_bundle','Thatch bundle','MATERIAL',1200,6000,FALSE,FALSE,0),
('wattle_panel','Wattle panel','MATERIAL',2300,12000,FALSE,FALSE,0),
('daub_mix','Daub mix','MATERIAL',2500,1800,FALSE,FALSE,0),
('daubed_panel','Daubed panel','MATERIAL',4700,13000,FALSE,FALSE,0),
('roof_shake','Roof shake','MATERIAL',210,700,TRUE,FALSE,0),
('floorboard','Floorboard','MATERIAL',1100,5000,FALSE,FALSE,0),
('sill_plate','Sill plate','MATERIAL',2400,9600,FALSE,FALSE,0),
('rafter_pole','Rafter pole','MATERIAL',2500,10000,FALSE,FALSE,0),
('ridge_beam','Ridge beam','MATERIAL',4900,19000,FALSE,FALSE,0),
('stone_course','Stone course','MATERIAL',6900,3400,FALSE,FALSE,0),
('mortar_mix','Mortar mix','MATERIAL',1600,900,FALSE,FALSE,0),
('mortared_course','Mortared course','MATERIAL',8500,4200,FALSE,FALSE,0),
('bark_roofing','Bark roofing','MATERIAL',900,3000,FALSE,FALSE,0),
('packed_floor','Packed floor','MATERIAL',4800,3000,FALSE,FALSE,0),
('moss_chinking','Moss chinking','MATERIAL',550,2000,FALSE,FALSE,0),
('door_blank','Door blank','MATERIAL',4200,16000,FALSE,FALSE,0),
('shutter_panel','Shutter panel','MATERIAL',1400,6000,FALSE,FALSE,0),
('smoke_hood','Smoke hood','MATERIAL',4000,12000,FALSE,FALSE,0),
-- Salt and preservation
('ground_salt','Ground salt','MATERIAL',450,200,TRUE,FALSE,0),
('salted_fish','Salted fish','FOOD',300,320,TRUE,FALSE,0),
('dried_fish','Dried fish','FOOD',180,220,TRUE,FALSE,0),
('smoked_fish','Smoked fish','FOOD',220,260,TRUE,FALSE,0),
('salted_meat','Salted meat','FOOD',520,620,TRUE,FALSE,0),
('dried_meat','Dried meat','FOOD',260,320,TRUE,FALSE,0),
('smoked_meat','Smoked meat','FOOD',340,420,TRUE,FALSE,0),
('smoked_fowl','Smoked fowl','FOOD',190,240,TRUE,FALSE,0),
('rendered_tallow','Rendered tallow','MATERIAL',340,360,TRUE,FALSE,0),
('pemmican','Pemmican','FOOD',420,380,TRUE,FALSE,0),
('dried_mushroom','Dried mushroom','FOOD',40,70,TRUE,FALSE,0),
('preserved_berries','Preserved berries','FOOD',300,280,TRUE,FALSE,0),
('dried_herb_bundle','Dried herb bundle','MATERIAL',30,60,TRUE,FALSE,0),
('salted_hide','Salted hide','MATERIAL',900,1800,FALSE,FALSE,0),
-- Fish processing
('gutted_fish','Gutted fish','FOOD',340,380,TRUE,FALSE,0),
('fish_fillet','Fish fillet','FOOD',150,160,TRUE,FALSE,0),
('fish_side','Split fish side','FOOD',150,170,TRUE,FALSE,0),
('fish_skin','Fish skin','MATERIAL',55,120,TRUE,FALSE,0),
('fish_skin_leather','Fish skin leather','MATERIAL',60,140,TRUE,FALSE,0),
('fish_glue','Fish glue','MATERIAL',130,120,TRUE,FALSE,0),
-- Bow production
('bow_stave','Bow stave','MATERIAL',900,4000,FALSE,FALSE,0),
('tillered_bow_stave','Tillered bow stave','MATERIAL',700,3200,FALSE,FALSE,0),
('sinew_backing','Sinew backing','MATERIAL',100,120,TRUE,FALSE,0),
('backed_bow_stave','Backed bow stave','MATERIAL',850,3400,FALSE,FALSE,0),
('bowstring','Bowstring','MATERIAL',60,80,TRUE,FALSE,0),
('hunting_bow','Hunting bow','TOOL',880,3600,FALSE,TRUE,0),
('arrow_shaft','Arrow shaft','MATERIAL',45,120,TRUE,FALSE,0),
('fletched_shaft','Fletched shaft','MATERIAL',50,130,TRUE,FALSE,0),
('stone_arrowhead','Stone arrowhead','MATERIAL',25,15,TRUE,FALSE,0),
('bone_arrowhead','Bone arrowhead','MATERIAL',20,14,TRUE,FALSE,0),
('hunting_arrow','Hunting arrow','TOOL',75,150,TRUE,FALSE,0),
('hide_glue','Hide glue','MATERIAL',120,110,TRUE,FALSE,0),
('arrow_quiver','Arrow quiver','CONTAINER',400,3000,FALSE,TRUE,0),
-- Leather and armour
('fleshed_hide','Fleshed hide','MATERIAL',850,1700,FALSE,FALSE,0),
('dehaired_hide','Dehaired hide','MATERIAL',800,1600,FALSE,FALSE,0),
('rawhide','Rawhide','MATERIAL',500,900,FALSE,FALSE,0),
('hardened_leather','Hardened leather','MATERIAL',700,1900,FALSE,FALSE,0),
('leather_lamella','Leather lamella','MATERIAL',60,60,TRUE,FALSE,0),
('leather_panel','Leather panel','MATERIAL',900,2600,FALSE,FALSE,0),
('leather_armor','Leather armour','CLOTHING',3700,12000,FALSE,TRUE,2),
('leather_bracer','Leather bracer','CLOTHING',300,900,FALSE,TRUE,1),
('leather_helm_cap','Leather helm cap','CLOTHING',400,1200,FALSE,TRUE,1),
('leather_boot_sole','Leather boot sole','MATERIAL',200,400,TRUE,FALSE,0),
('fur_lining','Fur lining','CLOTHING',800,3600,FALSE,TRUE,3),
-- Textiles
('wool_yarn','Wool yarn','MATERIAL',120,300,TRUE,FALSE,0),
('wool_cloth','Wool cloth','MATERIAL',400,1600,FALSE,FALSE,0),
('felt_sheet','Felt sheet','MATERIAL',500,2000,FALSE,FALSE,0),
('reed_mat','Reed mat','MATERIAL',900,6000,FALSE,FALSE,0),
('withy_rope','Withy rope','MATERIAL',600,1600,TRUE,FALSE,0),
-- Tools
('stone_adze','Stone adze','TOOL',1200,1600,FALSE,TRUE,0),
('stone_chisel','Stone chisel','TOOL',420,400,FALSE,TRUE,0),
('wooden_mallet','Wooden mallet','TOOL',900,1400,FALSE,TRUE,0),
('wooden_spoon','Wooden spoon','TOOL',60,90,TRUE,FALSE,0),
('wooden_bowl','Wooden bowl','CONTAINER',400,900,FALSE,TRUE,0),
('bone_awl','Bone awl','TOOL',25,30,TRUE,FALSE,0),
('bone_fish_hook','Bone fish hook','TOOL',8,8,TRUE,FALSE,0),
('antler_flaker','Antler pressure flaker','TOOL',180,200,FALSE,TRUE,0),
('wooden_shovel','Wooden shovel','TOOL',1200,3000,FALSE,TRUE,0),
('digging_stick','Digging stick','TOOL',600,1400,FALSE,TRUE,0),
('bone_comb','Bone comb','TOOL',40,60,TRUE,FALSE,0),
('drop_spindle','Drop spindle','TOOL',120,300,FALSE,TRUE,0),
('bone_scraper','Bone scraper','TOOL',90,100,TRUE,FALSE,0),
-- Containers
('leather_pouch','Leather pouch','CONTAINER',220,1200,FALSE,TRUE,0),
('bark_container','Bark container','CONTAINER',200,1600,FALSE,TRUE,0),
('wooden_trough','Wooden trough','CONTAINER',3000,14000,FALSE,FALSE,0),
('waterskin','Waterskin','CONTAINER',350,1200,FALSE,TRUE,0),
('fish_trap','Fish trap','TOOL',1400,12000,FALSE,FALSE,0),
('burden_basket','Burden basket','CONTAINER',1200,24000,FALSE,TRUE,0),
-- Light
('tallow_candle','Tallow candle','MATERIAL',90,90,TRUE,FALSE,0),
('rush_light','Rush light','MATERIAL',25,40,TRUE,FALSE,0),
('oil_lamp','Oil lamp','TOOL',700,900,FALSE,TRUE,0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('arrow_quiver',3000,3400),('leather_pouch',2500,1800),('bark_container',3000,2200),
('wooden_trough',20000,18000),('waterskin',3000,2600),('burden_basket',24000,30000),
('wooden_bowl',1500,1200)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('leather_armor','TORSO','OUTER'),
('leather_bracer','HAND_LEFT','ATTACHED'),('leather_bracer','HAND_RIGHT','ATTACHED'),
('leather_helm_cap','HEAD','CLOTHING'),
('fur_lining','TORSO','CLOTHING'),
('hunting_bow','HAND_RIGHT','ATTACHED'),('hunting_bow','BACK','CARRIED'),
('stone_adze','HAND_RIGHT','ATTACHED'),('stone_chisel','HAND_RIGHT','ATTACHED'),
('wooden_mallet','HAND_RIGHT','ATTACHED'),('wooden_shovel','HAND_RIGHT','ATTACHED'),
('digging_stick','HAND_RIGHT','ATTACHED'),('antler_flaker','HAND_RIGHT','ATTACHED'),
('drop_spindle','HAND_RIGHT','ATTACHED'),('oil_lamp','HAND_LEFT','CARRIED'),
('arrow_quiver','BACK','CARRIED'),('arrow_quiver','WAIST','ATTACHED'),
('leather_pouch','WAIST','ATTACHED'),('bark_container','HAND_LEFT','CARRIED'),
('waterskin','WAIST','ATTACHED'),('burden_basket','BACK','CARRIED'),
('wooden_bowl','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. PROCESSES
--
-- Written as DRAFT by the column default. Nothing below executes until the
-- review at the foot of this file clears it.
-- ---------------------------------------------------------------------------

-- --- Timber preservation ---------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('season_timber','Season structural timber','seasoned_timber',1,1,NULL,FALSE,FALSE,240,'woodworking','PROCESS','season the timber,air dry the timber,cure the timber,stack and sticker,season,dry,cure',
 'You stack the timber clear of the ground with air between every course, and leave it to give up its water in its own time.'),
('season_plank','Season planks','seasoned_plank',2,2,NULL,FALSE,FALSE,200,'woodworking','PROCESS','season the plank,dry the planks,sticker the planks,season,dry',
 'You lay the boards out with spacers between them. Wet wood moves after it is fitted, and moves whatever you fitted it to.'),
('char_post','Char a post','charred_post',1,1,NULL,TRUE,FALSE,60,'woodworking','PROCESS','char the post,scorch the post,char the timber,burn the surface,char,scorch,harden',
 'You turn the post in the flame until the outside is black and brittle and the wood beneath is dry and hard. What is already burnt does not rot.'),
('pitch_timber','Pitch-coat timber','pitched_timber',1,1,NULL,FALSE,FALSE,45,'woodworking','PROCESS','pitch the timber,tar the timber,coat the timber,tar,coat',
 'You work hot pitch into the grain until the surface stops drinking it. Water will bead on it now instead of soaking in.'),
('render_birch_tar','Render birch tar','birch_tar',1,2,NULL,TRUE,FALSE,150,'fire','PROCESS','birch tar,render tar,dry distil the bark,tar,render',
 'You pack the bark tight, bury it, and burn above it. What runs out at the bottom is black, stinking, and worth every hour.'),
('tar_cordage','Tar cordage','tarred_cordage',1,2,NULL,FALSE,FALSE,35,'textiles','PROCESS','tar the cordage,tarred cord,waterproof the rope,tar,soak',
 'You draw the cord through warm pitch and wipe the excess off between your fingers. It stiffens, blackens, and stops caring about rain.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('season_timber','structural_timber',1),
('season_plank','timber_plank',2),
('char_post','structural_timber',1),
('pitch_timber','structural_timber',1),('pitch_timber','pine_pitch',2),
('render_birch_tar','bark_sheet',6),
('tar_cordage','fiber_cordage',2),('tar_cordage','pine_pitch',1);

-- --- Joinery ---------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('carve_pegs','Carve wooden pegs','wooden_peg',4,10,'CUTTING',FALSE,FALSE,35,'woodworking','CRAFT','wooden peg,treenail,carve pegs,whittle pegs,carve,whittle',
 'You split the stock down and work each piece to a taper, a little fat at the head. Driven into a wet hole they will swell and never come out.'),
('turn_dowels','Shave wooden dowels','wooden_dowel',6,14,'CUTTING',FALSE,FALSE,40,'woodworking','PROCESS','wooden dowel,shave a dowel,round the stock,plane,taper',
 'You pull the stock through until it runs round and even along its whole length.'),
('split_wedges','Split wedges','wooden_wedge',3,6,'CUTTING',FALSE,FALSE,25,'woodworking','PROCESS','wooden wedge,split wedges,froe the wedges,split,rive',
 'You rive the billet into wedges, each one a long flat triangle. Everything that has to be split later starts with one of these.'),
('cut_mortise','Cut a mortise','mortised_beam',1,1,'CUTTING',FALSE,FALSE,70,'woodworking','PROCESS','mortise,cut a mortise,chop a mortise,socket the beam,cut,shape',
 'You chop down into the beam and lever the waste out, walking the hole along until the socket is square-sided and true to depth.'),
('cut_tenon','Cut a tenon','tenoned_beam',1,1,'CUTTING',FALSE,FALSE,60,'woodworking','PROCESS','tenon,cut a tenon,shoulder the beam,tongue the beam,cut,shape',
 'You cut the shoulders in first, then pare down to the line until a tongue stands proud of the end of the beam.'),
('notch_log','Notch a log for corners','notched_log',1,1,'AXE',FALSE,FALSE,55,'woodworking','PROCESS','notch,saddle notch,notch the log,corner notch,hew,cut',
 'You scoop a saddle out of the underside so the log will sit down onto the one below and not roll off it.'),
('scarf_joint','Cut a scarf joint','scarfed_beam',1,1,'CUTTING',FALSE,FALSE,90,'woodworking','PROCESS','scarf,scarf joint,splice the beams,lengthen the beam,cut,shape',
 'You cut long matching slopes on both ends and peg them through. Two short beams become one long one, and the join is stronger than either.'),
('lap_joint_planks','Cut a lap joint','lapped_plank',1,1,'CUTTING',FALSE,FALSE,45,'woodworking','PROCESS','lap joint,half lap,lap the planks,cross lap,cut,shape',
 'You take half the thickness out of each piece so they cross without standing proud of one another.'),
('dovetail_corner','Cut a dovetailed corner','dovetailed_corner',1,1,'CUTTING',FALSE,FALSE,80,'woodworking','PROCESS','dovetail,dovetail the corner,box joint,corner joint,cut,shape',
 'You saw the tails first and scribe the pins from them. Cut right, the corner holds without a single peg.'),
('edge_join_boards','Edge-join boards into a panel','board_panel',1,1,'CUTTING',FALSE,FALSE,75,'woodworking','CRAFT','edge join,board panel,glue up the boards,join the boards,join,fit',
 'You true the edges until no light shows between them, then dowel and draw the boards together into one wide surface.'),
('assemble_frame','Assemble a pegged frame','joined_frame',1,1,NULL,FALSE,FALSE,85,'woodworking','CRAFT','pegged frame,assemble the frame,peg the joint,draw bore,assemble,fit,join',
 'You bring the tenon into the mortise, offset the peg hole a hair, and drive the peg. The joint pulls itself tight as it goes in.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_pegs','wooden_component',1),
('turn_dowels','timber_plank',1),
('split_wedges','wooden_component',1),
('cut_mortise','structural_timber',1),
('cut_tenon','structural_timber',1),
('scarf_joint','structural_timber',2),('scarf_joint','wooden_peg',4),
('lap_joint_planks','timber_plank',2),('lap_joint_planks','wooden_peg',2),
('dovetail_corner','timber_plank',2),
('edge_join_boards','timber_plank',3),('edge_join_boards','wooden_dowel',4),
('assemble_frame','mortised_beam',1),('assemble_frame','tenoned_beam',1),('assemble_frame','wooden_peg',4);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('notch_log','log','oak_log',1),('notch_log','log','birch_log',1),('notch_log','log','pine_log',1),
('notch_log','log','ash_log',1),('notch_log','log','maple_log',1),('notch_log','log','spruce_log',1);

-- --- Building layers -------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('bundle_thatch','Bundle thatch','thatch_bundle',1,1,NULL,FALSE,FALSE,40,'construction','CONSTRUCT','thatch bundle,bundle the reeds,yealm the reeds,thatch',
 'You comb the reeds straight, butt the ends level, and tie the bundle off. Laid right, a roof of these will shed water for years.'),
('weave_wattle_panel','Weave a wattle panel','wattle_panel',1,1,'CUTTING',FALSE,FALSE,70,'construction','CONSTRUCT','wattle panel,weave the hurdle,hurdle,wattle,wall',
 'You set the uprights and weave the rods in and out of them until the panel stands on its own and springs back when you push it.'),
('mix_daub','Mix daub','daub_mix',1,1,NULL,FALSE,FALSE,50,'construction','CONSTRUCT','daub,mix the daub,cob,clay plaster,plaster',
 'You tread clay, chopped fiber and grit together until it stops crumbling and starts holding the shape of your hand.'),
('daub_panel','Daub a wattle panel','daubed_panel',1,1,NULL,FALSE,FALSE,65,'construction','CONSTRUCT','daub the panel,wattle and daub,skim the wall,plaster the panel,daub,wall',
 'You press the daub through the weave from both sides until the panel is one solid thing and no daylight comes through it.'),
('rive_shakes','Rive roof shakes','roof_shake',3,6,'AXE',FALSE,FALSE,50,'construction','CONSTRUCT','roof shake,shingle,rive shakes,roof',
 'You split them off the block along the grain rather than sawing them. Riven wood sheds water; sawn wood drinks it.'),
('lay_floorboards','Lay floorboards','floorboard',1,2,'CUTTING',FALSE,FALSE,60,'construction','CONSTRUCT','floorboard,lay the floor,floor board,floor,lay',
 'You lay each board tight to the last and peg it down. Underfoot it goes from earth to something that sounds hollow.'),
('cut_sill_plate','Cut a sill plate','sill_plate',1,1,'AXE',FALSE,FALSE,55,'construction','CONSTRUCT','sill plate,ground sill,mudsill,sill,lay',
 'You flatten one whole face so the timber will bed down on stone without rocking. Everything above will stand on this.'),
('shape_rafter','Shape a rafter','rafter_pole',1,1,'AXE',FALSE,FALSE,50,'construction','CONSTRUCT','rafter,roof rafter,common rafter,rafter,shape',
 'You take the taper out and cut the foot to sit against the wall plate at the angle the roof wants.'),
('set_ridge_beam','Make a ridge beam','ridge_beam',1,1,'AXE',FALSE,FALSE,80,'construction','CONSTRUCT','ridge beam,ridge pole,set the ridge,ridge,beam',
 'You true a long straight timber and mark where every pair of rafters will meet it. The whole roof hangs off this one piece.'),
('course_dry_stone','Course dry stone','stone_course',1,1,'STRIKING',FALSE,FALSE,75,'construction','CONSTRUCT','dry stone,stone course,coursing,course,wall,lay',
 'You set each stone on two below it and never one on one, and pack the middle as you go. No mortar, and it will still be standing when you are not.'),
('mix_mortar','Mix mortar','mortar_mix',1,1,NULL,FALSE,FALSE,40,'construction','CONSTRUCT','mortar,mix the mortar,bedding mortar,mortar',
 'You work ash and clay together with water until it takes a trowel-load without sliding off.'),
('lay_mortared_course','Lay a mortared course','mortared_course',1,1,'STRIKING',FALSE,FALSE,90,'construction','CONSTRUCT','mortared course,bed the stone,point the wall,lay the stone,lay,wall',
 'You bed each stone down onto mortar and work the joints solid behind it. Slower than dry work, and it does not shift.'),
('lay_bark_roofing','Lay bark roofing','bark_roofing',1,1,NULL,FALSE,FALSE,55,'construction','CONSTRUCT','bark roof,birch bark roofing,lay the bark,roofing,roof',
 'You lap the sheets from the eaves upward, each one over the last, and weight them down. Birch bark will not rot and will not let water through.'),
('pack_earth_floor','Pack an earth floor','packed_floor',1,1,NULL,FALSE,FALSE,90,'construction','CONSTRUCT','packed floor,earth floor,tamp the floor,beaten floor,floor,lay',
 'You lay stone, then clay, and beat it down until it rings dull and takes no more. It will polish underfoot over the years.'),
('chink_with_moss','Chink a wall with moss','moss_chinking',1,1,NULL,FALSE,FALSE,45,'construction','CONSTRUCT','chink the wall,chinking,moss the gaps,caulk the wall,chink,caulk',
 'You drive moss into every gap between the logs with a blunt stick, working from outside and then again from inside.'),
('build_door_blank','Build a plank door','door_blank',1,1,'CUTTING',FALSE,FALSE,100,'construction','CONSTRUCT','door blank,plank door,batten door,hang a door,build,fix',
 'You lay the boards edge to edge, batten them across the back, and peg through. Heavy, and it will not sag out of square.'),
('build_shutter','Build a window shutter','shutter_panel',1,1,'CUTTING',FALSE,FALSE,50,'construction','CONSTRUCT','window shutter,shutter,close the window,build,fix',
 'You board the opening over and hang the panel on leather straps so it can be swung shut against the weather.'),
('raise_smoke_hood','Raise a smoke hood','smoke_hood',1,1,NULL,FALSE,FALSE,120,'construction','CONSTRUCT','smoke hood,smoke bay,chimney,flue,raise,build',
 'You corbel clay and stone out above the hearth into a hood that draws. The air at head height stops stinging.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('bundle_thatch','reed_bundle',5),('bundle_thatch','fiber_cordage',1),
('weave_wattle_panel','hazel_rod',6),('weave_wattle_panel','vine',3),
('mix_daub','clay_lump',2),('mix_daub','plant_fiber',4),
('daub_panel','wattle_panel',1),('daub_panel','daub_mix',1),
('rive_shakes','timber_plank',1),
('lay_floorboards','seasoned_plank',2),
('cut_sill_plate','structural_timber',1),
('shape_rafter','structural_timber',1),
('set_ridge_beam','structural_timber',2),
('course_dry_stone','construction_stone',4),
('mix_mortar','wood_ash',4),('mix_mortar','clay_lump',1),
('lay_mortared_course','construction_stone',4),('lay_mortared_course','mortar_mix',1),
('lay_bark_roofing','bark_sheet',8),
('pack_earth_floor','clay_lump',3),('pack_earth_floor','field_stone',2),
('chink_with_moss','moss_bundle',3),
('build_door_blank','timber_plank',3),('build_door_blank','wooden_peg',6),
('build_shutter','timber_plank',1),('build_shutter','leather_cord',2),
('raise_smoke_hood','clay_lump',2),('raise_smoke_hood','construction_stone',1);

-- --- Salt and food preservation --------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('grind_salt','Grind rock salt','ground_salt',1,2,'STRIKING',FALSE,FALSE,25,'food_preservation','PROCESS','grind the salt,crush the salt,mill the salt,grind,crush,pound',
 'You pound the crystal down between two stones until it runs loose and white. Nothing else in the world keeps meat like this does.'),
('salt_fish','Salt fish','salted_fish',1,2,NULL,FALSE,FALSE,60,'food_preservation','PROCESS','salt the fish,salted fish,pack in salt,dry salt the fish,salt,cure',
 'You layer fish and salt and fish again, and set a weight on top. What comes out is stiff, pale, and will still be food in six months.'),
('brine_fish','Brine fish','salted_fish',2,3,NULL,FALSE,TRUE,80,'food_preservation','PROCESS','brine the fish,pickle the fish,steep in brine,brine,soak',
 'You mix the salt until an egg would float and lay the fish in it. Slower than dry salting, and it keeps the flesh softer.'),
('dry_fish','Dry fish','dried_fish',1,2,NULL,FALSE,FALSE,180,'food_preservation','PROCESS','dry the fish,dried fish,rack the fish,air dry the fish,dry',
 'You hang them where the wind gets at them and the sun does not. In a week they are boards, and boards do not spoil.'),
('smoke_fish','Smoke fish','smoked_fish',1,2,NULL,TRUE,FALSE,200,'food_preservation','PROCESS','smoke the fish,smoked fish,cold smoke the fish,hang in the smoke,smoke',
 'You keep the fire low and smoky and the fish well above it. The smoke does half the keeping and all of the flavour.'),
('salt_meat','Salt meat','salted_meat',1,1,NULL,FALSE,FALSE,70,'food_preservation','PROCESS','salt the meat,salted meat,pack the meat in salt,corn the meat,salt,cure',
 'You rub salt into every surface and every fold, then bury the joint in more of it. It will draw water for days.'),
('dry_meat','Dry meat','dried_meat',1,2,'CUTTING',FALSE,FALSE,200,'food_preservation','PROCESS','dry the meat,dried meat,rack the meat,jerk the meat,dry,cut',
 'You cut it with the grain, thin enough to see light through, and hang it out of the sun. It goes dark and hard and weighs almost nothing.'),
('smoke_meat','Smoke meat','smoked_meat',1,1,NULL,TRUE,FALSE,220,'food_preservation','PROCESS','smoke the meat,smoked meat,hang the meat in smoke,smoke',
 'You hang it high in the smoke and feed the fire green wood. Flies will not touch it afterward.'),
('smoke_fowl','Smoke fowl','smoked_fowl',1,2,NULL,TRUE,FALSE,150,'food_preservation','PROCESS','smoke the fowl,smoked bird,smoke the bird,smoke',
 'You split the birds flat and hang them breast-out over the smoke.'),
('render_tallow','Render tallow','rendered_tallow',1,2,NULL,TRUE,FALSE,90,'food_preservation','PROCESS','render tallow,render the fat,try out the fat,suet,render,boil',
 'You chop the fat small and hold it just under a boil until it runs clear and the solids sink. Poured off and cooled it turns white and hard.'),
('make_pemmican','Make pemmican','pemmican',1,2,NULL,FALSE,FALSE,80,'food_preservation','PROCESS','pemmican,winter ration,travel food,pound the dried meat,pound,process',
 'You pound the dried meat to a floss, work tallow and berries through it, and press it solid. It is not pleasant and it will keep you alive.'),
('dry_mushrooms','Dry mushrooms','dried_mushroom',2,4,NULL,FALSE,FALSE,150,'food_preservation','PROCESS','dry the mushrooms,dried mushroom,thread the mushrooms,dry',
 'You slice and thread them and hang the string near the fire but not in it. Dried, they weigh nothing and come back with water.'),
('preserve_berries','Preserve berries','preserved_berries',1,2,NULL,FALSE,FALSE,70,'food_preservation','PROCESS','preserve the berries,honeyed berries,berry preserve,preserve,process',
 'You crush the fruit into honey until it is coated through. Sugar keeps as surely as salt does, and tastes better doing it.'),
('dry_herbs','Dry herbs','dried_herb_bundle',1,2,NULL,FALSE,FALSE,120,'food_preservation','PROCESS','dry the herbs,herb bundle,hang the herbs,dry,season',
 'You tie the stems in small bunches and hang them heads-down in the dark. Dried fast and dark, they keep their colour and their smell.'),
('salt_hide','Salt a hide','salted_hide',1,1,NULL,FALSE,FALSE,50,'textiles','PROCESS','salt the hide,salted hide,dry salt the hide,salt',
 'You lay the hide flesh-up and cover it in salt to the depth of a thumb. It will keep now until you have time to tan it properly.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('grind_salt','rock_salt',2),
('salt_fish','raw_fish',2),('salt_fish','ground_salt',1),
('brine_fish','raw_fish',2),('brine_fish','ground_salt',2),
('dry_fish','raw_fish',2),
('smoke_fish','raw_fish',2),
('salt_meat','raw_game_meat',1),('salt_meat','ground_salt',1),
('dry_meat','raw_game_meat',1),
('smoke_meat','raw_game_meat',1),
('smoke_fowl','raw_fowl_meat',2),
('render_tallow','animal_fat',3),
('make_pemmican','dried_meat',2),('make_pemmican','rendered_tallow',1),('make_pemmican','wild_berries',1),
('preserve_berries','wild_berries',3),('preserve_berries','raw_honey',1),
('salt_hide','ground_salt',1);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('dry_mushrooms','mushroom','chanterelle',4),('dry_mushrooms','mushroom','porcini',4),
('dry_mushrooms','mushroom','oyster_mushroom',4),('dry_mushrooms','mushroom','lions_mane',4),
('dry_herbs','herb','mint_sprig',6),('dry_herbs','herb','nettle_leaf',6),
('dry_herbs','herb','yarrow_bundle',6),('dry_herbs','herb','comfrey_leaf',6),
('dry_herbs','herb','plantain_leaf',6),
('salt_hide','hide','animal_hide',1),('salt_hide','hide','deer_hide',1),('salt_hide','hide','boar_hide',1);

-- --- Fish processing -------------------------------------------------------
-- Category is honest rather than tidy: gutting and filleting classify as HUNT
-- because that is what the vocabulary does with 'gut' and 'fillet', and splitting
-- classifies as PROCESS because that is what it does with 'split'. A process
-- whose declared category disagrees with its own verbs is unreachable, which is
-- the exact defect V55 was written to catch.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('gut_fish','Gut a fish','gutted_fish',1,1,'CUTTING',FALSE,FALSE,10,'butchery','HUNT','gut the fish,clean the fish,dress the fish,gut,clean',
 'You open it from vent to gill, draw everything out in one handful, and scrape the dark line from along the backbone.'),
('fillet_fish','Fillet a fish','fish_fillet',1,2,'CUTTING',FALSE,FALSE,15,'butchery','HUNT','fillet the fish,take the fillets,bone the fish,fillet',
 'You run the blade along the spine from head to tail and lift the side off whole, then turn it and do the same again.'),
('split_fish','Split a fish for drying','fish_side',1,2,'CUTTING',FALSE,FALSE,15,'butchery','PROCESS','split the fish,butterfly the fish,split for drying,kipper the fish,split',
 'You open it flat along the backbone without cutting it in two, so it will hang and dry evenly instead of rotting at the thick end.'),
('skin_fish','Skin a fish','fish_skin',1,2,'CUTTING',FALSE,FALSE,12,'butchery','HUNT','skin the fish,fish skin,peel the skin,skin',
 'You work a thumb under the skin at the tail and pull it away in one piece. It comes off cleaner than you expect.'),
('cure_fish_skin','Cure fish skin into leather','fish_skin_leather',2,4,NULL,FALSE,TRUE,180,'textiles','PROCESS','fish leather,fish skin leather,cure the fish skin,cure',
 'You soak the skins until the scales lift away, then work them soft as they dry. Fish leather is thin, strong, and smells of nothing once it is finished.'),
('boil_fish_glue','Boil fish glue','fish_glue',2,3,NULL,TRUE,FALSE,120,'fire','PROCESS','fish glue,isinglass,boil the skins down,boil,render',
 'You boil skin and bone down for hours until what is left goes tacky between your fingers and stringy when you pull them apart.'),
('press_fish_oil','Press fish oil','fish_oil',2,4,NULL,TRUE,FALSE,100,'fire','PROCESS','fish oil,render fish oil,try out the oil,press the oil,render',
 'You heat the trimmings until the oil separates and skim it off the top. It burns, it waterproofs, and it goes rancid if you leave it.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('gut_fish','raw_fish',1),
('fillet_fish','gutted_fish',1),
('split_fish','gutted_fish',1),
('skin_fish','raw_fish',1),
('cure_fish_skin','fish_skin',6),('cure_fish_skin','lye_solution',1),
('boil_fish_glue','fish_skin',6),('boil_fish_glue','fish_bone',8),
('press_fish_oil','raw_fish',3);

-- --- Bow production --------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
-- Keywords deliberately do NOT include bare 'split' or 'rive': both collide with
-- split_planks, which also splits a log, and the alphabetical tiebreak would hand
-- "split the log into planks" to this process. 'stave' is what actually
-- distinguishes bow-riving, so every keyword carries it.
('rive_bow_stave','Rive a bow stave','bow_stave',2,4,'AXE',FALSE,FALSE,70,'tools','PROCESS','bow stave,bow blank,rive a stave,split a stave,rive the stave,stave',
 'You split the log down through the heart and again, taking staves from where the grain runs straightest. Most of the tree is not good enough.'),
('tiller_bow_stave','Tiller a bow stave','tillered_bow_stave',1,1,'CUTTING',FALSE,FALSE,180,'tools','PROCESS','tiller the bow,tiller,even the limbs,scrape the limbs,tiller,scrape',
 'You bend it, watch where it does not, scrape there, and bend it again. Hours of this, and one careless pass ruins all of them.'),
('prepare_sinew_backing','Prepare sinew backing','sinew_backing',1,1,NULL,FALSE,FALSE,60,'tools','PROCESS','sinew backing,shred the sinew,pound the sinew,pound,shred',
 'You dry the sinew hard and pound it until it comes apart into fibres finer than thread.'),
('back_bow_with_sinew','Back a bow with sinew','backed_bow_stave',1,1,NULL,FALSE,FALSE,150,'tools','CRAFT','back the bow,sinew backed bow,glue the backing,lay on the sinew,glue',
 'You lay the wet fibres along the back of the bow in overlapping courses and leave it a month to dry. It will throw harder and it will not break.'),
('twist_bowstring','Twist a bowstring','bowstring',1,2,NULL,FALSE,FALSE,50,'tools','PROCESS','bowstring,bow string,twist a string,reverse twist,twist,ply',
 'You lay the strands in opposite twists so the string tightens on itself and does not unwind under load. Loop one end, timber hitch the other.'),
('assemble_bow','Assemble a bow','hunting_bow',1,1,'CUTTING',FALSE,FALSE,90,'tools','CRAFT','assemble the bow,hunting bow,finish the bow,string the bow,assemble,fit',
 'You cut the nocks, string it, and draw it slowly to full for the first time. It holds, and the whole thing finally becomes a weapon.'),
('shave_arrow_shafts','Shave arrow shafts','arrow_shaft',4,8,'CUTTING',FALSE,FALSE,60,'tools','PROCESS','arrow shafts,arrow shaft,shave the shafts,straighten the shafts,shafts,shaft,shave,plane',
 'You shave each one round and roll it on a flat stone to find the bend, then heat and straighten until it rolls true.'),
('fletch_arrows','Fletch arrows','fletched_shaft',2,4,NULL,FALSE,FALSE,55,'tools','CRAFT','fletch,fletching,feather the arrows,bind the fletching,fletch',
 'You split the quills, set three to a shaft with an even spiral, and bind each end down with sinew.'),
('knap_arrowheads','Knap stone arrowheads','stone_arrowhead',4,10,'STRIKING',FALSE,FALSE,70,'tools','PROCESS','arrowhead,arrow point,knap arrowheads,pressure flake,knap,flake',
 'You take the flake down with pressure rather than blows, working alternate faces until the point is thin, straight and cruelly sharp.'),
('carve_bone_points','Carve bone points','bone_arrowhead',2,3,'CUTTING',FALSE,FALSE,45,'tools','CRAFT','bone point,bone arrowhead,carve points,barbed point,carve,whittle',
 'You grind the splinter to a long triangle and cut a barb into one edge. Slower than stone to make and it does not shatter on bone.'),
('assemble_arrows','Assemble arrows','hunting_arrow',2,3,NULL,FALSE,FALSE,50,'tools','CRAFT','make arrows,assemble arrows,haft the point,arrow,haft,assemble',
 'You seat each point in the split shaft, pitch it, and bind it down. Twenty of these is a season of meat.'),
('boil_hide_glue','Boil hide glue','hide_glue',2,4,NULL,TRUE,FALSE,130,'fire','PROCESS','hide glue,bone glue,boil glue,size,boil,render',
 'You boil the scraps down until the liquor ropes off the stick. Cooled, it sets to amber and comes back with heat as often as you like.'),
('weave_quiver','Weave a quiver','arrow_quiver',1,1,'CUTTING',FALSE,FALSE,60,'tools','CRAFT','quiver,arrow quiver,make a quiver,weave a quiver,make',
 'You roll the bark into a tube, stitch the seam, and fit a base. Arrows carried loose get broken; arrows carried here do not.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('tiller_bow_stave','bow_stave',1),
('prepare_sinew_backing','animal_sinew',4),
('back_bow_with_sinew','tillered_bow_stave',1),('back_bow_with_sinew','sinew_backing',1),('back_bow_with_sinew','hide_glue',1),
('twist_bowstring','animal_sinew',3),('twist_bowstring','plant_fiber',2),
('assemble_bow','backed_bow_stave',1),('assemble_bow','bowstring',1),
('fletch_arrows','arrow_shaft',4),('fletch_arrows','feather',8),('fletch_arrows','animal_sinew',1),
('knap_arrowheads','precision_tool_stone',1),
('assemble_arrows','fletched_shaft',3),('assemble_arrows','pine_pitch',1),
('boil_hide_glue','animal_bone',3),
('weave_quiver','bark_sheet',4),('weave_quiver','leather_cord',2);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('rive_bow_stave','log','oak_log',1),('rive_bow_stave','log','birch_log',1),('rive_bow_stave','log','pine_log',1),
('rive_bow_stave','log','ash_log',1),('rive_bow_stave','log','maple_log',1),('rive_bow_stave','log','spruce_log',1),
('shave_arrow_shafts','shoot','hazel_rod',2),('shave_arrow_shafts','shoot','willow_branch',2),
('carve_bone_points','bone','animal_bone',1),('carve_bone_points','bone','fish_bone',4),
('assemble_arrows','point','stone_arrowhead',3),('assemble_arrows','point','bone_arrowhead',3);

-- --- Leather and armour ----------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('flesh_hide','Flesh a hide','fleshed_hide',1,1,'CUTTING',FALSE,FALSE,70,'textiles','PROCESS','flesh the hide,scrape the hide,take the flesh off,flesh,scrape',
 'You draw the blade over the hide at an angle, taking off fat and membrane without cutting into the skin. It has to be clean or nothing after this works.'),
('dehair_hide','Dehair a hide','dehaired_hide',1,1,'CUTTING',FALSE,TRUE,150,'textiles','PROCESS','dehair the hide,buck the hide,lime the hide,strip the hair,dehair,strip',
 'You leave it in the lye until the hair slips at a touch, then push it all off with the back of the blade.'),
('make_rawhide','Make rawhide','rawhide',1,1,NULL,FALSE,FALSE,180,'textiles','PROCESS','rawhide,stretch the hide,dry the hide hard,dry',
 'You lace it into a frame and let it dry drum-tight. Untanned, it is board-stiff and near unbreakable, and it goes soft again if it gets wet.'),
('harden_leather','Harden leather','hardened_leather',1,1,NULL,TRUE,FALSE,90,'textiles','PROCESS','harden the leather,cuir bouilli,boiled leather,wax the leather,harden,boil',
 'You take it just hot enough to shrink and no hotter, shape it, and let it set. It comes out rigid and holds whatever curve you gave it.'),
('cut_lamellae','Cut leather lamellae','leather_lamella',6,10,'CUTTING',FALSE,FALSE,55,'textiles','PROCESS','lamella,lamellae,cut the plates,armour scale,cut,slice',
 'You cut plate after plate to the same pattern and punch the lacing holes while the leather is still on the board.'),
('sew_leather_panel','Sew a leather panel','leather_panel',1,1,'CUTTING',FALSE,FALSE,80,'textiles','CRAFT','leather panel,sew a panel,stitch the leather,sew,stitch',
 'You punch ahead of the needle and saddle-stitch the seam with two ends of one thread, so a broken stitch does not run.'),
('assemble_lamellar_armor','Assemble lamellar armour','leather_armor',1,1,NULL,FALSE,FALSE,240,'textiles','CRAFT','lamellar armour,leather armour,lace the lamellae,armour,assemble,lash',
 'You lace the plates edge over edge into rows and the rows to each other, tight enough to hold shape and loose enough to bend when you do.'),
('sew_bracer','Sew a bracer','leather_bracer',1,1,'CUTTING',FALSE,FALSE,45,'textiles','CRAFT','bracer,vambrace,arm guard,wrist guard,sew,stitch',
 'You cut it to your forearm, punch lacing holes down both edges, and lace it on. The string will stop taking skin off you now.'),
('sew_helm_cap','Sew a leather helm cap','leather_helm_cap',1,1,'CUTTING',FALSE,FALSE,60,'textiles','CRAFT','leather helm,helm cap,skull cap,sew,stitch',
 'You cut four gores and stitch them into a dome that sits close to the skull.'),
('cut_boot_soles','Cut boot soles','leather_boot_sole',1,2,'CUTTING',FALSE,FALSE,35,'textiles','PROCESS','boot soles,boot sole,cut soles,shoe sole,soles,sole,cut,slice',
 'You cut them oversize from the thickest part of the hide, because a sole wears through long before the upper does.'),
('line_with_fur','Line a garment with fur','fur_lining',1,1,'CUTTING',FALSE,FALSE,120,'textiles','CRAFT','fur lining,line with fur,sew in the fur,fur liner,fur,sew',
 'You sew the pelts fur-inward with the grain all running one way, so it lies flat and moves warm air toward you rather than away.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dehair_hide','fleshed_hide',1),('dehair_hide','lye_solution',1),
('make_rawhide','dehaired_hide',1),
('harden_leather','tanned_leather',1),('harden_leather','beeswax',2),
('cut_lamellae','hardened_leather',1),
('sew_leather_panel','tanned_leather',1),('sew_leather_panel','leather_cord',2),
('assemble_lamellar_armor','leather_lamella',40),('assemble_lamellar_armor','leather_cord',8),('assemble_lamellar_armor','leather_panel',1),
('sew_bracer','hardened_leather',1),('sew_bracer','leather_cord',1),
('sew_helm_cap','hardened_leather',1),('sew_helm_cap','leather_cord',1),
('cut_boot_soles','rawhide',1),
('line_with_fur','animal_sinew',2);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('flesh_hide','hide','animal_hide',1),('flesh_hide','hide','deer_hide',1),('flesh_hide','hide','boar_hide',1),
('flesh_hide','hide','salted_hide',1),
('line_with_fur','pelt','rabbit_pelt',4),('line_with_fur','pelt','fox_pelt',2),
('line_with_fur','pelt','wolf_pelt',1),('line_with_fur','pelt','lynx_pelt',1);

-- --- Textiles --------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('strip_bark_cordage','Twist bark cordage','fiber_cordage',2,3,NULL,FALSE,FALSE,45,'textiles','PROCESS','bark cordage,bast cord,inner bark cord,twist the bast,bast,twist',
 'You take the inner bark off in long ribbons, split them fine, and twist. Bast cord is coarse and it is strong.'),
('spin_wool_yarn','Spin wool yarn','wool_yarn',2,3,NULL,FALSE,FALSE,70,'textiles','PROCESS','spin wool,wool yarn,yarn,spin,ply',
 'You draft the wool out with one hand and let the spindle do the rest, joining a new lock in before the last runs out.'),
('weave_wool_cloth','Weave wool cloth','wool_cloth',1,1,NULL,FALSE,FALSE,180,'textiles','PROCESS','wool cloth,weave wool,woollen cloth,weave',
 'You weight the warp, pass the weft, and beat it up. A hand-span an hour, and a whole day is a sleeve.'),
('felt_wool','Felt wool','felt_sheet',1,1,NULL,FALSE,TRUE,90,'textiles','PROCESS','felt the wool,mat the wool,full the wool,felt',
 'You wet it, work it, and keep working it long past when your arms want to stop. The fibres lock and it stops being wool.'),
('weave_reed_mat','Weave a reed mat','reed_mat',1,1,NULL,FALSE,FALSE,80,'textiles','PROCESS','reed mat,sleeping mat,weave a mat,plait the reeds,plait',
 'You plait the reeds flat and wide. Between you and cold ground it is worth more than another blanket.'),
('plait_withy_rope','Plait a withy rope','withy_rope',1,2,'CUTTING',FALSE,FALSE,60,'textiles','PROCESS','withy rope,willow rope,plait the withies,withy,plait',
 'You steam the withies limber and plait three strands hard against each other. Heavy, stiff, and it will hold a roof beam.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('strip_bark_cordage','bark_sheet',4),
('spin_wool_yarn','wool_tuft',6),
('weave_wool_cloth','wool_yarn',4),
('felt_wool','wool_tuft',8),
('weave_reed_mat','reed_bundle',4),
('plait_withy_rope','willow_branch',2);

-- --- Tools -----------------------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('haft_stone_adze','Haft a stone adze','stone_adze',1,1,'CUTTING',FALSE,FALSE,75,'tools','CRAFT','stone adze,adze,haft an adze,make an adze,haft,mount',
 'You set the blade across the haft rather than in line with it. Swung down the length of a log it hollows where an axe would only bite.'),
('haft_stone_chisel','Haft a stone chisel','stone_chisel',1,1,'CUTTING',FALSE,FALSE,55,'tools','CRAFT','stone chisel,chisel,haft a chisel,make a chisel,haft,fit',
 'You seat the narrow blade in a short handle you can hold steady and hit at the same time.'),
('carve_wooden_mallet','Carve a wooden mallet','wooden_mallet',1,1,'CUTTING',FALSE,FALSE,50,'tools','CRAFT','wooden mallet,mallet,beetle,carve a mallet,carve',
 'You cut head and handle from one piece so there is no joint to fail, and leave the head heavy.'),
('carve_wooden_spoon','Carve a wooden spoon','wooden_spoon',1,2,'CUTTING',FALSE,FALSE,40,'tools','CRAFT','wooden spoon,spoon,carve a spoon,whittle a spoon,carve,whittle',
 'You hollow the bowl first while there is still wood to hold, then take the handle down to something your hand likes.'),
('carve_wooden_bowl','Carve a wooden bowl','wooden_bowl',1,1,'CUTTING',FALSE,FALSE,120,'tools','CRAFT','wooden bowl,bowl,hollow a bowl,carve a bowl,carve,hollow',
 'You burn and scrape the middle out by turns, working across the grain, until the walls are even and thin enough to lift easily.'),
('grind_bone_awl','Grind a bone awl','bone_awl',1,2,'CUTTING',FALSE,FALSE,30,'tools','PROCESS','bone awl,awl,grind an awl,make an awl,make,grind',
 'You take a splinter down to a long slow point on wet stone. It will punch leather that a needle will not.'),
('carve_fish_hook','Carve a fish hook','bone_fish_hook',1,3,'CUTTING',FALSE,FALSE,45,'tools','CRAFT','fish hook,bone hook,carve a hook,barbed hook,carve,whittle',
 'You cut the blank as a V, work the barb in with the point of the knife, and groove the shank for the line.'),
('make_antler_flaker','Make an antler pressure flaker','antler_flaker',1,1,'CUTTING',FALSE,FALSE,35,'tools','CRAFT','pressure flaker,antler flaker,flaking tool,make a flaker,make,fit',
 'You cut a tine to length and blunt the tip square. Everything fine that stone can be made to do is done with one of these.'),
('carve_wooden_shovel','Carve a wooden shovel','wooden_shovel',1,1,'CUTTING',FALSE,FALSE,90,'tools','CRAFT','wooden shovel,shovel,spade,carve a shovel,carve',
 'You dish the blade out of the plank and leave a spine down the middle so it will not fold on the first stone it meets.'),
('make_digging_stick','Make a digging stick','digging_stick',1,1,'CUTTING',FALSE,FALSE,25,'tools','CRAFT','digging stick,dibber,fire harden the point,make,fit',
 'You point one end and turn it in the embers until the tip is hard and black. The simplest tool there is, and it never stops being useful.'),
('carve_bone_comb','Carve a bone comb','bone_comb',1,1,'CUTTING',FALSE,FALSE,60,'tools','CRAFT','bone comb,comb,carve a comb,cut the teeth,carve',
 'You saw the teeth in one at a time, all the way down, and any one of them going wrong wastes the whole piece.'),
('make_drop_spindle','Make a drop spindle','drop_spindle',1,1,'CUTTING',FALSE,FALSE,40,'tools','CRAFT','drop spindle,spindle,whorl,make a spindle,make,fit',
 'You bore the whorl through the centre and seat the shaft tight. Off-centre by a little and it wobbles and fights you all day.'),
('grind_bone_scraper','Grind a bone scraper','bone_scraper',1,2,'CUTTING',FALSE,FALSE,35,'tools','PROCESS','bone scraper,scraper,hide scraper,grind a scraper,grind,make',
 'You take one edge of the blade down to a bevel and leave it just short of sharp, so it takes flesh off a hide without cutting through it.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('haft_stone_adze','precision_tool_stone',1),('haft_stone_adze','wooden_component',1),('haft_stone_adze','leather_cord',2),
('haft_stone_chisel','precision_tool_stone',1),('haft_stone_chisel','wooden_component',1),
('carve_wooden_mallet','wooden_component',2),
('carve_wooden_spoon','wooden_component',1),
('carve_wooden_bowl','wooden_component',1),
('carve_wooden_shovel','timber_plank',1),
('make_digging_stick','wooden_component',1),
('make_drop_spindle','wooden_component',1),('make_drop_spindle','clay_lump',1),
('make_antler_flaker','deer_antler',1);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('grind_bone_awl','bone','animal_bone',1),('grind_bone_awl','bone','fish_bone',3),
('carve_fish_hook','bone','animal_bone',1),('carve_fish_hook','bone','fish_bone',2),
('carve_bone_comb','bone','animal_bone',1),('carve_bone_comb','bone','deer_antler',1),
('grind_bone_scraper','bone','animal_bone',1),('grind_bone_scraper','bone','deer_antler',1);

-- --- Containers and light --------------------------------------------------
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration) VALUES
('sew_leather_pouch','Sew a leather pouch','leather_pouch',1,1,'CUTTING',FALSE,FALSE,50,'items','CRAFT','leather pouch,pouch,belt bag,sew a pouch,sew,stitch',
 'You cut a circle, punch it round the edge, and run a drawstring through. Small things stop being lost.'),
('make_bark_container','Make a bark container','bark_container',1,1,'CUTTING',FALSE,FALSE,55,'items','CRAFT','bark container,bark box,birch bark basket,make a container,make,fold',
 'You score the bark while it is green, fold the corners in, and peg the rim. Watertight if you fold it right.'),
('carve_wooden_trough','Carve a wooden trough','wooden_trough',1,1,'AXE',FALSE,FALSE,180,'items','CRAFT','wooden trough,trough,dugout trough,hollow the log,hollow,carve',
 'You burn and adze the length of the log out a little at a time. Big enough to soak a whole hide, and it will not be moved once it is full.'),
('make_waterskin','Make a waterskin','waterskin',1,1,'CUTTING',FALSE,FALSE,90,'items','CRAFT','waterskin,water bag,skin bottle,make a waterskin,make,sew',
 'You sew it inside out, turn it, and pitch the seams from within. It will sweat for a day and then hold.'),
('weave_fish_trap','Weave a fish trap','fish_trap',1,1,'CUTTING',FALSE,FALSE,110,'items','CRAFT','fish trap,fish weir basket,eel trap,funnel trap,weave,plait',
 'You weave the funnel inward so what swims in cannot find the way back out, and set it facing the current.'),
('weave_burden_basket','Weave a burden basket','burden_basket',1,1,'CUTTING',FALSE,FALSE,120,'items','CRAFT','burden basket,pack basket,carrying frame,weave a burden basket,weave',
 'You weave it deep and narrow so the weight rides high on your back, and fit shoulder straps of twisted bark.'),
('make_tallow_candle','Make tallow candles','tallow_candle',3,6,NULL,TRUE,FALSE,60,'fire','CRAFT','tallow candle,candles,candle,dip candles,make candles,dip,make',
 'You dip the wick, let it cool, and dip again, forty times over. Each pass adds the thickness of a fingernail.'),
('make_rush_light','Make rush lights','rush_light',4,8,NULL,TRUE,FALSE,45,'fire','CRAFT','rush light,rushlight,peel the rushes,dip the rushes,dip,make',
 'You peel all but a spine of skin off the pith and draw it through hot fat. It burns for half an hour and costs almost nothing.'),
('make_oil_lamp','Make an oil lamp','oil_lamp',1,1,NULL,FALSE,FALSE,70,'fire','CRAFT','oil lamp,lamp,stone lamp,make a lamp,make,form',
 'You hollow a dish out of soft stone with a lip for the wick to lie in. Filled with oil it gives a small steady light that wind does not kill.');

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('sew_leather_pouch','tanned_leather',1),('sew_leather_pouch','leather_cord',1),
('make_bark_container','bark_sheet',3),('make_bark_container','fiber_cordage',1),
('make_waterskin','tanned_leather',1),('make_waterskin','pine_pitch',1),('make_waterskin','leather_cord',1),
('weave_fish_trap','hazel_rod',4),('weave_fish_trap','vine',4),
('weave_burden_basket','vine',10),('weave_burden_basket','plant_fiber',8),
('make_tallow_candle','rendered_tallow',2),('make_tallow_candle','plant_fiber',1),
('make_rush_light','rendered_tallow',1),('make_rush_light','bulrush_stalk',2),
('make_oil_lamp','stone_slab',1),('make_oil_lamp','plant_fiber',1);

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('carve_wooden_trough','log','oak_log',1),('carve_wooden_trough','log','birch_log',1),('carve_wooden_trough','log','pine_log',1),
('carve_wooden_trough','log','ash_log',1),('carve_wooden_trough','log','maple_log',1),('carve_wooden_trough','log','spruce_log',1);

-- ---------------------------------------------------------------------------
-- 5. REACHABILITY AND ROUTING DATA
--
-- Sources first, because V53's unobtainable-input check reads them, and subjects
-- immediately after, derived by exactly the rule V54 used so a new process cannot
-- drift out of agreement with its own inputs any more than an old one can.
-- ---------------------------------------------------------------------------
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT output_item_key, 'TECHNIQUE', 'material_process:' || process_key FROM material_process
ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT item_key, 'FLORA_DROP', 'flora_drop' FROM flora_drop ON CONFLICT DO NOTHING;
INSERT INTO item_source (item_key, source_kind, detail)
SELECT DISTINCT mineral_key, 'MINERAL', 'mineral_definition' FROM mineral_definition ON CONFLICT DO NOTHING;

INSERT INTO process_subject (process_key, subject_term)
SELECT DISTINCT x.process_key, w.word
FROM (
    SELECT process_key, item_key FROM material_process_input
    UNION SELECT process_key, item_key FROM material_process_input_group
    UNION SELECT process_key, output_item_key FROM material_process
) x
JOIN item_definition d ON d.item_key = x.item_key
CROSS JOIN LATERAL (
    SELECT regexp_split_to_table(x.item_key, '_') AS word
    UNION
    SELECT regexp_split_to_table(lower(d.display_name), '[^a-z]+')
) w
WHERE length(w.word) >= 3
  AND w.word NOT IN ('the','and','for','raw','new','old','one','two','cut','set',
                     'made','from','with','into','unit','item','piece','small','large')
ON CONFLICT DO NOTHING;

-- Plurals and synonyms the derivation cannot produce. Subject matching is
-- whole-word, so "carve pegs" does not contain "peg" and a process reachable only
-- through its singular is reachable only by players who happen to use it.
INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_pegs','pegs'),('turn_dowels','dowels'),('split_wedges','wedges'),
('rive_shakes','shakes'),('rive_shakes','shingles'),('rive_shakes','shingle'),
('lay_floorboards','floorboards'),('lay_floorboards','boards'),
('cut_lamellae','lamellae'),('cut_lamellae','plates'),('cut_lamellae','scales'),
('cut_boot_soles','soles'),('cut_boot_soles','boots'),
('knap_arrowheads','arrowheads'),('knap_arrowheads','points'),('knap_arrowheads','arrows'),
('carve_bone_points','points'),('carve_bone_points','arrowheads'),
('shave_arrow_shafts','shafts'),('shave_arrow_shafts','arrows'),
('fletch_arrows','arrows'),('fletch_arrows','feathers'),
('assemble_arrows','arrows'),
('dry_mushrooms','mushrooms'),('dry_mushrooms','mushroom'),
('dry_herbs','herbs'),('dry_herbs','herb'),
('preserve_berries','berries'),
('carve_wooden_spoon','spoons'),('grind_bone_awl','awls'),
('carve_fish_hook','hooks'),('carve_fish_hook','hook'),
('grind_bone_scraper','scrapers'),
('make_tallow_candle','candles'),('make_tallow_candle','candle'),('make_tallow_candle','wick'),
('make_rush_light','rushes'),('make_rush_light','rush'),('make_rush_light','lights'),
('make_oil_lamp','lamp'),('make_oil_lamp','wick'),
('bundle_thatch','reeds'),('bundle_thatch','straw'),
('weave_wattle_panel','rods'),('weave_wattle_panel','hurdle'),
('mix_daub','cob'),('daub_panel','wall'),
('course_dry_stone','stones'),('lay_mortared_course','stones'),
('chink_with_moss','gaps'),('chink_with_moss','wall'),
('build_door_blank','doorway'),('build_shutter','window'),
('raise_smoke_hood','hearth'),('raise_smoke_hood','chimney'),
('notch_log','logs'),('notch_log','corner'),
('scarf_joint','beams'),('lap_joint_planks','planks'),('dovetail_corner','corner'),
('edge_join_boards','boards'),('assemble_frame','joint'),
('cut_mortise','joint'),('cut_tenon','joint'),
('season_plank','planks'),('season_timber','beams'),
('char_post','stake'),('pitch_timber','beam'),
('tar_cordage','rope'),('tar_cordage','cord'),
('strip_bark_cordage','bast'),('strip_bark_cordage','cord'),('strip_bark_cordage','rope'),
('plait_withy_rope','withies'),('plait_withy_rope','withy'),('plait_withy_rope','willow'),
('weave_reed_mat','reeds'),('weave_reed_mat','mat'),
('spin_wool_yarn','fleece'),('weave_wool_cloth','cloth'),('felt_wool','fleece'),
('salt_fish','fish'),('brine_fish','fish'),('dry_fish','fish'),('smoke_fish','fish'),
('salt_meat','meat'),('dry_meat','meat'),('dry_meat','strips'),('smoke_meat','meat'),
('smoke_fowl','bird'),('smoke_fowl','fowl'),
('render_tallow','fat'),('render_tallow','suet'),
('split_fish','fish'),('split_fish','strips'),('gut_fish','fish'),
('fillet_fish','fish'),('fillet_fish','fillets'),('skin_fish','fish'),
('cure_fish_skin','skins'),('boil_fish_glue','skins'),('press_fish_oil','trimmings'),
('rive_bow_stave','bow'),('rive_bow_stave','blank'),('rive_bow_stave','staves'),
('tiller_bow_stave','limbs'),('tiller_bow_stave','bow'),
('back_bow_with_sinew','bow'),('twist_bowstring','string'),('assemble_bow','bow'),
('weave_quiver','arrows'),
('flesh_hide','hides'),('dehair_hide','hair'),('dehair_hide','hides'),
('harden_leather','leather'),('sew_leather_panel','leather'),
('assemble_lamellar_armor','armour'),('assemble_lamellar_armor','armor'),
('sew_bracer','arm'),('sew_bracer','wrist'),('sew_helm_cap','head'),('sew_helm_cap','helm'),
('line_with_fur','pelts'),('line_with_fur','fur'),
('haft_stone_adze','adze'),('haft_stone_chisel','chisel'),
('carve_wooden_mallet','mallet'),('carve_wooden_bowl','bowl'),
('make_antler_flaker','flaker'),('make_antler_flaker','tine'),
('carve_wooden_shovel','shovel'),('carve_wooden_shovel','spade'),
('make_digging_stick','stick'),('make_drop_spindle','spindle'),('make_drop_spindle','whorl'),
('carve_bone_comb','comb'),('carve_bone_comb','teeth'),
('sew_leather_pouch','pouch'),('sew_leather_pouch','bag'),
('make_bark_container','container'),('make_bark_container','box'),
('carve_wooden_trough','trough'),('make_waterskin','waterskin'),('make_waterskin','water'),
('weave_fish_trap','trap'),('weave_fish_trap','weir'),
('weave_burden_basket','basket'),('weave_burden_basket','pack')
ON CONFLICT DO NOTHING;

-- Subject bleed correction. season_plank's input is timber_plank, so the V54
-- derivation gives it the subject 'timber' -- which lets "season the oak timber"
-- satisfy season_plank's subject gate, and the alphabetical tiebreak then hands
-- it the plank process instead of season_timber. Planking is what 'plank' and
-- 'boards' are for; 'timber' belongs to the timber process alone.
DELETE FROM process_subject WHERE process_key = 'season_plank' AND subject_term = 'timber';

-- Verb leak. The output item's display name is "Split fish side", so the V54
-- derivation reads 'split' as a subject term of split_fish -- but 'split' is the
-- verb, not a material. Left in, "split the log into planks" satisfies split_fish's
-- subject gate and the alphabetical tiebreak steals it from split_planks. A
-- subject term names a thing worked on; a verb never does.
DELETE FROM process_subject WHERE process_key = 'split_fish' AND subject_term = 'split';

-- Reagent leak. The salt these processes consume (ground_salt) puts 'salt' into their
-- derived subjects, but salt is what they apply, not what they preserve -- so "salt the
-- deer hide" satisfied salt_fish's subject through 'salt' and the alphabetical tiebreak
-- stole it. The material worked is fish/meat/hide; salt names the reagent alone.
DELETE FROM process_subject WHERE subject_term = 'salt'
  AND process_key IN ('salt_fish','brine_fish','salt_meat','salt_hide');

-- ---------------------------------------------------------------------------
-- 6. THE GATE
--
-- The same deterministic review V53 runs, applied to everything that is still
-- DRAFT. Written as one pass over all four blocking checks rather than four
-- separate passes so a process with two problems gets two findings.
-- ---------------------------------------------------------------------------
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT b.process_key, 'MASS_CREATION', 'BLOCKING',
       'Output mass (' || b.max_output_grams || 'g at maximum yield) exceeds minimum input mass ('
       || b.min_input_grams || 'g). The recipe would create matter.'
FROM process_mass_balance b
JOIN material_process mp ON mp.process_key = b.process_key
WHERE mp.review_state = 'DRAFT' AND NOT mp.conservation_exempt
  AND b.min_input_grams > 0 AND b.max_output_grams > b.min_input_grams * 1.05;

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'NO_INPUTS', 'BLOCKING', 'Process declares no inputs of any kind.'
FROM material_process mp
WHERE mp.review_state = 'DRAFT' AND NOT mp.conservation_exempt
  AND NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key = mp.process_key)
  AND NOT EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key = mp.process_key);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'SELF_LOOP', 'BLOCKING', 'Output ' || mp.output_item_key || ' is also one of its own inputs.'
FROM material_process mp
WHERE mp.review_state = 'DRAFT'
  AND (EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key = mp.process_key AND i.item_key = mp.output_item_key)
    OR EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key = mp.process_key AND g.item_key = mp.output_item_key));

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT DISTINCT x.process_key, 'UNOBTAINABLE_INPUT', 'BLOCKING',
       'Input ' || x.item_key || ' has no acquisition path in the world.'
FROM (SELECT process_key, item_key FROM material_process_input
      UNION SELECT process_key, item_key FROM material_process_input_group) x
JOIN material_process mp ON mp.process_key = x.process_key
WHERE mp.review_state = 'DRAFT'
  AND NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = x.item_key);

-- A process whose declared category disagrees with the category its own keywords
-- classify to can never match anything. V55 found four of these among twenty; at
-- this volume the check has to be a gate rather than an afterthought, so it is
-- BLOCKING here rather than an exception that would stop the migration dead.
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'CATEGORY_DISAGREEMENT', 'BLOCKING',
       'No keyword of this process classifies to its own category (' || mp.category_key || '), so it is unreachable.'
FROM material_process mp
WHERE mp.review_state = 'DRAFT'
  AND NOT EXISTS (
      SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) AS k
      JOIN category_term ct ON ct.category_key = mp.category_key
      WHERE ' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %'
         OR ' ' || ct.term  || ' ' LIKE '% ' || btrim(k) || ' %');

-- A process with no subject terms is unreachable on the other axis, for the same
-- reason and with the same silence.
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'NO_SUBJECT', 'BLOCKING', 'Process has no subject terms and could never match.'
FROM material_process mp
WHERE mp.review_state = 'DRAFT'
  AND NOT EXISTS (SELECT 1 FROM process_subject s WHERE s.process_key = mp.process_key);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT process_key, 'IMPLAUSIBLE_DURATION', 'ADVISORY',
       'Duration of ' || duration_minutes || ' minutes looks wrong for this kind of work.'
FROM material_process WHERE review_state = 'DRAFT' AND (duration_minutes < 5 OR duration_minutes > 600);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT process_key, 'MISSING_HEAT', 'ADVISORY',
       'Firing, rendering, or baking without requires_fire set.'
FROM material_process
WHERE review_state = 'DRAFT' AND NOT requires_fire
  AND (keywords ILIKE '%fire the%' OR keywords ILIKE '%bake%' OR keywords ILIKE '%render%' OR keywords ILIKE '%melt%');

-- Promote what passed; hold what did not. Nothing here is trusted because it was
-- written carefully -- it is trusted because it survived the checks above.
UPDATE material_process SET review_state = 'VERIFIED', reviewed_at = now()
WHERE review_state = 'DRAFT' AND NOT EXISTS (
    SELECT 1 FROM process_review r
    WHERE r.process_key = material_process.process_key AND r.severity = 'BLOCKING' AND r.resolved_at IS NULL);

UPDATE material_process SET review_state = 'NEEDS_REFINEMENT', reviewed_at = now()
WHERE review_state = 'DRAFT' AND EXISTS (
    SELECT 1 FROM process_review r
    WHERE r.process_key = material_process.process_key AND r.severity = 'BLOCKING' AND r.resolved_at IS NULL);

-- ---------------------------------------------------------------------------
-- 7. BOOKKEEPING
-- ---------------------------------------------------------------------------
INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, proven_in, principle)
SELECT 'process_' || process_key, display_name, domain_key,
       CASE WHEN duration_minutes >= 90 THEN 'ADVANCED' WHEN duration_minutes >= 45 THEN 'INTERMEDIATE' ELSE 'PRIMITIVE' END,
       output_item_key, tool_class, 'V57', narration
FROM material_process WHERE review_state = 'VERIFIED'
ON CONFLICT (technique_key) DO NOTHING;

-- These were catalogued as deliberately unreachable because no processing step
-- distinguished them. There is one now, for each.
DELETE FROM item_unreachable_known WHERE item_key IN ('stone_axe','vine','large_basket');

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('preservation_chain','Preservation Chain','Salt, smoke, drying and brine: the chain that turns a day''s catch into winter food, and the rock salt the whole of it stands on.','V57','PREBUILT'),
       ('joinery','Joinery','Mortise, tenon, scarf, lap and dovetail -- the joints that let timber become structure without a single nail.','V57','PREBUILT'),
       ('archery','Archery','Stave to bow and shaft to arrow: riving, tillering, sinew backing, fletching and hafting.','V57','PREBUILT'),
       ('armoury','Armoury','Hide to hardened plate: fleshing, dehairing, boiling and lacing leather into something that turns an edge.','V57','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
