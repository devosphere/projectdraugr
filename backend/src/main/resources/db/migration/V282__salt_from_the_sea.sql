-- #157 — salt from the sea.
--
-- Nothing in the world produced salt. `rock_salt` was only ever GATHERED as a mineral — from mountain, highland,
-- a salt-marsh site, and (since V279) a cave mouth — and the only salt-adjacent processes were grind_salt, which
-- mills salt you already have, and brine_fish, which spends it. Evaporating seawater, historically the dominant
-- source of salt everywhere people lived near a coast, and the obvious thing to do standing on a shore, did not
-- exist. The shore had ecology and no industry.
--
-- This is why it could not be added as data alone. `requires_water` is gated by PhysicalItemService.waterToWorkWith,
-- which accepts wetland, river bank and freshwater spring/stream sites and deliberately EXCLUDES the coast —
-- correctly, because retting, tanning and liming want fresh water and brine would spoil them. So a salt pan
-- declared with requires_water would have been doable at a freshwater spring, which is precisely wrong. The
-- question "is there salt water here" is a different question and gets its own flag and its own gate.

ALTER TABLE material_process ADD COLUMN IF NOT EXISTS requires_salt_water BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN material_process.requires_salt_water IS
  'Does this work turn on SALT water — the shore, ground touching open sea, or a salt marsh? Distinct from '
  'requires_water, whose gate accepts fresh water only and excludes the coast on purpose.';

-- Boiling brine down: fire, a vessel's worth of seawater, and patience. Conservation-exempt on the gather_ash
-- precedent — the salt is taken from the sea, not conjured, which is the same reason ash is exempt.
INSERT INTO material_process
  (process_key, display_name, output_item_key, output_min, output_max, tool_class,
   requires_fire, requires_water, requires_salt_water, duration_minutes, domain_key, keywords, narration,
   review_state, reviewed_at, conservation_exempt, exempt_reason, category_key)
VALUES
  ('boil_seawater_for_salt', 'Boil seawater down for salt', 'rock_salt', 1, 3, NULL,
   TRUE, FALSE, TRUE, 90, 'items',
   'boil seawater for salt,boil sea water for salt,boil down seawater,evaporate seawater,evaporate sea water,'
   || 'boil brine down for salt,make a salt pan,work a salt pan,pan salt from the sea,render sea salt',
   'You carry seawater up and set it over the fire, and boil it down through the afternoon. The pot goes from '
   || 'water to a thick grey brine to a crust that cracks and lifts from the bottom — coarse salt, still damp, '
   || 'tasting of the sea it came out of.',
   'VERIFIED', now(), TRUE,
   'The salt is taken from seawater, which the world holds in quantity — the same take-from-the-world case as gather_ash.',
   'PROCESS');

-- Subjects: the nouns a Chronicle would actually name doing this.
INSERT INTO process_subject (process_key, subject_term) VALUES
  ('boil_seawater_for_salt', 'seawater'),
  ('boil_seawater_for_salt', 'sea water'),
  ('boil_seawater_for_salt', 'salt'),
  ('boil_seawater_for_salt', 'brine'),
  ('boil_seawater_for_salt', 'sea');

-- No mass-balance row is written: process_mass_balance is a VIEW computed from the process and its inputs, so
-- this recipe appears in it automatically as output with no input — matter from nothing. That is exactly what
-- conservation_exempt above is for, and it is the same shape as gather_ash: the mass comes from the world (here,
-- the sea) rather than from a carried ingredient, so the Auditor's matter gate passes it over by design.

DO $$
DECLARE n int;
BEGIN
  -- The recipe must exist, must be exempt (or the matter gate fails it), and must demand salt water.
  SELECT count(*) INTO n FROM material_process
   WHERE process_key='boil_seawater_for_salt' AND conservation_exempt AND requires_salt_water AND requires_fire;
  IF n <> 1 THEN RAISE EXCEPTION 'V282: the salt pan must exist, be conservation-exempt, and turn on salt water and fire'; END IF;

  -- It must not have quietly become the answer to a bare "salt" — salt_fish owns that word.
  IF EXISTS (SELECT 1 FROM material_process, unnest(string_to_array(keywords, ',')) k
             WHERE process_key='boil_seawater_for_salt' AND trim(k) IN ('salt','brine','boil','cure')) THEN
    RAISE EXCEPTION 'V282: the salt pan must not claim a bare keyword another recipe already owns';
  END IF;

  -- And salt must still be gatherable as rock, so the sea is an addition rather than a replacement.
  IF NOT EXISTS (SELECT 1 FROM mineral_definition WHERE mineral_key='rock_salt') THEN
    RAISE EXCEPTION 'V282: rock salt must remain a mineral you can find in the ground';
  END IF;
END $$;
