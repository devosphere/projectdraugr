-- #37/#99 — the beast you raised, and the one thing you could never do with it.
--
-- A keeper of this world can find an animal, approach it, tame it, name it, feed it, water it, groom it, tend
-- it, treat it when it sickens, work it, breed it, see its young born, turn it out to grass and shut it in
-- again (V450). Ten husbandry passes run over it every tick. What a keeper cannot do is kill it.
--
-- Measured live before this migration: `kill the sheep` is read as a HUNT, and answered
--
--     "You work this ground over for bighorn sheep and find none of it here."
--
-- to a keeper standing beside their own flock. Honest for a hunter. Wrong for a keeper, and wrong in the way
-- this project cares about most: husbandry has no terminal product. What a tamed animal gives is catalogued in
-- tamed_yield, and every row of it is MILK, an EGG or WOOL -- things taken off a living animal. No row gives
-- meat, hide, bone, sinew or fat, because those come only from a death, and no act of this world kills a kept
-- beast. So every tamable species in the registry is a dead end for the single product husbandry exists for.
--
-- The whole of the other half is already built and already reached from somewhere else:
--
--     wildlife_carcass          what a kill leaves on the ground            HARVEST_CARCASS butchers it
--     V343's sinew/bone/fat     a carcass is more than meat                 the same butchering
--     carcass_scent             what blood on the ground draws              the predator pass
--     the preservation tiers    RAW 18h < COOKED 72h < SMOKED 30d ...       cooking, smoking, salting
--
-- So this is one act wired into an existing chain, not a subsystem: SLAUGHTER_STOCK ends the keeping and lays
-- a carcass down, and every word a player already knows for what comes next -- butcher, skin, gut, take the
-- hide, smoke the meat -- works on it unchanged. The butchering verbs are deliberately NOT claimed by the new
-- act: `kill the goat` leaves a carcass and `butcher it` works it, which is the real order of the work.
--
-- ONE CARD, because this is an act and not a reading: it costs the body, it takes real time, and it marks the
-- ground it is done on. A slaughter is quieter than a fight -- the beast is held, and it is quick -- but blood
-- and a herd's reaction both carry past the pen, so it drifts.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES
 ('SLAUGHTER_STOCK', 'COMMOTION', 12, TRUE, FALSE, 10, 10, 'LOAD', 20,
  'Killing a beast you have kept. Less commotion than a fight, because it is held and it is quick, but the '
  || 'blood carries and so does the rest of the herd knowing. Bloodier per minute than butchering a wild kill '
  || 'where it fell, and it is the keeper who has to stand there and do it.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    -- 1. THE ACT HAS A CARD AND IS AN ACT. A reading that marked the ground would be wrong; an act that cost
    --    nothing and took no time would be weightless, which is what the guard in V450 was written to catch.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='SLAUGHTER_STOCK' AND footprint_kind IS NOT NULL AND footprint_amount > 0
       AND labor_energy > 0 AND duration_minutes > 0;
    IF n <> 1 THEN RAISE EXCEPTION 'V453: SLAUGHTER_STOCK has no card, or its card costs nothing'; END IF;

    -- 2. THE THREE RECORDS THE ACT WRITES. A slaughter decrements the head on the population, lays a carcass
    --    down, and ends the keeping of the last one. If any of these goes, the act silently stops meaning
    --    anything: a carcass with no population to come off would be matter from nothing, and a keeping that
    --    never ended would leave a keeper milking an animal they ate.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_population' AND column_name='population_count')
    THEN RAISE EXCEPTION 'V453: wildlife_population.population_count is gone; the head could not be taken'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='released_at')
    THEN RAISE EXCEPTION 'V453: wildlife_bond.released_at is gone (V439); the keeping could not end'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='wildlife_carcass')
    THEN RAISE EXCEPTION 'V453: wildlife_carcass is gone; a slaughter would leave nothing behind'; END IF;

    -- 3. AND SOMETHING TO DO WITH WHAT IS LEFT. This is the half that makes the act terminally useful rather
    --    than a new dead end of its own: the carcass it lays down is butchered by an act that already exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key='HARVEST_CARCASS')
    THEN RAISE EXCEPTION 'V453: HARVEST_CARCASS has no card; a slaughtered beast could not be butchered'; END IF;

    -- 4. MEAT IS NEVER A YIELD, which is the conservation rule this act exists to respect rather than dodge.
    --    Milk, eggs and wool come off a living animal and the animal stays. Meat, hide, bone, sinew and fat
    --    come off a dead one. If a later cycle is ever tempted to give a kept animal meat on an interval, that
    --    is matter from nothing and the keeper's flock would never shrink -- this is where it stops.
    --
    --    tamed_yield_kind_check already confines yield_kind to EGG, MILK and WOOL, and it cannot see this: the
    --    row that proved this guard fires was raw_game_meat LABELLED 'MILK', which the CHECK accepted without
    --    complaint. The label is not the thing. This reads the ITEM.
    SELECT COUNT(*) INTO n FROM tamed_yield
     WHERE item_key LIKE '%meat%' OR item_key LIKE '%hide%' OR item_key LIKE '%sinew%' OR item_key LIKE '%tallow%';
    IF n > 0 THEN
        RAISE EXCEPTION 'V453: % tamed_yield row(s) give meat, hide, sinew or tallow off a LIVING animal. '
                        'Those come from a death; a slaughter is the act that provides them', n;
    END IF;

    -- 5. THERE IS SOMETHING TO SLAUGHTER. The act is reachable only for a species a keeper can actually come
    --    to keep, so at least one must be tamable and not forbidden to keep (#109's cognition gate).
    SELECT COUNT(*) INTO n FROM wildlife_species ws
     WHERE ws.tamability > 0 AND ws.kingdom_class <> 'MONSTRUM'
       AND NOT EXISTS (SELECT 1 FROM cognition_profile cp
                        WHERE cp.species_key=ws.species_key AND cp.domestication_prohibited);
    IF n = 0 THEN RAISE EXCEPTION 'V453: no species can be kept; the act would be unreachable'; END IF;
END $$;
