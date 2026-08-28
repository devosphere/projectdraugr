-- V186 — animal-assisted logistics, first step (EPIC #100: draft/pack/sledge/haulage). Taming exists (a bonded beast
-- becomes an owned world_object, #45), and a Chronicle's own carry is capped by chronicle_carry_capacity with equipped
-- carry-aids adding a bonus (#57). But a tamed animal could only ever give a yield (milk/wool); it could not WORK. A
-- settled people's first machine was the draft animal dragging a load it could never have been carried on a back —
-- the travois, two poles lashed into a drag frame, hitched behind an ox or a deer.
--
-- This lays that first step: a craftable travois, and a table of which tamed species can pull one and how much haul it
-- adds. The haul bonus is wired into the carry-capacity computation (PhysicalItemService.loadState): a Chronicle who
-- has BOTH a travois and a TAMED draft-capable animal hauls far beyond their own back — the animal drags the frame.
-- Purely additive (a Chronicle with neither is unchanged), so it lifts no existing load limit by accident.
--
-- Deferred to later #100 slices: the load riding physically on the travois (not the Chronicle), animal fatigue/welfare
-- under draft, terrain/route effects, and the heavier vehicles (sledge, cart) that a yoke and a wheel bring.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('travois', 'Travois', 'TOOL', 2800, 9000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('travois', 'TECHNIQUE', 'lashed from poles and cordage into a drag frame')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_travois', 'Make a travois', 'travois', 1, 1, NULL, FALSE, FALSE, 50, 'items', 'CRAFT',
 'make a travois,travois,drag frame,build a travois,lash a travois,make a drag frame',
 'You lash two long poles into a narrow A and web the span between with cordage — a drag frame to trail behind a beast, carrying what no back could bear.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_travois', 'wooden_component', 4),
('make_travois', 'fiber_cordage', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_travois', 'travois'),
('make_travois', 'frame')
ON CONFLICT DO NOTHING;

-- Which tamed species can pull a travois, and the haul each adds to its handler's capacity while a travois is to hand.
-- Sized to body: the HUGE aurochs pulls most; the LARGE deer and reindeer less; small tamed beasts do not pull at all.
CREATE TABLE draft_species (
    species_key      VARCHAR(60) PRIMARY KEY,
    haul_bonus_grams INTEGER NOT NULL CHECK (haul_bonus_grams > 0),
    bulk_bonus_ml    INTEGER NOT NULL CHECK (bulk_bonus_ml > 0)
);

INSERT INTO draft_species (species_key, haul_bonus_grams, bulk_bonus_ml) VALUES
('aurochs',   250000, 350000),
('elk',       140000, 200000),
('red_deer',  110000, 160000),
('reindeer',  120000, 170000)
ON CONFLICT (species_key) DO NOTHING;
