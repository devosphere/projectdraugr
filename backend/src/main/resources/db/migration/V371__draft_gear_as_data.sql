-- #106 (epic #100) — draft gear as data, sized to the body it was made for.
--
-- WHAT WAS WRONG. The gear that hitches a beast to a load was two string literals in Java:
--
--     ti.item_key IN ('draft_harness','draft_yoke')
--
-- and how much it helped was one constant, DRAFT_FATIGUE_HARNESSED. Everything else about draft work is already
-- data — `draft_species` says who can pull and how much, `draft_vehicle` says what can be pulled, `stock_cover`
-- says what keeps a beast warm. The gear between them was the one link that could not be extended without
-- editing Java, which is why none of #106's catalogue of yokes, collars and pads could be added: a
-- `double_ox_yoke` or a `buffalo_yoke` had nowhere to exist.
--
-- AND IT FIT EVERYTHING. One `draft_yoke` sat as well on a donkey as on an ox. #106's first acceptance
-- criterion is that gear be "species/anatomy/size/working-role compatible", with "no generic animal equipment
-- class" bypassing the check. A neck yoke is a cattle thing and a collar harness is an equine thing, and until
-- now the world could not tell them apart.
--
-- THE RULE IS V369's, REUSED. `fits_up_to_size` is the largest `wildlife_species.size_tier` the gear goes on,
-- and it goes on that body and every smaller one — the same ceiling, compared with the same `body_size_rank()`
-- function, as a shelter holding the bodies that fit in it. No second vocabulary, which is the trap V313's
-- wearable positions fell into.
--
-- NOBODY LOSES A CAPABILITY. The yoke keeps the HUGE ceiling it effectively had, so the three HUGE draft
-- species (aurochs, ox, water buffalo) are geared exactly as before. The harness drops to LARGE, which is what
-- a collar harness is: every LARGE and MEDIUM draft species still has it, and the HUGE ones still have the yoke.
-- Every one of the eleven draft species keeps gear that fits.

CREATE TABLE draft_gear (
    item_key          VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    fits_up_to_size   VARCHAR(10)  NOT NULL CHECK (fits_up_to_size IN ('TINY','SMALL','MEDIUM','LARGE','HUGE')),
    eases_fatigue_to  SMALLINT     NOT NULL CHECK (eases_fatigue_to BETWEEN 1 AND 20),
    notes             TEXT         NOT NULL CHECK (length(notes) >= 40)
);

COMMENT ON TABLE draft_gear IS
  '#106: the gear that hitches a beast to a load. Was two string literals in Java beside a single constant, so no yoke, collar or pad could be added without editing code and every piece fit every animal. fits_up_to_size is V369''s ceiling, read with the same body_size_rank().';

COMMENT ON COLUMN draft_gear.eases_fatigue_to IS
  '#106: fatigue a beast wearing this takes per bout of work, in place of the ungeared 20. Lower is better gear; the floor of 1 keeps a bout from ever being free.';

INSERT INTO draft_gear (item_key, fits_up_to_size, eases_fatigue_to, notes) VALUES
 ('draft_yoke',    'HUGE',  12,
  'A neck yoke: a shaped beam across the shoulders of cattle, which is what carries a load on an animal with that neck. Sits on anything up to an ox.'),
 ('draft_harness', 'LARGE', 12,
  'A collar harness: padding at the shoulder with traces back to the load, which is how an equine pulls. Made for a horse, a donkey or a yak, and not for the neck of an ox.');

-- The guard. Gear added later has to say what it goes on and what it is worth, the same way V369 made a stock
-- shelter say which bodies fit in it. A table of names that all behave alike is the Java constant again.
DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM draft_gear;
    IF n < 2 THEN RAISE EXCEPTION 'V371: expected the yoke and the harness, found %', n; END IF;
    SELECT COUNT(*) INTO n FROM draft_gear g
      WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = g.item_key);
    IF n > 0 THEN RAISE EXCEPTION 'V371: % piece(s) of draft gear name no catalogue item', n; END IF;
END $$;
