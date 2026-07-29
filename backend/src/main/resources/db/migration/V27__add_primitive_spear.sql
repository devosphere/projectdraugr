INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('primitive_spear','Primitive spear','TOOL',1100,1800,FALSE,TRUE)
ON CONFLICT (item_key) DO NOTHING;
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('primitive_spear','HAND_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;
