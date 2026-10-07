-- V410 — nowhere to handle a working animal (#108)
--
-- #108 asks for species-appropriate animal infrastructure. V284 gave the animals somewhere to LIVE — a coop, a
-- byre, a fold, a sty. This gives the WORKING animal somewhere to be HANDLED, and its gear somewhere to hang,
-- which is a different need and was served by one structure in the whole catalogue.
--
-- THE FINDING, which is a measurement rather than a wish. construction_kind.holds_an_animal_still is read by
-- WildlifeEncounterService when it decides whether handling a beast is SAFE — "a stanchion makes the work safe;
-- carried gear only makes it possible", and the best single restraint applies rather than stacking, precisely so
-- that a building is worth raising. Exactly ONE structure in the catalogue carried that flag:
--
--   holds_an_animal_still: MILKING_STANCHION
--
-- So to hold an ox still for harness inspection or hoof care, a keeper had to build a DAIRY STANCHION. The flag
-- was live, read and correct; there was simply nothing to carry it but a milking frame. #108 names the thing that
-- should: `draft_animal_stall`, "restraint-safe work/rest bay for harness inspection and recovery".
--
-- The same shape, one step along: shelters_gear is read when the game asks where tack and harness are kept, and
-- was carried by TOOL_SHED, WOOD_STORE, OX_SHED and TIMBER_BARN — a toolshed, a woodpile, an ox byre and a barn.
-- A keeper with a harness and no barn had nowhere dry to put it. #108 names `tack_room`, `harness_rack` and
-- `yoke_rack` for that, and the first two are here.
--
-- KEYWORDS — two traps, both dodged deliberately rather than discovered in CI:
--
--   1. No keyword may contain "pen". BUILD_PEN runs before the assembly matcher and swallows it whole. That is
--      the #513 trap and it already cost six structures once (V284 records it).
--
--   2. "rack" is owned by CRAFT_SHELF, which matches a build verb beside shelf/shelves/rack/archive and carries
--      an exclusion list that already names the drying, fuel, wood, firewood, log, kindling, hay, fodder and
--      smoke racks. A harness rack is the tenth of them, so the exclusion is extended in the same place in Java
--      rather than worked around here — a migration alone would have shipped an unreachable structure.
--
--   3. And one trap of our own making: "stable" is an ADJECTIVE as often as it is a building. The assembly
--      matcher works on substrings, so a bare "stable" keyword would make "is the shelter stable" and "is the
--      ground stable" into a request to raise a horse stable. The keywords therefore name "a stable" with its
--      article, or "stables", or "horse stable" — never the bare word. This is the same discipline that keeps
--      "hone" out of "honey" and "whet" out of "whether".

INSERT INTO construction_kind
  (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('DRAFT_ANIMAL_STALL', 'Draft animal stall', 'construction', TRUE,  FALSE, TRUE, 'V410'),
  ('HORSE_STABLE',       'Horse stable',       'construction', TRUE,  FALSE, TRUE, 'V410'),
  ('TACK_ROOM',          'Tack room',          'construction', FALSE, FALSE, TRUE, 'V410'),
  ('HARNESS_RACK',       'Harness rack',       'construction', FALSE, FALSE, TRUE, 'V410');

-- What each one IS, in the flags the code actually reads.
--
-- The stall is the point of the whole migration: it holds an animal still, which is what makes hoof care and
-- harness work safe, and it shelters the beast and the gear hanging in it besides.
-- Sized HUGE, because the draft beast this is built for is the OX (HUGE), and shelter size is compared against
-- the animal's own tier by body_size_rank: a bay too small for the beast shelters nothing. The ox, the aurochs
-- and the water buffalo are all HUGE; the horse, the yak and the musk ox are LARGE.
UPDATE construction_kind SET shelters_stock = TRUE, holds_an_animal_still = TRUE, shelters_gear = TRUE,
                             shelters_up_to_size = 'HUGE',
                             encloses = TRUE, is_barrier = TRUE, barrier_strength = 20, gives_shade = TRUE,
                             flammable = TRUE
 WHERE project_kind = 'DRAFT_ANIMAL_STALL';

-- A stable is partitioned stalls under one roof: stock, their gear, and a wall a wolf will not come through.
-- It holds an animal still for the same reason the stall does — a horse in its own stall can be worked on.
--
-- Sized LARGE, DELIBERATELY, and this is where #108's "species-appropriate" stops being decoration: a horse is
-- LARGE and an ox is HUGE, so a horse stable will NOT house an ox and the keeper must raise the ox shed or the
-- draft stall for that. Sizing it HUGE "to be safe" would have made every other animal house redundant and
-- turned the whole size system into a formality.
UPDATE construction_kind SET shelters_stock = TRUE, holds_an_animal_still = TRUE, shelters_gear = TRUE,
                             shelters_up_to_size = 'LARGE',
                             encloses = TRUE, is_barrier = TRUE, barrier_strength = 24, gives_shade = TRUE,
                             flammable = TRUE
 WHERE project_kind = 'HORSE_STABLE';

-- A tack room keeps gear and nothing else. It is not stock shelter and must not pretend to be: an animal shut
-- in a tack room is not housed, it is in the wrong building.
UPDATE construction_kind SET shelters_gear = TRUE, encloses = TRUE, flammable = TRUE
 WHERE project_kind = 'TACK_ROOM';

-- And a rack is a frame on a wall. It keeps harness off the ground, which is the whole of its claim: no roof, no
-- walls, no shade, nothing a wolf notices.
UPDATE construction_kind SET shelters_gear = TRUE, flammable = TRUE
 WHERE project_kind = 'HARNESS_RACK';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key,
   keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('draft_animal_stall','STRUCTURE','Draft animal stall',FALSE,NULL,'DRAFT_ANIMAL_STALL','construction',
   'build a draft animal stall,raise a draft animal stall,build a draft stall,raise a draft stall,'
   || 'build a handling stall,build a handling bay,draft animal stall,draft stall,handling stall,handling bay',
   'draft animal stall,draft stall,handling stall,handling bay',
   'You bed a narrow bay with a beam across the front of it, wide enough for one beast and no wider. '
   || 'An ox that cannot swing its quarters is an ox whose feet you can work on.',
   'VERIFIED', now()),
  ('horse_stable','STRUCTURE','Horse stable',FALSE,NULL,'HORSE_STABLE','construction',
   'build a stable,raise a stable,build a horse stable,raise a horse stable,build the stables,'
   || 'horse stable,stables',
   'horse stable,stables',
   'Two rows of partitioned stalls under one roof, floored to drain and open enough at the eaves that the air '
   || 'moves. A horse kept standing in its own breath goes wrong in the lungs.',
   'VERIFIED', now()),
  ('tack_room','STRUCTURE','Tack room',FALSE,NULL,'TACK_ROOM','construction',
   'build a tack room,raise a tack room,build a tack store,build a harness room,tack room,tack store,harness room',
   'tack room,tack store,harness room',
   'A dry shut room with pegs down the long wall. Leather left out in the weather is leather you will be '
   || 'cutting up for something else by spring.',
   'VERIFIED', now()),
  ('harness_rack','STRUCTURE','Harness rack',FALSE,NULL,'HARNESS_RACK','construction',
   'build a harness rack,raise a harness rack,build a yoke rack,raise a yoke rack,build a tack rack,'
   || 'harness rack,yoke rack,tack rack',
   'harness rack,yoke rack,tack rack',
   'A frame of pegged rails set against a wall, high enough that a harness hangs clear of the floor and keeps '
   || 'its shape instead of taking the shape of the ground.',
   'VERIFIED', now());

INSERT INTO assembly_stage
  (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration)
VALUES
  ('draft_animal_stall_posts','draft_animal_stall',1,'Set the stall posts',NULL,0,'CUTTING',FALSE,
   'You sink four heavy posts, close-set, and check each one by leaning your whole weight on it. A beam a beast '
   || 'can push over is worse than no beam, because you would trust it.'),
  ('draft_animal_stall_bed','draft_animal_stall',2,'Bar and bed the stall','draft_animal_stall_posts',0,NULL,FALSE,
   'You lash a bar across the front at chest height and bed the floor deep, so a standing animal is held and a '
   || 'resting one is dry.'),
  ('horse_stable_posts','horse_stable',1,'Frame the stable',NULL,0,'CUTTING',FALSE,
   'You raise two lines of posts with a passage down the middle, each bay long enough for an animal to lie '
   || 'down in and turn around.'),
  ('horse_stable_partitions','horse_stable',2,'Partition the stalls','horse_stable_posts',0,NULL,FALSE,
   'You board between the posts to shoulder height, leaving the air free above. Horses that can see each other '
   || 'and not reach each other keep quiet.'),
  ('horse_stable_roof','horse_stable',3,'Roof and drain it','horse_stable_partitions',0,NULL,FALSE,
   'You thatch the whole length and cut the floor to fall toward the door, and the building stops smelling of '
   || 'ammonia within the week.'),
  ('tack_room_walls','tack_room',1,'Wall the tack room',NULL,0,NULL,FALSE,
   'You daub a small room tight to the weather, which for leather matters more than size does.'),
  ('tack_room_pegs','tack_room',2,'Peg it out','tack_room_walls',0,NULL,FALSE,
   'You drive pegs down the long wall at a spacing that lets a harness hang without touching its neighbour.'),
  ('harness_rack_frame','harness_rack',1,'Build the harness rack',NULL,0,NULL,FALSE,
   'You peg rails to uprights and set the whole frame against a wall, and the harness comes up off the floor '
   || 'for the first time since you made it.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('draft_animal_stall_posts','timber_log',4), ('draft_animal_stall_posts','fiber_cordage',3),
  ('draft_animal_stall_bed','hazel_rod',4),    ('draft_animal_stall_bed','thatch_bundle',2),
  ('horse_stable_posts','timber_log',6),       ('horse_stable_posts','fiber_cordage',4),
  ('horse_stable_partitions','hazel_rod',8),   ('horse_stable_partitions','bark_sheet',4),
  ('horse_stable_roof','thatch_bundle',6),     ('horse_stable_roof','field_stone',4),
  ('tack_room_walls','hazel_rod',5),           ('tack_room_walls','clay_lump',4),
  ('tack_room_pegs','dry_branch',4),
  ('harness_rack_frame','timber_log',2),       ('harness_rack_frame','fiber_cordage',2);

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every item these stages ask for must exist, or the structure is unbuildable and nothing says so. This is the
  -- guard V284 wrote and it is the one that earns its keep most often.
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
   WHERE s.assembly_key IN ('draft_animal_stall','horse_stable','tack_room','harness_rack')
     AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V410: these stages ask for items that do not exist: %', bad;
  END IF;

  -- TRAP 1. No keyword may contain "pen": BUILD_PEN runs before the assembly matcher.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key IN ('draft_animal_stall','horse_stable','tack_room','harness_rack')
     AND trim(k) LIKE '%pen%';
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V410: these keywords would be swallowed by the BUILD_PEN intent: %', bad;
  END IF;

  -- TRAP 3. No keyword may be the bare word "stable", which is an adjective as often as a building. The matcher
  -- works on substrings, so "is the ground stable" would ask to raise one.
  IF EXISTS (SELECT 1 FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
              WHERE assembly_key = 'horse_stable' AND trim(k) = 'stable') THEN
    RAISE EXCEPTION 'V410: a bare "stable" keyword turns every question about steadiness into a building project';
  END IF;
  IF EXISTS (SELECT 1 FROM assembly_definition, unnest(string_to_array(subjects, ',')) k
              WHERE assembly_key = 'horse_stable' AND trim(k) = 'stable') THEN
    RAISE EXCEPTION 'V410: likewise for the subjects list';
  END IF;

  -- THE POINT OF THE MIGRATION, asserted rather than assumed: a working animal must now have somewhere to be
  -- held still that is not a dairy stanchion. If this count ever returns to one, the gap has reopened.
  SELECT count(*) INTO n FROM construction_kind WHERE holds_an_animal_still;
  IF n < 3 THEN
    RAISE EXCEPTION 'V410: holds_an_animal_still is carried by only % structure(s). The flag is read when the '
      'game decides whether handling a beast is safe, and a keeper must not have to raise a MILKING STANCHION '
      'to see to an ox''s feet', n;
  END IF;

  -- SPECIES-APPROPRIATE, ASSERTED. The stall must take the largest draft beast there is, and the horse stable
  -- must NOT — otherwise it quietly becomes a universal animal house and every other building in the catalogue
  -- is redundant. This is the guard that keeps the size system from becoming a formality.
  IF NOT EXISTS (SELECT 1 FROM construction_kind ck
                  WHERE ck.project_kind = 'DRAFT_ANIMAL_STALL'
                    AND body_size_rank(ck.shelters_up_to_size)
                        >= (SELECT max(body_size_rank(ws.size_tier)) FROM wildlife_species ws
                             WHERE ws.species_key IN ('ox','aurochs','water_buffalo'))) THEN
    RAISE EXCEPTION 'V410: the draft stall must be sized for the heaviest draft beast, or an ox cannot stand in it';
  END IF;
  IF EXISTS (SELECT 1 FROM construction_kind ck, wildlife_species ws
              WHERE ck.project_kind = 'HORSE_STABLE' AND ws.species_key = 'ox'
                AND body_size_rank(ck.shelters_up_to_size) >= body_size_rank(ws.size_tier)) THEN
    RAISE EXCEPTION 'V410: a horse stable sized to hold an ox makes the ox shed and the draft stall pointless — '
      '#108 asks for species-appropriate housing, which means each building has an animal it is WRONG for';
  END IF;

  -- And a tack room must not quietly become stock housing. An animal shut in one is in the wrong building.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind IN ('TACK_ROOM','HARNESS_RACK') AND shelters_stock) THEN
    RAISE EXCEPTION 'V410: a tack room keeps gear, not animals';
  END IF;

  -- A rack is a frame on a wall: no roof, no walls, nothing a wolf notices. If it ever claims to enclose or to
  -- bar the way, it has been confused with the building it hangs inside.
  IF EXISTS (SELECT 1 FROM construction_kind
              WHERE project_kind = 'HARNESS_RACK' AND (encloses OR is_barrier OR gives_shade)) THEN
    RAISE EXCEPTION 'V410: a harness rack is a frame of rails — it shelters nothing and stops nothing';
  END IF;

  -- Timber, hurdle, bark and thatch carry flame. This is the flag nothing forces and V284 was caught by.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE project_kind IN ('DRAFT_ANIMAL_STALL','HORSE_STABLE','TACK_ROOM','HARNESS_RACK') AND NOT flammable;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V410: these are built of wood and thatch and must carry flame: %', bad;
  END IF;

  -- Every stage must be reachable: a prerequisite naming a stage that does not exist is a structure that can be
  -- started and never finished.
  SELECT string_agg(stage_key, ', ') INTO bad FROM assembly_stage
   WHERE assembly_key IN ('draft_animal_stall','horse_stable','tack_room','harness_rack')
     AND prerequisite_stage_key IS NOT NULL
     AND prerequisite_stage_key NOT IN (SELECT stage_key FROM assembly_stage);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V410: these stages wait on a prerequisite that does not exist: %', bad;
  END IF;
END $$;

COMMENT ON COLUMN construction_kind.holds_an_animal_still IS
  'Whether this structure holds a beast still enough that handling it is SAFE rather than merely possible — read '
  'by WildlifeEncounterService, where the best single restraint applies and carried gear does not stack with a '
  'building. Carried by the milking stanchion, the draft animal stall and the horse stable (V410): before that '
  'stall existed, seeing to a working ox''s feet meant raising a dairy frame (#108).';
