-- V470: the catalogue says what is somewhere to sleep (#919, #893).
--
-- `ChroniclePhysiologyService.beddedAt` kept a hand-written list of three project kinds — GROUND_BED,
-- RAISED_SLEEPING_PLATFORM, GROUNDSHEET — and the world had grown two more that it never learned:
-- RAISED_BED_PLATFORM and SLEEPING_BENCH, both catalogued, both buildable, and both no better to sleep on
-- than the bare earth. A Chronicle who built a raised bed platform and lay down on it was warmed by nothing.
--
-- This is the shape this codebase keeps paying for: a capability declared in data and named as literals in
-- code. The literals cannot grow when the catalogue does. So the catalogue carries the answer now, and the
-- code asks it.
ALTER TABLE construction_kind
    ADD COLUMN is_bedding BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.is_bedding IS
    'Somewhere to sleep that gets a body up off the cold, wet ground. Independent of is_shelter: a bed turns '
    'no weather and a roof is not a mattress, which is why sleeping under a roof ON a bed is better than '
    'either alone.';

-- THE TWO THE CODE ALREADY KNEW BUT THE CATALOGUE DID NOT.
--
-- `makeBed` writes GROUND_BED projects and `placeCover` writes GROUNDSHEET ones, and neither kind has ever
-- had a `construction_kind` row — so the forty-seven readers that join through that table have never been
-- able to see a player's own reed pallet at all. They are catalogued here so one question answers for every
-- bed in the world.
--
-- `decays` is FALSE for both, deliberately: nothing has ever weathered them, `makeBed` has its own
-- refresh-to-whole path, and starting to wear them down in the same migration that makes them visible would
-- be two changes wearing one coat. Whether a reed pallet should rot is a question for its own ticket.
INSERT INTO construction_kind (project_kind, display_name, domain_key, decays, proven_in, is_bedding) VALUES
    ('GROUND_BED',  'Bedding pallet', 'construction', FALSE, 'V470', TRUE),
    ('GROUNDSHEET', 'Groundsheet',    'construction', FALSE, 'V470', TRUE)
ON CONFLICT (project_kind) DO NOTHING;

-- And the three the catalogue already held.
UPDATE construction_kind SET is_bedding = TRUE
 WHERE project_kind IN ('RAISED_BED_PLATFORM', 'RAISED_SLEEPING_PLATFORM', 'SLEEPING_BENCH');

DO $$
DECLARE
    beds INTEGER;
    missing TEXT;
BEGIN
    -- Every kind the old literal list named, and both the kinds it missed, must now answer yes — or a
    -- Chronicle loses a bed they had before this migration, which is the one way this change could hurt.
    SELECT string_agg(k, ', ') INTO missing FROM unnest(ARRAY[
        'GROUND_BED','GROUNDSHEET','RAISED_SLEEPING_PLATFORM','RAISED_BED_PLATFORM','SLEEPING_BENCH']) AS k
     WHERE NOT EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = k AND ck.is_bedding);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V470: these are somewhere to sleep and the catalogue does not say so: %', missing;
    END IF;

    -- And nothing else quietly became a bed.
    SELECT COUNT(*) INTO beds FROM construction_kind WHERE is_bedding;
    IF beds <> 5 THEN
        RAISE EXCEPTION 'V470: expected five beddings, found %', beds;
    END IF;
END $$;
