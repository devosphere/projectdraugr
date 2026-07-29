CREATE TABLE chronicle_carry_capacity (
    chronicle_id UUID PRIMARY KEY REFERENCES chronicle(id),
    sustained_mass_grams INTEGER NOT NULL DEFAULT 25000 CHECK (sustained_mass_grams > 0),
    direct_bulk_ml INTEGER NOT NULL DEFAULT 18000 CHECK (direct_bulk_ml > 0),
    maximum_single_lift_grams INTEGER NOT NULL DEFAULT 40000 CHECK (maximum_single_lift_grams >= sustained_mass_grams)
);
INSERT INTO chronicle_carry_capacity (chronicle_id) SELECT id FROM chronicle ON CONFLICT DO NOTHING;
