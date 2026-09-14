-- #108 — a salt lick.
--
-- `salt_lick_station` is on #108's welfare list, and the last comment there said what it waited on: a mineral need
-- for stock. Stock have one, and it is one of the oldest facts of husbandry. Grazing animals cannot get enough sodium
-- from grass, and a dam short of salt eats less and gives less milk. It is why herders have always carried salt to
-- their animals, and why wild herds wear paths to natural licks.
--
-- WHAT IT DOES, stated narrowly: a milk herd worked where a salt lick stands gives more. Half as much again, and at
-- least one more measure, because what the lick changes is how much each dam has in her.
--
-- WHAT IT DOES NOT DO, and why:
--   * It is not a gate. Stock kept without salt still milk, as they always have here; salt is the difference between
--     a poor pail and a good one, not between milk and none. Making it a gate would silently halve every herd
--     already in play.
--   * Wool and eggs are untouched. Salt shows first and plainest in milk; a fleece does not grow faster for it, and
--     laying hens want grit and shell, which is a different need.
--   * It does not make matter. The lick is set from rock salt that is consumed when it is built, and the milk comes
--     from the herd the keeper already has, capped by the same count as before.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS gives_salt BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.gives_salt IS
  'Salt stock can lick. A dam with salt gives more milk than one without — the one mineral grazing animals '
  'cannot get from grass.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('SALT_LICK', 'Salt lick', 'construction', FALSE, FALSE, TRUE, 'V330')
ON CONFLICT (project_kind) DO NOTHING;

-- Rock salt on a stone: nothing to burn. It wears away as it is licked, which is what `decays` means here.
UPDATE construction_kind SET gives_salt = TRUE, flammable = FALSE WHERE project_kind = 'SALT_LICK';

-- No gathering verb in any keyword: "gather"/"collect"/"dig for" with "salt" is GATHER_MINERAL's.
INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('salt_lick_station','STRUCTURE','Salt lick',FALSE,NULL,'SALT_LICK','construction',
   'set out a salt lick,build a salt lick,raise a salt lick,salt lick station,salt lick,work on the salt lick',
   'salt lick',
   'A block of rock salt bedded on a flat stone where the stock come to it, and already worn smooth on one side.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('salt_lick_set','salt_lick_station',1,'Set the salt on its stone',NULL,0,NULL,FALSE,
   'You bed the rock salt on flat stones clear of the mud, where the animals pass, and they find it before you have stepped back.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('salt_lick_set','rock_salt',3), ('salt_lick_set','field_stone',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'salt_lick_station'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V330: the lick asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'salt_lick_station'
     AND trim(k) ~ '(search|look for|prospect|dig for|find|gather|collect|split|break|(^|\s)pens?($|\s))';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V330: a Java intent would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='SALT_LICK' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V330: nobody can build a salt lick';
  END IF;

  -- The lick feeds milk, so something kept must give milk or the lick changes nothing.
  SELECT count(*) INTO n FROM tamed_yield WHERE yield_kind='MILK';
  IF n = 0 THEN RAISE EXCEPTION 'V330: no kept animal gives milk, so a salt lick would change nothing'; END IF;

  SELECT count(*) INTO n FROM construction_kind WHERE gives_salt;
  IF n <> 1 THEN RAISE EXCEPTION 'V330: expected one salt lick, found %', n; END IF;
END $$;
