-- #108 — a keeper's death ends the keeping, and the animals go on living.
--
-- #108's regression list names "restart, death" among the lifecycles that must be covered, and death was the
-- one with nothing behind it. The death path itself is careful — it relocates the Chronicle's possessions to
-- the ground where they fell, writes a chronicle_death_snapshot, records CHRONICLE_DIED on the chronicle and
-- on the world — and it says nothing whatever about the animals.
--
-- MEASURED on a booted world, killing a keeper through the real path (critical dehydration) with a tamed ox:
--
--   before death           thirst=4  hunger=3
--   three turns after it   thirst=16 hunger=12   bond_stage: still TAMED
--
-- The ox went on getting thirstier and hungrier for ever, tamed to a corpse, and would have gone on breeding,
-- fouling the ground and catching cold in a byre nobody owned. Eight of the ten husbandry ticks filter on
-- `bond_stage='TAMED'`, so the keeping is what has to end — and `keepsSuchABeast` already asks whether the
-- keeper is LIVING, so the world's own convention was there to follow.
--
-- WHAT THIS DOES NOT DO: it does not delete the bond. The animals were tamed, and that happened; this project
-- keeps history rather than removing it. The bond's stage goes back to WILD — a value the CHECK constraint has
-- always allowed — and `released_at` records when the keeping ended, so a later cycle can tell a herd that was
-- never tamed from one whose keeper died. trust_level is left where it was, which is the honest basis for a new
-- Chronicle taming the same animals again: they knew a person once.
--
-- The POPULATION is untouched, which is the whole point. The animals are a wildlife_population like any other
-- and remain in the world — findable, trackable, huntable and tameable by whoever comes next.

ALTER TABLE wildlife_bond
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMPTZ;

DO $$
DECLARE n INT;
BEGIN
    -- 1. The column is there to be stamped.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='released_at')
    THEN RAISE EXCEPTION 'V439: wildlife_bond.released_at was not added'; END IF;

    -- 2. NOTHING IS RELEASED YET. This migration adds the record, not the act — a row stamped at install time
    --    would be claiming a keeper died when none has.
    SELECT COUNT(*) INTO n FROM wildlife_bond WHERE released_at IS NOT NULL;
    IF n <> 0 THEN RAISE EXCEPTION 'V439: % bonds are already marked released', n; END IF;

    -- 3. WILD IS A STAGE THE CONSTRAINT ALREADY ALLOWS, which is what lets a keeping end without inventing a
    --    value or widening a CHECK. If this ever stops holding, the death path has nowhere to put a released
    --    bond and every husbandry tick goes back to charging a corpse's stock. Read off the constraint itself
    --    rather than from a list kept here — the shape that made 45 garments unwearable (V313).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'wildlife_bond'::regclass
                      AND pg_get_constraintdef(oid) LIKE '%bond_stage%'
                      AND pg_get_constraintdef(oid) LIKE '%WILD%')
    THEN RAISE EXCEPTION 'V439: bond_stage no longer allows WILD, so a keeping cannot be ended'; END IF;

    -- 4. NULLABLE, AND WITH NO DEFAULT. Both halves matter and neither is decoration: a NOT NULL column would
    --    have needed a value for every bond that exists, and a DEFAULT now() would stamp every bond made after
    --    this as already released — which is the opposite of what it records, and would stop the husbandry
    --    ticks for living keepers the moment anybody tamed anything.
    SELECT COUNT(*) INTO n FROM information_schema.columns
     WHERE table_name='wildlife_bond' AND column_name='released_at'
       AND is_nullable='YES' AND column_default IS NULL;
    IF n <> 1 THEN RAISE EXCEPTION 'V439: released_at must be nullable and have no default'; END IF;
END $$;
