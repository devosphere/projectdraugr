-- V189 — the wheel and the cart (EPIC #100, fourth draft-logistics slice). The travois drags on poles, the sledge on
-- runners — both slide the whole load along the ground, and the ground fights back. The wheel is the answer the old
-- world took an age to find: a cart rolls its load instead of dragging it, and a beast that could drag a sledge can
-- pull a far heavier cart because the wheels carry the weight, not the earth. This lays the wheel as a real made
-- thing and the cart that rides on two of them — the first rolling vehicle, and the biggest load-bed yet.
--
-- The cart is a genuine tech step: it cannot be built without first making its wheels (make_cart requires two
-- cart_wheels), and a wheel is itself shaped from planks and a hub. Being a registered draft_vehicle (V188), the cart
-- grants a tamed draft beast's haul exactly as the travois and sledge do; its bed (600 kg / 700 L) is the largest, so
-- the fullest cart wants the most beasts hitched.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('cart_wheel', 'Cart wheel', 'MATERIAL', 3000,  8000, TRUE,  FALSE, 0),
('cart',       'Cart',       'TOOL',     8000, 15000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('cart_wheel', 'TECHNIQUE', 'shaped from planks around a hub into a solid wheel'),
('cart',       'TECHNIQUE', 'framed on two wheels into a rolling load-bed')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_cart_wheel', 'Make a cart wheel', 'cart_wheel', 1, 1, NULL, FALSE, FALSE, 80, 'items', 'CRAFT',
 'make a cart wheel,cart wheel,make a wheel,shape a wheel,wheel,felloe',
 'You saw and dress the planks to a curve, peg them around a stout hub, and true the rim until the wheel runs round and does not wobble — the hardest small thing you have made.', 'VERIFIED', now()),
('make_cart', 'Make a cart', 'cart', 1, 1, NULL, FALSE, FALSE, 120, 'items', 'CRAFT',
 'make a cart,cart,build a cart,assemble a cart,frame a cart',
 'You frame a bed over an axle, set a trued wheel to each end, and lash and peg the whole together — a cart that rolls its load along instead of dragging it into the ground.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_cart_wheel', 'timber_plank', 2),
('make_cart_wheel', 'wooden_component', 1),
('make_cart', 'cart_wheel', 2),
('make_cart', 'wooden_component', 4),
('make_cart', 'fiber_cordage', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_cart_wheel', 'wheel'),
('make_cart', 'cart')
ON CONFLICT DO NOTHING;

-- The cart's bed is the largest load platform, and it is a rolling draft vehicle.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('cart', 600000, 700000)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO draft_vehicle (item_key) VALUES ('cart')
ON CONFLICT (item_key) DO NOTHING;
