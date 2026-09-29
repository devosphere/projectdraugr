-- #37 — "cover my tracks" READ tracks instead of covering them, and reported success.
--
--   cover my tracks  ->  TRACK / SUCCEEDED
--   "You find a line of prints pressed into the softer ground."
--
-- A hunter covering their trail is trying to leave LESS sign. The world read sign instead. TRACK owns the word
-- "tracks" outright, so every sentence containing it became an act of reading them -- the same shape as
-- "is the water safe to drink" being answered by drinking it (V392): the words are about the thing, and the
-- world does the thing.
--
-- And there was a real mechanic waiting for it. A fresh kill carried on the body adds 10 to the passive-encounter
-- chance -- blood and scent on the wind, the reason carrying a carcass through predator ground makes you the bait
-- (#123/#127). Cooking, storing or caching the meat removes the draw entirely; a camp store on the ground removes
-- it too. There was nothing a hunter could do about it OUT in the country, between the kill and the camp, which
-- is exactly where the risk is and exactly when a person brushes out their prints and takes to hard ground.
--
-- Covering the trail does NOT remove the draw: blood still smells, and a predator that has the scent does not
-- need the footprints. It takes most of it off for a few hours -- long enough to get a carcass home, which is the
-- whole of what the act is for.

ALTER TABLE chronicle ADD COLUMN trail_hidden_at TIMESTAMPTZ;

COMMENT ON COLUMN chronicle.trail_hidden_at IS
 'When this Chronicle last brushed out their trail (#37). While it is recent, a fresh kill carried on the body '
 'draws far less than it otherwise would -- it never draws nothing, because blood still carries.';

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('HIDE_TRAIL', NULL, 0, FALSE, FALSE,
  'Unmaking a mark rather than making one. Brushing out prints, scattering the disturbed leaf litter back over itself and stepping onto hard ground leave the place closer to how it was found than the walking did.',
  7, 2, 'LOCOMOTION', 25)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='HIDE_TRAIL';
    IF n <> 1 THEN RAISE EXCEPTION 'V393: HIDE_TRAIL must have exactly one impact card, found %', n; END IF;

    -- It is work: stooping along your own back trail for the better part of half an hour. If a later edit makes
    -- it free, that is a claim that hiding a trail costs nothing, and it should be argued for. TRACK -- reading a
    -- trail -- costs 6, and this is the heavier of the two: you walk it twice and work the ground as you go.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='HIDE_TRAIL' AND (labor_energy < 4 OR duration_minutes < 15);
    IF n > 0 THEN RAISE EXCEPTION 'V393: covering a trail is work and must cost like work'; END IF;

    -- And it must cost MORE than merely looking at tracks, which is the act it was being confused with.
    SELECT COUNT(*) INTO n FROM activity_impact h JOIN activity_impact t ON t.intent_key='TRACK'
     WHERE h.intent_key='HIDE_TRAIL' AND h.labor_energy > t.labor_energy;
    IF n <> 1 THEN RAISE EXCEPTION 'V393: covering a trail must cost more than reading one';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle' AND column_name='trail_hidden_at') THEN
        RAISE EXCEPTION 'V393: chronicle.trail_hidden_at did not take';
    END IF;
END $$;
