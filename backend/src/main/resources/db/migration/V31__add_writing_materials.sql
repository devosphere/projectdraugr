-- Writing needs a medium to write WITH and a medium to write ON. These are the
-- first primitive-era versions: charcoal (implement) and a bark sheet (surface).
-- Animal hide (already defined) also serves as a writing surface.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('charcoal','Charcoal','MATERIAL',30,40,FALSE,FALSE),
('bark_sheet','Bark sheet','MATERIAL',120,400,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
