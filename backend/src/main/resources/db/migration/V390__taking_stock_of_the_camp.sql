-- #37 — "Structures you raised stand here."
--
-- One sentence for a windbreak, and the same sentence for a byre, a well, a latrine and a drying rack standing
-- together. construction_kind has carried every one of their display names since the table existed, and perception
-- named none of them. Nor did it ever say what state any of them was in, though integrity_percent decides whether
-- a shelter still shelters, a pen still holds and a latrine still takes anything.
--
-- It also counted a RUIN as standing: the survey asked for state='COMPLETED' and never for integrity, so a
-- collapsed hut read exactly like a sound one. A Chronicle could walk into their own camp and be told everything
-- was fine.
--
-- And "take stock of the camp" -- which is what a person says when they want exactly this -- reached nothing at
-- all. TAKE_STOCK_OF_CAMP is that: read-only, fuller than the survey, naming the ruins the survey now passes over
-- and the work still unfinished with how far along it is. None of it is information the world did not have. It is
-- the world's own record, said out loud.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('TAKE_STOCK_OF_CAMP', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking, listening, smelling, reading, measuring, following sign and walking your own ground to account for it all take the world exactly as it is and put nothing back.',
  3, 0, 'ATTENTION', 15)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='TAKE_STOCK_OF_CAMP';
    IF n <> 1 THEN RAISE EXCEPTION 'V390: TAKE_STOCK_OF_CAMP must have exactly one impact card, found %', n; END IF;

    -- Walking your own camp and counting it marks nothing. If a later edit gives this a footprint, that is a claim
    -- that looking at a thing damages it, and it should be argued for.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='TAKE_STOCK_OF_CAMP' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V390: taking stock of your own camp must leave it exactly as it was'; END IF;

    -- What this reads must exist, or the report is a sentence about nothing.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_kind' AND column_name='display_name') THEN
        RAISE EXCEPTION 'V390: construction_kind.display_name is what the camp is named from';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_project' AND column_name='integrity_percent') THEN
        RAISE EXCEPTION 'V390: integrity_percent is what tells a sound structure from a ruin';
    END IF;
END $$;
