-- #37/#99 — the card for the flora reading, and the guards on the two tables it stands on.
--
-- chunk_flora carries 1,440 rows: what grows where, how much of it stands, and how much the patch can hold.
-- flora_definition carries 102 kinds with the tool each wants, whether it is poisonous, and how many days it
-- takes to come back. Every gathering act reads all of it. The QUESTION reached nothing — measured live:
--
--     what grows here / what plants are here / what plants grow here  ->  nothing at all
--
-- while `forage around here` combed the same ground and came back with three dry twigs. A keeper could strip a
-- patch of plantain and never once be told what was growing under their feet.
--
-- The reading says how much is LEFT as well as what is there, because the quantity against the capacity is the
-- difference between a patch untouched and one picked bare, and it gives the catalogue's own regrowth figure
-- rather than an adjective. Trees are left to the woodland sentence that already describes the stand — and
-- which falls back to the biome's natural stand where nothing has been recorded yet, so a forest is never
-- reported bare for want of a row.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Looking over what grows is attention and a few minutes of it.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_FLORA', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 4,
        'Perception alters nothing it touches. Looking over what grows on this ground — what stands, how much '
        || 'of it is left, what wants a tool and what is poison — costs a little attention and marks nothing. '
        || 'Nothing is picked by asking.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_FLORA')
    THEN RAISE EXCEPTION 'V445: JUDGE_FLORA has no activity_impact row'; END IF;

    -- A reading marks nothing and still costs the body a little, asserted from both sides.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_FLORA' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V445: a reading must not mark the ground'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_FLORA';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V445: the reading costs nothing and takes no time'; END IF;

    -- NOTHING IS PICKED BY ASKING, which is the one thing that would make this reading an act. The gathering
    -- intents carry a footprint; this must not, and the clause above is what says so.

    -- AND THE COLUMNS IT READS. Each of these is a sentence in the answer: the count, the capacity it is
    -- measured against, the days it takes to come back, the tool it wants and whether it is poison. If any of
    -- them went, the reading would quietly stop saying that part and nobody would notice.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chunk_flora' AND column_name='capacity')
    THEN RAISE EXCEPTION 'V445: chunk_flora.capacity is gone; a picked patch cannot be told from a full one'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='flora_definition' AND column_name='regrowth_days')
    THEN RAISE EXCEPTION 'V445: flora_definition.regrowth_days is gone; the reading cannot say when it returns'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='flora_definition' AND column_name='is_poisonous')
    THEN RAISE EXCEPTION 'V445: flora_definition.is_poisonous is gone; the reading cannot warn'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='flora_definition' AND column_name='tool_required')
    THEN RAISE EXCEPTION 'V445: flora_definition.tool_required is gone; the reading cannot say what it wants'; END IF;

    -- And there is something for it to read. A reading of an empty table is a true answer that is always
    -- "nothing", and this world generates its flora at genesis.
    SELECT COUNT(*) INTO n FROM flora_definition;
    IF n = 0 THEN RAISE EXCEPTION 'V445: the catalogue holds no flora at all'; END IF;
END $$;
