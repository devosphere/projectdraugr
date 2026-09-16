-- #77 — a laid way is quicker to walk (the paths tier).
--
-- V335 gave the ground a going: a chunk of open grass costs 15 minutes to cross, forest 22, a fen 36, mountain 40,
-- averaged along the line actually walked. That was half of what #77's paths tier needs. This is the other half: a
-- way somebody LAID takes time off the ground it lies on, so the labour of building a road is repaid every time
-- anybody walks it — which is the only reason roads have ever been built.
--
-- WHAT IT ADDS.
--   * `construction_kind.eases_going` — minutes taken off the going of the chunk a sound way stands on.
--   * A **laid path**: the line cleared and graded, then bedded with gravel and set with stone. Two stages, both
--     of ordinary materials, because this is the first-era road and not a Roman one.
--
-- THE FLOOR, and why there is one. No laid way may bring any ground below the going of open grass (15). A path
-- through a wood makes the wood walkable; it does not make it a meadow, and a hand-set stone path across a bog
-- would still be a bog underneath. The floor is read from terrain_going rather than written as a number here, so
-- if the going of grassland is ever retuned the floor follows it instead of silently becoming wrong.
--
-- WHAT IT DOES NOT DO. Nothing else reads `eases_going` yet — not the fen causeway, whose own effect is carrying a
-- laden walker across soft ground, and which earns its easing in its own migration once both are in the same tree.
-- A path does not shelter, does not burn, and wears like everything else: a way nobody repairs stops being a way.
--
-- KEYWORDS. "track" and "trackway" are unusable — TRACK takes any phrase containing "track" and would send a
-- builder off reading the ground for spoor — and "trail" goes the same way to MARK. "walk to" is TRAVEL's, and
-- "platform" is BUILD_STORAGE_AREA's. What is left is path, paving and causeway-free road vocabulary.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS eases_going SMALLINT NOT NULL DEFAULT 0
    CONSTRAINT construction_kind_eases_going_sane CHECK (eases_going BETWEEN 0 AND 30);

COMMENT ON COLUMN construction_kind.eases_going IS
  'Minutes taken off the going of the ground this sound structure stands on (V335 terrain_going). Never below '
  'the going of open grass: a laid way makes hard country walkable, never better than a meadow.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('LAID_PATH', 'Laid path', 'construction', FALSE, FALSE, TRUE, 'V336')
ON CONFLICT (project_kind) DO NOTHING;

UPDATE construction_kind SET eases_going = 5, flammable = FALSE WHERE project_kind = 'LAID_PATH';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('laid_path','STRUCTURE','Laid path',FALSE,NULL,'LAID_PATH','construction',
   'lay a path,build a path,make a path,stone path,gravel path,paved way,paved path,flagged path,'
   'lay a road,build a road,stone road,work on the path',
   'path,stone path,gravel path,paved way',
   'A cleared, graded line bedded with gravel and set with flat stone, dry underfoot and firm where the ground was not.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('laid_path_grade','laid_path',1,'Clear and grade the line',NULL,0,NULL,FALSE,
   'You clear the line of brush and stone and work it level, so that water runs off it instead of standing on it.'),
  ('laid_path_set','laid_path',2,'Bed and set the stone','laid_path_grade',0,NULL,FALSE,
   'You bed the line with gravel and set flat stone into it, tamping each one down until it does not rock underfoot.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('laid_path_grade','dry_branch',3),
  ('laid_path_set','river_gravel',4), ('laid_path_set','field_stone',6)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'laid_path'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V336: the path asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'laid_path'
     AND trim(k) ~ '(track|trail|walk to|bridge|platform|safe way|way out|(^|\s)fence($|\s)|(^|\s)pens?($|\s))';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V336: a Java intent would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='LAID_PATH' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V336: nobody can lay a path';
  END IF;

  -- An easing nothing carries is a column nobody reads, and an easing that outruns the ground it stands on would
  -- make a wood quicker to walk than open grass.
  SELECT count(*) INTO n FROM construction_kind WHERE eases_going > 0;
  IF n < 1 THEN RAISE EXCEPTION 'V336: no structure eases the going'; END IF;

  SELECT string_agg(ck.project_kind, ', ') INTO bad
    FROM construction_kind ck
   WHERE ck.eases_going >= (SELECT MIN(minutes_per_chunk) FROM terrain_going);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V336: a way that takes off more than the easiest ground costs is not a way: %', bad;
  END IF;
END $$;
