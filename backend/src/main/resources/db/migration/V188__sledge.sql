-- V188 — the sledge, and a draft-vehicle registry (EPIC #100, third draft-logistics slice). The travois (V186/V187)
-- was the first draft vehicle: a beast drags the frame, the load rides on it. A sledge is its heavier kin — a floored
-- drag with runners that bears far more than a travois' poles, the load-bed a settled people hauled timber, stone, and
-- the harvest on before the wheel. It works the same way: hitched behind a draft beast, its bed carries the cargo.
--
-- This also lifts the draft-haul wiring out of a hard-coded 'travois' and into a draft_vehicle registry, so any vehicle
-- listed here lets a tamed draft beast add its haul (PhysicalItemService.loadState now reads the registry). The travois
-- is backfilled in; the sledge joins it. Future vehicles (cart, wagon) are then pure data. A sledge's bed holds more
-- than a travois (400 kg / 500 L), so more beasts hitched — the haul sums across every tamed draft bond — move a
-- fuller sledge; one beast fills only part of it, which is the honest limit of a single ox on a loaded sledge.

CREATE TABLE draft_vehicle (
    item_key VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key)
);

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('sledge', 'Sledge', 'TOOL', 4200, 12000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('sledge', 'TECHNIQUE', 'built from a floored bed on runners, lashed and pegged')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_sledge', 'Make a sledge', 'sledge', 1, 1, NULL, FALSE, FALSE, 90, 'items', 'CRAFT',
 'make a sledge,sledge,drag sled,build a sledge,make a sled,lash a sledge',
 'You shape two long runners, floor a bed of split timber across them, and lash and peg the whole into a low, strong drag-sled — a bed to haul what no back or travois could.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_sledge', 'wooden_component', 6),
('make_sledge', 'fiber_cordage', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_sledge', 'sledge'),
('make_sledge', 'sled')
ON CONFLICT DO NOTHING;

-- The sledge's bed holds more than the travois' poles.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('sledge', 400000, 500000)
ON CONFLICT (item_key) DO NOTHING;

-- Register both draft vehicles; loadState reads this to grant a tamed draft beast's haul when one is to hand.
INSERT INTO draft_vehicle (item_key) VALUES ('travois'), ('sledge')
ON CONFLICT (item_key) DO NOTHING;
