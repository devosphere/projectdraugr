-- #108/#52 — the ones that do not live.
--
-- V296 gave stock young; V297 gave the young a way to die, and a roof to be saved by. That left #108's
-- farrowing shelter, brooder shelter and foaling stall in an awkward place: a roof ALREADY answers the only
-- risk young face, completely, so a purpose-built birthing house would have been a fourth name for a byre. This
-- catalogue has rejected that shape before — three fireplaces that were three names for one behaviour — and it
-- should reject it here too.
--
-- So these three do not exist until there is something for them to do that a byre cannot. There is: the losses
-- that happen AT birth. In real stock-keeping perinatal loss is the single largest one there is — far larger
-- than winter — and reducing it is precisely and only what a farrowing house, a brooder and a foaling box are
-- for. A byre keeps the weather off; it does not stand over a sow at three in the morning.
--
-- THREE TIERS, and each is a thing the keeper built:
--
--   open ground              -> the full loss the species carries
--   a roofed stock shelter   -> half of it (out of the wind and the wet, which is most of what kills a newborn)
--   a purpose-built birthing -> none (the point of the building)
--
-- The middle tier is what makes the byre worth more than it was and the new houses worth building anyway.
--
-- THE LOSS IS PER ANIMAL AND DETERMINISTIC. Rolled from the pregnancy and the animal's index within the litter,
-- so a save resumed twice loses the same young — the same rule the litter size follows. Per animal rather than
-- per litter because a cow carries one: a percentage of a litter of one rounds to nothing, and a foaling stall
-- that helps every species except horses would be an absurd thing to ship.
--
-- The rates are the real ones, roughly: pigs and sheep lose most, cattle and horses least, and a clutch of fowl
-- loses a share to eggs that never hatch. They live on breeding_profile because they are a fact about a species.

ALTER TABLE breeding_profile ADD COLUMN IF NOT EXISTS birth_loss_percent SMALLINT NOT NULL DEFAULT 0
  CHECK (birth_loss_percent BETWEEN 0 AND 60);

COMMENT ON COLUMN breeding_profile.birth_loss_percent IS
  'Share of young lost at birth on open ground, per animal. Halved under any roofed stock shelter and removed '
  'entirely by a purpose-built birthing house. Perinatal loss is the largest single loss in keeping stock.';

UPDATE breeding_profile SET birth_loss_percent = CASE species_key
    WHEN 'mountain_goat'  THEN 18
    WHEN 'bighorn_sheep'  THEN 20
    WHEN 'aurochs'        THEN 10
    WHEN 'water_buffalo'  THEN 10
    WHEN 'reindeer'       THEN 14
    WHEN 'horse'          THEN 8
    WHEN 'donkey'         THEN 8
    ELSE 15                                   -- fowl: the share of a clutch that never hatches
  END;

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS shelters_birth BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.shelters_birth IS
  'Built for the birth itself — a place to stand over a labouring animal and keep the newborn alive. Removes '
  'birth loss outright, where a merely roofed shelter halves it.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('FARROWING_SHELTER', 'Farrowing shelter', 'construction', TRUE, FALSE, TRUE, 'V298'),
  ('BROODER_SHELTER',   'Brooder shelter',   'construction', TRUE, FALSE, TRUE, 'V298'),
  ('FOALING_STALL',     'Foaling stall',     'construction', TRUE, FALSE, TRUE, 'V298')
ON CONFLICT (project_kind) DO NOTHING;

-- All three are roofed buildings a beast is inside, they stand in a wolf's way, they hold stock, they are the
-- birthing houses, and all three burn — timber, hurdle and thatch, like the byre and the coop before them.
UPDATE construction_kind SET encloses = TRUE, is_barrier = TRUE, shelters_stock = TRUE, shelters_birth = TRUE,
                             flammable = TRUE, barrier_strength = 18
 WHERE project_kind IN ('FARROWING_SHELTER','BROODER_SHELTER','FOALING_STALL');

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('farrowing_shelter','STRUCTURE','Farrowing shelter',FALSE,NULL,'FARROWING_SHELTER','construction',
   'build a farrowing shelter,raise a farrowing shelter,build a farrowing house,build a farrowing hut,farrowing shelter,farrowing house,farrowing hut',
   'farrowing shelter,farrowing house,farrowing hut',
   'A low creep of stout rails set a hand off the wall, so a sow can lie down without lying on what she has just had.',
   'VERIFIED', now()),
  ('brooder_shelter','STRUCTURE','Brooder shelter',FALSE,NULL,'BROODER_SHELTER','construction',
   'build a brooder shelter,raise a brooder shelter,build a brooder,build a chick shelter,brooder shelter,brooder house,chick shelter',
   'brooder shelter,brooder house,chick shelter,brooder',
   'A small close box banked with litter, dark and dry, where a clutch can be kept warm by their own crowding.',
   'VERIFIED', now()),
  ('foaling_stall','STRUCTURE','Foaling stall',FALSE,NULL,'FOALING_STALL','construction',
   'build a foaling stall,raise a foaling stall,build a foaling box,build a birthing stall,foaling stall,foaling box,birthing stall',
   'foaling stall,foaling box,birthing stall',
   'A wide box bedded deep and walled to the shoulder, quiet enough that a mare will lie down in it at all.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('farrowing_shelter_frame','farrowing_shelter',1,'Frame the farrowing shelter',NULL,0,'CUTTING',FALSE,
   'You set a low frame close to the ground, no higher than a sow standing, and floor it dry.'),
  ('farrowing_shelter_rails','farrowing_shelter',2,'Set the creep rails','farrowing_shelter_frame',0,NULL,FALSE,
   'You run stout rails along the walls a hand''s width out, leaving a gap a piglet fits into and a sow does not.'),
  ('brooder_shelter_box','brooder_shelter',1,'Build the brooder box',NULL,0,'CUTTING',FALSE,
   'You build a close box, low and lidded, small enough that what is in it warms it by being in it.'),
  ('brooder_shelter_bed','brooder_shelter',2,'Bank it with litter','brooder_shelter_box',0,NULL,FALSE,
   'You bank dry litter deep around and under, until the inside of it holds its warmth through a night.'),
  ('foaling_stall_posts','foaling_stall',1,'Set the foaling stall posts',NULL,0,'CUTTING',FALSE,
   'You sink posts wide enough apart that a mare can go down and get up again without touching either wall.'),
  ('foaling_stall_bed','foaling_stall',2,'Wall and bed the stall','foaling_stall_posts',0,NULL,FALSE,
   'You board it to the shoulder and bed it deep, and the noise of the place drops away to almost nothing.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('farrowing_shelter_frame','timber_log',3), ('farrowing_shelter_frame','fiber_cordage',2),
  ('farrowing_shelter_rails','hazel_rod',5),
  ('brooder_shelter_box','hazel_rod',4), ('brooder_shelter_box','bark_sheet',3),
  ('brooder_shelter_bed','dry_grass_bundle',3),
  ('foaling_stall_posts','timber_log',4), ('foaling_stall_posts','fiber_cordage',3),
  ('foaling_stall_bed','hazel_rod',6), ('foaling_stall_bed','dry_grass_bundle',4)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every item these stages ask for must exist, or the structure is unbuildable and nothing says so.
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key IN ('farrowing_shelter','brooder_shelter','foaling_stall')
     AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: these stages ask for items that do not exist: %', bad; END IF;

  -- The V284 trap: BUILD_PEN runs before the assembly matcher and swallows any keyword containing "pen".
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key IN ('farrowing_shelter','brooder_shelter','foaling_stall') AND trim(k) LIKE '%pen%';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: these keywords would be swallowed by the BUILD_PEN intent: %', bad; END IF;

  -- The whole reason these exist: they must do something a byre cannot.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE project_kind IN ('FARROWING_SHELTER','BROODER_SHELTER','FOALING_STALL') AND NOT shelters_birth;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: a birthing house that does not shelter a birth is a fourth name for a byre: %', bad; END IF;

  -- And the ordinary animal houses must NOT claim it, or the distinction collapses the other way.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE shelters_birth AND project_kind IN ('CATTLE_BYRE','POULTRY_COOP','TIMBER_BARN','ANIMAL_PEN','GOAT_FOLD','PIG_STY');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: a byre keeps the weather off; it does not stand over a sow at three in the morning: %', bad; END IF;

  -- A birthing house must also be a roofed stock shelter, or it would remove birth loss while failing to keep
  -- the young it saved alive through the first frost.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE shelters_birth AND NOT (shelters_stock AND encloses);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: a birthing house must shelter what it delivers: %', bad; END IF;

  -- The loss must be real for every breeding species, or the tiers gate nothing for some of them.
  SELECT string_agg(species_key, ', ' ORDER BY species_key) INTO bad
    FROM breeding_profile WHERE birth_loss_percent <= 0;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: nothing is ever lost at birth for these, so no birthing house helps them: %', bad; END IF;

  -- Each must be reachable, or wiring it changes nothing — the lesson V293 wrote down.
  SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO bad FROM construction_kind ck
   WHERE ck.shelters_birth AND NOT EXISTS (
     SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key = ad.assembly_key
      WHERE ad.construction_kind = ck.project_kind AND ad.review_state = 'VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V298: nobody can build these: %', bad; END IF;

  SELECT count(*) INTO n FROM construction_kind WHERE shelters_birth;
  IF n <> 3 THEN RAISE EXCEPTION 'V298: expected three birthing houses, found %', n; END IF;
END $$;
