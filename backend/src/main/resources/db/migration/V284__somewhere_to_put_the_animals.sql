-- #77/#108 — somewhere to put the animals.
--
-- A keeper had exactly one way to shelter stock: ANIMAL_PEN, raised by a Java intent, one size for everything
-- from a hare to an aurochs. #108 asks for species-appropriate pens, coops, stables and sheds, and there is now a
-- reason for them beyond variety: V281 made is_barrier the thing the night predator raid actually reads, so a
-- structure that stands in a wolf's way protects a herd and one that does not, does not. These are barriers that
-- earn their keep rather than four more cards.
--
-- Each is staged like the rest of the catalogue, and each is named for the animal it suits, because that is the
-- distinction #108 is asking for:
--   * a poultry coop is small, roofed and shut — fowl are taken from above as readily as from the side;
--   * a byre is roofed and walled for cattle, which are worth housing through a winter;
--   * a goat fold is a hurdle ring, unroofed — goats want air and a wall, not a roof;
--   * a pig sty is a low walled run with a shelter at one end.
--
-- KEYWORDS: none may contain "pen", which the BUILD_PEN Java intent owns and which runs before the assembly
-- matcher. That is the trap six structures fell into before (#513), and the standing invariant in
-- ConstructionRegistryCompleteIntegrationTest now fails the build if any keyword here is shadowed.

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('POULTRY_COOP', 'Poultry coop', 'construction', TRUE,  FALSE, TRUE, 'V284'),
  ('CATTLE_BYRE',  'Cattle byre',  'construction', TRUE,  FALSE, TRUE, 'V284'),
  ('GOAT_FOLD',    'Goat fold',    'construction', FALSE, FALSE, TRUE, 'V284'),
  ('PIG_STY',      'Pig sty',      'construction', FALSE, FALSE, TRUE, 'V284');

-- The coop and the byre are roofed, so an animal is inside them and out of the weather. The fold and the sty are
-- walls without a roof: they stop a wolf and nothing more, which is what is_barrier says and encloses does not.
UPDATE construction_kind SET encloses = TRUE  WHERE project_kind IN ('POULTRY_COOP','CATTLE_BYRE');
UPDATE construction_kind SET is_barrier = TRUE WHERE project_kind IN ('POULTRY_COOP','CATTLE_BYRE','GOAT_FOLD','PIG_STY');

-- And they burn, which is a flag it is very easy to forget because nothing forces it. A coop of hurdle and bark,
-- a byre roofed in thatch and a ring of hazel hurdles are as combustible as the wattle fence and the reed hut
-- already marked flammable; only the sty, which is a stone wall with a scrap of roof, is not.
UPDATE construction_kind SET flammable = TRUE WHERE project_kind IN ('POULTRY_COOP','CATTLE_BYRE','GOAT_FOLD');

-- THATCH_ROOF was marked not to burn, which is the same omission already in the tree: thatch is the most
-- combustible roofing there has ever been, and a wattle fence beside it was flammable while it was not. The
-- registry invariant only ever asserted that stone and earth do NOT burn, so a wooden build marked fireproof
-- passed in silence — which is exactly how these four were about to.
UPDATE construction_kind SET flammable = TRUE WHERE project_kind = 'THATCH_ROOF';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('poultry_coop','STRUCTURE','Poultry coop',FALSE,NULL,'POULTRY_COOP','construction',
   'build a poultry coop,raise a poultry coop,build a hen house,build a fowl house,poultry coop,hen house,fowl house',
   'poultry coop,hen house,fowl house,coop',
   'A low box of hurdle and bark, shut at night against the things that take birds from above as readily as from the side.',
   'VERIFIED', now()),
  ('cattle_byre','STRUCTURE','Cattle byre',FALSE,NULL,'CATTLE_BYRE','construction',
   'build a cattle byre,raise a cattle byre,build a byre,build a cow shed,cattle byre,cow shed,byre',
   'cattle byre,cow shed,byre',
   'A roofed and walled house for cattle, deep enough in litter to stand a winter in.',
   'VERIFIED', now()),
  ('goat_fold','STRUCTURE','Goat fold',FALSE,NULL,'GOAT_FOLD','construction',
   'build a goat fold,raise a goat fold,build a sheep fold,goat fold,sheep fold,hurdle fold',
   'goat fold,sheep fold,hurdle fold,fold',
   'A ring of hurdles set on open ground. Goats want air and a wall between them and the dark, not a roof.',
   'VERIFIED', now()),
  ('pig_sty','STRUCTURE','Pig sty',FALSE,NULL,'PIG_STY','construction',
   'build a pig sty,raise a pig sty,build a sty,pig sty,hog sty,sty',
   'pig sty,hog sty,sty',
   'A low walled run with a shelter at one end, floored in whatever the pigs have not yet rooted up.',
   'VERIFIED', now());

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('poultry_coop_frame','poultry_coop',1,'Frame the coop',NULL,0,NULL,FALSE,
   'You set a low frame of rods and lash the corners, small enough to shut and light enough to move.'),
  ('poultry_coop_close','poultry_coop',2,'Board and shut it','poultry_coop_frame',0,NULL,FALSE,
   'You lap bark over the frame and hang a door that will hold against a fox nosing at it in the dark.'),
  ('cattle_byre_posts','cattle_byre',1,'Set the byre posts',NULL,0,'CUTTING',FALSE,
   'You sink heavy posts in two lines, far enough apart that a full-grown beast can turn between them.'),
  ('cattle_byre_walls','cattle_byre',2,'Wall and roof the byre','cattle_byre_posts',0,NULL,FALSE,
   'You weave the walls between the posts and lay thatch over the whole of it, and the space beneath goes quiet and warm.'),
  ('goat_fold_hurdles','goat_fold',1,'Set the hurdles',NULL,0,NULL,FALSE,
   'You weave hurdles and set them in a ring, high enough that nothing gets over and close enough that nothing gets through.'),
  ('pig_sty_walls','pig_sty',1,'Wall the sty',NULL,0,NULL,FALSE,
   'You raise a low wall of stone and stake around a corner of ground, and roof one end of it against the rain.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('poultry_coop_frame','hazel_rod',3), ('poultry_coop_frame','fiber_cordage',2),
  ('poultry_coop_close','bark_sheet',3),
  ('cattle_byre_posts','timber_log',4), ('cattle_byre_posts','fiber_cordage',3),
  ('cattle_byre_walls','hazel_rod',6), ('cattle_byre_walls','thatch_bundle',4),
  ('goat_fold_hurdles','hazel_rod',6), ('goat_fold_hurdles','fiber_cordage',4),
  ('pig_sty_walls','field_stone',6), ('pig_sty_walls','dry_branch',4);

DO $$
DECLARE bad text;
BEGIN
  -- Every item these stages ask for must exist, or the structure is unbuildable and nothing says so.
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key IN ('poultry_coop','cattle_byre','goat_fold','pig_sty')
     AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V284: these stages ask for items that do not exist: %', bad; END IF;

  -- No keyword may contain "pen": BUILD_PEN runs before the assembly matcher and would swallow it whole.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key IN ('poultry_coop','cattle_byre','goat_fold','pig_sty') AND trim(k) LIKE '%pen%';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V284: these keywords would be swallowed by the BUILD_PEN intent: %', bad; END IF;

  -- Every one of them must actually stand in a wolf's way, which is the whole reason they exist.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE project_kind IN ('POULTRY_COOP','CATTLE_BYRE','GOAT_FOLD','PIG_STY') AND NOT is_barrier;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V284: an animal shelter that stops nothing protects no stock: %', bad; END IF;

  -- And the unroofed two must not claim to be shelter for a Chronicle.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind IN ('GOAT_FOLD','PIG_STY') AND encloses) THEN
    RAISE EXCEPTION 'V284: a hurdle ring is a wall, not a roof — it must not enclose';
  END IF;

  -- Wood, bark, hurdle and thatch burn. This is the side of the flammability question nothing was asserting.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE project_kind IN ('POULTRY_COOP','CATTLE_BYRE','GOAT_FOLD','THATCH_ROOF') AND NOT flammable;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V284: hurdle, bark and thatch carry flame: %', bad; END IF;

  -- The sty is stone and must not have been swept up with them.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='PIG_STY' AND flammable) THEN
    RAISE EXCEPTION 'V284: a stone-walled sty does not catch';
  END IF;
END $$;
