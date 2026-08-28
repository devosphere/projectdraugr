-- V196 — the mordant (EPIC #171 / #177 textile finishing, the last piece). Cloth could be dyed — ochre ground to
-- pigment and worked into wetted cloth (V104) — but the colour was fugitive: it sat on the fibre and washed and faded,
-- because nothing FIXED it. A mordant is the fixative the old dyers could not do without: a bath of tannin leached from
-- bark bites the dye into the fibre so the colour holds fast against water and wear. This lays it — bark (stripped
-- from any tree) leached into a tannin mordant, and a mordant-dyeing that fixes the pigment colourfast into the cloth.
-- With this, #177's finishing is complete: dyes (V104), mordants (here), waterproofing (V143/V96), washing (WASH),
-- drying, and fulling (felt) — the textile road from fibre to finished, coloured, weatherable cloth is whole.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('tannin_mordant', 'Tannin mordant', 'MATERIAL', 40, 100, TRUE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('tannin_mordant', 'TECHNIQUE', 'leached from tree bark as a dye fixative')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('prepare_tannin_mordant', 'Leach a tannin mordant', 'tannin_mordant', 1, 2, NULL, FALSE, TRUE, 45, 'items', 'PROCESS',
 'leach tannin,tannin mordant,leach the bark,mordant from bark,prepare a mordant,leach a mordant,make a mordant',
 'You steep the stripped bark in water and let it stand until the tan darkens the bath — a tannin mordant to bite the dye fast into the cloth.', 'VERIFIED', now()),
('mordant_dye_cloth', 'Dye cloth fast with a mordant', 'dyed_cloth', 1, 1, NULL, FALSE, TRUE, 50, 'items', 'PROCESS',
 'dye with a mordant,mordant dye,mordant the cloth,fix the dye,colourfast dye,colorfast dye,fast dye,set the dye fast,dye the cloth with a mordant,dye the cloth with the mordant',
 'You work the cloth through the tannin bath first, then the pigment — the mordant bites the colour into the fibre so it strikes deep and holds fast, where a plain dyeing would have washed pale.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('prepare_tannin_mordant', 'bark_sheet', 2),
('mordant_dye_cloth', 'pigment', 1),
('mordant_dye_cloth', 'tannin_mordant', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- The mordant-dyeing takes the same cloths the plain dyeing does.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('mordant_dye_cloth', 'cloth', 'wool_cloth', 1),
('mordant_dye_cloth', 'cloth', 'linen_cloth', 1),
('mordant_dye_cloth', 'cloth', 'felt_sheet', 1),
('mordant_dye_cloth', 'cloth', 'textile_material', 1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('prepare_tannin_mordant', 'mordant'),
('mordant_dye_cloth', 'mordant')
ON CONFLICT DO NOTHING;

-- 'dye' is a genuine process verb but was never registered as a PROCESS category-term (the original dye_cloth predates
-- the self-classify gate); register it so both the mordant-dyeing and the plain dyeing classify against their category.
INSERT INTO category_term (category_key, term, weight) VALUES ('PROCESS', 'dye', 2)
ON CONFLICT (category_key, term) DO NOTHING;
