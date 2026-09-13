-- #161 — what sleeps through the winter.
--
-- #161's remaining scope is "season/climate eligibility per candidate". Plants already answer to the season —
-- flora_drop.season has decided when berries, nuts and roots can be taken since V39 — and so do insects, through
-- insect_colony_kind.season_active. Animals never have. A brown bear could be found, tracked, confronted and
-- tamed on a January morning; an adder basked, a hedgehog crossed a frozen clearing, a frog sat at the edge of ice.
-- The world had a winter for everything that grows and for nothing that breathes.
--
-- WHAT THIS ADDS. wildlife_species.dormant_months: the months a species spends hibernating or brumating. In those
-- months it is not abroad — not seen, not tracked, not come upon, not tamed, not caught in a trap or a snare, and
-- not raiding a keeper's stock. NULL means the species is never dormant, which is nearly every species.
--
-- ONE DEFINITION. wildlife_abroad(species) is the single rule, and it reads the world clock itself rather than
-- being handed a time. Every place that asks whether a wild animal is out calls it; nothing re-derives "is it
-- winter". The season already had three separate definitions in Java that happen to agree — this adds no fourth.
-- A dormancy rule applied in perception but not in hunting would be the declared-but-ignored defect in a new
-- shape: the survey says no bear, and the hunt finds one.
--
-- The months are the northern temperate ones, because the calendar here is: seasonOf maps December to February to
-- WINTER everywhere, and every biome in this world is a temperate or cold one.
--
-- WHO SLEEPS, AND WHO DELIBERATELY DOES NOT
--   Bears: December to March. The den is taken before the deep cold and left as the snow goes.
--   Bats, hedgehogs, chipmunks: November to March. True hibernators.
--   Dormice: October to April — the longest sleepers in the catalogue, and the reason for the name.
--   Marmots, groundhogs, ground squirrels: October to March.
--   Snakes and lizards: November to February. Brumation, and it is the cold that decides it, not the species'
--     homeland — every reptile catalogued here is catalogued on temperate ground, constrictor and monitor included.
--   Tortoises and turtles: November to March. They bury in mud or leaf litter and wait.
--   Frogs, toads, newts and salamanders: November to February.
--
--   NOT dormant: badger, raccoon, skunk. They go torpid in hard spells and come out again in mild ones, so a winter
--   badger is scarcer, not absent — and scarcity is a different mechanic that this does not pretend to be. Red and
--   flying squirrels do not hibernate at all; they cache and stay abroad. Monsters are untouched: whether a thing out
--   of legend keeps a season is a question for its own profile, not a borrowed rule. Fish and birds are untouched.

ALTER TABLE wildlife_species ADD COLUMN dormant_months SMALLINT[];

COMMENT ON COLUMN wildlife_species.dormant_months IS
  'Months (1-12, world clock, UTC) the species spends hibernating or brumating and is not abroad. NULL = never '
  'dormant. Read only through wildlife_abroad(species_key); no query should test the months directly.';

UPDATE wildlife_species SET dormant_months = ARRAY[12,1,2,3]::smallint[]          WHERE species_key IN ('brown_bear','cave_bear');
UPDATE wildlife_species SET dormant_months = ARRAY[11,12,1,2,3]::smallint[]       WHERE species_key IN ('common_bat','hedgehog','eastern_chipmunk');
UPDATE wildlife_species SET dormant_months = ARRAY[10,11,12,1,2,3,4]::smallint[]  WHERE species_key IN ('forest_dormouse');
UPDATE wildlife_species SET dormant_months = ARRAY[10,11,12,1,2,3]::smallint[]    WHERE species_key IN ('marmot','groundhog','ground_squirrel');
UPDATE wildlife_species SET dormant_months = ARRAY[11,12,1,2]::smallint[]
 WHERE species_key IN ('common_adder','grass_snake','water_snake','constrictor_snake',
                       'common_lizard','horned_lizard','monitor_lizard','badger_lizard');
UPDATE wildlife_species SET dormant_months = ARRAY[11,12,1,2,3]::smallint[]
 WHERE species_key IN ('tortoise','pond_turtle','river_turtle','softshell_turtle');
UPDATE wildlife_species SET dormant_months = ARRAY[11,12,1,2]::smallint[]
 WHERE species_key IN ('common_frog','common_toad','bullfrog','tree_frog','great_crested_newt','tree_newt','fire_salamander');

-- The single rule. STABLE, not IMMUTABLE: it reads the world clock, which moves.
-- A species not in the registry, a species with no dormancy, or a world with no clock row is abroad.
CREATE OR REPLACE FUNCTION wildlife_abroad(p_species TEXT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
  SELECT COALESCE((
    SELECT NOT (EXTRACT(MONTH FROM (SELECT simulated_at FROM simulation_clock WHERE id = 1) AT TIME ZONE 'UTC')::int
                = ANY (ws.dormant_months))
      FROM wildlife_species ws
     WHERE ws.species_key = p_species AND ws.dormant_months IS NOT NULL
  ), TRUE)
$$;

COMMENT ON FUNCTION wildlife_abroad(TEXT) IS
  'Whether a wild animal of this species is out and about at the world clock''s current month (#161). The one '
  'definition of seasonal dormancy: every wild-facing query calls it, and none re-derives the season.';

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every name above must be a real species, or a typo silently leaves an animal awake all winter. The lists name
  -- 2 bears + 3 true hibernators + 1 dormouse + 3 marmots and kin + 8 snakes and lizards + 4 turtles + 7 amphibians.
  SELECT count(*) INTO n FROM wildlife_species WHERE dormant_months IS NOT NULL;
  IF n <> 28 THEN RAISE EXCEPTION 'V312: expected 28 dormant species, found % — a key above is misspelt', n; END IF;

  -- Months are months.
  SELECT string_agg(species_key, ', ') INTO bad FROM wildlife_species
   WHERE dormant_months IS NOT NULL
     AND EXISTS (SELECT 1 FROM unnest(dormant_months) m WHERE m < 1 OR m > 12);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V312: months outside 1-12: %', bad; END IF;

  -- Dormancy is a winter thing in this world. A species asleep in July would be a bug in the list, not biology.
  SELECT string_agg(species_key, ', ') INTO bad FROM wildlife_species
   WHERE dormant_months IS NOT NULL
     AND (NOT (1 = ANY (dormant_months)) OR 7 = ANY (dormant_months) OR 6 = ANY (dormant_months) OR 8 = ANY (dormant_months));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V312: these would sleep through summer or wake in January: %', bad; END IF;

  -- An animal asleep for most of the year is a catalogue token for most of the year. Seven months is the dormouse.
  SELECT string_agg(species_key, ', ') INTO bad FROM wildlife_species
   WHERE dormant_months IS NOT NULL AND cardinality(dormant_months) > 7;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V312: dormant for more than seven months: %', bad; END IF;

  -- Monsters, fish and birds keep no borrowed season.
  SELECT string_agg(species_key, ', ') INTO bad FROM wildlife_species
   WHERE dormant_months IS NOT NULL AND (kingdom_class IN ('MONSTRUM','AVES','PISCES') OR movement_class = 'AQUATIC');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V312: a monster, bird or fish was given a dormancy: %', bad; END IF;

  -- A draft or tamed-stock species going dormant would strand a keeper's working animal in a rule written for the
  -- wild. None of the dormant species can be drafted; assert it so a later draft entry cannot collide silently.
  SELECT string_agg(ds.species_key, ', ') INTO bad FROM draft_species ds
    JOIN wildlife_species ws ON ws.species_key = ds.species_key WHERE ws.dormant_months IS NOT NULL;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V312: these draft species would hibernate out of harness: %', bad; END IF;
END $$;
