-- Regression: first-era nut foods (V86, M1 #75 slice).
--
-- Pins the acquire → use journey for the nut family and the honest grounding of the acorn: four nuts are
-- directly-edible FOOD (nourished through EAT), while the acorn is a MATERIAL whose ONLY use is being leached
-- and ground into acorn flour — so an inedible-raw plant is never silently turned into nourishment. Read-only.

BEGIN;

DO $$
DECLARE
    edible text[] := ARRAY['hazelnut','walnut','chestnut','pine_nut'];
    k text;
    m_acorn int; m_flour int;
BEGIN
    -- 1. Every nut is obtainable through an ecological source (flora drop), not injected.
    FOREACH k IN ARRAY (edible || ARRAY['acorn']) LOOP
        IF NOT EXISTS (SELECT 1 FROM item_source WHERE item_key = k AND source_kind = 'FLORA_DROP') THEN
            RAISE EXCEPTION 'REGRESSION: % has no FLORA_DROP source', k;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM flora_drop WHERE item_key = k) THEN
            RAISE EXCEPTION 'REGRESSION: % is dropped by no flora', k;
        END IF;
    END LOOP;

    -- 2. The four nuts are edible FOOD — their use is EAT.
    FOREACH k IN ARRAY edible LOOP
        IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = k AND category = 'FOOD') THEN
            RAISE EXCEPTION 'REGRESSION: % is not FOOD', k;
        END IF;
    END LOOP;

    -- 3. The acorn is NOT directly-edible FOOD: it is a MATERIAL, so EAT cannot nourish on it raw.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'acorn' AND category = 'MATERIAL') THEN
        RAISE EXCEPTION 'REGRESSION: raw acorn must be a MATERIAL, not directly-edible FOOD';
    END IF;

    -- 4. The acorn HAS a verified use: leached and ground into acorn_flour, which IS edible FOOD.
    IF NOT EXISTS (SELECT 1 FROM material_process mp JOIN material_process_input mi ON mi.process_key = mp.process_key
                   WHERE mi.item_key = 'acorn' AND mp.output_item_key = 'acorn_flour' AND mp.review_state = 'VERIFIED') THEN
        RAISE EXCEPTION 'REGRESSION: acorn has no verified leach-to-flour use';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'acorn_flour' AND category = 'FOOD') THEN
        RAISE EXCEPTION 'REGRESSION: acorn_flour must be edible FOOD';
    END IF;

    -- 5. Leaching conserves mass: the flour weighs less than the acorns it takes.
    SELECT di.unit_mass_grams * mi.quantity, dout.unit_mass_grams * mp.output_max
      INTO m_acorn, m_flour
      FROM material_process mp
      JOIN material_process_input mi ON mi.process_key = mp.process_key
      JOIN item_definition di ON di.item_key = mi.item_key
      JOIN item_definition dout ON dout.item_key = mp.output_item_key
     WHERE mp.process_key = 'leach_acorn_flour';
    IF m_flour >= m_acorn THEN
        RAISE EXCEPTION 'REGRESSION: acorn flour (% g) is not lighter than the acorns it takes (% g)', m_flour, m_acorn;
    END IF;

    RAISE NOTICE 'PASS: 4 edible nuts + acorn — obtainable, nuts edible via EAT, raw acorn inedible until leached to flour, mass-conserving (V86 #75)';
END $$;

ROLLBACK;
