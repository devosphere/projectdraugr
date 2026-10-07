-- V409 — what stands on the next ground (#224)
--
-- No data. The last line of #224's required contract is a visible "ruin, or structure with direction/distance
-- band", and the reading that answers it is derived, not stored: VisualContextService builds it from three tables
-- every time it is asked, so there is nothing here to insert. What this migration does is make the dependency
-- EXPLICIT and hold the three shapes it reads to their meaning, exactly as V403 did for world_chunk.
--
-- The reason that is worth a file of its own: this reading is the one place in the project where the payload is
-- built from the Overseer's own plan and sent to the player. Every other such field has been safe because it only
-- ever carried the ground underfoot. This one reaches one chunk further, and what keeps it safe is a single
-- distinction — A KIND IS NOT AN IDENTITY. The silhouette of a tower crosses; which tower it is does not. If a
-- later cycle widens any of these three queries, or adds a fourth, the guards below are where that decision gets
-- stopped and reconsidered rather than shipped.

DO $$
DECLARE
  ruin_sites int;
  categories text;
BEGIN
  -- 1. A RUIN IS AN ecology_site, NOT A TABLE OF ITS OWN. Nothing named "ruin" exists in this schema, which is
  --    worth recording because it is the first thing anyone looking for one will search for and fail to find.
  --    Genesis places them as MarkerSpec('RUIN', ...) rows, so the category is the only handle there is.
  IF to_regclass('public.ecology_site') IS NULL THEN
    RAISE EXCEPTION 'the skyline reading needs ecology_site: a ruin is a site of category RUIN, not a table';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_name='ecology_site' AND column_name='site_category') THEN
    RAISE EXCEPTION 'ecology_site.site_category is what tells a ruin from a berry stand; the reading cannot work without it';
  END IF;

  -- 2. The categories the reading must NOT report. This is the whole safety argument as an assertion: a ruin, a
  --    finished building and a standing village break a horizon and cannot be missed from the next chunk; an ore
  --    seam, a berry stand and a wolf's range do not, and are learned by walking the ground. If a new category
  --    appears, this guard fires and somebody has to decide which side of that line it falls on.
  SELECT string_agg(DISTINCT site_category, ', ' ORDER BY site_category) INTO categories FROM ecology_site;
  IF categories IS NOT NULL AND EXISTS (
       SELECT 1 FROM (SELECT DISTINCT site_category c FROM ecology_site) s
        WHERE s.c NOT IN ('WILDLIFE','RESOURCE','RUIN','MINERAL','FLORA','WATER','MONSTER')) THEN
    RAISE EXCEPTION 'an unrecognised ecology_site category exists (have: %). The skyline reading reports RUIN and '
      'nothing else, so a new category must be judged visible-or-not deliberately rather than by default', categories;
  END IF;

  -- 3. The other two shapes a skyline can carry. Both are read through world_object.current_location_id and
  --    lifecycle_state, so a building that burns down (#111) stops being scenery the moment it stops standing —
  --    which is the property that makes the fingerprint worth trusting.
  IF to_regclass('public.construction_project') IS NULL THEN
    RAISE EXCEPTION 'the skyline reading needs construction_project for the BUILT kind';
  END IF;
  IF to_regclass('public.native_settlement_site') IS NULL THEN
    RAISE EXCEPTION 'the skyline reading needs native_settlement_site for the SETTLEMENT kind';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_name='world_object' AND column_name='lifecycle_state') THEN
    RAISE EXCEPTION 'world_object.lifecycle_state is how a burnt village stops being scenery; the reading needs it';
  END IF;

  -- 4. Occlusion needs a biome on the ground being looked AT, because there is no seeing into rock.
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_name='world_chunk' AND column_name='biome') THEN
    RAISE EXCEPTION 'world_chunk.biome is how a cave interior is excluded from a skyline; the reading needs it';
  END IF;

  -- 5. Simulation state only once the simulation has something to be in. A clean CI boot runs every migration
  --    before genesis, so a world is NOT here yet and must not be demanded — the bug V400 shipped and had to be
  --    fixed. When there IS a world, a generated one must contain at least one ruin, or the tier it is being
  --    built for would have nothing to show and nobody would notice.
  IF EXISTS (SELECT 1 FROM world_chunk) THEN
    SELECT count(*) INTO ruin_sites FROM ecology_site WHERE site_category='RUIN';
    IF ruin_sites = 0 THEN
      RAISE EXCEPTION 'a generated world holds no RUIN site, so no skyline could ever carry one — genesis places '
        'these as MarkerSpec(''RUIN'', ...) and something has stopped doing so';
    END IF;
  END IF;
END $$;

COMMENT ON COLUMN ecology_site.site_category IS
  'What kind of site this is. WILDLIFE/RESOURCE and the rest are learned by walking the ground; RUIN is the one '
  'category visible from the NEXT chunk, because a broken silhouette cannot be missed in daylight (#224). The '
  'visual-context reading reports that a ruin is there and in which direction, and never which ruin it is: the '
  'shape is the invitation, the identity is the discovery.';
