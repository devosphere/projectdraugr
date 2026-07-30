-- Furniture is crafted and set down at a place, not carried on the body: a work
-- surface, a seat, a shelf to hold records. These become world objects fixed to a
-- location. The stone shelf (the chronicle's archive) also holds documents.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('wooden_desk','Wooden desk','FURNITURE',18000,120000,FALSE,FALSE),
('wooden_chair','Wooden chair','FURNITURE',7000,70000,FALSE,FALSE),
('stone_shelf','Stone slab shelf','FURNITURE',42000,150000,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
