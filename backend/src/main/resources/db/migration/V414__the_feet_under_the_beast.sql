-- V414 — the feet under the beast (#106)
--
-- THE LAST GENUINE MODEL GAP ON #106, and the ticket has carried it unchanged across four comments: "hoof care —
-- no hoof or lameness state; routing it to grooming would claim the COAT improved, which is a lie."
--
-- And the asking already works. `ChronicleActionService` sends `is he lame`, `is she limping`, `is the goat
-- lame` to CHECK_STOCK — which reports hunger, thirst, fatigue and sickness, every one of them real and
-- maintained, and has nothing whatever to say about a foot. Face 5 of the standing defect, with the question
-- already in the player's mouth and the world unable to answer it.
--
-- WHAT THIS ADDS, AND WHY IT IS TWO COLUMNS AND NOT ONE:
--
--   wildlife_species.feet_need_paring  — WHICH animals have feet that want seeing to
--   wildlife_bond.hoof_condition       — HOW those feet are, on this animal, right now
--
-- The species flag has to be data rather than a hand-kept list in Java, because that is exactly how the
-- husbandry vocabularies drifted before: tending knew nine species, grooming knew none and feeding knew seven
-- and not the goat, so the beast you could tend you could not groom. One declared fact, read by everything.
--
-- WHY `feet_need_paring` AND NOT `walks_on_hooves`. Seventeen of the eighteen are true ungulates and the word
-- would have been right for them. The llama is not — a camelid walks on a two-toed pad with nails, not on a
-- wall of horn — and its nails still overgrow and still want paring. Naming the column after the FACT THE MODEL
-- NEEDS rather than after the anatomy keeps it true of all eighteen. The test is "a foot that carries the
-- animal's weight and grows faster than the ground wears it".
--
-- Not given to anything a keeper does not keep and work: a fox's claws and a crow's feet are real and are not
-- husbandry. Every species here is either a draught animal or a tameable hoofed one.
--
-- 100 is a sound foot and 0 is an animal that cannot put weight on it. Same direction as `coat_condition`, which
-- this is deliberately modelled on — a keeper's state that falls on its own and is put back by an act.

ALTER TABLE wildlife_species
  ADD COLUMN IF NOT EXISTS feet_need_paring BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wildlife_species.feet_need_paring IS
  'This animal''s feet carry its weight on horn or nail that grows faster than the ground wears it, so a kept '
  'one needs its feet pared. True of the ungulates and of the llama (nails on a pad, not hooves, and they '
  'overgrow the same way). Read by the hoof tick, by CHECK_STOCK and by the paring action — never by a list '
  'kept in Java, because that is how the tend/groom/feed vocabularies drifted apart.';

UPDATE wildlife_species SET feet_need_paring = TRUE
 WHERE species_key IN ('antelope','aurochs','bighorn_sheep','chamois','donkey','elk','gazelle','horse','ibex',
                       'llama','mountain_goat','musk_ox','ox','red_deer','reindeer','water_buffalo','wild_boar',
                       'yak');

ALTER TABLE wildlife_bond
  ADD COLUMN IF NOT EXISTS hoof_condition SMALLINT NOT NULL DEFAULT 100;

ALTER TABLE wildlife_bond
  DROP CONSTRAINT IF EXISTS wildlife_bond_hoof_condition_range;
ALTER TABLE wildlife_bond
  ADD CONSTRAINT wildlife_bond_hoof_condition_range CHECK (hoof_condition BETWEEN 0 AND 100);

COMMENT ON COLUMN wildlife_bond.hoof_condition IS
  '100 is a sound foot; 0 is an animal that will not put weight on it. Falls every turn — faster over stony '
  'going, which chips and bruises, and faster again on standing wet, which softens the horn and rots it — and '
  'is put back by paring the feet with a blade. Below the lame threshold the animal draws as though it had no '
  'gear at all, because a collar cannot help a foot the beast will not stand on.';

-- AND WHAT THE ACT DOES TO THE GROUND. `ActivityImpactCardIntegrationTest` holds that every performable intent
-- declares its footprint, its labour, its domain and its duration — and because it is an integration test it
-- SKIPS on this machine, so a new intent without a card gets found 65 minutes later in CI. It happened to
-- JUDGE_FOOD one slice ago; this is that lesson applied rather than repeated.
--
-- The numbers come from GROOM_ANIMAL (6 energy, 3 hygiene, 30 minutes), which is the nearest act there is: the
-- same animal, the same stooping, the same tool in the hand. Paring four feet is shorter than combing a whole
-- coat and harder on the back, so 7 and 20. No footprint: a keeper takes nothing from the ground and leaves
-- nothing on it but horn parings.
INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('PARE_HOOVES', NULL, 0, false, false,
        'Taking up a foot, holding a leaning animal and paring overgrown horn back to the sole is work done '
        || 'entirely on the animal. It takes nothing from the ground and leaves nothing on it but parings, which '
        || 'are horn and are gone by the next rain.',
        7, 3, 'FINE_MOTOR', 20)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n int; bad text;
BEGIN
  -- 0. THE CARD MUST BE THERE, and must satisfy the four other invariants that file asserts: nothing marks the
  --    land while costing the body nothing, nothing costs hygiene without costing effort, heavy work is not
  --    instant, and a card that marks nothing explains itself in at least forty characters of notes.
  SELECT count(*) INTO n FROM activity_impact WHERE intent_key = 'PARE_HOOVES';
  IF n <> 1 THEN
    RAISE EXCEPTION 'V414: PARE_HOOVES has % activity_impact rows, expected exactly 1', n;
  END IF;
  IF EXISTS (SELECT 1 FROM activity_impact WHERE intent_key='PARE_HOOVES'
              AND (labor_energy = 0 OR length(btrim(notes)) < 40
                   OR (labor_energy >= 12 AND duration_minutes < 10))) THEN
    RAISE EXCEPTION 'V414: PARE_HOOVES'' card breaks one of the activity-impact invariants';
  END IF;

  -- 1. EVERY DRAUGHT ANIMAL MUST HAVE FEET THAT WANT SEEING TO. This is the guard that matters: the whole point
  --    of the column is that a WORKING animal's feet wear, so a draught species without the flag would be an
  --    ox whose feet never need attention — the declared-but-ignored defect inverted.
  SELECT string_agg(ds.species_key, ', ') INTO bad
    FROM draft_species ds JOIN wildlife_species ws ON ws.species_key = ds.species_key
   WHERE NOT ws.feet_need_paring;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V414: these draught species have feet that never need paring: %', bad;
  END IF;

  -- 2. AND THE FLAG MUST HAVE LANDED SOMEWHERE. An IN-list against a catalogue whose spellings have moved is the
  --    single most common way one of these migrations silently does nothing — 73 of #106's own asked-for item
  --    keys turned out not to exist under the names the ticket used.
  SELECT count(*) INTO n FROM wildlife_species WHERE feet_need_paring;
  IF n < 10 THEN
    RAISE EXCEPTION 'V414: only % species have feet_need_paring set; the IN-list has probably drifted from the '
      'catalogue''s own spellings', n;
  END IF;

  -- 3. NOTHING THAT IS NOT KEPT. A fox's claws and a crow's feet are real and are not husbandry; giving them the
  --    flag would put every wild animal in the world into a keeper's chores.
  SELECT string_agg(ws.species_key, ', ') INTO bad
    FROM wildlife_species ws
   WHERE ws.feet_need_paring AND ws.tamability = 0
     AND ws.species_key NOT IN (SELECT species_key FROM draft_species);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V414: % cannot be kept and cannot be worked, so its feet are nobody''s chore: %', bad, bad;
  END IF;

  -- 4. AND EVERY EXISTING BOND STARTS SOUND rather than lame. A DEFAULT on a new column is applied to existing
  --    rows, but a seeded database is where that is worth checking out loud: a world whose every beast woke up
  --    lame would be a migration that broke the haulage model on contact.
  SELECT count(*) INTO n FROM wildlife_bond WHERE hoof_condition <> 100;
  IF n > 0 THEN
    RAISE EXCEPTION 'V414: % existing bonds did not start with sound feet', n;
  END IF;
END $$;
