-- #37 / #164 / #165 / #166 — "How is the crop?"
--
-- Act twenty-one swept the GROUND AND THE CROP: forty-three sentences about breaking ground, sowing, tending,
-- asking after it, reaping and resting it. TWENTY-FIVE REACHED NOTHING.
--
-- SEVEN THINGS DECIDE WHAT A HARVEST GIVES, AND A PLAYER COULD LEARN NONE OF THEM UNTIL THE GRAIN WAS IN.
-- harvestCrop weighs every one of these, each of them simulated, each of them invisible:
--
--   a TILLED seedbed            six heads instead of four
--   WORN SOIL below 60          minus two, won back only by fallow rest
--   a GRAZED stand              minus two, and a fence would have kept it whole
--   WEEDED even once            plus one, for freeing the grain from what competes with it
--   DRY GROUND never watered    minus two, and the penalty lifts for a stand that was watered
--   POLLINATORS                 plus one or two, which is the whole reason to keep bees beside a plot
--   LEFT PAST ITS CLEAN WINDOW  the ripe heads shatter and the yield halves; keeping the birds off buys TIME
--                               against that, but does not buy the grain back
--
-- Every one of those is something a keeper can still act on while the crop stands. A model this careful that says
-- nothing until the reaping is the "world knew and would not say" defect at its most expensive: the information
-- arrives exactly one moment after it could have been used.
--
-- AND THE GROUND MENDS ITSELF INVISIBLY TOO. field_soil.fertility climbs with every fallow day, faster on a
-- floodplain where the flood lays down silt, and faster again where a manure pit or compost bay stands — read
-- from construction_kind.fertilises_field. So "let the ground rest", "leave it fallow" and "manure the ground"
-- are answered by what the ground is actually doing, rather than by a sentence that reached nothing.
--
-- THREE CONFIDENTLY WRONG ANSWERS WENT WITH IT:
--
--   plant the seed          ->  PLANT_TREE: "gather acorns under an oak, or pine nuts from the cones" — said to
--                               a keeper holding a handful of grain. A crop seed and a tree seed are the same
--                               word, and no wording settles it; what they are CARRYING does.
--   let the ground rest     ->  REST: "You remain still for an hour." Asked to leave a field fallow, the player
--                               sat down.
--   how long until harvest  ->  MEASURE: "You pace it out and reckon by eye" — and no figure, while crop_stand
--                               holds the sowing date and the maturity in days.
--
-- Read-only. CHECK_CROP reports what the reaping will weigh, by the same thresholds the reaping weighs it by.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('CHECK_CROP', NULL, 0, FALSE, FALSE,
  'Walking a plot and looking over what stands in it takes nothing out of the ground and puts nothing back. The '
  'grain ripens at its own rate whether or not anybody counts the days.',
  2, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'CHECK_CROP';
    IF n <> 1 THEN RAISE EXCEPTION 'V408: looking over the crop needs exactly one impact card, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'CHECK_CROP'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V408: looking over the crop may not leave a mark on the ground'; END IF;

    -- EVERY COLUMN THE SEVEN FACTORS ARE READ FROM. Each was already maintained and none was readable, and if one
    -- is ever dropped the reading must fail here rather than quietly stop mentioning that factor — a reading that
    -- silently omits what is against a stand is worse than no reading, because the keeper trusts it.
    SELECT string_agg(c, ', ') INTO bad FROM (
        VALUES ('crop_key'), ('sown_at'), ('maturity_days'), ('harvested'), ('tilled'), ('grazed'),
               ('weeded_at'), ('watered_at'), ('birds_scared_at')
    ) AS want(c)
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'crop_stand' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V408: the crop reading is built from crop_stand columns that do not exist: %', bad;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'field_soil' AND column_name = 'fertility') THEN
        RAISE EXCEPTION 'V408: field_soil.fertility is how worn ground is known';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'construction_kind' AND column_name = 'fertilises_field') THEN
        RAISE EXCEPTION 'V408: fertilises_field is what tells a keeper their manure pit is working';
    END IF;

    -- AND SOMETHING MUST BE ABLE TO FERTILISE A FIELD, or the reading names a thing nobody can raise. The whole
    -- point of saying "faster beside a manure pit" is that a manure pit is a thing in this catalogue.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE fertilises_field > 0) THEN
        RAISE EXCEPTION 'V408: no construction kind fertilises a field, so the reading promises a thing that cannot be built';
    END IF;

    -- The seed the sowing gate looks for must be a real item, or "plant the seed" goes back to offering acorns.
    SELECT string_agg(k, ', ') INTO bad FROM (VALUES ('wild_grain_head')) AS want(k)
     WHERE NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = want.k);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V408: the field seed the sowing gate looks for does not exist: %', bad;
    END IF;
END $$;
