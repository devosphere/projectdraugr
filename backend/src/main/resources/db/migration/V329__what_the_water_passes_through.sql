-- #77 — what the water passes through.
--
-- Water in this world is treated in the hand and nowhere else. A Chronicle can boil it, pour it through a bark cone
-- or a fired clay filter, or skim it with a ladle — and every one of those is a thing carried, done a vesselful at a
-- time. #77 names the three structures a first-era camp builds so that it stops doing that: `settling_basin`,
-- `sand_filter_bed` and `spring_head_protection`. None existed, and the camp's water was exactly as foul beside a
-- dug, gravelled, sanded bed as beside a bare bank.
--
-- THREE DIFFERENT ANSWERS, which is what keeps them from being three names for one hole:
--
--   * A **settling basin** lets the silt drop out before you draw. It eases untreated water; it does not clean it.
--   * A **sand filter bed** is the real thing — water drawn off beneath a bed of sand, gravel and charcoal comes out
--     as clear as anything poured through a filter by hand. So a vessel filled there fills with filtered water, and
--     a drink taken there carries the filtered-water risk, not the raw one.
--   * **Spring-head protection** does something neither of the others can: it keeps a spring clean. A walled,
--     covered spring head is fed from below, so the refuse of a fouled camp running over the ground does not reach
--     it. It does nothing for a stream or a river — those carry what is thrown into them — and the Java asks for a
--     spring on the ground, not merely water.
--
-- What none of them does: make water out of nothing. Every one needs water already in reach, exactly as a vessel
-- does, and none makes a draw safe that the Chronicle fouled on purpose — ground they named for waste stays foul.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS clarifies_draw SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS draws_filtered BOOLEAN  NOT NULL DEFAULT FALSE;
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS shields_spring BOOLEAN  NOT NULL DEFAULT FALSE;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='construction_kind_clarifies_draw_check') THEN
    ALTER TABLE construction_kind ADD CONSTRAINT construction_kind_clarifies_draw_check CHECK (clarifies_draw BETWEEN 0 AND 5);
  END IF;
END $$;

COMMENT ON COLUMN construction_kind.clarifies_draw IS
  'How much of the gut risk of drinking untreated water from the source here it takes away. Never all of it: a '
  'draw is at least the risk of filtered water, because only a boil makes water safe.';
COMMENT ON COLUMN construction_kind.draws_filtered IS
  'A vessel filled where this stands fills with filtered water, not raw — the water is drawn off beneath a filter bed.';
COMMENT ON COLUMN construction_kind.shields_spring IS
  'Walls and covers a spring head so a fouled camp does not foul it. A spring only — a stream carries what is thrown in.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('SETTLING_BASIN',         'Settling basin',         'construction', FALSE, FALSE, TRUE, 'V329'),
  ('SAND_FILTER_BED',        'Sand filter bed',        'construction', FALSE, FALSE, TRUE, 'V329'),
  ('SPRING_HEAD_PROTECTION', 'Spring-head protection', 'construction', FALSE, FALSE, TRUE, 'V329')
ON CONFLICT (project_kind) DO NOTHING;

-- Clay, gravel, sand and stone. None of them carries flame.
UPDATE construction_kind SET clarifies_draw = 2, flammable = FALSE WHERE project_kind = 'SETTLING_BASIN';
-- 6 - 4 = 2: the same risk as drinking water poured through a bark-and-charcoal cone, because that is what it is.
UPDATE construction_kind SET clarifies_draw = 4, draws_filtered = TRUE, flammable = FALSE WHERE project_kind = 'SAND_FILTER_BED';
UPDATE construction_kind SET shields_spring = TRUE, flammable = FALSE WHERE project_kind = 'SPRING_HEAD_PROTECTION';

-- Keywords avoid "water" (FILTER_WATER and COLLECT_WATER read it), "camp" (MAINTAIN_CAMP), "a bed"/"the bed"
-- (MAKE_BED) and "pen". IntentClassificationRegressionTest asks the classifier about each build phrase.
INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('settling_basin','STRUCTURE','Settling basin',FALSE,NULL,'SETTLING_BASIN','construction',
   'build a settling basin,dig a settling basin,settling basin,work on the settling basin',
   'settling basin',
   'A clay-lined hollow beside the water, gravelled at the bottom, where what is drawn stands and drops its silt.',
   'VERIFIED', now()),
  ('sand_filter_bed','STRUCTURE','Sand filter bed',FALSE,NULL,'SAND_FILTER_BED','construction',
   'build a sand filter bed,dig a sand filter bed,lay a sand filter bed,sand filter bed,slow sand filter,work on the sand filter bed',
   'sand filter bed,sand filter',
   'A clayed pit laid with gravel, then charcoal, then sand, with the water let in at the top and drawn off below.',
   'VERIFIED', now()),
  ('spring_head_protection','STRUCTURE','Spring-head protection',FALSE,NULL,'SPRING_HEAD_PROTECTION','construction',
   'build a spring box,wall the spring head,protect the spring head,cover the spring head,spring head protection,spring box,work on the spring box',
   'spring box,spring head',
   'A ring of stone set close about the spring, clay-sealed and roofed, so nothing reaches the water but what rises from below.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('settling_basin_dig','settling_basin',1,'Dig and clay the basin',NULL,0,NULL,FALSE,
   'You dig a hollow beside the water and puddle clay into its sides until it holds.'),
  ('settling_basin_gravel','settling_basin',2,'Gravel the bottom','settling_basin_dig',0,NULL,FALSE,
   'You lay gravel across the bottom, so the silt settles into it and not into what you draw.'),
  ('sand_filter_bed_dig','sand_filter_bed',1,'Dig and clay the bed',NULL,0,NULL,FALSE,
   'You dig the pit deep and seal it with clay, leaving a low channel at one side to draw from.'),
  ('sand_filter_bed_gravel','sand_filter_bed',2,'Lay the gravel','sand_filter_bed_dig',0,NULL,FALSE,
   'You lay a hand''s depth of gravel over the channel so the water can run out beneath and the sand cannot.'),
  ('sand_filter_bed_sand','sand_filter_bed',3,'Lay the charcoal and sand','sand_filter_bed_gravel',0,NULL,FALSE,
   'You spread crushed charcoal over the gravel and bury it under sand, and the first water through runs clear.'),
  ('spring_head_wall','spring_head_protection',1,'Wall the spring head',NULL,0,'STRIKING',FALSE,
   'You set stones in a close ring about the spring and seal the joints with clay, so no surface water runs in.'),
  ('spring_head_cover','spring_head_protection',2,'Cover the spring head','spring_head_wall',0,'CUTTING',FALSE,
   'You lay a split timber over the ring and clay it down, leaving a spout low on one side to draw from.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('settling_basin_dig','clay_lump',4),
  ('settling_basin_gravel','river_gravel',3),
  ('sand_filter_bed_dig','clay_lump',4),
  ('sand_filter_bed_gravel','river_gravel',4),
  ('sand_filter_bed_sand','river_sand',6), ('sand_filter_bed_sand','charcoal',2),
  ('spring_head_wall','granite_cobble',6), ('spring_head_wall','clay_lump',2),
  ('spring_head_cover','timber_log',1), ('spring_head_cover','clay_lump',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every stage asks for something that exists and that a Chronicle can come by (the Auditor's own rule).
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key IN ('settling_basin','sand_filter_bed','spring_head_protection')
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V329: these stages ask for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key IN ('settling_basin','sand_filter_bed','spring_head_protection')
     AND (trim(k) ~ '(^|\s)(water|camp|pens?)($|\s)' OR trim(k) LIKE '%a bed%' OR trim(k) LIKE '%the bed%');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V329: a Java intent would take these keywords first: %', bad; END IF;

  SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO bad FROM construction_kind ck
   WHERE ck.project_kind IN ('SETTLING_BASIN','SAND_FILTER_BED','SPRING_HEAD_PROTECTION')
     AND NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                      WHERE ad.construction_kind=ck.project_kind AND ad.review_state='VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V329: nobody can build these: %', bad; END IF;

  -- The basin eases, the bed filters; if the basin cleared as much as the bed the difference would say nothing.
  IF (SELECT clarifies_draw FROM construction_kind WHERE project_kind='SETTLING_BASIN')
     >= (SELECT clarifies_draw FROM construction_kind WHERE project_kind='SAND_FILTER_BED') THEN
    RAISE EXCEPTION 'V329: a settling basin must ease less than a sand filter bed';
  END IF;
  SELECT count(*) INTO n FROM construction_kind WHERE draws_filtered AND clarifies_draw = 0;
  IF n > 0 THEN RAISE EXCEPTION 'V329: a structure that fills vessels with filtered water must also clear a drink'; END IF;

  -- A spring box shields; it does not filter. It is the other answer, not a stronger one.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE shields_spring AND (clarifies_draw > 0 OR draws_filtered)) THEN
    RAISE EXCEPTION 'V329: a walled spring keeps a spring clean — it does not filter what is drawn elsewhere';
  END IF;
END $$;
