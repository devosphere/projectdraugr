-- #108/#106 — a bull among the young.
--
-- V302 gave species a temperament, and a DANGEROUS animal now hurts the keeper who works on it without a
-- stanchion. It does not yet hurt anything else. A bull, a boar or a buffalo stands in the same fold as the
-- kids and lambs and is no more trouble to them than a goose.
--
-- That is the last thing #108's `boar_isolation_pen` was waiting on, and it is why V302 deliberately did not
-- build it: restraining an animal while you work on it and keeping it away from the rest of the herd are two
-- different jobs, and the second one needs the herd to be able to come to harm. Now it can.
--
-- WHAT HAPPENS. Young are the ones at risk — they are small, they are slow, and they are already the fragile
-- thing in this simulation (V297 loses them to cold, V298 to a hard birth). A dangerous animal kept on the same
-- ground as young, with nothing separating them, kills some. That is not a flourish either: trampling and goring
-- by breeding males is a real and ordinary loss in stock-keeping, and separating the boar is the ordinary answer.
--
-- ONE PEN, AND THE NAME IS THE PROBLEM. #108 calls it `boar_isolation_pen`, and BUILD_PEN matches the whole word
-- "pen" and runs before the assembly matcher — the V284 trap that has caught this catalogue before. So the
-- structure is `BULL_ISOLATION_YARD`: the word "pen" appears nowhere in its keywords, "boar" and "bull" both
-- reach it, and the guard below fails the build if "pen" ever creeps back in.
--
-- It is NOT the sick bay. An isolation shelter (V299) keeps a sick animal from infecting the herd; this keeps a
-- dangerous one from injuring it. Same word, different job, and the guard holds them apart — a keeper who built
-- one should not silently get the other.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS separates_dangerous BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.separates_dangerous IS
  'Somewhere to keep a dangerous animal away from the rest of the herd. Stops it killing young; does not make it '
  'safe to work on (that is holds_an_animal_still) and does not stop sickness (that is isolates_sick).';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('BULL_ISOLATION_YARD', 'Bull isolation yard', 'construction', FALSE, FALSE, TRUE, 'V304')
ON CONFLICT (project_kind) DO NOTHING;

-- A stout walled yard off to one side. It holds stock and stands in a wolf's way, because it is a real enclosure;
-- it has no roof, because a bull yard is a yard.
UPDATE construction_kind SET separates_dangerous = TRUE, shelters_stock = TRUE, is_barrier = TRUE,
                             barrier_strength = 20, flammable = TRUE
 WHERE project_kind = 'BULL_ISOLATION_YARD';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('bull_isolation_yard','STRUCTURE','Bull isolation yard',FALSE,NULL,'BULL_ISOLATION_YARD','construction',
   'build a bull isolation yard,raise a bull isolation yard,build a bull yard,build a boar yard,build an isolation yard,bull isolation yard,bull yard,boar yard,isolation yard',
   'bull isolation yard,bull yard,boar yard,isolation yard',
   'A stout walled yard set apart from the rest, high enough and heavy enough that what is in it stays in it.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('bull_isolation_yard_posts','bull_isolation_yard',1,'Sink the yard posts',NULL,0,'CUTTING',FALSE,
   'You sink posts far heavier than a fold needs, because what goes in here can push a hurdle flat without trying.'),
  ('bull_isolation_yard_rails','bull_isolation_yard',2,'Rail it round','bull_isolation_yard_posts',0,NULL,FALSE,
   'You rail it round at three heights and lash every joint twice, and lean on it hard before you trust it.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('bull_isolation_yard_posts','timber_log',5), ('bull_isolation_yard_posts','fiber_cordage',3),
  ('bull_isolation_yard_rails','hazel_rod',8)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key='bull_isolation_yard' AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V304: these stages ask for items that do not exist: %', bad; END IF;

  -- The V284 trap, and the reason this is a yard and not a pen.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key='bull_isolation_yard' AND trim(k) ~ '(^|\s)pens?($|\s)';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V304: BUILD_PEN would swallow these keywords: %', bad; END IF;

  -- #108 asks for it by the boar's name, so the boar's word must reach it.
  IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key='bull_isolation_yard'
                   AND keywords LIKE '%boar yard%') THEN
    RAISE EXCEPTION 'V304: #108 names this for the boar, so a keeper asking for a boar yard must get it';
  END IF;

  -- Three separate jobs, three separate flags, and nothing may hold two of them — a keeper who built one must
  -- not silently get another.
  SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad FROM construction_kind
   WHERE (separates_dangerous AND isolates_sick)
      OR (separates_dangerous AND holds_an_animal_still)
      OR (isolates_sick AND holds_an_animal_still);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V304: separating, isolating and restraining are three jobs, not one: %', bad;
  END IF;

  -- Exactly one thing separates, and it must be buildable and able to hold what it separates.
  SELECT count(*) INTO n FROM construction_kind WHERE separates_dangerous;
  IF n <> 1 THEN RAISE EXCEPTION 'V304: expected one bull yard, found %', n; END IF;
  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE separates_dangerous AND shelters_stock) THEN
    RAISE EXCEPTION 'V304: a yard that separates stock must be somewhere stock can be kept';
  END IF;
  SELECT count(*) INTO n FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
    JOIN construction_kind ck ON ck.project_kind=ad.construction_kind
   WHERE ck.separates_dangerous AND ad.review_state='VERIFIED';
  IF n = 0 THEN RAISE EXCEPTION 'V304: nobody can build the bull yard'; END IF;

  -- And the harm it prevents must be possible: something dangerous must be keepable, or the yard guards nothing.
  SELECT count(*) INTO n FROM wildlife_species ws WHERE ws.temperament='DANGEROUS'
    AND (ws.species_key IN (SELECT species_key FROM tamed_yield) OR ws.species_key IN (SELECT species_key FROM draft_species));
  IF n = 0 THEN RAISE EXCEPTION 'V304: no dangerous animal is one a keeper would keep, so nothing threatens the young'; END IF;
END $$;
