-- V194 — the pack-saddle (EPIC #100 / #102 draft equipment). The travois, sledge, and cart all DRAG a load behind a
-- beast. But a beast can also CARRY — a pack-saddle strapped to its back, panniers slung either side, the load riding
-- on the animal itself over ground no dragged frame could cross (a steep or broken path where a cart would founder).
-- It carries less than a dragged bed (a back bears less than runners slide), but it goes where wheels cannot.
--
-- Modelled on the proven draft-vehicle pattern: a pack-saddle is a registered draft_vehicle with a container bed, so
-- a tamed draft beast wearing one adds its haul (V186) and the panniers hold the load (V187) — the same welfare and
-- forage rules apply. Its bed is the smallest of the draft vehicles (120 kg), the honest limit of a back over a bed.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('pack_saddle', 'Pack-saddle', 'TOOL', 2000, 9000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('pack_saddle', 'TECHNIQUE', 'framed and strapped to sit a beast''s back with panniers either side')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_pack_saddle', 'Make a pack-saddle', 'pack_saddle', 1, 1, NULL, FALSE, FALSE, 70, 'items', 'CRAFT',
 'make a pack saddle,pack saddle,packsaddle,make a pack-saddle,pack-saddle,make panniers,pack frame',
 'You shape a light frame to sit a beast''s back without galling it, pad it, and rig a pannier to hang either side — a saddle to carry a load where no dragged frame could go.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_pack_saddle', 'wooden_component', 3),
('make_pack_saddle', 'fiber_cordage', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_pack_saddle', 'saddle'),
('make_pack_saddle', 'panniers')
ON CONFLICT DO NOTHING;

-- The panniers hold a back's worth — less than a dragged bed.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('pack_saddle', 120000, 180000)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO draft_vehicle (item_key) VALUES ('pack_saddle')
ON CONFLICT (item_key) DO NOTHING;
