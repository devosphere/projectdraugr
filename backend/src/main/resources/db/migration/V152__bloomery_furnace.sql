-- V152: the bloomery furnace — a proper smelting hearth (EPIC #180 heavy industry / furnace infrastructure).
--
-- The metal smelts (V144-V151) have driven their heat from an open fire. A bloomery — a clay-and-stone shaft that
-- holds and concentrates the heat and draught — smelts far better: the metal comes out cleaner and truer. This
-- adds a buildable bloomery_furnace and marks it as the workstation (station_kind) of every smelt and the
-- carburise, so working the ore at a furnace lifts the metal's grade the way a bench or loom eases bench work.
-- Ripple-safe by construction: station_kind only EASES a process (executeProcess.atStation), it never gates it —
-- a Chronicle with no furnace still smelts over a fire exactly as before, just without the furnace's finer result.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('bloomery_furnace', 'Bloomery furnace', 'FURNITURE', 90000, 120000, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bloomery_furnace', 'TECHNIQUE', 'a clay-and-stone smelting shaft raised and packed')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Built from clay and stone. 'make'/'raise' route to CRAFT; the furnace/bloomery subject keeps it distinct.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_bloomery_furnace', 'Raise a bloomery furnace', 'bloomery_furnace', 1,1, NULL, FALSE, FALSE, 180, 'construction', 'CRAFT',
 'make a bloomery furnace,make a smelting furnace,raise a bloomery furnace,raise a smelting furnace,build a bloomery furnace,lay up a bloomery,bloomery furnace,smelting furnace',
 'You raise a tall shaft of stone and pack it inside and out with clay, leaving a low arch to tap it and a hole for the draught — a bloomery that will hold a smelting heat far hotter and steadier than any open fire.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_bloomery_furnace', 'clay_lump', 6), ('make_bloomery_furnace', 'field_stone', 8)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_bloomery_furnace','furnace'), ('make_bloomery_furnace','bloomery')
ON CONFLICT DO NOTHING;

-- A furnace is rammed up in place from clay and earth dug at the site, so its mass legitimately exceeds the few
-- carried clay lumps and stones that face it — the same kind of unmodelled-source exemption as gather_ash / leach_lye.
UPDATE material_process SET conservation_exempt = TRUE,
    exempt_reason = 'A bloomery is rammed up in place from clay and earth dug at the site; its mass legitimately exceeds the carried clay lumps and facing stones.'
WHERE process_key = 'make_bloomery_furnace';

-- Mark the furnace as the workstation of the heat-metallurgy processes: at a furnace, the metal comes out finer.
UPDATE material_process SET station_kind = 'bloomery_furnace'
 WHERE process_key IN ('smelt_copper','smelt_tin','smelt_iron','alloy_bronze','carburise_iron_axe');
