-- #119 (epic #109) — what each candidacy is waiting on, in the register rather than only in the prose.
--
-- THE DEFECT. #119 asked for goblin, ogre, cyclops and orc candidates. They were registered (V355) and given long
-- cards in 12.1-Native-Peoples, and three of the four rows in the table code actually reads said, in full:
-- "Defined in #119." Seven rows across #117/#118/#119 were stubs of that shape. The cards carry the one fact that
-- decides anything — what must exist in the world before this people could be placed at all — and the register,
-- which is what a future cycle queries to choose the next culture, carried none of it.
--
-- WHAT THIS DOES. Every candidacy states what it waits on, as data. `needs_first` is the reason the six activation
-- reviews are still FALSE: not an opinion about the species, but the named system that is missing. Two rows say
-- "Nothing", because those two peoples are placed, and that is the whole point of the column — the register can
-- now answer "what is the cheapest next people?" without anyone reading a design document.
--
-- THE GUARD. NOT NULL with a length floor, so a candidacy registered in some later migration cannot be added
-- without saying what stands between it and the world. A register of names is a catalogue; a register of names and
-- blockers is a plan.

ALTER TABLE native_candidate ADD COLUMN needs_first TEXT;

COMMENT ON COLUMN native_candidate.needs_first IS
  '#119: the systems that must exist before this candidacy could be activated — the reason its six reviews are still FALSE, named so a later cycle can pick the next people from the table instead of from a design document. "Nothing" for a people already placed.';

-- ---------------------------------------------------------------------------------------------------------------
-- The two that are placed.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE native_candidate SET needs_first =
  'Nothing: the first people placed (#115, DR-0024, V346). Every people system in the world was built against them.'
 WHERE candidate_key='reedkin';

UPDATE native_candidate SET needs_first =
  'Nothing: placed in V366, once felling within a territory became the gravest encroachment (V365) and fire became a real threat to a settlement (#111).'
 WHERE candidate_key='grovebound';

-- ---------------------------------------------------------------------------------------------------------------
-- #119's four, from their cards in 12.1-Native-Peoples. The notes on three of them were the stub this fixes.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE native_candidate SET
    home_form = 'burrows and camps, hidden but physical, shifted when they are found',
    note = 'Defined in #119. Cunning folk with a material culture of repair: a band owns almost nothing it made new and almost nothing that does not work. The cache is the winter, and robbing one is the gravest wrong. No band is hostile by name.',
    needs_first = 'Caches as real hidden stores that can be found and robbed (#211 property, extended to a store that is not a building), and shifting camps — a settlement that moves without being a migration.'
 WHERE candidate_key='goblin_bands';

UPDATE native_candidate SET
    needs_first = 'Calorie load as a real constraint on where a settlement can stand (an ogre homestead must sit where it can be fed). Paid heavy work as an agreement a Chronicle can make is already in (#113, V353).'
 WHERE candidate_key='ogre_lineages';

UPDATE native_candidate SET
    home_form = 'one built household with a work-yard, which stays where it is',
    note = 'Defined in #119. Large-bodied stoneworkers in households rather than a state, with the depth perception one eye gives: superb at what is in their hands, poor at what is thrown from a distance. Their trade is a reason to travel.',
    needs_first = 'Large stone goods worth the journey (a quern a Chronicle cannot make, and a cart or a boat to move it), and a contracted-work agreement longer than a day — #113''s agreements are the shape, with a term and a delivery.'
 WHERE candidate_key='cyclops_households';

UPDATE native_candidate SET
    home_form = 'built settlements with fields, stock and a wall',
    note = 'Defined in #119. A full sapient people whose biology is unremarkable, which is the point of the card: politics differ between communities and there is no species-wide alignment. A clan that has been raided becomes a clan that raids.',
    needs_first = 'Fields and stock at a settlement''s scale, which the community model does not yet run for any people, and a decision about the release cap — an orc clan makes the world one with two organised peoples in it (#115).'
 WHERE candidate_key='orc_clans';

-- ---------------------------------------------------------------------------------------------------------------
-- #118's cards, including the three other stub notes.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE native_candidate SET
    note = 'Defined in #118. A mobile confederacy of bands on open ground, with a seasonal circuit rather than a settlement; the first candidate whose home is a route.',
    needs_first = 'Band movement along a seasonal circuit, band territory as a moving region rather than a fixed home chunk, and pursuit.'
 WHERE candidate_key='centaur_confederacy';

UPDATE native_candidate SET
    note = 'Defined in #118. Ectothermic people of warm wetland and sunlit slope whose working hours are set by the weather, with nest mounds as the site their community is built around.',
    needs_first = 'Body temperature as a real state for a people — their working hours and their winter — and nest-mound sites the world places and protects.'
 WHERE candidate_key='saurian_kin';

UPDATE native_candidate SET
    note = 'Defined in #118. A people of the rock with their own stone, ore and lime work. A sapient stonekin is never a troll carcass: V344''s constraints already make that impossible, and the instinctive cave troll stays a separate PRIMAL species.',
    needs_first = 'Cave-interior settlement — a home inside the rock with air, water and light — and the stone, ore and lime chains specified as theirs rather than as generic quarry output.'
 WHERE candidate_key='stonekin_enclaves';

UPDATE native_candidate SET needs_first =
  'Vertical settlement: a home a walker cannot reach and stores that are hauled to it, plus flight as a real constraint — carrying limits, weather, and no service flights for a Chronicle.'
 WHERE candidate_key='harpy_aerie_clans';

UPDATE native_candidate SET needs_first =
  'An individually authored being with its own dialogue of knowledge rather than quests, which means a knowledge-exchange model the game does not have, and the discipline to keep it fallible.'
 WHERE candidate_key='deepstone_oracle';

UPDATE native_candidate SET needs_first =
  'A being whose responses are ecological rather than personal — paths lost, forage withheld, every creature in the wood made unwilling — which is a conflict model nothing in the world implements yet.'
 WHERE candidate_key='elder_groveheart';

-- ---------------------------------------------------------------------------------------------------------------
-- #117's monsters and the remaining candidacies. Each one names the system, not a mood.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE native_candidate SET
    note = 'Defined in #117. A Tier IV sovereign with a hoard that is property in a real place, authored one at a time rather than spawned.',
    needs_first = 'A hoard that is real property in a real location a Chronicle can reach, an individually authored being rather than a spawn, and an answer to what a world with a dragon in it is like for everything else in it.'
 WHERE candidate_key='dragon';

UPDATE native_candidate SET needs_first =
  'Cave-interior settlement, and an appetite for ground and material that the world enforces as a constraint on placement rather than recording as a number on a card.'
 WHERE candidate_key='cave_giant';

UPDATE native_candidate SET needs_first =
  'Very large bodies as a real load on the ground a family holds, and a way to keep interactions few without making the being a locked door.'
 WHERE candidate_key='forest_giant';

UPDATE native_candidate SET needs_first =
  'Deep halls with air, water and light as physical requirements of a home, and a material and ecological life that is demonstrably not the stonekin''s — otherwise these are one candidacy, not two.'
 WHERE candidate_key='dwarrow_deepfolk';

UPDATE native_candidate SET needs_first =
  'Herding as a community''s economy — stock a people keeps, moves and can lose — and a seasonal camp that relocates without being a migration.'
 WHERE candidate_key='caprine_folk';

UPDATE native_candidate SET needs_first =
  'Farming and grazing at a household''s scale, which the community model does not yet run for any people, and a homestead that is one family rather than a settlement.'
 WHERE candidate_key='minotaur_homesteads';

UPDATE native_candidate SET needs_first =
  'A diet, a disease exposure and a footprint that hold together as one body, and a clear physical line between these families and the hostile bog wraith already in the world.'
 WHERE candidate_key='moss_troll_families';

UPDATE native_candidate SET needs_first =
  'Moisture, air, spore and waste as constraints on where it can live at all, and communication slow enough to cost a Chronicle real time to use — with no remote sense until one is proven.'
 WHERE candidate_key='mycelial_collective';

UPDATE native_candidate SET needs_first =
  'Body temperature as a real state, nesting ground the world places and protects, and river settlement on the RIVER_BANK ground that now exists (#156).'
 WHERE candidate_key='naga_river_clans';

UPDATE native_candidate SET needs_first =
  'Warren interiors as physical homes with compact stores, and smoke and flooding as things that can actually reach inside one.'
 WHERE candidate_key='kobold_warrens';

UPDATE native_candidate SET needs_first =
  'Ruins holding salvage that runs out, and a repair economy in which a mended thing is honestly worth less than a made one.'
 WHERE candidate_key='ruin_scavenger_folk';

UPDATE native_candidate SET needs_first =
  'Habitat engineering that changes the map — a dam that floods ground and a lodge that stands and can be broken — before a colony is worth placing at all.'
 WHERE candidate_key='giant_beaver_colony';

UPDATE native_candidate SET needs_first =
  'Coordinated group hunting as behaviour the ecology actually runs, and nesting ground the world places and the prides defend.'
 WHERE candidate_key='griffin_prides';

UPDATE native_candidate SET needs_first =
  'An apex the world places rarely on ground of its own, and no path by which it becomes a mount or a speaking ally.'
 WHERE candidate_key='thornback_griffin';

UPDATE native_candidate SET needs_first =
  'A clutch as a social unit with its own ground, and a mountain predator distinct in body and behaviour from a Tier IV dragon rather than a smaller one.'
 WHERE candidate_key='crag_drake';

UPDATE native_candidate SET needs_first =
  'A gaze and a venom that are physical facts with a measured reach, and a predator the world places rarely rather than spawning where a Chronicle happens to walk.'
 WHERE candidate_key='basilisk';

UPDATE native_candidate SET needs_first =
  'Deep water as ground a Chronicle can be on and in, and an apex that is met first through signs and territory rather than by being spawned into an encounter.'
 WHERE candidate_key='marsh_leviathan';

UPDATE native_candidate SET needs_first =
  'Ruin interiors as habitable sites with stone, heat and shelter modelled physically, and a nocturnal cycle the world actually runs.'
 WHERE candidate_key='gargoyle_roost';

-- The guard. A candidacy registered later cannot be a name with a shrug attached: it must say what stands between
-- it and the world, at the length that takes an actual sentence.
UPDATE native_candidate SET needs_first = 'Unreviewed: no system has been named as standing between this candidacy and the world.'
 WHERE needs_first IS NULL;

ALTER TABLE native_candidate ALTER COLUMN needs_first SET NOT NULL;
ALTER TABLE native_candidate ADD CONSTRAINT native_candidate_says_what_it_waits_on CHECK (length(needs_first) >= 40);
