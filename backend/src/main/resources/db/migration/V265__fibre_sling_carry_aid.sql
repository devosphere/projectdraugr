-- V265 — a carry sling that actually carries. fibre_sling is equippable CLOTHING with no insulation, no water
-- resistance, no tool or weapon profile, and no code reading it anywhere: wearing one did precisely nothing. It is
-- a carrying strap, so it joins the carry aids that already work — burden_frame, carry_pole, rope_harness,
-- shoulder_yoke — which add to SUSTAINED mass and bulk while worn.
--
-- Sized honestly against those: a shoulder yoke bears +10kg and a rope harness +4kg, so a light fibre strap earns
-- +2kg and a little bulk. As with every carry aid, the single-object lift limit is untouched — a sling spreads a
-- load across the body, it does not make one heavy thing easier to heave.
INSERT INTO carry_aid_bonus (item_key, mass_bonus_grams, bulk_bonus_ml) VALUES
('fibre_sling', 2000, 1500)
ON CONFLICT (item_key) DO NOTHING;
