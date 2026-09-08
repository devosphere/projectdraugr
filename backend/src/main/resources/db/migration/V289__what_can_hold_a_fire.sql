-- What can hold a fire (#71/#77).
--
-- FireService named STONE_FIRE_PIT literally in five places: lighting, banking, feeding, finding the fire that
-- burns here, and counting active fires. So the stone fire pit was the only thing in the world that could hold a
-- flame — and the catalogue already lets a Chronicle build a CLAY_LINED_HEARTH in three staged pieces of work,
-- gathering the stones, lining them with clay, and waiting for the lining to cure.
--
-- At the end of that they had a hearth they could not light. Not a hearth that burned badly, or burned out fast:
-- one that could not take a fire at all, because five queries were looking for a different word.
--
-- Which structures hold a fire is a fact about the catalogue, so it is declared here and read there, the way #93
-- did for weapons and tools and V280/V281 did for what encloses and what stands in the way.
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS holds_fire BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE construction_kind SET holds_fire = TRUE
 WHERE project_kind IN ('STONE_FIRE_PIT', 'CLAY_LINED_HEARTH');

-- A hearth that holds fire must also be flammable-safe in the sense the fire code already understands: it is the
-- fire's container, not its fuel. Both of these are stone and clay and neither should burn down; assert it rather
-- than assume it, because V284 found four shelters that had been left off the flammable list entirely.
DO $$
DECLARE offender text;
BEGIN
    SELECT string_agg(project_kind, ', ') INTO offender
      FROM construction_kind WHERE holds_fire AND flammable;
    IF offender IS NOT NULL THEN
        RAISE EXCEPTION 'V289: a hearth is what contains a fire, not what feeds it: %', offender;
    END IF;

    IF (SELECT count(*) FROM construction_kind WHERE holds_fire) < 2 THEN
        RAISE EXCEPTION 'V289: the world must hold more than one kind of fireplace, or this changed nothing';
    END IF;
END $$;
