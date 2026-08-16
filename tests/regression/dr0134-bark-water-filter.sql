-- dr0134 — a fire-free bark-and-charcoal water filter is reachable for the first hours (#141, EPIC #123).
--
-- Regression: every water-treatment path once required fired clay (BOIL_WATER a fireproof vessel;
-- FILTER_WATER only the clay_water_filter, whose process requires firing), so a newly arrived Chronicle at
-- standing water had no way to clarify it before the whole pottery chain. V132 adds a bare-hand bark filter
-- (no tool, no fire) and FILTER_WATER accepts it. This pins the reachable, fire-free craft so the gap stays
-- closed; BarkWaterFilterIntegrationTest proves the make + filter play out through the action pipeline.
--
-- Self-contained: BEGIN/ROLLBACK, asserts against the migrated catalogue, changes nothing.
BEGIN;

DO $$
BEGIN
    -- The filter is a real, obtainable item made by a VERIFIED process.
    IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key='bark_water_filter') THEN
        RAISE EXCEPTION 'bark_water_filter has no acquisition path'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='make_bark_water_filter' AND output_item_key='bark_water_filter' AND review_state='VERIFIED') THEN
        RAISE EXCEPTION 'make_bark_water_filter is missing or not VERIFIED'; END IF;

    -- The whole point: no tool and no fire — it is a first-hours, bare-hand option.
    IF EXISTS (SELECT 1 FROM material_process WHERE process_key='make_bark_water_filter' AND (tool_class IS NOT NULL OR requires_fire)) THEN
        RAISE EXCEPTION 'make_bark_water_filter must need neither a tool nor fire'; END IF;

    -- Its inputs are all themselves reachable (an early Chronicle can get bark, charcoal, and grass).
    IF EXISTS (SELECT 1 FROM material_process_input mpi WHERE mpi.process_key='make_bark_water_filter'
               AND NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key=mpi.item_key)) THEN
        RAISE EXCEPTION 'a make_bark_water_filter input has no acquisition path'; END IF;

    -- Conservation: the filter weighs no more than the bark, charcoal, and grass it is packed from.
    IF (SELECT max_output_grams > min_input_grams * 1.05 FROM process_mass_balance WHERE process_key='make_bark_water_filter') THEN
        RAISE EXCEPTION 'make_bark_water_filter creates mass from nothing'; END IF;

    RAISE NOTICE 'PASS: bark-and-charcoal water filter is a reachable, fire-free, mass-conserving first-hours craft (#141)';
END $$;

ROLLBACK;
