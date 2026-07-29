INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('stone_knife','Stone knife','TOOL',240,350,FALSE,TRUE),
('stone_hammer','Stone hammer','TOOL',950,1100,FALSE,TRUE),
('primitive_pickaxe','Primitive pickaxe','TOOL',1800,2200,FALSE,TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('stone_knife','HAND_RIGHT','ATTACHED'),
('stone_hammer','HAND_RIGHT','ATTACHED'),
('primitive_pickaxe','HAND_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;
