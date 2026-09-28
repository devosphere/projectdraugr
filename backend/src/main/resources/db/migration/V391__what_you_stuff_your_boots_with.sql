-- #37 — "stuff my boots with dry grass" reached nothing, and the four things you would stuff them with sat in
-- the catalogue declaring an insulation value of zero.
--
-- Lining a garment with whatever is to hand is one of the oldest cold-weather answers there is, and until #709
-- it would not have mattered much: the body was warmed as though it stood in the lowlands wherever it was, so
-- a degree of insulation bought very little. Now that altitude, biome and shelter all reach the skin, the
-- difference between bare boots and boots packed with dry grass is a real difference in how long a Chronicle
-- can stand on a mountain.
--
-- The lining is a property of THE OBJECT, not of its kind: these boots are stuffed and those ones are not, and
-- the stuffing stays with them until they are lined again. That is why it is a column on item_instance and not
-- a second item_definition row for a "grass-lined boot" -- the catalogue already has grass_lined_bark_sandals,
-- which is a different SHOE rather than a shoe somebody stuffed.
--
-- What may be used for it is data, because there is no reason a new soft dry material added later should need
-- code to become stuffing.

CREATE TABLE lining_material (
    item_key         VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    adds_insulation  SMALLINT     NOT NULL,
    notes            TEXT         NOT NULL,
    CONSTRAINT lining_material_adds_check CHECK (adds_insulation BETWEEN 1 AND 8),
    CONSTRAINT lining_material_notes_check CHECK (length(notes) >= 30)
);

COMMENT ON TABLE lining_material IS
 'What a garment can be stuffed or lined with, and how much warmth it adds to THAT garment (#37). Read by the '
 'lining action and, through item_instance.lining_bonus, by the body''s insulation sum.';

ALTER TABLE item_instance ADD COLUMN lining_bonus SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE item_instance ADD CONSTRAINT item_instance_lining_bonus_check CHECK (lining_bonus BETWEEN 0 AND 8);

COMMENT ON COLUMN item_instance.lining_bonus IS
 'Warmth this particular garment has been given by stuffing or lining it (#37), added to its kind''s '
 'insulation_value when the body reckons what it is wearing. Capped so no amount of grass makes a boot a fur.';

INSERT INTO lining_material (item_key, adds_insulation, notes) VALUES
 ('dry_grass_bundle', 3, 'Dry grass packed in around the foot: the oldest answer there is, and it works because it traps air rather than because it is warm.'),
 ('moss_bundle',      3, 'Moss packed into a boot or a mitten. Soft, plentiful, and it keeps its loft better than grass when it is damp.'),
 ('sphagnum_moss',    4, 'Bog moss, which holds its loft even wet and was used for exactly this as well as for dressing wounds.'),
 ('wool_tuft',        4, 'Raw wool teased out and packed in. It keeps warming after it has taken damp, which grass does not.'),
 ('shed_fur_tuft',    5, 'Shed fur gathered in handfuls and packed in — the warmest of the loose stuffings, and the hardest to gather enough of.')
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('LINE_GARMENT', NULL, 0, FALSE, FALSE,
  'Working on a thing already in your hands. Stuffing a boot, lining a hood or packing a mitten takes material you are carrying and puts it inside a garment you are wearing; the ground is neither dug, cut, burned nor built on.',
  3, 1, 'FINE_MOTOR', 20)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM lining_material;
    IF n < 5 THEN RAISE EXCEPTION 'V391: expected at least five stuffings, found %', n; END IF;

    -- Every stuffing must be a thing a Chronicle can actually come by, or this is a table of wishes.
    SELECT COUNT(*) INTO n FROM lining_material lm
     WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = lm.item_key);
    IF n > 0 THEN RAISE EXCEPTION 'V391: % stuffing(s) name no real item', n; END IF;

    -- And none of them may be a thing that is already warm on its own: these are LOOSE materials packed inside
    -- something else. A fur cloak is not stuffing.
    SELECT COUNT(*) INTO n FROM lining_material lm JOIN item_definition d ON d.item_key = lm.item_key
     WHERE d.insulation_value > 0;
    IF n > 0 THEN RAISE EXCEPTION 'V391: a garment that is warm in itself is not a stuffing (% of them)', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='LINE_GARMENT';
    IF n <> 1 THEN RAISE EXCEPTION 'V391: LINE_GARMENT must have exactly one impact card, found %', n; END IF;
END $$;
