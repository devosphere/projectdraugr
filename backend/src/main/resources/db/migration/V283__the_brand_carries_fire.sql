-- #75 — the brand carries fire.
--
-- The catalogue calls the firebrand "a wrapped brand to carry fire and ward off beasts". It did neither, and it
-- did not give light either. The light was fixed already; this migration is the carrying half.
--
-- Carrying fire itself was NOT missing — ember_transfer exists and is the honest way most of prehistory kept
-- fire, an ember nursed in a bundle. But it consumes the bundle, and the firebrand is the other way of doing the
-- same thing: an open flame walked from one hearth to the next. So this is a second method rather than a second
-- requirement bolted onto the first — adding firebrand to ember_transfer's requirements would have demanded BOTH
-- a bundle and a brand to do what either alone does.
--
-- It is easier than nursing an ember (difficulty 10 against 14) because a burning brand is already fire and needs
-- no coaxing, and harder than nothing because a brand gutters: it will not carry in the wet, which is what
-- requires_dry says, and is why the stone lantern cover earns its keep beside it.

INSERT INTO fire_method (method_key, display_name, difficulty, requires_daylight, requires_dry, era, principle)
VALUES ('brand_transfer', 'Carried brand', 10, FALSE, TRUE, 'PALEOLITHIC',
        'A brand lit at a fire that already burns and walked to where the next one is wanted. Open flame rather '
        || 'than a nursed ember: quicker and surer to set down, but it burns itself away as it travels and will '
        || 'not survive weather a hooded ember would.');

-- The brand is spent: it is set into the new fire and becomes it.
INSERT INTO fire_method_requirement (method_key, item_key, quantity, consumed)
VALUES ('brand_transfer', 'firebrand', 1, TRUE);

DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM fire_method_requirement WHERE method_key='brand_transfer';
  IF n <> 1 THEN RAISE EXCEPTION 'V283: carrying a brand must turn on the brand alone, not a kit'; END IF;

  IF NOT EXISTS (SELECT 1 FROM fire_method_requirement WHERE method_key='brand_transfer' AND item_key='firebrand' AND consumed) THEN
    RAISE EXCEPTION 'V283: the brand must be spent — it is set into the new fire and becomes it';
  END IF;

  -- ember_transfer must be left exactly as it was: the two are alternatives, not a combined requirement.
  IF (SELECT count(*) FROM fire_method_requirement WHERE method_key='ember_transfer') <> 1
     OR NOT EXISTS (SELECT 1 FROM fire_method_requirement WHERE method_key='ember_transfer' AND item_key='ember_bundle') THEN
    RAISE EXCEPTION 'V283: carrying an ember must still turn on the ember bundle alone';
  END IF;

  -- Carrying fire must remain the easiest way to have it: neither transfer may cost more than striking one.
  IF (SELECT difficulty FROM fire_method WHERE method_key='brand_transfer')
     >= (SELECT MIN(difficulty) FROM fire_method WHERE method_key IN ('bow_drill','hand_drill','fire_plough','fire_saw')) THEN
    RAISE EXCEPTION 'V283: walking a lit brand must be easier than making fire from nothing';
  END IF;
END $$;
