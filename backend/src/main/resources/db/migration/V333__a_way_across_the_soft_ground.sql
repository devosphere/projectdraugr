-- #77 — a way across the soft ground (the fen causeway).
--
-- THE DEFECT. `waterCrossing` (#156/#157) already refuses a laden Chronicle the marsh: soft ground will take a
-- walker and will not take a walker with a heavy pack, and you sink to the thigh with nothing to push off. Its own
-- comment names the answer — "a shallow ford is the place where neither rule applies, which is what a ford IS, and
-- why fen causeways were built at all" — and then the only thing that can lift the refusal is an `ecology_site`
-- with `ford` in its name: a crossing the WORLD happened to place. Nothing a Chronicle builds can make soft ground
-- carry them. A camp on the wrong side of a fen must either find a ford or carry nothing across, for ever.
--
-- WHAT IT ADDS. `construction_kind.crosses_soft_ground`, and the oldest engineered road there is: a laid timber
-- way over a bog. Pegs driven in crossed pairs, a rail dropped into the crotch, planks pinned along the rail — the
-- Sweet Track, built in a single season and still there six thousand years later. Where a sound causeway stands,
-- the marsh takes a laden walker exactly as a ford does.
--
-- WHAT IT DOES NOT DO.
--   * It is not a bridge over open water. The sea still refuses a loaded body and always will: a plank road laid on
--     peat is not a span over a channel, and a causeway that crossed the ocean would be a boat wearing the wrong
--     name. OCEAN is untouched here, deliberately.
--   * It does not make the marsh quick. Soft ground is slow ground whether or not a way is laid across it; travel
--     time is per-chunk and terrain-blind today, and that is its own work, not this.
--   * A rotted way carries nothing. The timber lies in standing water, so it decays like every other structure, and
--     at no integrity the refusal comes back — which is exactly what happens to a fen track nobody repairs.
--
-- WHY IT DOES NOT BURN. Every other laid timber structure in the catalogue is flammable; this one is not. It lies
-- in a bog with water standing over and through it. A fen track rots away; it does not catch.
--
-- KEYWORDS avoid every Java intent that would take the phrase before the assembly matcher sees it: no "track" or
-- "trail" (TRACK/MARK), no "walk to" (TRAVEL), no "bridge" with a repair verb (REPAIR_STRUCTURE), no "safe way" or
-- "way out" (SCOUT), no "platform" (BUILD_STORAGE_AREA).

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS crosses_soft_ground BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.crosses_soft_ground IS
  'A laid way that makes soft ground carry a laden walker — the built counterpart of a natural ford. Read by '
  'waterCrossing for WETLAND only; open water still refuses a load, whatever is laid on the bank.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('FEN_CAUSEWAY', 'Fen causeway', 'construction', FALSE, FALSE, TRUE, 'V333')
ON CONFLICT (project_kind) DO NOTHING;

UPDATE construction_kind SET crosses_soft_ground = TRUE, flammable = FALSE WHERE project_kind = 'FEN_CAUSEWAY';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('fen_causeway','STRUCTURE','Fen causeway',FALSE,NULL,'FEN_CAUSEWAY','construction',
   'build a causeway,lay a causeway,raise a causeway,fen causeway,marsh causeway,bog causeway,causeway,'
   'plank way,plank road,log road,corduroy road,corduroy way,boardwalk,work on the causeway',
   'causeway,plank way,corduroy road,boardwalk',
   'A line of pegged timber laid over the bog, standing a hand above the water, holding firm under a full load.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('fen_causeway_brush','fen_causeway',1,'Bed the line with brush',NULL,0,NULL,FALSE,
   'You cut brush and tread it into the peat along the line you mean to cross, so there is something under the '
   'timber besides water.'),
  ('fen_causeway_pegs','fen_causeway',2,'Drive the pegs','fen_causeway_brush',0,'CUTTING',FALSE,
   'You drive the pegs in crossed pairs down the whole line, each pair leaning into the other, until they hold '
   'against your weight.'),
  ('fen_causeway_planks','fen_causeway',3,'Lay and pin the walking timber','fen_causeway_pegs',0,'CUTTING',FALSE,
   'You drop the rail into the crotch of each pair and pin the walking timber along it. It lies a hand above the '
   'water and does not shift when you cross it loaded.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('fen_causeway_brush','dry_branch',6),
  ('fen_causeway_pegs','hazel_rod',6),
  ('fen_causeway_planks','timber_log',4), ('fen_causeway_planks','fiber_cordage',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'fen_causeway'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V333: the causeway asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'fen_causeway'
     AND trim(k) ~ '(track|trail|walk to|bridge|platform|safe way|way out|(^|\s)fence($|\s)|(^|\s)pens?($|\s))';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V333: a Java intent would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='FEN_CAUSEWAY' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V333: nobody can build a causeway';
  END IF;

  -- One built crossing, and it is neither a shelter nor a workstation: it is a way over ground, and nothing else.
  SELECT count(*) INTO n FROM construction_kind WHERE crosses_soft_ground;
  IF n <> 1 THEN RAISE EXCEPTION 'V333: expected one built crossing, found %', n; END IF;
  IF EXISTS (SELECT 1 FROM construction_kind WHERE crosses_soft_ground AND (is_shelter OR is_workstation OR flammable)) THEN
    RAISE EXCEPTION 'V333: a causeway shelters nobody, is no workbench, and lies in a bog';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE crosses_soft_ground AND decays) THEN
    RAISE EXCEPTION 'V333: timber laid in standing water rots; the causeway must decay';
  END IF;
END $$;
