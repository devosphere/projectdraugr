-- #108 — an offal pit (carcass_disposal_site).
--
-- Butchering a carcass at camp adds 15 refuse to the ground (#218) — the heaviest single source there is — and
-- refuse is wired to real consequences: it fouls the water (V295), breeds illness, and lets pests at the stores.
-- #108 names `carcass_disposal_site`, and its last comment said it waited on site-level hygiene. Refuse IS that
-- hygiene, and it has been on the ground all along; what was missing was anywhere for the offal to go.
--
-- WHAT IT DOES: butchering on ground where an offal pit stands leaves 3 refuse instead of 15. The guts, the lights
-- and the scraps go straight into the pit and are covered, rather than onto the ground the camp lives on.
--
-- WHY IT IS NOT A LATRINE. A latrine drains refuse that is already there, over hours (WildlifeSimulationService).
-- A pit for offal stops the worst of it arriving. A camp that butchers often wants both, and a camp that does not
-- hunt wants only the latrine — which is the difference that keeps this from being a second name for one hole.
--
-- KEYWORDS avoid HARVEST_CARCASS's verbs (harvest/butcher/skin) and BUILD_LATRINE's nouns (refuse pit, waste pit,
-- rubbish pit, midden, latrine), or a Java intent would take the phrase before the assembly matcher sees it.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS takes_offal BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.takes_offal IS
  'A covered pit the waste of butchery goes straight into, so it does not foul the camp ground. Stops refuse '
  'arriving; a latrine drains what is already there.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('CARCASS_PIT', 'Offal pit', 'construction', FALSE, FALSE, TRUE, 'V331')
ON CONFLICT (project_kind) DO NOTHING;

-- A hole in the ground with a cover of branches and stones. It fills and caves in; it does not burn.
UPDATE construction_kind SET takes_offal = TRUE, flammable = FALSE WHERE project_kind = 'CARCASS_PIT';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('offal_pit','STRUCTURE','Offal pit',FALSE,NULL,'CARCASS_PIT','construction',
   'dig an offal pit,dig a carcass pit,carcass disposal site,carcass disposal pit,offal pit,carcass pit,work on the offal pit',
   'offal pit,carcass pit',
   'A deep pit well away from the hearth, with a lid of branches weighted with stones against scavengers.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('offal_pit_dig','offal_pit',1,'Dig the pit',NULL,0,NULL,FALSE,
   'You dig it deep and well away from the hearth and the water, so that what goes in is out of the camp for good.'),
  ('offal_pit_cover','offal_pit',2,'Cover the pit','offal_pit_dig',0,NULL,FALSE,
   'You lay branches across it and weight them with stones, so no fox or dog can drag back out what has gone in.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('offal_pit_dig','dry_branch',2),
  ('offal_pit_cover','dry_branch',4), ('offal_pit_cover','field_stone',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'offal_pit'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V331: the offal pit asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'offal_pit'
     AND trim(k) ~ '(harvest|butcher|skin|latrine|privy|cesspit|cess pit|refuse pit|waste pit|rubbish pit|toilet pit|midden|(^|\s)pens?($|\s))';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V331: a Java intent would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='CARCASS_PIT' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V331: nobody can dig an offal pit';
  END IF;

  -- One structure takes offal, and it is not the latrine: the two answer different halves of camp hygiene.
  SELECT count(*) INTO n FROM construction_kind WHERE takes_offal;
  IF n <> 1 THEN RAISE EXCEPTION 'V331: expected one offal pit, found %', n; END IF;
  IF EXISTS (SELECT 1 FROM construction_kind WHERE takes_offal AND project_kind = 'LATRINE') THEN
    RAISE EXCEPTION 'V331: a latrine drains refuse; it is not where butchery goes';
  END IF;
END $$;
