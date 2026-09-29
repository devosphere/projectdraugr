-- #37 — "How long will the food last?"
--
-- Food spoilage in this game is a finished subsystem. Five tiers with their own spans (raw 18h, cooked 72h,
-- smoked 30 days, dried 45, salted 60), a safe_until clock kept per object in food_preservation_state, a pest
-- check, storage that re-owns items, and a foodborne illness for eating what has gone over.
--
-- None of it could be asked about. A Chronicle could carry two months of salted meat and a fish that would be
-- finished by evening and had no way in the language of the game to tell them apart. Every phrasing of the
-- question reached nothing:
--
--   check my food stores          -> nothing
--   how long will the food last   -> nothing
--   what food do I have           -> nothing
--   is anything going off         -> nothing
--   will the meat keep            -> nothing
--
-- And the one survey that DOES walk your own ground — take stock of the camp, V390 — counts only structures.
--
-- So the world punished a mistake it would not let you see coming: eating spoiled food sickens you (PR #389),
-- and nothing anywhere told you which of the things you were carrying had gone over.
--
-- TAKE_STOCK_OF_FOOD is the question, answered from the world's own record and on the same reach rule that
-- decides what you can eat — so what it counts is exactly what is within your hands, never a number kept
-- somewhere else. Soonest to spoil is named first. Read-only: it adds no capability and moves nothing.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('TAKE_STOCK_OF_FOOD', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking, listening, smelling, reading, measuring, following sign and going through your own food to account for it all take the world exactly as it is and put nothing back.',
  2, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='TAKE_STOCK_OF_FOOD';
    IF n <> 1 THEN RAISE EXCEPTION 'V395: TAKE_STOCK_OF_FOOD must have exactly one impact card, found %', n; END IF;

    -- Going through your own stores counts it and eats none of it. A footprint here would be a claim that
    -- looking at food spoils it, and it should be argued for.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='TAKE_STOCK_OF_FOOD' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V395: going through your own food must leave every piece of it as it was'; END IF;

    -- What this reads must exist, or the answer is a sentence about nothing. These three columns ARE the answer:
    -- the tier the food keeps on, the moment it stops being safe, and the category that says it is food at all.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='food_preservation_state' AND column_name='preparation_kind') THEN
        RAISE EXCEPTION 'V395: food_preservation_state.preparation_kind is the tier the answer names';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='food_preservation_state' AND column_name='safe_until') THEN
        RAISE EXCEPTION 'V395: food_preservation_state.safe_until is how long is left in it';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE category='FOOD') THEN
        RAISE EXCEPTION 'V395: nothing in the catalogue is FOOD, so there is nothing to take stock of';
    END IF;

    -- The five tiers the report names in words. If a tier is ever added, the report should learn its word
    -- rather than printing the enum at the player, so this states the set the prose was written against.
    SELECT COUNT(DISTINCT preparation_kind) INTO n FROM food_preservation_state
     WHERE preparation_kind NOT IN ('RAW','FRESH','COOKED','SMOKED','DRIED','SALTED');
    IF n > 0 THEN RAISE EXCEPTION 'V395: % preservation tier(s) exist that the stocktake has no word for', n; END IF;
END $$;

-- The stocktake also turned up eight made foods with no preservation tier at all, so they never spoiled: acorn
-- flour, grain flour, wild grain, hazelnut and walnut kernels, a peeled root, a washed root and a bait pouch.
-- A washed root is the #60 defect once more — PROCESSING A PERISHABLE FOOD LAUNDERED IT INTO FOOD THAT NEVER
-- SPOILED, the same way gutting a fish once did.
--
-- Fixed not by adding eight cases to the hand-written map but by letting anything FOOD fall back to the foraged
-- reading, which both paths already agreed on for all eight. Four FOOD items are held back from that fallback
-- BY NAME, each for a real reason, and this states them so a rename cannot make the held-back list stale
-- without failing here.
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(k, ', ') INTO missing FROM (VALUES
        -- Not spoiling is a real property of honey, not an omission from a list.
        ('raw_honey'),
        -- Water is filed under FOOD so that drinking can find it. It carries its own risk, judged where it is
        -- drawn and drunk; a bucket does not go over in four days.
        ('clean_water'), ('filtered_water'), ('raw_water')
    ) AS held(k) WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=held.k AND d.category='FOOD');
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V395: foragedKeepKind holds back % by name, and no such FOOD item exists', missing;
    END IF;
END $$;
