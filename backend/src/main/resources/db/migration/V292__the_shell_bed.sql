-- The shell bed (#157), and what makes a bed different from scattered shellfish.
--
-- A mussel bed is not a place where mussels happen to be. It is a dense mat of them cemented to each other and
-- to the rock, and that density is what lets it recover: spat settles on the shells already there, so a bed
-- reseeds itself from its own population in a way a thin scatter over open shore cannot. Work a bed hard and it
-- comes back; work a scatter as hard and you have taken the seed with the crop.
--
-- The colonies already reach the shore — mussel_bed and river_snail_bed carry COAST — and there is already a
-- working depletion model (insect_colony.product_ready_at, set from insect_colony_kind.regrowth_days). What was
-- missing is any ground where that recovery differs, so every stretch of coast came back at exactly one rate.
--
-- Which colonies are shellfish is declared here rather than named in the Java, for the reason this cycle has
-- found seven times over: a list in code is a snapshot of the catalogue that stops being true. The next shellfish
-- added gets this by setting a column, not by someone remembering a query.
ALTER TABLE insect_colony_kind ADD COLUMN IF NOT EXISTS shellfish BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE insect_colony_kind SET shellfish = TRUE
 WHERE colony_kind IN ('mussel_bed', 'river_snail_bed');

DO $$
DECLARE problem text;
BEGIN
    IF (SELECT count(*) FROM insect_colony_kind WHERE shellfish) < 2 THEN
        RAISE EXCEPTION 'V292: the world must know more than one shellfish colony, or this changed nothing';
    END IF;

    -- A shellfish colony that no coast carries would be a rule about ground the player never stands on.
    SELECT string_agg(colony_kind, ', ') INTO problem
      FROM insect_colony_kind
     WHERE shellfish AND biome_affinity NOT ILIKE '%COAST%';
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V292: shellfish that never reach the shore: %', problem;
    END IF;

    -- Every shellfish colony must actually yield something, or the bed speeds up nothing.
    SELECT string_agg(k.colony_kind, ', ') INTO problem
      FROM insect_colony_kind k
     WHERE k.shellfish
       AND NOT EXISTS (SELECT 1 FROM insect_colony_product p WHERE p.colony_kind = k.colony_kind);
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V292: shellfish colonies that yield nothing: %', problem;
    END IF;

    -- The Auditor's own obtainability rule, asserted where it costs a second rather than an hour in CI.
    SELECT string_agg(d.item_key, ', ') INTO problem
      FROM item_definition d
     WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = d.item_key)
       AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key = d.item_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V292: item(s) with no declared way to be obtained: %', problem; END IF;
END $$;
