-- #37 — "Is the fire still going?"
--
-- Act eighteen swept the FIRE, which a Chronicle tends more often than anything but walking. Forty-four
-- sentences about getting one going, keeping it, killing it, working at it and asking after it:
-- THIRTY REACHED NOTHING.
--
-- THE SHARPEST OF THEM IS A NUMBER THE SIMULATION KEEPS TO THE MINUTE. fire_state.fuel_minutes is the burning
-- time remaining: light() sets it, feed() adds forty-five, bank() rakes the coals tighter and raises both it and
-- its ceiling, advanceTo() counts it down every single turn of the world, ChroniclePhysiologyService reads it
-- when it decides how fast a body loses heat, and PersistentStateAuditor holds that a fire cannot be ALIGHT with
-- none of it left. It is maintained more carefully than almost any other number in this game.
--
-- Not one sentence could ask. `is the fire still going`, `how is the fire`, `is there any heat left`,
-- `how long will it burn`, `will it last the night`, `is there enough wood` and `how much firewood have I got`
-- all reached nothing at all.
--
-- AND "LIGHT A FIRE" WORKED WHILE "START A FIRE" AND "MAKE A FIRE" DID NOT. The classifier knows the ignition
-- METHODS by name — bow drill, hand drill, fire plough, flint and pyrite, a lens on tinder, carrying an ember —
-- which is the right design and was built for #49. The plain request fell to a legacy rule that wanted the verb
-- "light" or "ignite" beside the word fire. So two of the commonest ways of saying the commonest thing a cold
-- person does reached nothing, along with `kindle a fire`, `get a fire going`, `strike a spark` and
-- `light the tinder`.
--
-- The same shape killed the tending verbs. `stoke the fire` worked and `put more wood on` did not, because the
-- rule wanted the word "fire" beside the verb and a person standing over one talks about the WOOD. `put the fire
-- out` worked and `smother it` did not, for the same reason, with "it".
--
-- CHECK_FIRE reports what the tick spends: whether it is alight, how long the fuel will hold, whether that will
-- see the night out (by the same 06:00-20:00 the sky reading uses, so the two can never disagree about when the
-- night ends), what wood is to hand, and — when the fire is cold — whether the stone still holds yesterday's
-- heat, asked by the SAME predicate the processes accept heat by. A reading that said the stone was still warm
-- while a smelt refused for want of heat would contradict the game rather than the world.
--
-- Read-only. No new mechanic: every number here was already maintained.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('CHECK_FIRE', NULL, 0, FALSE, FALSE,
  'Crouching to look at a fire and reckoning what is left in it burns nothing and leaves nothing. The fuel goes '
  'down at the same rate whether or not anybody counts it.',
  1, 0, 'ATTENTION', 5)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'CHECK_FIRE';
    IF n <> 1 THEN RAISE EXCEPTION 'V406: looking at the fire needs exactly one impact card, found %', n; END IF;

    -- only_with_fire is deliberately FALSE: the question is worth asking at a COLD hearth, and that is most of
    -- the point of it. A card that required a fire would make "is the fire still going" unaskable exactly when
    -- the answer is no.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'CHECK_FIRE'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V406: looking at the fire may not leave a mark, nor require one to be lit'; END IF;

    -- What the answer is made of.
    SELECT string_agg(c, ', ') INTO bad FROM (VALUES ('active'), ('fuel_minutes'), ('last_updated_at'))
        AS want(c)
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'fire_state' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V406: the fire reading is built from fire_state columns that do not exist: %', bad;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'construction_kind' AND column_name = 'retained_heat_minutes') THEN
        RAISE EXCEPTION 'V406: retained_heat_minutes is how a cold hearth is still worth something';
    END IF;

    -- The wood it counts must be the wood the fire EATS. feed() consumes dry_branch, so a reading that counted
    -- anything else would promise burning time from fuel the fire will not take — which is the declared-but-
    -- ignored defect pointed at the player instead of at the code.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'dry_branch') THEN
        RAISE EXCEPTION 'V406: dry_branch is what a fire is fed with, and there is no such item';
    END IF;

    -- And something must be able to hold a fire at all, or the reading describes a hearth nobody can raise.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE holds_fire) THEN
        RAISE EXCEPTION 'V406: no construction kind holds a fire, so there is no hearth to ask after';
    END IF;
END $$;
