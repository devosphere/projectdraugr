-- #108 — overcrowding: how many beasts a thing holds, so that keeping too many in it tells.
--
-- #108's third acceptance criterion asks that "welfare, escape risk, disease/parasites, predators, mud, waste,
-- drainage, weather, overcrowding, breeding/young, and food/water access use the actual structure condition AND
-- ANIMAL POPULATION". V434 is the population half. There was no overcrowding in this world at all: a keeper
-- could hold forty goats in one nesting box and nothing anywhere asked how many went in. Every structure was
-- a boolean — it either stood there or it did not.
--
-- THE NUMBERS COME FROM THE CATALOGUE'S OWN WORDS, not from an invented budget. Every one of the twenty kinds
-- that declares shelters_stock already describes itself, and most of them say the number outright:
--
--   FARROWING_SHELTER   "so a sow can lie down without lying on what she has just had"      -> 1
--   FOALING_STALL       "a wide box ... quiet enough that a mare will lie down in it"        -> 1
--   NESTING_BOX         "where a bird will sit because it feels unseen"                     -> 1
--   SICK_ANIMAL_SHELTER "a small shut box set well apart from the rest"                     -> 1
--   BULL_ISOLATION_YARD "high enough and heavy enough that what is in it stays in it"       -> 1
--   HITCHING_POST       "somewhere a beast can be tied and left standing"                   -> 1
--   OX_SHED             "wide enough to lead a yoked PAIR through"                          -> 2
--   YAK_SHELTER         "a heavy three-sided shelter ... built to break a wind"              -> 4
--   HORSE_STABLE        "TWO ROWS of partitioned stalls under one roof"                     -> 6
--   CATTLE_BYRE         "a roofed and walled house for cattle, deep enough in litter"       -> 6
--   PIG_STY             "a low walled run with a shelter at one end"                        -> 6
--   TETHER_LINE         "a picket line that holds stock together and quiet through the night"-> 6
--   TIMBER_BARN         "a dry, roomy outbuilding"                                          -> 6
--   ANIMAL_PEN          the Java-built ring of ground, the generic one                      -> 8
--   GOAT_FOLD           "a ring of hurdles set on open ground"                              -> 8
--   DUCK_HOUSE          "a low shelter floored above the wet with a ramp down to the water" -> 8
--   BROODER_SHELTER     "where A CLUTCH can be kept warm by their own crowding"             -> 10
--   POULTRY_COOP        "a low box of hurdle and bark"                                      -> 12
--   POULTRY_RUN         "a fenced STRETCH OF GROUND ... so the birds can scratch"           -> 20
--   WATERFOWL_RUN       "a fenced stretch taking in standing water"                         -> 20
--
-- WHAT WAS NOT DONE, AND WHY. DR-0027 refused an area budget for #77 on the grounds that it "would need a
-- number invented for 117 kinds" — and that refusal holds here, which is why this is NOT one. It is a head
-- count on the twenty kinds that declare they hold stock, it is the unit keepers actually use (a byre for six
-- cows), and the words above decided every number rather than a spreadsheet. A beast is a beast for this: a
-- SIZE rule already exists and is enforced separately (body_size_rank against shelters_up_to_size), so a
-- brooder box holding ten CHICKS cannot be filled with ten aurochs by this column — the size gate refuses that
-- before the count is reached.
--
-- DRAFT_ANIMAL_STALL IS LEFT OUT although its words ("wide enough for one beast and no wider") are the plainest
-- of the lot: it declares holds_an_animal_still rather than shelters_stock, because it is a work bay a beast is
-- led into to have its feet done, not where stock are kept. Counting it as room would let a keeper house a herd
-- in the place they pare hooves.

ALTER TABLE construction_kind
    ADD COLUMN IF NOT EXISTS holds_how_many INT;

ALTER TABLE construction_kind
    DROP CONSTRAINT IF EXISTS holds_how_many_is_a_real_number;
ALTER TABLE construction_kind
    ADD CONSTRAINT holds_how_many_is_a_real_number
    CHECK (holds_how_many IS NULL OR holds_how_many >= 1);

UPDATE construction_kind SET holds_how_many = v.n FROM (VALUES
    ('FARROWING_SHELTER', 1), ('FOALING_STALL', 1), ('NESTING_BOX', 1),
    ('SICK_ANIMAL_SHELTER', 1), ('BULL_ISOLATION_YARD', 1), ('HITCHING_POST', 1),
    ('OX_SHED', 2), ('YAK_SHELTER', 4),
    ('HORSE_STABLE', 6), ('CATTLE_BYRE', 6), ('PIG_STY', 6), ('TETHER_LINE', 6), ('TIMBER_BARN', 6),
    ('ANIMAL_PEN', 8), ('GOAT_FOLD', 8), ('DUCK_HOUSE', 8),
    ('BROODER_SHELTER', 10), ('POULTRY_COOP', 12),
    ('POULTRY_RUN', 20), ('WATERFOWL_RUN', 20)
) AS v(kind, n)
 WHERE construction_kind.project_kind = v.kind;

DO $$
DECLARE bad TEXT; n INT;
BEGIN
    -- 1. EVERY kind that says it holds stock says how many. A shelter with no number would read as unlimited,
    --    which is the defect this file exists to remove, and a future husbandry kind must not slip in that way.
    SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad
      FROM construction_kind WHERE shelters_stock AND holds_how_many IS NULL;
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V434: % declares shelters_stock and no room — every stock structure must say how many it holds', bad; END IF;

    -- 2. And NOTHING ELSE carries a number, because a count on a kind that holds no stock gates nothing and
    --    would be read by the crowding rule as room that does not exist.
    SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad
      FROM construction_kind WHERE holds_how_many IS NOT NULL AND NOT shelters_stock;
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V434: % holds no stock and yet declares room for some', bad; END IF;

    -- 3. The six whose own words say ONE BEAST are one, named individually. These are the ones the mechanic
    --    turns on: a farrowing shelter that quietly held six would make the crowding rule unreachable for the
    --    structures it matters most to, and nothing else in the schema records that a stall is for one animal.
    SELECT string_agg(project_kind, ', ' ORDER BY project_kind) INTO bad
      FROM construction_kind
     WHERE project_kind IN ('FARROWING_SHELTER','FOALING_STALL','NESTING_BOX',
                            'SICK_ANIMAL_SHELTER','BULL_ISOLATION_YARD','HITCHING_POST')
       AND holds_how_many <> 1;
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V434: % is described as holding one beast and holds more than one', bad; END IF;

    -- 4. A yoked pair is two. The ox shed's whole description is the width of a yoked pair, and the pair is the
    --    unit of draught work everywhere else in this game (#101).
    IF (SELECT holds_how_many FROM construction_kind WHERE project_kind='OX_SHED') <> 2
    THEN RAISE EXCEPTION 'V434: an ox shed is wide enough to lead a yoked pair through, so it holds two'; END IF;

    -- 5. Birds are kept in numbers no mammal house is. If a bird run ever held fewer than a byre, somebody has
    --    been filling the column in from the size tier — which runs the other way for poultry — rather than
    --    from what the structure is for.
    SELECT MIN(holds_how_many) INTO n FROM construction_kind
     WHERE project_kind IN ('POULTRY_RUN','WATERFOWL_RUN','POULTRY_COOP','BROODER_SHELTER');
    IF n <= (SELECT MAX(holds_how_many) FROM construction_kind WHERE project_kind IN ('CATTLE_BYRE','OX_SHED','HORSE_STABLE'))
    THEN RAISE EXCEPTION 'V434: a bird house must hold more than a cattle house (birds %, cattle %)',
        n, (SELECT MAX(holds_how_many) FROM construction_kind WHERE project_kind IN ('CATTLE_BYRE','OX_SHED','HORSE_STABLE')); END IF;

    -- 6. One number per kind that shelters stock, and no other kind. Counted so that
    --    a VALUES row misspelling a kind cannot pass silently — it would simply update nothing.
    SELECT COUNT(*) INTO n FROM construction_kind WHERE holds_how_many IS NOT NULL;
    IF n <> (SELECT COUNT(*) FROM construction_kind WHERE shelters_stock) THEN RAISE EXCEPTION
        'V434: % kinds carry a room count against % that shelter stock', n,
        (SELECT COUNT(*) FROM construction_kind WHERE shelters_stock); END IF;
END $$;
