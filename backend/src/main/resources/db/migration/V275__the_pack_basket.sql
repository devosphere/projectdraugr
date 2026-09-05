-- V275 — the pack basket can be made, and carrying it on your back does what a back does (#95/#57).
--
-- `backpack_basket` is a CONTAINER in the catalogue, equippable to the BACK, with a Phase-0 technique
-- (`backpack_basket_weaving`) whose own principle line states the capability outright:
--
--     "Load carried on the back leaves the hands free and spreads weight across the frame."
--
-- Nothing produced it — no material process, no assembly, no Java intent — so there was no way in the world to
-- have one. And nothing would have come of having one: `carry_aid_bonus` holds five entries (burden frame, carry
-- pole, fibre sling, rope harness, shoulder yoke) and a pack basket was not among them, so the one thing it
-- exists to do was the one thing it could not do.
--
-- Distinct from the burden_basket, which IS makeable: that is a basket you carry, this is a basket you WEAR.
-- The difference is the two lengths of cordage that make shoulder straps of it, and the difference in play is
-- that the weight rides on your back instead of in your arms.

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('backpack_basket', 'TECHNIQUE', 'withies and vine woven onto a frame, with cordage strapping for the shoulders')
ON CONFLICT DO NOTHING;

-- "backpack basket" is the distinctive keyword: the incumbent weave_burden_basket carries "burden basket",
-- "pack basket" and a bare "weave", and the matcher takes the longest keyword — whole-word, so "pack basket"
-- cannot match inside "backpack". Category classifies CRAFT on "basket" (weight 2) over "weave" (PROCESS, 1),
-- which is also what satisfies the auditor's self-classify gate.
INSERT INTO material_process
    (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
     duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at)
VALUES
    ('weave_backpack_basket', 'Weave a backpack basket', 'backpack_basket', 1, 1, 'CUTTING', FALSE, FALSE,
     150, 'items', 'CRAFT',
     'backpack basket,back basket,pack frame basket,weave a backpack basket,shoulder basket',
     'You work withy and vine into a deep basket over a light frame, then cut and fit two lengths of cordage to it '
     || 'for shoulder straps. Shrugged on, it sits high against your back and leaves both hands free.',
     'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- Same stock as a burden basket, plus the cordage that makes straps of it. 8x80 + 10x60 + 2x110 = 1460g into a
-- 1400g basket, so the conservation gate is satisfied with the offcuts unaccounted, as elsewhere.
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
    ('weave_backpack_basket', 'plant_fiber', 8),
    ('weave_backpack_basket', 'vine', 10),
    ('weave_backpack_basket', 'fiber_cordage', 2)
ON CONFLICT DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
    ('weave_backpack_basket', 'basket'),
    ('weave_backpack_basket', 'backpack'),
    ('weave_backpack_basket', 'vine'),
    ('weave_backpack_basket', 'fiber')
ON CONFLICT DO NOTHING;

-- What wearing it is for. Less than a purpose-built burden frame (5000g/9000ml) — it is a basket with straps,
-- not a pack frame — but real, and the reason to carry loads on your back rather than in your arms.
INSERT INTO carry_aid_bonus (item_key, mass_bonus_grams, bulk_bonus_ml) VALUES
    ('backpack_basket', 3000, 6000)
ON CONFLICT DO NOTHING;
