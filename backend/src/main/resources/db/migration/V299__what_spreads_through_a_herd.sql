-- #108/#52/#79 — what spreads through a herd.
--
-- Stock could be hungry, thirsty, worked to exhaustion, and they could die of cold as newborns. They could not
-- get sick. That absence is what left #108's quarantine pen and sick-animal shelter recorded as blocked for
-- three cycles running: there is nothing to isolate an animal FROM.
--
-- It also left a hole in the middle of the husbandry loop. `foulGroundWithLivestock` has made kept stock foul
-- the ground they stand on since V-earlier, and a manure pit or compost bay CONTAINS that muck — a real decision
-- with a real cost. But the cost was only ever paid by the Chronicle and their larder: refuse draws predators,
-- costs body condition, and docks the shelf life of stored food. **The animals standing in it were unaffected**,
-- which is exactly backwards. Filth is the oldest reason stock sicken and the oldest reason to muck out.
--
-- So sickness is not a new system bolted on; it is the missing consequence of one that already runs:
--
--     stock foul the ground  ->  the ground sickens the stock  ->  the keeper mucks out, or does not
--
-- WHAT SICKNESS COSTS. A sick animal gives nothing — no milk, no eggs, no fleece — and does not get in calf.
-- Both are consequences the keeper feels without anything dying, which is the right weight for a first slice:
-- the herd stops paying until it is looked after. Death by disease is deliberately NOT here. V297 already
-- established that young can be lost on a deadline, and a second way to lose animals wants its own slice and its
-- own evidence rather than riding in on this one.
--
-- WHAT SPREADS. Sickness runs through a keeper's animals of the same species, because that is what makes an
-- isolation shelter worth building. Without one, a sick beast drags the rest of its kind down with it. With one,
-- it does not. That is the whole of the structure's job and the reason it is not a fourth pen.
--
-- ONE STRUCTURE, NOT TWO. #108 names both `quarantine_pen` and `sick_animal_shelter`. They are one behaviour
-- under two names — somewhere to put an animal so it cannot infect the others — and this catalogue has rejected
-- that shape before (three fireplaces that were three names for one behaviour). `SICK_ANIMAL_SHELTER` is built;
-- `quarantine_pen` is deliberately not a second row, and its words are keywords on this one so a keeper who asks
-- for a quarantine shelter gets it.
--
-- AND ITS KEYWORDS MUST NOT CONTAIN "pen". `BUILD_PEN` matches the whole word `pen`/`pens` and runs before the
-- assembly matcher, so "build a quarantine pen" is swallowed and raises a generic animal pen instead. That is
-- the V284 trap, and the guard below fails the build if it is ever reintroduced — which is also why this is
-- named a shelter rather than a pen, so the next reader is not tempted.

ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS sickness SMALLINT NOT NULL DEFAULT 0
  CHECK (sickness BETWEEN 0 AND 100);

COMMENT ON COLUMN wildlife_bond.sickness IS
  'How sick this keeper''s animals of this population are, 0-100. Rises on fouled ground, falls on clean. A sick '
  'animal gives no yield and does not conceive, and spreads to the keeper''s other stock of its species unless an '
  'isolation shelter stands.';

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS isolates_sick BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.isolates_sick IS
  'Somewhere to put an animal so it cannot infect the rest. Stops sickness spreading through a herd; does not '
  'cure the animal already in it.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('SICK_ANIMAL_SHELTER', 'Sick animal shelter', 'construction', TRUE, FALSE, TRUE, 'V299')
ON CONFLICT (project_kind) DO NOTHING;

-- A roofed, walled box off to one side: it holds stock, it keeps the weather off, it stands in a wolf's way, and
-- it is the one thing that stops a sickness running through the rest. It does NOT shelter a birth — a sick pen is
-- the last place to put a labouring animal, and claiming otherwise would quietly make it a birthing house too.
UPDATE construction_kind SET encloses = TRUE, is_barrier = TRUE, shelters_stock = TRUE, isolates_sick = TRUE,
                             flammable = TRUE, barrier_strength = 16
 WHERE project_kind = 'SICK_ANIMAL_SHELTER';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('sick_animal_shelter','STRUCTURE','Sick animal shelter',FALSE,NULL,'SICK_ANIMAL_SHELTER','construction',
   'build a sick animal shelter,raise a sick animal shelter,build a quarantine shelter,raise a quarantine shelter,build an isolation shelter,build a sick shelter,sick animal shelter,quarantine shelter,isolation shelter,isolation hut',
   'sick animal shelter,quarantine shelter,isolation shelter,isolation hut,sick shelter',
   'A small shut box set well apart from the rest, with its own door and its own litter, so that whatever is in it stays in it.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('sick_animal_shelter_frame','sick_animal_shelter',1,'Frame it well apart',NULL,0,'CUTTING',FALSE,
   'You set it downwind and a good way off, because the whole point of it is distance.'),
  ('sick_animal_shelter_close','sick_animal_shelter',2,'Close and litter it','sick_animal_shelter_frame',0,NULL,FALSE,
   'You board it tight and bed it with its own litter, kept separate from everything the rest of the stock touch.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('sick_animal_shelter_frame','timber_log',3), ('sick_animal_shelter_frame','fiber_cordage',2),
  ('sick_animal_shelter_close','hazel_rod',4), ('sick_animal_shelter_close','dry_grass_bundle',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every item these stages ask for must exist, or the structure is unbuildable and nothing says so.
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'sick_animal_shelter' AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V299: these stages ask for items that do not exist: %', bad; END IF;

  -- The V284 trap, and the reason this is a shelter and not a pen: BUILD_PEN matches the whole word "pen" and
  -- runs before the assembly matcher, so any keyword containing it is swallowed whole.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'sick_animal_shelter'
     AND (trim(k) ~ '(^|\s)pens?($|\s)');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V299: BUILD_PEN would swallow these keywords: %', bad; END IF;

  -- A keeper must be able to ask for it by the name the ticket uses, even though no row is called that.
  IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key='sick_animal_shelter'
                   AND keywords LIKE '%quarantine shelter%') THEN
    RAISE EXCEPTION 'V299: quarantine_pen is deliberately not a second row, so its words must reach this one';
  END IF;

  -- The whole reason it exists.
  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='SICK_ANIMAL_SHELTER' AND isolates_sick) THEN
    RAISE EXCEPTION 'V299: an isolation shelter that isolates nothing is a fourth pen';
  END IF;

  -- Nothing else may claim isolation, or the distinction collapses and every byre becomes a sick bay.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE isolates_sick AND project_kind <> 'SICK_ANIMAL_SHELTER';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V299: these are not sick bays: %', bad; END IF;

  -- A sick bay is the last place to put a labouring animal; it must not quietly become a birthing house.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE isolates_sick AND shelters_birth) THEN
    RAISE EXCEPTION 'V299: an isolation shelter must not also shelter a birth';
  END IF;

  -- It must still hold stock and keep the weather off, or an animal put in it would be saved from its herd and
  -- lost to the first frost.
  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='SICK_ANIMAL_SHELTER'
                   AND shelters_stock AND encloses) THEN
    RAISE EXCEPTION 'V299: an isolation shelter must shelter what it isolates';
  END IF;

  -- Reachable, or wiring it changes nothing — the lesson V293 wrote down.
  SELECT count(*) INTO n FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
   WHERE ad.construction_kind='SICK_ANIMAL_SHELTER' AND ad.review_state='VERIFIED';
  IF n = 0 THEN RAISE EXCEPTION 'V299: nobody can build the sick animal shelter'; END IF;
END $$;
