-- V169: dressed stone and dry ashlar — the finer masonry (EPIC #180 heavy industry / #183 stone shaping & masonry).
--
-- Rough coursing stone (construction_stone) beds into a wall on a bed of mortar (lay_mortared_course, V57/V164). The
-- next craft up is ASHLAR: each block dressed square and true so the stones fit face to face and need no mortar at all
-- — a dry ashlar wall stands by the precision of its cutting alone. It is far more work to raise (every block dressed
-- by hand) but wants no lime, so it is the mason's alternative to the mortared rubble course: labour spent at the
-- stone instead of fuel spent burning lime. This completes the stone-shaping ladder: field stone -> coursing stone ->
-- dressed ashlar.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('ashlar_block',  'Dressed ashlar block', 'MATERIAL', 1500, 700,  FALSE, FALSE, 0),
('ashlar_course', 'Dry ashlar course',    'MATERIAL', 5800, 2800, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('ashlar_block',  'TECHNIQUE', 'a rough coursing stone dressed square and true with hammer and chisel'),
('ashlar_course', 'TECHNIQUE', 'dressed ashlar blocks laid dry, face to face, holding by the fit of the cutting')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Dress a coursing stone into a squared ashlar block. 'dress'/'shape' are PROCESS terms (V57); STRIKING work.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('dress_ashlar', 'Dress an ashlar block', 'ashlar_block', 1,1, 'STRIKING', FALSE, FALSE, 90, 'stoneworking', 'PROCESS',
 'dress an ashlar block,dress the ashlar,square the stone,shape an ashlar block,dress a building block,cut ashlar',
 'You work a rough coursing stone down with hammer and chisel, dressing every face square and true, until it is a clean ashlar block that will bed against its neighbours with no gap to fill.', 'VERIFIED', now()),
('lay_ashlar_course', 'Lay a dry ashlar course', 'ashlar_course', 1,1, 'STRIKING', FALSE, FALSE, 120, 'construction', 'CONSTRUCT',
 'lay an ashlar course,lay the dressed stone,lay dry ashlar,course the ashlar wall,build a dry ashlar wall',
 'You set each dressed block down onto the last, tapping it true, and the ashlar beds so tight against its neighbours that the wall stands on the fit of the cutting alone — no mortar, only stone on stone.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('dress_ashlar', 'construction_stone', 1),
('lay_ashlar_course', 'ashlar_block', 4)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('dress_ashlar','ashlar'), ('dress_ashlar','block'),
('lay_ashlar_course','ashlar'), ('lay_ashlar_course','course')
ON CONFLICT DO NOTHING;
