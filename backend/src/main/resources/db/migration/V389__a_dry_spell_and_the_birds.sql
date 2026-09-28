-- #37 / #165 — the field reckoned six things and not the two a farmer spends most of the season on.
--
-- harvestCrop already reads a tilled seedbed, the soil's fertility, whether the animals got in, whether the stand
-- was weeded, what the bees and worms did for it, and how late it was cut. Two of the plainest things anybody does
-- to a crop were nowhere in it:
--
--   "water the seedlings"        -> UNKNOWN. Nothing in the world.
--   "scare the birds off the crop" -> UNKNOWN. And the harvest prose ALREADY says "the birds have been at it".
--
-- Both are read off data the world already keeps. Drought is not invented: world_chunk.moisture is the same column
-- that decides how warm the body is where it stands (#709) and whether a well can be sunk there (#726). A stand
-- grown on dry ground gives less, and carrying water to it is the answer -- which is why it is the DRY plots that
-- reward the work, and why watering a wetland plot does nothing but tire you.
--
-- Birds are the other half of the lateness rule that already exists. A late stand shatters and the birds work at
-- it; keeping them off it buys back part of the clean window. It does not make a late harvest whole -- grain that
-- has shattered onto the ground is gone -- so this is RECOVERY, never more matter than the stand ever held.

ALTER TABLE crop_stand ADD COLUMN watered_at      TIMESTAMPTZ;
ALTER TABLE crop_stand ADD COLUMN birds_scared_at TIMESTAMPTZ;

COMMENT ON COLUMN crop_stand.watered_at IS
 'When this stand was last watered by hand (#37/#165). A stand on DRY COUNTRY (world_chunk.moisture below 350, '
 'which is 16 of the world''s 86 grassland chunks rather than the 50 that a 450 line would have caught) yields '
 'less unless it was watered during its season; on ground that is damp of itself, watering changes nothing and '
 'the refusal says so.';

COMMENT ON COLUMN crop_stand.birds_scared_at IS
 'When the birds were last driven off this stand (#37/#165). Extends the clean-harvest window, because the '
 'shattering rule already blames them; it never recovers grain that has already gone onto the ground.';

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('WATER_CROP', NULL, 0, FALSE, FALSE,
  'Working a plot that is already broken. Sowing, weeding, watering, reaping and setting a sapling are done to ground that has had the ground-breaking done to it, and the tilling is where that reckoning belongs.',
  7, 3, 'LOAD', 30),
 ('SCARE_BIRDS', NULL, 0, FALSE, FALSE,
  'Shouting and walking a standing crop marks nothing. The ground is already broken and sown, the birds leave of their own accord, and nothing is cut, dug, burned or carried away by it.',
  3, 0, 'ATTENTION', 20)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key IN ('WATER_CROP','SCARE_BIRDS');
    IF n <> 2 THEN RAISE EXCEPTION 'V389: both new intents need an impact card, found %', n; END IF;

    -- The companion rules the card guard enforces, reproduced here so a bad row never reaches the suite:
    -- a footprint costs energy, and a card claiming NO footprint must say why in the notes.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key IN ('WATER_CROP','SCARE_BIRDS')
       AND (labor_energy <= 0 OR duration_minutes <= 0 OR (footprint_kind IS NULL AND length(notes) < 40));
    IF n > 0 THEN RAISE EXCEPTION 'V389: an impact card must cost something and account for its footprint'; END IF;

    -- Carrying water is work, and doing it must cost more than shouting at birds does.
    SELECT COUNT(*) INTO n FROM activity_impact a JOIN activity_impact b ON b.intent_key='SCARE_BIRDS'
     WHERE a.intent_key='WATER_CROP' AND a.labor_energy > b.labor_energy;
    IF n <> 1 THEN RAISE EXCEPTION 'V389: hauling water must cost more than driving birds off'; END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='crop_stand' AND column_name='watered_at') THEN
        RAISE EXCEPTION 'V389: crop_stand.watered_at did not take';
    END IF;
END $$;
