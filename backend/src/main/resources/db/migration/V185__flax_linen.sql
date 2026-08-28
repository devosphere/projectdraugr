-- V185 — flax → linen: the plant-fibre road to woven cloth (EPIC #171 / story #173 retting & scutching, #174/#175
-- the plant side of spinning & weaving). The wool road to cloth is whole — shear → spin_wool_yarn → weave_wool_cloth
-- → dye — but the plant road dead-ended: flax_stalk (gathered in grassland) and the bast fibres only ever reached
-- cordage, never thread and never cloth, and textile_material sat in the catalogue with a note that it "awaits a
-- fiber-processing step beyond fiber_cordage". This lays that step: the old craft of turning a flax stalk into linen.
--
-- The chain: gather flax → RET it in standing water until the woody boon rots free of the fibre → SCUTCH and heckle
-- the retted straw, breaking away the shive and combing the long line fibre clean → SPIN the line into linen thread
-- → WEAVE the thread into linen cloth on the loom (the loom eases it, as it does wool) → SEW the cloth into a linen
-- shift to wear, and the same cloth takes dye by the existing dye_cloth path. Woven plant cloth finally has a body to
-- clothe, mirroring the wool chain step for step (same masses, so the matter-conservation gate holds).
--
-- All data: retting/scutching/spinning/weaving route through the existing PROCESS_MATERIAL dispatch by keyword (ret,
-- dress, spin, weave are PROCESS category-terms); the shift is a CRAFT like make_reed_hat, and is named a "shift"
-- (not tunic/coat/cloak) so it falls to the material-process craft, not the Java CRAFT_GARMENT path.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('retted_flax', 'Retted flax',  'MATERIAL', 300, 1200, TRUE,  FALSE, 0),
('line_flax',   'Line flax',    'MATERIAL',  80,  400, TRUE,  FALSE, 0),
('linen_thread','Linen thread', 'MATERIAL', 120,  300, TRUE,  FALSE, 0),
('linen_cloth', 'Linen cloth',  'MATERIAL', 400, 1600, FALSE, FALSE, 0),
('linen_shift', 'Linen shift',  'CLOTHING', 700, 2400, FALSE, TRUE,  2)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('retted_flax', 'TECHNIQUE', 'retted from flax stalks in standing water'),
('line_flax',   'TECHNIQUE', 'scutched and heckled clean from retted flax'),
('linen_thread','TECHNIQUE', 'spun from line flax'),
('linen_cloth', 'TECHNIQUE', 'woven from linen thread on a loom'),
('linen_shift', 'TECHNIQUE', 'sewn from woven linen cloth')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('linen_shift', 'TORSO', 'INNER')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('ret_flax', 'Ret flax', 'retted_flax', 2, 3, NULL, FALSE, TRUE, 120, 'textiles', 'PROCESS',
 'ret the flax,ret flax,rett the flax,rot the flax,soak the flax,retting,ret',
 'You weight the flax down under standing water and leave it to ret, the still days rotting the woody boon loose from the fibre until it slips free.', 'VERIFIED', now()),
('scutch_flax', 'Scutch flax', 'line_flax', 4, 6, NULL, FALSE, FALSE, 90, 'textiles', 'PROCESS',
 'scutch the flax,break the flax,dress the flax,heckle the flax,swingle the flax,scutch,dress',
 'You break the dried retted straw and scutch the shive away, then dress it through the heckle until only the long, pale line fibre is left, combed and clean.', 'VERIFIED', now()),
('spin_linen_thread', 'Spin linen thread', 'linen_thread', 2, 3, NULL, FALSE, FALSE, 70, 'textiles', 'PROCESS',
 'spin linen,spin the flax,spin line flax,linen thread,spin flax,spin',
 'You draft the line flax out fine and let the spindle twist it into a smooth, strong linen thread, joining each new length in before the last runs out.', 'VERIFIED', now()),
('weave_linen_cloth', 'Weave linen cloth', 'linen_cloth', 1, 1, NULL, FALSE, FALSE, 180, 'textiles', 'PROCESS',
 'weave linen,weave the linen,linen cloth,weave flax,weave',
 'You warp the loom with the linen thread, pass the weft, and beat it up close — a slow hand-span an hour, until a length of smooth pale linen cloth is off the loom.', 'VERIFIED', now()),
('sew_linen_shift', 'Sew a linen shift', 'linen_shift', 1, 1, NULL, FALSE, FALSE, 60, 'items', 'CRAFT',
 'linen shift,sew a linen shift,stitch a linen shift,make a linen shift,shift',
 'You cut the linen cloth to the body and sew it into a simple shift — light against the skin, and warmer worn under the rest than bare wool ever is.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('ret_flax', 'flax_stalk', 4),
('scutch_flax', 'retted_flax', 3),
('spin_linen_thread', 'line_flax', 6),
('weave_linen_cloth', 'linen_thread', 4),
('sew_linen_shift', 'linen_cloth', 2),
('sew_linen_shift', 'linen_thread', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Woven linen cloth takes dye by the same path as wool cloth (the pigment strikes the fibre alike).
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('dye_cloth', 'cloth', 'linen_cloth', 1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('ret_flax', 'flax'),
('scutch_flax', 'flax'),
('spin_linen_thread', 'linen'),
('weave_linen_cloth', 'linen'),
('sew_linen_shift', 'shift')
ON CONFLICT DO NOTHING;
