-- #108/#106/#100 — what carries you instead of a load.
--
-- Eight species pull. `draft_species` has given every one of them a haul bonus since #100, so a Chronicle with a
-- tamed horse can drag a season's timber on a travois — and then walks the whole way beside it. **Nothing in the
-- world can be ridden.** A horse and an ox are the same animal to this simulation: a number of grams.
--
-- That is what left #108's `mounting_block` and the whole tack half of #106 recorded as blocked. It is also the
-- largest thing still missing from the working-animal loop, because riding is not a convenience — it is the
-- reason a horse was worth more than the meat on it.
--
-- WHAT RIDING IS HERE. Not a state with a mount and a dismount action and a saddle to lose track of. It is HOW
-- YOU TRAVEL: set out for a place with a beast that can be ridden and something to guide it by, and you ride.
-- A Chronicle who qualifies goes at better than twice walking pace; one who does not, walks. There is nothing
-- to remember and nothing to toggle.
--
-- AND THE COST MOVES RATHER THAN VANISHING. The journey still tires something — it tires the ANIMAL. draft
-- fatigue already gates haulage, so a horse ridden hard all week is a horse that cannot pull, and a keeper who
-- rides everywhere finds their draft team useless exactly when they need it. That is the trade real keepers
-- made, and it costs no new table to express.
--
-- WHICH SPECIES. The ones people actually rode: horse, donkey, reindeer, water buffalo. Cattle are driven, not
-- ridden — an ox under saddle is a curiosity, not husbandry — and elk and red deer are here as draft animals
-- rather than mounts. `rideable` is a fact about a species, so it lives beside its haul bonus.
--
-- NO NEW TACK ITEM. A `draft_harness` and a `rope_harness` already exist and are already the things a keeper
-- makes to work an animal; people rode for millennia before the saddle, and a harness to guide by is the honest
-- floor. Inventing a riding_saddle here would add a second thing to keep in step for no behaviour a harness does
-- not already carry.

ALTER TABLE draft_species ADD COLUMN IF NOT EXISTS rideable BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN draft_species.rideable IS
  'Did people ride this? Horse, donkey, reindeer, water buffalo. Cattle are driven, not ridden. A rideable beast '
  'carries its handler at better than twice walking pace, and takes the journey''s fatigue in their place.';

UPDATE draft_species SET rideable = TRUE WHERE species_key IN ('horse','donkey','reindeer','water_buffalo');

-- The mounting block. Its job is narrow and real: getting onto a tall animal while carrying a load is the part
-- that actually stops people, and a block is the oldest answer to it. Without one, a heavily laden Chronicle
-- walks and leads the beast instead.
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS aids_mounting BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.aids_mounting IS
  'Something to climb from — the difference between getting up onto a tall animal while laden and not. Read only '
  'when the handler is carrying enough that mounting would otherwise be beyond them.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('MOUNTING_BLOCK', 'Mounting block', 'construction', FALSE, FALSE, TRUE, 'V301')
ON CONFLICT (project_kind) DO NOTHING;

-- A stack of dressed stone at the right height. It shelters nothing, encloses nothing and stops nothing — which
-- is the point: it does exactly one thing, and the guard below holds it to that.
UPDATE construction_kind SET aids_mounting = TRUE, flammable = FALSE WHERE project_kind = 'MOUNTING_BLOCK';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('mounting_block','STRUCTURE','Mounting block',FALSE,NULL,'MOUNTING_BLOCK','construction',
   'build a mounting block,raise a mounting block,build a mounting step,build a horse block,mounting block,mounting step,horse block',
   'mounting block,mounting step,horse block',
   'Three courses of dressed stone set square beside the tether, high enough to swing a leg from while laden.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('mounting_block_courses','mounting_block',1,'Lay the block',NULL,0,'STRIKING',FALSE,
   'You lay three courses of stone square and level, and stand on it once to be sure it does not rock.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('mounting_block_courses','field_stone',5)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Something must be rideable, or the whole slice is inert.
  SELECT count(*) INTO n FROM draft_species WHERE rideable;
  IF n = 0 THEN RAISE EXCEPTION 'V301: nothing can be ridden'; END IF;

  -- And something must not, or "rideable" is a column that says nothing.
  SELECT count(*) INTO n FROM draft_species WHERE NOT rideable;
  IF n = 0 THEN RAISE EXCEPTION 'V301: everything is rideable, so the distinction carries nothing'; END IF;

  -- Cattle are driven, not ridden. Stated because it is the one people reach for.
  SELECT string_agg(species_key, ', ' ORDER BY species_key) INTO bad FROM draft_species
   WHERE rideable AND species_key IN ('ox','aurochs');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V301: cattle are driven, not ridden: %', bad; END IF;

  -- Every rideable species must be tamable, or it can never be ridden by anyone.
  SELECT string_agg(ds.species_key, ', ' ORDER BY ds.species_key) INTO bad FROM draft_species ds
   WHERE ds.rideable AND NOT EXISTS (
     SELECT 1 FROM wildlife_species ws WHERE ws.species_key = ds.species_key AND ws.tamability > 0);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V301: nobody can tame these, so nobody can ride them: %', bad; END IF;

  -- The harness a rider guides by must exist, or riding is gated on an item nobody can hold.
  SELECT string_agg(k, ', ') INTO bad FROM unnest(ARRAY['draft_harness','rope_harness']) k
   WHERE k NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V301: riding is gated on tack that does not exist: %', bad; END IF;

  -- The mounting block does one thing. It is not a shelter, not a barrier, and holds no stock — if it ever
  -- claims to be any of those it has stopped being a block and become another pen.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='MOUNTING_BLOCK'
               AND (is_shelter OR encloses OR is_barrier OR shelters_stock OR shelters_birth OR isolates_sick)) THEN
    RAISE EXCEPTION 'V301: a mounting block is a step to climb from, nothing else';
  END IF;

  -- Exactly one thing aids mounting, and it must be buildable.
  SELECT count(*) INTO n FROM construction_kind WHERE aids_mounting;
  IF n <> 1 THEN RAISE EXCEPTION 'V301: expected one mounting aid, found %', n; END IF;
  SELECT count(*) INTO n FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
    JOIN construction_kind ck ON ck.project_kind=ad.construction_kind
   WHERE ck.aids_mounting AND ad.review_state='VERIFIED';
  IF n = 0 THEN RAISE EXCEPTION 'V301: nobody can build the mounting block'; END IF;

  -- The V284 trap: no keyword may be swallowed by BUILD_PEN.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key='mounting_block' AND trim(k) ~ '(^|\s)pens?($|\s)';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V301: BUILD_PEN would swallow these keywords: %', bad; END IF;
END $$;
