-- #106 — something to hold a beast with.
--
-- V302 gave species a temperament and made a DANGEROUS animal cost the keeper who handles it unrestrained:
-- getting in close to milk or shear an aurochs, an ox, a water buffalo or a wild boar wrenches something, unless
-- a milking stanchion stands here to hold it still. That is the whole of the answer today, and it has one shape:
-- a fixed structure at a fixed place.
--
-- Which leaves the keeper standing in a field with a sick bull and no stanchion for a mile. #106 has been asking
-- for `muzzle_basket`, `leg_hobble` and `calming_blindfold` since it was written, and until temperament existed
-- they were three names for "a thing you put on an animal" with nothing to be for. Temperament exists now.
--
-- ONE ITEM, not three. A muzzle stops an animal biting and a blindfold stops it bolting, but the four dangerous
-- species in this world are an aurochs, an ox, a water buffalo and a boar — they hurt a keeper with their weight
-- and their horns, and none of them hurt anybody by biting. Three items that all reduce the same number is the
-- duplication this project keeps declining; a hobble round the legs of something that weighs six times what you
-- do is the restraint that actually answers the injury being modelled. If a biting animal is ever added, a muzzle
-- becomes a real distinction and can be added then.
--
-- AND IT IS WORSE THAN A STANCHION, deliberately. A stanchion holds the animal and the work is safe. A hobble
-- only slows it — it makes the work possible, not safe — so carried restraint REDUCES the injury and never
-- removes it. Anything else would make the stanchion a building nobody would raise.
--
-- The gradation is what makes this a table rather than a flag. A harness is not a restraint, but a keeper with
-- one in hand has the beast's head, which is better than having nothing, so the two harnesses that already exist
-- gain a second use here rather than a fourth item being invented to do what they plainly do.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable) VALUES
  ('leg_hobble', 'Leg hobble', 'TOOL', 220, 400, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
  ('leg_hobble', 'TECHNIQUE', 'leather cord and fibre cordage knotted into a short tie for a beast''s forelegs')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process
  (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
   duration_minutes, domain_key, keywords, narration, review_state, reviewed_at, category_key, station_kind)
VALUES
  ('make_leg_hobble', 'Make a leg hobble', 'leg_hobble', 1, 1, NULL, FALSE, FALSE,
   35, 'taming',
   'make a leg hobble,make a hobble,knot a leg hobble,braid a leg hobble,make hobbles,leg hobble',
   'You knot two lengths of leather cord onto a short span of cordage, sized so a beast can stand and shuffle and cannot kick or bolt. It is not much to look at and it is not meant to be.',
   'VERIFIED', now(), 'CRAFT', NULL)
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
  ('make_leg_hobble', 'leather_cord', 2),
  ('make_leg_hobble', 'fiber_cordage', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
  ('make_leg_hobble', 'hobble'),
  ('make_leg_hobble', 'hobbles')
ON CONFLICT (process_key, subject_term) DO NOTHING;

CREATE TABLE animal_restraint (
    item_key           VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    eases_handling_by  SMALLINT NOT NULL,
    strains            BOOLEAN  NOT NULL DEFAULT FALSE,
    notes              TEXT     NOT NULL,
    -- Never as good as a stanchion. HANDLING_INJURY is 12 in WildlifeEncounterService and
    -- RestraintForADangerousBeastIntegrationTest asserts this ceiling against the constant itself, so the two
    -- cannot drift apart silently; this keeps a careless edit from doing it in one step.
    CONSTRAINT animal_restraint_never_replaces_a_stanchion CHECK (eases_handling_by BETWEEN 1 AND 11)
);

COMMENT ON TABLE animal_restraint IS
  'Carried gear that makes handling a DANGEROUS animal less costly. Read by WildlifeEncounterService when taking '
  'a tamed yield and by PhysicalItemService when tending a sick beast. The best single restraint applies — they '
  'do not stack, or enough rope would be as good as a stanchion.';
COMMENT ON COLUMN animal_restraint.strains IS
  'TRUE when the animal''s strength goes into the gear rather than into the keeper: a hobble tied round the legs '
  'of something struggling wears SOUND to WORN to BROKEN. A harness merely held does not, because what it does '
  'not absorb is the injury the keeper still takes.';

INSERT INTO animal_restraint (item_key, eases_handling_by, strains, notes) VALUES
  ('leg_hobble', 8, TRUE,
   'Made for this and nothing else. A beast that cannot kick or bolt can still throw its weight, so the work is possible rather than safe — and the hobble takes that weight, which is why it wears out.'),
  ('draft_harness', 4, FALSE,
   'Not a restraint, but a keeper holding a harnessed head has the animal where a bare hand does not. It was built to pull against, so the struggle does not hurt it.'),
  ('rope_harness', 3, FALSE,
   'The same leverage with less of it — rope gives where a fitted harness holds.');

DO $$
DECLARE bad text; n int;
BEGIN
  -- Every restraint must be a real item, and a gettable one. A restraint nobody can obtain is a mechanic that
  -- never fires, which is worse than not having it — the injury stays and the answer is invisible.
  SELECT string_agg(r.item_key, ', ') INTO bad FROM animal_restraint r
   WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = r.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V308: these restraints cannot be obtained by anybody: %', bad; END IF;

  -- The hobble's own inputs must exist, or the one purpose-built restraint is unmakeable.
  SELECT string_agg(i.item_key, ', ') INTO bad FROM material_process_input i
   WHERE i.process_key = 'make_leg_hobble' AND i.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V308: the hobble asks for items that do not exist: %', bad; END IF;

  -- Both routing gates, together: a keyword that classifies to the process's own category, and a subject term
  -- that appears in its own keywords. A recipe failing either is unreachable however good the other looks.
  IF NOT EXISTS (
      SELECT 1 FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k
       JOIN category_term ct ON ct.category_key = 'CRAFT'
       WHERE mp.process_key = 'make_leg_hobble' AND ' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %') THEN
    RAISE EXCEPTION 'V308: no keyword of make_leg_hobble classifies as CRAFT, so the category gate rejects them all';
  END IF;
  SELECT string_agg(btrim(k), ', ') INTO bad
    FROM material_process mp, LATERAL unnest(string_to_array(mp.keywords, ',')) k
   WHERE mp.process_key = 'make_leg_hobble' AND btrim(k) <> ''
     AND NOT EXISTS (SELECT 1 FROM process_subject s WHERE s.process_key = mp.process_key
                       AND position(' ' || s.subject_term || ' ' in ' ' || btrim(k) || ' ') > 0);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V308: these keywords name no subject and can never fire: %', bad; END IF;

  -- Conservation, over the whole catalogue. A hobble is knotted from cord that was carried here, so it balances
  -- on its own figures and is not exempt from anything.
  SELECT string_agg(b.process_key || ' (' || b.max_output_grams || 'g out of ' || b.min_input_grams || 'g in)', ', ')
    INTO bad FROM process_mass_balance b JOIN material_process mp ON mp.process_key = b.process_key
   WHERE mp.review_state = 'VERIFIED' AND NOT mp.conservation_exempt
     AND b.min_input_grams > 0 AND b.max_output_grams > b.min_input_grams * 1.05;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V308: these processes would create matter from nothing: %', bad; END IF;

  -- The purpose-built restraint must beat the two that are only being repurposed, or there is no reason to make
  -- one and the item is a token with a recipe attached.
  SELECT eases_handling_by INTO n FROM animal_restraint WHERE item_key = 'leg_hobble';
  IF n <= (SELECT COALESCE(MAX(eases_handling_by), 0) FROM animal_restraint WHERE item_key <> 'leg_hobble') THEN
    RAISE EXCEPTION 'V308: a hobble made for the job must be better than a harness pressed into it';
  END IF;

  -- And there must be a dangerous animal for any of this to be about.
  SELECT count(*) INTO n FROM wildlife_species WHERE temperament = 'DANGEROUS';
  IF n = 0 THEN RAISE EXCEPTION 'V308: nothing in this world is dangerous to handle, so restraint answers nothing'; END IF;
END $$;
