-- #37/#99 — the card for the reading of what a keeper has learned, and the guard on the record behind it.
--
-- chronicle_discovery has existed since the first migrations and was written in exactly TWO places:
--
--     CRAFT_BASKET   -> "WOVEN_BASKET"
--     BUILD_FIRE_PIT -> "STONE_FIRE_PIT"
--
-- out of a hundred and forty intents, five hundred material processes and a hundred staged builds. So a keeper
-- who had tanned a hide, fired pottery, knapped a blade, smelted iron and raised a byre had learned NOTHING by
-- the world's own record — and `chronicle.journey`'s discovery count, which the client shows, read 0 for every
-- playthrough that has ever run. Measured live: `what have I discovered`, `what do I know how to make` and
-- `what can I make` all reached nothing at all.
--
-- Both halves ship together. The recording: a completed process and a finished build now carry their own key
-- back to the action, which writes one row where the ledger rows are written, rather than in each of the
-- hundred and forty branches — which is exactly how it came to be written in only two. The reading:
-- JUDGE_LEARNING resolves those keys back through the catalogue, so the answer is in the names a keeper would
-- use: "You have carried these through yourself, and could again: tan a hide, flesh a hide and fire a pot."
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Priced with the other readings: casting back over what you know how to do is attention
-- and nothing else.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_LEARNING', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 3,
        'Perception alters nothing it touches. Casting back over the work you have carried through yourself — '
        || 'the processes and the builds you could set your hand to again from memory — costs a little '
        || 'attention and marks nothing.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    -- 1. The card exists.
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_LEARNING')
    THEN RAISE EXCEPTION 'V444: JUDGE_LEARNING has no activity_impact row'; END IF;

    -- 2. A reading marks nothing, and 3. still costs the body a little.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_LEARNING' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V444: a reading must not mark the ground'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_LEARNING';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V444: the reading costs nothing and takes no time'; END IF;

    -- 4. THE RECORD IT READS IS STILL THERE, and still keyed so a thing is learned only once. The whole slice
    --    rests on chronicle_discovery's primary key: the recording writes on every success and relies on
    --    (chronicle_id, discovery_key) to make the second time a no-op.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle_discovery' AND column_name='discovery_key')
    THEN RAISE EXCEPTION 'V444: chronicle_discovery.discovery_key is gone; nothing can be learned'; END IF;
    SELECT COUNT(*) INTO n FROM pg_constraint
     WHERE conrelid='chronicle_discovery'::regclass AND contype='p'
       AND pg_get_constraintdef(oid) LIKE '%chronicle_id%' AND pg_get_constraintdef(oid) LIKE '%discovery_key%';
    IF n = 0 THEN
        RAISE EXCEPTION 'V444: chronicle_discovery has no (chronicle_id, discovery_key) key, so the same work '
                        'would be learned again on every repetition';
    END IF;

    -- 5. AND THE CATALOGUE THE KEYS RESOLVE AGAINST. The reading joins 'PROCESS:' || process_key and
    --    'ASSEMBLY:' || assembly_key back to their display names; without those two tables it would answer in
    --    raw keys, which is the kind of answer this ticket exists to stop.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='material_process' AND column_name='display_name')
    THEN RAISE EXCEPTION 'V444: material_process.display_name is gone; the reading would answer in keys'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='assembly_definition' AND column_name='display_name')
    THEN RAISE EXCEPTION 'V444: assembly_definition.display_name is gone; the reading would answer in keys'; END IF;
END $$;
