-- V71: Fishing nets as craftable objects (GitHub #36, #43, #44).
--
-- A net woven from processed fibre cordage was catalogued knowledge but had no runnable make-route, so
-- "weave a fishing net" fell through to water-observation or a generic no-effect (#36/#43). Nets are a core
-- first-era craft — peers of the basket, spear, and stone tools — so they are handled as explicit craft
-- intents in ChronicleActionService (CRAFT_NET), exactly like those. This migration adds only the finished
-- objects and how they may be carried; the recipe (cordage -> mesh -> net) and its gates live in
-- PhysicalItemService.craftFishingNet, which consumes reachable cordage and produces the object below.
--
-- Two variants (#44): a full drag/gill fishing net (mesh only) and a hoop landing net (mesh on a bent frame).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('fishing_net', 'Woven fishing net', 'TOOL', 700, 5000, FALSE, TRUE, 0),
('landing_net', 'Hoop landing net',  'TOOL', 420, 2600, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

-- How they are worn/carried when equipped: a fishing net is slung on the back; a landing net is held.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fishing_net', 'BACK',       'CARRIED'),
('landing_net', 'HAND_RIGHT', 'CARRIED'),
('landing_net', 'HAND_LEFT',  'CARRIED')
ON CONFLICT DO NOTHING;
