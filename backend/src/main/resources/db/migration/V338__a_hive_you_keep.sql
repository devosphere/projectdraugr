-- #77 — a hive you keep.
--
-- THE GAP. Pollination is wired and real (#162/#74): a field on ground the honeybee works gives more, and a colony
-- robbed flat stops giving. But the bees are wherever the world put them — `insect_colony_kind.biome_affinity`
-- decides it — and honeybees work forest, highland and open grass. They do NOT work the river bank, the marsh
-- margin or the shore.
--
-- Which is exactly where the best fields are. A floodplain wins its fertility back at 5 a day against a meadow's 2
-- (V332), so the ground a Chronicle most wants to farm is the ground with no pollinator on it, and there was
-- nothing whatever they could do about that. Beekeeping is the oldest answer to it: you do not go to the bees, you
-- bring them.
--
-- WHAT IT ADDS.
--   * `insect_colony_kind.can_be_kept` — which small life a person can actually keep. TRUE for the honeybee, which
--     humans have kept in coiled straw since the Neolithic; FALSE for the earthworm, which is not kept but simply
--     lives in the ground you are already standing on.
--   * `construction_kind.keeps_bees`, and a buildable **bee skep**: straw twisted into rope, then coiled and
--     stitched into a dome. Two stages of grass and cordage — the whole point of a skep is that it is made of what
--     grows in the field it stands beside.
--   * Where a sound skep stands, a keepable colony's pollination counts on ground its own affinity never reached.
--
-- WHAT IT DOES NOT DO.
--   * **It does not beat the season.** Bees do not fly in winter, and a skep does not change that: the season rule
--     is physical and is left exactly as it was. A skep on a winter field does nothing at all.
--   * **It does not stack.** Pollination is the best single bonus on the ground, not a sum; a skep beside wild bees
--     that already work this country adds nothing, because the field is already worked.
--   * **It does not make honey by itself.** Robbing a hive is the existing colony mechanic and is untouched here —
--     this is about what the bees do for the field, which is the larger part of what keeping them was ever for.
--
-- KEYWORDS avoid RAID_HIVE, which takes a taking verb (raid/harvest/smoke/rob/take/collect/gather) beside
-- hive/nest/honey/beeswax: none of the skep's phrases carries one. "weave" is avoided too, so CRAFT_BASKET cannot
-- reach for it.

ALTER TABLE insect_colony_kind ADD COLUMN IF NOT EXISTS can_be_kept BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE construction_kind  ADD COLUMN IF NOT EXISTS keeps_bees  BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN insect_colony_kind.can_be_kept IS
  'Small life a person can actually keep, so a hive a Chronicle raises counts where the wild ones do not reach. '
  'The season still rules: keeping bees does not make them fly in winter.';
COMMENT ON COLUMN construction_kind.keeps_bees IS
  'A raised home for a keepable colony. Where a sound one stands, that colony pollinates ground its own biome '
  'affinity never covered.';

UPDATE insect_colony_kind SET can_be_kept = TRUE WHERE colony_kind = 'honeybee_hive';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('BEE_SKEP', 'Bee skep', 'construction', FALSE, FALSE, TRUE, 'V338')
ON CONFLICT (project_kind) DO NOTHING;

-- Coiled straw over a stand: it rots and must be re-made, and it plainly burns.
UPDATE construction_kind SET keeps_bees = TRUE, flammable = TRUE WHERE project_kind = 'BEE_SKEP';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('bee_skep','STRUCTURE','Bee skep',FALSE,NULL,'BEE_SKEP','construction',
   'build a bee skep,make a bee skep,raise a bee skep,set up a bee skep,build a skep,make a skep,raise a skep,'
   'bee skep,skep,straw hive,work on the skep',
   'bee skep,skep,straw hive',
   'A coiled straw dome on a low stand, its mouth turned from the wind, and a swarm settled into the dark of it.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('bee_skep_rope','bee_skep',1,'Twist the straw into rope',NULL,0,NULL,FALSE,
   'You damp the straw and twist it into a long even rope, thick as your wrist, coiling it as it comes.'),
  ('bee_skep_coil','bee_skep',2,'Coil and stitch the dome','bee_skep_rope',0,'CUTTING',FALSE,
   'You coil the rope round on itself and stitch each round to the last until a dome stands, with a mouth at the '
   'bottom turned away from the wind. What comes to it comes on its own.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('bee_skep_rope','straw_bundle',4), ('bee_skep_rope','fiber_cordage',1),
  ('bee_skep_coil','straw_bundle',4), ('bee_skep_coil','fiber_cordage',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'bee_skep'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V338: the skep asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'bee_skep'
     AND trim(k) ~ '(raid|rob|harvest|weave|honey|beeswax|(^|\s)(take|collect|gather|smoke)(\s|$))';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V338: a Java intent would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='BEE_SKEP' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V338: nobody can raise a skep';
  END IF;

  -- A skep with nothing keepable to put in it would be scenery, and a keepable colony that pollinates nothing
  -- would leave the skep with no effect. Both halves must be present for this to be worth anything.
  SELECT count(*) INTO n FROM insect_colony_kind WHERE can_be_kept AND pollination_bonus > 0;
  IF n < 1 THEN RAISE EXCEPTION 'V338: nothing keepable pollinates anything'; END IF;
  IF EXISTS (SELECT 1 FROM insect_colony_kind WHERE can_be_kept AND season_active = 'ALL') THEN
    RAISE EXCEPTION 'V338: bees do not fly all year, and a kept hive must not pretend otherwise';
  END IF;

  -- The ground this is FOR: country a keepable pollinator does not reach on its own. If the honeybee ever gains
  -- affinity for every biome the world makes, the skep stops being worth building and this must say so.
  SELECT count(*) INTO n FROM (SELECT DISTINCT biome FROM terrain_going) b
   WHERE NOT EXISTS (SELECT 1 FROM insect_colony_kind ck
                      WHERE ck.can_be_kept AND ck.biome_affinity ILIKE '%' || b.biome || '%');
  IF n < 1 THEN RAISE EXCEPTION 'V338: the wild bees already work every kind of ground; a kept hive adds nothing'; END IF;
END $$;
