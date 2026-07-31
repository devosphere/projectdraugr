-- V48: Thermal insulation, and garments worth making.
--
-- Clothing existed as items from V1 but never touched the body's heat balance:
-- the exposure model in ChroniclePhysiologyService read shelter and fire only.
-- A chronicle spawned in a shirt and trousers froze exactly as fast as one
-- standing naked, which is why every chronicle in testing died of hypothermia
-- on a 20C day.
--
-- insulation_value is "clo-like": roughly the percentage of convective heat loss
-- the garment prevents at its body position. Summed across worn items and capped
-- in the physiology service, so no stack of rags makes a chronicle invulnerable.

ALTER TABLE item_definition ADD COLUMN insulation_value SMALLINT NOT NULL DEFAULT 0
    CHECK (insulation_value BETWEEN 0 AND 60);

-- What a chronicle arrives wearing. Ordinary modern clothes: real, but thin.
UPDATE item_definition SET insulation_value = 12 WHERE item_key = 'arrival_shirt';
UPDATE item_definition SET insulation_value = 12 WHERE item_key = 'arrival_trousers';
UPDATE item_definition SET insulation_value =  4 WHERE item_key IN ('arrival_left_shoe','arrival_right_shoe');

-- Garments a chronicle can actually make from what the world provides. Hide and
-- fur are the warm ones; woven plant fiber is breathable but poor against cold.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('hide_coat',    'Hide coat',      'CLOTHING', 2400, 9000, FALSE, TRUE, 34),
('fur_cloak',    'Fur cloak',      'CLOTHING', 3100, 12000, FALSE, TRUE, 40),
('hide_leggings','Hide leggings',  'CLOTHING', 1500, 5200, FALSE, TRUE, 22),
('hide_boots',   'Hide boots',     'CLOTHING',  900, 2600, FALSE, TRUE, 14),
('fiber_tunic',  'Woven tunic',    'CLOTHING',  600, 2600, FALSE, TRUE, 10)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('hide_coat','TORSO','OUTER'),
('fur_cloak','TORSO','OUTER'),
('hide_leggings','WAIST','OUTER'),
('hide_boots','FOOT_LEFT','CLOTHING'), ('hide_boots','FOOT_RIGHT','CLOTHING'),
('fiber_tunic','TORSO','CLOTHING')
ON CONFLICT DO NOTHING;

-- The techniques that produce them, catalogued alongside the Phase 0 heritage set.
INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, proven_in, principle) VALUES
('hide_coat_sewing',    'Hide coat sewing',   'textiles','INTERMEDIATE','hide_coat',    'CUTTING','V48','Hide worn against wind holds far more heat than anything woven from plant fiber.'),
('fur_cloak_sewing',    'Fur cloak sewing',   'textiles','INTERMEDIATE','fur_cloak',    'CUTTING','V48','Fur left on the hide traps a layer of still air, which is what actually keeps a body warm.'),
('hide_leggings_sewing','Hide leggings sewing','textiles','INTERMEDIATE','hide_leggings','CUTTING','V48','Legs lose heat fast in wind; covering them matters nearly as much as the torso.'),
('hide_boot_sewing',    'Hide boot sewing',   'textiles','PRIMITIVE',   'hide_boots',   'CUTTING','V48','Feet in cold wet ground draw heat out of the whole body.'),
('fiber_tunic_weaving', 'Fiber tunic weaving','textiles','PRIMITIVE',   'fiber_tunic',  NULL,     'V48','Woven fiber turns rain and sun but does little against cold.')
ON CONFLICT (technique_key) DO NOTHING;
