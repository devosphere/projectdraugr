-- V76: carrying aids (M1 #57, EPIC #54) — a pole, yoke, harness, or pack frame lets you bear more.
--
-- Carry capacity has been a fixed property of the body (chronicle_carry_capacity + the load-conditioning
-- mastery). A carrying aid, once WORN/HELD, distributes weight or adds volume so the Chronicle can haul more
-- than bare arms allow. Each aid declares its bonus in carry_aid_bonus; loadState adds the bonus of every
-- EQUIPPED aid to the mass/bulk capacity. Aids are ordinary craftable, equippable objects — nothing teleports,
-- and the bonus applies only while the aid is actually equipped.

-- 1. The bonus each aid grants while equipped.
CREATE TABLE carry_aid_bonus (
    item_key         VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    mass_bonus_grams INTEGER NOT NULL DEFAULT 0 CHECK (mass_bonus_grams >= 0),
    bulk_bonus_ml    INTEGER NOT NULL DEFAULT 0 CHECK (bulk_bonus_ml >= 0)
);

-- 2. The aid objects.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('carry_pole',    'Carry pole',     'TOOL', 800,  3000, FALSE, TRUE, 0),
('shoulder_yoke', 'Shoulder yoke',  'TOOL', 1500, 5000, FALSE, TRUE, 0),
('rope_harness',  'Rope harness',   'TOOL', 300,  1200, FALSE, TRUE, 0),
('burden_frame',  'Burden frame',   'TOOL', 1200, 6000, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- 3. How each is worn/held.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('carry_pole',   'HAND_RIGHT','CARRIED'), ('carry_pole','HAND_LEFT','CARRIED'),
('shoulder_yoke','BACK',      'ATTACHED'),
('rope_harness', 'TORSO',     'ATTACHED'),
('burden_frame', 'BACK',      'CARRIED')
ON CONFLICT DO NOTHING;

-- 4. The bonuses (mass distributes over the frame/yoke; the frame and harness also add some bulk).
INSERT INTO carry_aid_bonus (item_key, mass_bonus_grams, bulk_bonus_ml) VALUES
('carry_pole',    6000,  0),
('shoulder_yoke', 10000, 0),
('rope_harness',  4000,  2000),
('burden_frame',  5000,  9000)
ON CONFLICT (item_key) DO NOTHING;

-- 5. Craft procedures (CRAFT, obtainable first-era stock, output mass < input mass).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_carry_pole',   'Make a carry pole',   'carry_pole',   1, 1, 'CUTTING', FALSE, FALSE, 40, 'logistics', 'CRAFT', 'carry pole,carrying pole,shoulder pole,make a carry pole,make a carrying pole', 'You choose a straight, springy pole and trim it smooth, notching the ends so a load will sit and not slide.', 'VERIFIED', now()),
('make_shoulder_yoke','Make a shoulder yoke','shoulder_yoke',1, 1, 'CUTTING', FALSE, FALSE, 70, 'logistics', 'CRAFT', 'shoulder yoke,carrying yoke,make a yoke,make a shoulder yoke,shape a yoke', 'You shape a broad yoke to sit across the shoulders, hollowing it to the neck and fitting cord loops for two loads.', 'VERIFIED', now()),
('make_rope_harness', 'Make a rope harness', 'rope_harness', 1, 1, 'CUTTING', FALSE, FALSE, 45, 'logistics', 'CRAFT', 'rope harness,carrying harness,make a harness,make a rope harness,tumpline', 'You knot cordage into a harness with a brow-band and shoulder straps, so a pack rides high and close on the back.', 'VERIFIED', now()),
('lash_burden_frame', 'Lash a burden frame', 'burden_frame', 1, 1, 'CUTTING', FALSE, FALSE, 80, 'logistics', 'CRAFT', 'burden frame,pack frame,carrying frame,lash a burden frame,make a burden frame,make a pack frame', 'You lash a light rigid frame to carry on the back, so an awkward, bulky load rides as one steady weight.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

-- 6. Inputs.
INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_carry_pole',    'dry_branch', 2), ('make_carry_pole',    'fiber_cordage', 1),
('make_shoulder_yoke', 'timber_plank',1), ('make_shoulder_yoke','fiber_cordage', 1),
('make_rope_harness',  'fiber_cordage',4),
('lash_burden_frame',  'dry_branch', 3), ('lash_burden_frame',  'fiber_cordage', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- 7. Subjects.
INSERT INTO process_subject (process_key, subject_term) VALUES
('make_carry_pole','pole'),
('make_shoulder_yoke','yoke'),
('make_rope_harness','harness'), ('make_rope_harness','tumpline'),
('lash_burden_frame','frame'), ('lash_burden_frame','burden'), ('lash_burden_frame','pack')
ON CONFLICT DO NOTHING;
