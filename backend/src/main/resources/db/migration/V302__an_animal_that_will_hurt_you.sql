-- #106/#108/#52 — an animal that will hurt you.
--
-- Every tamed animal in this world behaves identically. A milk goat and a full-grown boar are the same creature
-- once the bond reaches TAMED: the same to approach, the same to work, the same to stand beside. `tamability`
-- decides how hard an animal is to WIN OVER and then stops mattering entirely.
--
-- That is why #106's `muzzle_basket`, `leg_hobble`, `calming_blindfold`, `training_target` and
-- `milking_stanchion` sat blocked, and why #108's `boar_isolation_pen` is still recorded as blocked: there is
-- nothing for a restraint to restrain. It is also the last thing keeping husbandry from being about the animals
-- rather than about their numbers.
--
-- TEMPERAMENT IS A FACT ABOUT A SPECIES, and it lives beside the other ones. Three values, and the line between
-- them is what a keeper has to do differently:
--
--   BIDDABLE  — handled without thinking about it. Goats, fowl, sheep, donkeys.
--   WARY      — will not hurt you, but will not stand still either. Deer, reindeer, horses.
--   DANGEROUS — will hurt you, and the keeper who forgets it is the one who gets hurt. Cattle, boar, buffalo.
--
-- WHAT IT COSTS. Handling a DANGEROUS animal — milking it, feeding it, dosing it — risks a real injury, through
-- the same `applyInjury` every other trauma in this world goes through. Not a die roll every time: a keeper who
-- has built somewhere to hold the animal still is safe, and one who has not is taking a chance with a beast that
-- weighs six times what they do. WARY animals cost nothing; they are here so the column says something about
-- most of the catalogue rather than dividing it in two.
--
-- THE ANSWER IS A STANCHION. A milking stanchion is a frame that closes on an animal's neck and holds it while
-- you work: the oldest answer to "this animal will not stand still, and is strong enough that it does not have
-- to". #106 names it. It is one structure doing one job, and the guard below holds it to that.
--
-- `boar_isolation_pen` is deliberately still NOT built. Separating a dangerous animal from the HERD is a
-- different job from restraining it while you work, and it needs the herd to be able to come to harm — which is
-- the next slice, not this one. Recorded so it reads as sequenced rather than forgotten.

ALTER TABLE wildlife_species ADD COLUMN IF NOT EXISTS temperament VARCHAR(12) NOT NULL DEFAULT 'BIDDABLE'
  CHECK (temperament IN ('BIDDABLE','WARY','DANGEROUS'));

COMMENT ON COLUMN wildlife_species.temperament IS
  'How an animal behaves once TAMED, which tamability does not say. DANGEROUS animals injure the handler unless '
  'something holds them still. Distinct from tamability, which is only about winning one over in the first place.';

-- The ones that will actually hurt you. Cattle and buffalo are the classic killers of stock-keepers — a bull or
-- a cow with a calf far more than any predator in this catalogue — and a boar is the reason boars are kept apart.
UPDATE wildlife_species SET temperament = 'DANGEROUS'
 WHERE species_key IN ('aurochs','ox','water_buffalo','wild_boar','boar');

-- Strong, quick, and no intention of hurting anyone. These are the ones a stanchion is convenience for rather
-- than safety.
UPDATE wildlife_species SET temperament = 'WARY'
 WHERE species_key IN ('horse','reindeer','elk','red_deer','bighorn_sheep');

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS holds_an_animal_still BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.holds_an_animal_still IS
  'A frame that closes on an animal and holds it while the keeper works. Makes handling a DANGEROUS animal safe; '
  'does nothing for one that was never going to hurt anybody.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('MILKING_STANCHION', 'Milking stanchion', 'construction', FALSE, FALSE, TRUE, 'V302')
ON CONFLICT (project_kind) DO NOTHING;

-- It holds an animal and nothing else: no roof, no walls, nothing in a wolf's way, no stock kept in it.
UPDATE construction_kind SET holds_an_animal_still = TRUE, flammable = TRUE WHERE project_kind = 'MILKING_STANCHION';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('milking_stanchion','STRUCTURE','Milking stanchion',FALSE,NULL,'MILKING_STANCHION','construction',
   'build a milking stanchion,raise a milking stanchion,build a stanchion,build a milking frame,build a head yoke,milking stanchion,stanchion,milking frame',
   'milking stanchion,stanchion,milking frame,head yoke',
   'Two uprights and a closing bar, set where the beast will walk into it for feed and then find it cannot walk out.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('milking_stanchion_frame','milking_stanchion',1,'Set the uprights',NULL,0,'CUTTING',FALSE,
   'You sink two stout uprights a neck''s width apart, deep enough that a shoving beast will not move them.'),
  ('milking_stanchion_bar','milking_stanchion',2,'Hang the closing bar','milking_stanchion_frame',0,NULL,FALSE,
   'You hang a bar that drops into place behind the head, and try it once by hand to be sure it will not jam.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('milking_stanchion_frame','timber_log',2), ('milking_stanchion_frame','fiber_cordage',2),
  ('milking_stanchion_bar','hazel_rod',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every item these stages ask for must exist, or the structure is unbuildable and nothing says so.
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'milking_stanchion' AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V302: these stages ask for items that do not exist: %', bad; END IF;

  -- The V284 trap: BUILD_PEN matches the whole word "pen" and runs before the assembly matcher.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key='milking_stanchion' AND trim(k) ~ '(^|\s)pens?($|\s)';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V302: BUILD_PEN would swallow these keywords: %', bad; END IF;

  -- Something must be dangerous, or the whole slice is inert; and something must not, or every animal becomes a
  -- hazard and the distinction says nothing.
  SELECT count(*) INTO n FROM wildlife_species WHERE temperament='DANGEROUS';
  IF n = 0 THEN RAISE EXCEPTION 'V302: nothing is dangerous to handle'; END IF;
  SELECT count(*) INTO n FROM wildlife_species WHERE temperament='BIDDABLE';
  IF n = 0 THEN RAISE EXCEPTION 'V302: nothing is biddable, so every animal is a hazard'; END IF;

  -- A dangerous animal nobody can tame can never hurt a keeper, so the risk would be unreachable. At least one
  -- must be a species a keeper actually keeps — which means one that gives a yield or pulls a load.
  SELECT count(*) INTO n FROM wildlife_species ws WHERE ws.temperament='DANGEROUS'
    AND (ws.species_key IN (SELECT species_key FROM tamed_yield)
      OR ws.species_key IN (SELECT species_key FROM draft_species));
  IF n = 0 THEN RAISE EXCEPTION 'V302: no dangerous animal is one a keeper would ever handle'; END IF;

  -- The milk animals must not ALL be dangerous, or milking becomes impossible without a stanchion and the slice
  -- is a tax rather than a choice.
  SELECT count(*) INTO n FROM tamed_yield ty JOIN wildlife_species ws ON ws.species_key=ty.species_key
   WHERE ty.yield_kind='MILK' AND ws.temperament <> 'DANGEROUS';
  IF n = 0 THEN RAISE EXCEPTION 'V302: every milk animal is dangerous; there would be no safe way to start'; END IF;

  -- The stanchion does one thing.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='MILKING_STANCHION'
               AND (is_shelter OR encloses OR is_barrier OR shelters_stock OR shelters_birth OR isolates_sick)) THEN
    RAISE EXCEPTION 'V302: a stanchion holds an animal still, nothing else';
  END IF;
  SELECT count(*) INTO n FROM construction_kind WHERE holds_an_animal_still;
  IF n <> 1 THEN RAISE EXCEPTION 'V302: expected one restraint, found %', n; END IF;

  -- And it must be buildable, or wiring it changes nothing.
  SELECT count(*) INTO n FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
    JOIN construction_kind ck ON ck.project_kind=ad.construction_kind
   WHERE ck.holds_an_animal_still AND ad.review_state='VERIFIED';
  IF n = 0 THEN RAISE EXCEPTION 'V302: nobody can build the stanchion'; END IF;
END $$;
