-- #37/#108 — the choice a keeper could not make: whether the stock are shut in or out at grass.
--
-- Measured live, on the last sweep of ordinary sentences: `let the animals out` reached nothing at all. And
-- the reason is not the words — the world has no such state. Everything about keeping stock reads ONE thing:
-- whether a shelters_stock or barrier structure stands on the keeper's ground.
--
--     rest overnight          restPennedDraftBeasts    -> a shelters_stock structure standing
--     protection from a raid  raidUnprotectedStock     -> a barrier or enclosing structure, sound
--     settling to breed       the breeding pass        -> a shelters_stock structure standing
--     overcrowding (V434)     crowdingTellsOnStock     -> head against the room the pens hold
--     grazing relief (#104)   advanceDraftHunger       -> the keeper standing on GRASSLAND
--
-- So a keeper who raised a byre had their beasts in it, always, and could not turn them out to grass; and a
-- keeper on open grassland had them out, always, and could not shut them in. The trade-off every stock-keeper
-- in history has made twice a day — fed on grass, or safe behind a wall — was not theirs to make.
--
-- at_pasture_since is that state, and NULL is TODAY'S WORLD: kept as the structures on the ground dictate.
-- Turning them out sets it; bringing them in clears it. What changes while they are out:
--
--     * they graze: extra relief on grazable ground, ADDED to what they already got, so nothing a keeper has
--       today gets worse by this migration landing;
--     * the pen does not rest them and does not keep a wolf off them -- an animal at grass is out of reach of
--       both;
--     * they are not crowded, however small the pen is, because they are not in it;
--     * and they will eat a ripe, unfenced stand on that ground, which until now only WILD herbivores would do
--       -- a keeper's own goats could stand in the barley and never touch it.
--
-- TWO ACTS, so two cards, and these are acts rather than readings: walking a herd out to grass and bringing
-- them back in is work, it takes time, and it marks the ground they are driven over.

ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS at_pasture_since TIMESTAMPTZ;

COMMENT ON COLUMN wildlife_bond.at_pasture_since IS
    'When this animal was last turned out to grass (#37/#108). NULL means kept as the structures on the '
    'keeper''s ground dictate, which is how every herd behaved before this column existed: penned where a pen '
    'stands, loose where none does. While it is set the animal grazes, is not rested or protected by the pen, '
    'is not counted against the pen''s room, and will eat an unfenced ripe stand on that ground.';

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES
 ('TURN_OUT_STOCK', 'TRAMPLING', 4, FALSE, FALSE, 4, 1, 'LOAD', 20,
  'Driving the stock out of the pen and onto the grass. They mark the ground they are walked over, and a herd '
  || 'does not go where it is pointed without some work.'),
 ('BRING_IN_STOCK', 'TRAMPLING', 4, FALSE, FALSE, 5, 1, 'LOAD', 25,
  'Gathering the stock off the grass and shutting them in. Harder than turning them out, as anyone who has '
  || 'done it at dusk knows.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    -- 1. The state exists and defaults to today's world.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='at_pasture_since')
    THEN RAISE EXCEPTION 'V450: wildlife_bond.at_pasture_since was not added'; END IF;
    SELECT COUNT(*) INTO n FROM wildlife_bond WHERE at_pasture_since IS NOT NULL;
    IF n > 0 THEN
        RAISE EXCEPTION 'V450: % existing bond(s) came out of this migration already at pasture; the default '
                        'must be the world as it was', n;
    END IF;

    -- 2. BOTH ACTS HAVE A CARD, and they are acts: they cost the body, take real time, and mark the ground.
    --    A reading that marked the ground would be wrong; an act that marks nothing would be weightless.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key IN ('TURN_OUT_STOCK','BRING_IN_STOCK');
    IF n <> 2 THEN RAISE EXCEPTION 'V450: expected two cards for the two acts, found %', n; END IF;
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key IN ('TURN_OUT_STOCK','BRING_IN_STOCK')
       AND (footprint_kind IS NULL OR footprint_amount <= 0 OR labor_energy <= 0 OR duration_minutes <= 0);
    IF n > 0 THEN RAISE EXCEPTION 'V450: % of the two acts cost nothing or mark nothing', n; END IF;

    -- 3. AND THE FIVE THINGS THAT READ THE STATE ARE ALL STILL THERE. Each of these is a place the Java now
    --    asks whether the animal is out: the rest, the raid, the crowding, the hunger and the crop. If one
    --    went, turning stock out would quietly stop meaning anything, which is the defect this closes.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='draft_hunger')
    THEN RAISE EXCEPTION 'V450: wildlife_bond.draft_hunger is gone; grass would feed nothing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='draft_fatigue')
    THEN RAISE EXCEPTION 'V450: wildlife_bond.draft_fatigue is gone; the pen would rest nothing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='last_raid_at')
    THEN RAISE EXCEPTION 'V450: wildlife_bond.last_raid_at is gone; the raid cannot keep its own time'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_kind' AND column_name='holds_how_many')
    THEN RAISE EXCEPTION 'V450: construction_kind.holds_how_many is gone; crowding cannot be read'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='crop_stand' AND column_name='grazed')
    THEN RAISE EXCEPTION 'V450: crop_stand.grazed is gone; stock at grass could not eat a stand'; END IF;

    -- 4. There is somewhere to bring them IN to, or the act would be a refusal for every keeper. At least one
    --    buildable kind must shelter stock.
    SELECT COUNT(*) INTO n FROM construction_kind WHERE shelters_stock;
    IF n = 0 THEN RAISE EXCEPTION 'V450: no construction kind shelters stock; nothing could be brought in'; END IF;
END $$;
