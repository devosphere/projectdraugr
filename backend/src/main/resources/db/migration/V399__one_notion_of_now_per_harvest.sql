-- #37 — "One notion of now per harvest."
--
-- `in_season(smallint[])` answers whether a month is in a season's list, and it reads the month from the GLOBAL
-- simulation_clock:
--
--   SELECT p_months IS NULL
--       OR EXTRACT(MONTH FROM (SELECT simulated_at FROM simulation_clock WHERE id=1) ...) = ANY (p_months)
--
-- Four callers pass an explicit Instant for the moment their work happens, and then ask that function what
-- month it is — so each of them mixes two notions of "now" in one statement:
--
--   PhysicalItemService.collectInsects(chronicle, chunk, text, occurredAt)
--   WildlifeEncounterService.takeTamedYield(chronicle, at, text)
--   WildlifeEncounterService.takeSpeciesDrops(chronicle, species, at)
--   WildlifeEncounterService.fish(chronicle, chunk, action, at, text)
--
-- In ordinary play the two agree, because an action's resolvedAt comes from the clock — which is exactly why
-- this has been invisible. It is not invisible in its consequences:
--
-- A harvest asked about a summer morning picked its COLONY KIND from that morning (seasonOf(occurredAt)) and
-- then had the colony's PRODUCTS filtered by whatever month the world clock stood in. Cricket and grasshopper
-- yields exist only in months 6-9, so with the clock outside June-September a summer harvest of either came
-- away empty FOR EVER. And because nothing was taken, the ground was never stamped as worked — so it could
-- never be worked out either, and the depletion the simulation models became unreachable. Measured: twelve
-- harvests in a row, every one of them "You work at the cricket colony for a while, but come away
-- empty-handed", with the colony row never written.
--
-- This adds a two-argument form that takes the month it should judge by. The one-argument form is left exactly
-- as it is, for the callers that genuinely mean the world's own now.

CREATE OR REPLACE FUNCTION in_season(p_months smallint[], p_month int)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT p_months IS NULL OR p_month = ANY (p_months)
$$;

COMMENT ON FUNCTION in_season(smallint[], int) IS
 'Whether a month is in a season list, judged by the month GIVEN rather than by the world clock (#37). Use '
 'this wherever the caller already holds the instant its work happens at; the one-argument form reads '
 'simulation_clock and is for callers that mean the world''s own now.';

DO $$
DECLARE n INT;
BEGIN
    -- Both forms must exist: the new one for callers holding an instant, the old one for callers meaning now.
    SELECT COUNT(*) INTO n FROM pg_proc WHERE proname='in_season';
    IF n <> 2 THEN RAISE EXCEPTION 'V399: expected both forms of in_season, found %', n; END IF;

    -- And the new form must agree with the old one when it is handed the clock's own month, or the two
    -- definitions have drifted apart and the sweep has made things worse rather than better.
    IF (SELECT in_season(ARRAY[6,7,8,9]::smallint[],
                         EXTRACT(MONTH FROM (SELECT simulated_at FROM simulation_clock WHERE id=1) AT TIME ZONE 'UTC')::int))
       <> (SELECT in_season(ARRAY[6,7,8,9]::smallint[])) THEN
        RAISE EXCEPTION 'V399: the two forms of in_season disagree about the clock''s own month';
    END IF;

    -- A sanity check on the thing that was actually wrong: a summer month must find the summer yields.
    IF NOT (SELECT in_season(available_months, 7) FROM insect_colony_product WHERE colony_kind='cricket_colony' LIMIT 1) THEN
        RAISE EXCEPTION 'V399: July must be in season for a cricket, or the harvest fix rests on nothing';
    END IF;
    IF (SELECT in_season(available_months, 1) FROM insect_colony_product WHERE colony_kind='cricket_colony' LIMIT 1) THEN
        RAISE EXCEPTION 'V399: January must NOT be in season for a cricket, or the test is vacuous';
    END IF;
END $$;
