-- V250 — story #96, slice 7: feet. Seven left/right pairs — hide/leather/fur boots, leather gaiters, hide/fur socks,
-- snowshoes. Boots are MADE by "make a <material> moccasin" (not "boot"/"shoe", both owned by CRAFT_GARMENT), and
-- snowshoes by "lace a <side> snowshoe" ('make'+'shoe' would hit CRAFT_GARMENT; 'lace' is not a garment verb). Each
-- side its own FOOT/LOWER_LEG slot and keyword. All CRAFT (no rawhide). tool_class CUTTING.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value, water_resistance) VALUES
('hide_boot_left',    'Hide boot (left)',    'CLOTHING', 300, 800, FALSE, TRUE, 6, 6),
('hide_boot_right',   'Hide boot (right)',   'CLOTHING', 300, 800, FALSE, TRUE, 6, 6),
('leather_boot_left', 'Leather boot (left)', 'CLOTHING', 320, 850, FALSE, TRUE, 6, 9),
('leather_boot_right','Leather boot (right)','CLOTHING', 320, 850, FALSE, TRUE, 6, 9),
('fur_boot_left',     'Fur boot (left)',     'CLOTHING', 340, 900, FALSE, TRUE, 16, 4),
('fur_boot_right',    'Fur boot (right)',    'CLOTHING', 340, 900, FALSE, TRUE, 16, 4),
('leather_gaiter_left', 'Leather gaiter (left)', 'CLOTHING', 200, 550, FALSE, TRUE, 4, 8),
('leather_gaiter_right','Leather gaiter (right)','CLOTHING', 200, 550, FALSE, TRUE, 4, 8),
('hide_sock_left',    'Hide sock (left)',    'CLOTHING', 120, 350, FALSE, TRUE, 6, 4),
('hide_sock_right',   'Hide sock (right)',   'CLOTHING', 120, 350, FALSE, TRUE, 6, 4),
('fur_sock_left',     'Fur sock (left)',     'CLOTHING', 140, 400, FALSE, TRUE, 14, 3),
('fur_sock_right',    'Fur sock (right)',    'CLOTHING', 140, 400, FALSE, TRUE, 14, 3),
('snowshoe_left',     'Snowshoe (left)',     'CLOTHING', 400, 1500, FALSE, TRUE, 1, 4),
('snowshoe_right',    'Snowshoe (right)',    'CLOTHING', 400, 1500, FALSE, TRUE, 1, 4)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('hide_boot_left','FOOT_LEFT','OUTER'),('hide_boot_right','FOOT_RIGHT','OUTER'),
('leather_boot_left','FOOT_LEFT','OUTER'),('leather_boot_right','FOOT_RIGHT','OUTER'),
('fur_boot_left','FOOT_LEFT','OUTER'),('fur_boot_right','FOOT_RIGHT','OUTER'),
('leather_gaiter_left','LOWER_LEG_LEFT','OUTER'),('leather_gaiter_right','LOWER_LEG_RIGHT','OUTER'),
('hide_sock_left','FOOT_LEFT','INNER'),('hide_sock_right','FOOT_RIGHT','INNER'),
('fur_sock_left','FOOT_LEFT','INNER'),('fur_sock_right','FOOT_RIGHT','INNER'),
('snowshoe_left','FOOT_LEFT','ATTACHED'),('snowshoe_right','FOOT_RIGHT','ATTACHED')
ON CONFLICT DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('hide_boot_left','TECHNIQUE','a hide boot cut and laced for the left foot'),
('hide_boot_right','TECHNIQUE','a hide boot cut and laced for the right foot'),
('leather_boot_left','TECHNIQUE','a leather boot cut and sewn for the left foot'),
('leather_boot_right','TECHNIQUE','a leather boot cut and sewn for the right foot'),
('fur_boot_left','TECHNIQUE','a fur boot sewn for the left foot'),
('fur_boot_right','TECHNIQUE','a fur boot sewn for the right foot'),
('leather_gaiter_left','TECHNIQUE','a leather gaiter laced over the left lower leg'),
('leather_gaiter_right','TECHNIQUE','a leather gaiter laced over the right lower leg'),
('hide_sock_left','TECHNIQUE','a soft hide sock for the left foot'),
('hide_sock_right','TECHNIQUE','a soft hide sock for the right foot'),
('fur_sock_left','TECHNIQUE','a fur sock for the left foot'),
('fur_sock_right','TECHNIQUE','a fur sock for the right foot'),
('snowshoe_left','TECHNIQUE','a bent frame webbed with leather for the left foot'),
('snowshoe_right','TECHNIQUE','a bent frame webbed with leather for the right foot')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_hide_boot_left','Lace a left hide moccasin','hide_boot_left',1,1,'CUTTING',FALSE,FALSE,30,'items','CRAFT','make a left hide moccasin,left hide moccasin', 'You cut and lace a hide moccasin for the left foot.', 'VERIFIED', now()),
('make_hide_boot_right','Lace a right hide moccasin','hide_boot_right',1,1,'CUTTING',FALSE,FALSE,30,'items','CRAFT','make a right hide moccasin,right hide moccasin', 'You cut and lace a hide moccasin for the right foot.', 'VERIFIED', now()),
('make_leather_boot_left','Sew a left leather moccasin','leather_boot_left',1,1,'CUTTING',FALSE,FALSE,32,'items','CRAFT','make a left leather moccasin,left leather moccasin', 'You cut and sew a leather moccasin for the left foot.', 'VERIFIED', now()),
('make_leather_boot_right','Sew a right leather moccasin','leather_boot_right',1,1,'CUTTING',FALSE,FALSE,32,'items','CRAFT','make a right leather moccasin,right leather moccasin', 'You cut and sew a leather moccasin for the right foot.', 'VERIFIED', now()),
('make_fur_boot_left','Sew a left fur moccasin','fur_boot_left',1,1,'CUTTING',FALSE,FALSE,32,'items','CRAFT','make a left fur moccasin,left fur moccasin', 'You sew a fur moccasin for the left foot against the cold.', 'VERIFIED', now()),
('make_fur_boot_right','Sew a right fur moccasin','fur_boot_right',1,1,'CUTTING',FALSE,FALSE,32,'items','CRAFT','make a right fur moccasin,right fur moccasin', 'You sew a fur moccasin for the right foot against the cold.', 'VERIFIED', now()),
('make_leather_gaiter_left','Lace a left leather gaiter','leather_gaiter_left',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a left leather gaiter,left leather gaiter', 'You lace a leather gaiter over the left lower leg.', 'VERIFIED', now()),
('make_leather_gaiter_right','Lace a right leather gaiter','leather_gaiter_right',1,1,'CUTTING',FALSE,FALSE,22,'items','CRAFT','make a right leather gaiter,right leather gaiter', 'You lace a leather gaiter over the right lower leg.', 'VERIFIED', now()),
('make_hide_sock_left','Sew a left hide sock','hide_sock_left',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a left hide sock,left hide sock', 'You sew a soft hide sock for the left foot.', 'VERIFIED', now()),
('make_hide_sock_right','Sew a right hide sock','hide_sock_right',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a right hide sock,right hide sock', 'You sew a soft hide sock for the right foot.', 'VERIFIED', now()),
('make_fur_sock_left','Sew a left fur sock','fur_sock_left',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a left fur sock,left fur sock', 'You sew a fur sock for the left foot.', 'VERIFIED', now()),
('make_fur_sock_right','Sew a right fur sock','fur_sock_right',1,1,'CUTTING',FALSE,FALSE,20,'items','CRAFT','make a right fur sock,right fur sock', 'You sew a fur sock for the right foot.', 'VERIFIED', now()),
('make_snowshoe_left','Lace a left snowshoe','snowshoe_left',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','lace a left snowshoe,left snowshoe', 'You bend a frame and web it with leather into a snowshoe for the left foot.', 'VERIFIED', now()),
('make_snowshoe_right','Lace a right snowshoe','snowshoe_right',1,1,'CUTTING',FALSE,FALSE,40,'items','CRAFT','lace a right snowshoe,right snowshoe', 'You bend a frame and web it with leather into a snowshoe for the right foot.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_hide_boot_left','animal_hide',1),('make_hide_boot_right','animal_hide',1),
('make_leather_boot_left','tanned_leather',1),('make_leather_boot_right','tanned_leather',1),
('make_fur_boot_left','fur_lining',1),('make_fur_boot_right','fur_lining',1),
('make_leather_gaiter_left','tanned_leather',1),('make_leather_gaiter_right','tanned_leather',1),
('make_hide_sock_left','animal_hide',1),('make_hide_sock_right','animal_hide',1),
('make_fur_sock_left','fur_lining',1),('make_fur_sock_right','fur_lining',1),
('make_snowshoe_left','tanned_leather',1),('make_snowshoe_right','tanned_leather',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_hide_boot_left','moccasin'),('make_hide_boot_right','moccasin'),
('make_leather_boot_left','moccasin'),('make_leather_boot_right','moccasin'),
('make_fur_boot_left','moccasin'),('make_fur_boot_right','moccasin'),
('make_leather_gaiter_left','gaiter'),('make_leather_gaiter_right','gaiter'),
('make_hide_sock_left','sock'),('make_hide_sock_right','sock'),
('make_fur_sock_left','sock'),('make_fur_sock_right','sock'),
('make_snowshoe_left','snowshoe'),('make_snowshoe_right','snowshoe')
ON CONFLICT DO NOTHING;
