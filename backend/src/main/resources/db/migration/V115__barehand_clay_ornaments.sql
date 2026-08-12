-- V115: bare-hand clay beads and seals (M1 #197, EPIC #191).
--
-- Most of #197 already exists: temper_clay (bare hand) kneads grit out and wedges the clay; form_vessel (bare
-- hand) coils an unfired_vessel that air-dries fragile and only becomes a durable clay_pot through fire_vessel (a
-- separate fire chain); mix_daub smears mud daub; pack_earth_floor packs earth; grind_pigment grinds ochre to a
-- pigment that goes "to cloth or skin". The two shaping primitives still missing are the small pressed forms —
-- clay beads and a clay seal. Both honour the acceptance: unfired they are fragile and go no further than the
-- hand that made them; only firing (requires_fire, the separate fuel chain) yields a durable trinket, and only a
-- fired trinket can be strung into a wearable cord. A bare hand cannot make a durable ornament by itself.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('clay_bead',          'Unfired clay bead',  'MATERIAL', 8,  6,   TRUE,  FALSE, 0),
('clay_seal',          'Unfired clay seal',  'MATERIAL', 30, 20,  TRUE,  FALSE, 0),
('fired_clay_trinket', 'Fired clay trinket', 'MATERIAL', 8,  6,   TRUE,  FALSE, 0),
('clay_trinket_cord',  'Clay trinket cord',  'CLOTHING', 60, 80,  FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

-- The cord is worn at the neck; it warms nothing (insulation 0) — it is adornment/identity, not clothing insulation.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('clay_trinket_cord','NECK','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('clay_bead',          'TECHNIQUE', 'rolled from tempered clay by hand; fragile until fired'),
('clay_seal',          'TECHNIQUE', 'pressed from tempered clay by hand; fragile until fired'),
('fired_clay_trinket', 'TECHNIQUE', 'a clay bead or seal hardened in a fire'),
('clay_trinket_cord',  'TECHNIQUE', 'fired clay trinkets strung on a cord by hand')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Shaping and firing are PROCESS work (like temper_clay/fire_vessel); stringing is CRAFT assembly (like a garment).
-- Keywords stay on-category: shape/press/bake/kiln/harden are PROCESS verbs; string is a CRAFT verb; fire/roll/
-- thread/stamp are not vocabulary and classify to nothing (which drops the category gate). No ACQUIRE token appears.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('shape_clay_bead',    'Shape clay beads',   'clay_bead',          2,4,NULL, FALSE,FALSE,12,'items','PROCESS','shape a clay bead,roll a clay bead,shape clay beads,roll clay beads,shape a bead', 'You roll and pierce small beads from the tempered clay and set them aside to dry — fragile little things until a fire hardens them.', 'VERIFIED', now()),
('press_clay_seal',    'Press a clay seal',  'clay_seal',          1,1,NULL, FALSE,FALSE,8, 'items','PROCESS','press a clay seal,press a seal,stamp a clay seal,impress a clay seal,press clay seals', 'You press a flat disc of tempered clay and work a mark into its face — an unfired seal, soft and easily spoiled until it is fired.', 'VERIFIED', now()),
('fire_clay_trinkets', 'Fire clay trinkets', 'fired_clay_trinket', 1,1,NULL, TRUE, FALSE,60,'items','PROCESS','fire the clay beads,bake the clay beads,harden the clay beads,fire the clay seal,kiln the trinkets,bake the clay trinkets', 'You bed the little clay pieces in the embers and let them harden through — brittle no longer, they ring faintly when tapped.', 'VERIFIED', now()),
('thread_trinket_cord','Thread a trinket cord','clay_trinket_cord',1,1,NULL, FALSE,FALSE,15,'items','CRAFT','string the trinkets,string a bead cord,thread a trinket cord,make a trinket necklace,thread the beads onto a cord,bead necklace', 'You thread the fired trinkets onto a length of cordage and knot it closed — a cord to wear at the neck.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('shape_clay_bead','tempered_clay',1),
('press_clay_seal','tempered_clay',1),
('thread_trinket_cord','fired_clay_trinket',3),('thread_trinket_cord','fiber_cordage',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Firing takes either an unfired bead or an unfired seal.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('fire_clay_trinkets','trinket','clay_bead',1),('fire_clay_trinkets','trinket','clay_seal',1)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('shape_clay_bead','bead'),('shape_clay_bead','clay bead'),
('press_clay_seal','seal'),('press_clay_seal','clay seal'),
('fire_clay_trinkets','trinket'),('fire_clay_trinkets','bead'),('fire_clay_trinkets','seal'),
('thread_trinket_cord','cord'),('thread_trinket_cord','necklace'),('thread_trinket_cord','trinket')
ON CONFLICT DO NOTHING;
