INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('arrival_shirt','Everyday shirt','CLOTHING',260,900,FALSE,TRUE),
('arrival_trousers','Everyday trousers','CLOTHING',480,1300,FALSE,TRUE),
('arrival_left_shoe','Left everyday shoe','CLOTHING',420,950,FALSE,TRUE),
('arrival_right_shoe','Right everyday shoe','CLOTHING',420,950,FALSE,TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('arrival_shirt','TORSO','CLOTHING'),
('arrival_trousers','WAIST','CLOTHING'),
('arrival_left_shoe','FOOT_LEFT','OUTER'),
('arrival_right_shoe','FOOT_RIGHT','OUTER')
ON CONFLICT DO NOTHING;
