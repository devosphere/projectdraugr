-- #37 — "Empty the pot." / "Bring the meat in."
--
-- The storage mechanism is finished. Storing re-owns an item so that a cached perishable stops drawing
-- predators, a container has capacity and an access state, and a named thing can be put in or taken out.
--
-- TWO WAYS OF ASKING FOR IT REACHED NOTHING.
--
-- One: the container could be opened, closed, sealed, filled by name and drawn from by name, and there was no
-- way to EMPTY it. "empty the pot" reached nothing at all, so a Chronicle who could not remember what they had
-- put by had to name each thing in turn to get it back. EMPTY_CONTAINER is that, honouring the same rules the
-- single take honours: a sealed container must be opened first, and what will not fit stays inside and is said
-- to stay inside.
--
-- Two: of the ways a person says "put this by", only some reached the store. Measured over five things worth
-- keeping, 26 of 75 phrasing/thing pairs reached nothing:
--
--   store / stow / stash / cache the X      reached STORE
--   put the X away / in the basket / in storage   reached STORE
--   bring the X in                          reached nothing
--   put the X in the store                  reached nothing
--   take the X inside                       reached nothing
--   set the X by                            reached nothing
--   put the X by for later                  reached nothing
--   get the X under cover                   reached nothing
--   lay the X up                            reached nothing
--
-- Each is gated on a thing worth keeping, so "bring the stock in" stays the animals' rule and "bring in the
-- harvest" stays the crop's.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('EMPTY_CONTAINER', NULL, 0, FALSE, FALSE,
  'Turning a container out and taking up what was in it moves things from one place to another and leaves no '
  'mark on the ground. The load is carried afterwards, which the carry itself accounts for.',
  3, 0, 'FINE_MOTOR', 5)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='EMPTY_CONTAINER';
    IF n <> 1 THEN RAISE EXCEPTION 'V397: EMPTY_CONTAINER must have exactly one impact card, found %', n; END IF;

    -- Turning a pot out marks nothing. A footprint here would be a claim that unpacking fouls the ground.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='EMPTY_CONTAINER' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V397: emptying a container must leave the ground as it was'; END IF;

    -- What this reads must exist, or the act is a sentence about nothing: the containment rows it moves, and
    -- the access state that decides whether it may be opened at all.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='item_containment' AND column_name='container_id') THEN
        RAISE EXCEPTION 'V397: item_containment is what emptying a container moves';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='container_properties' AND column_name='access_state') THEN
        RAISE EXCEPTION 'V397: access_state is what refuses a sealed container';
    END IF;

    -- And STORE must still have its own card, since this migration widens how it is reached rather than
    -- replacing it. A widening that quietly orphaned the intent it widens would be worse than the gap.
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='STORE';
    IF n <> 1 THEN RAISE EXCEPTION 'V397: STORE must still have exactly one impact card, found %', n; END IF;
END $$;
