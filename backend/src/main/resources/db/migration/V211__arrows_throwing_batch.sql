-- V211 — story #93 catalogue batch 4: arrows and thrown weapons (the projectile group's ammunition and hand-cast
-- weapons, functional on the existing bow and thrown model — no new bow needed). Six items, pure data on V205:
--   flint_arrowhead  — MATERIAL, knapped from flint; the point a broadhead arrow carries
--   fletched_arrow   } WEAPON, ARROW role — loosed from any bow (confront: archery = a bow + arrows)
--   broadhead_arrow  }
--   blunt_arrow      }  (blunt for small game/stunning; still an arrow)
--   throwing_stick   } WEAPON, JAVELIN role — cast by hand for reach (as the existing javelins are)
--   throwing_club    }
-- Distinct longer keywords beat the existing bare arrow recipes (fletch/arrow/arrowhead/knap); routing verified
-- locally (classify+match). Keywords hyphen-free; arrows/throwers equippable (dead-end-clean); the arrowhead is
-- consumed by the broadhead recipe; matter-safe.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('flint_arrowhead', 'Flint arrowhead', 'MATERIAL', 12, 8,  TRUE,  FALSE, 0),
('fletched_arrow',  'Fletched arrow',  'WEAPON',   45, 110, TRUE,  TRUE,  0),
('broadhead_arrow', 'Broadhead arrow', 'WEAPON',   55, 110, TRUE,  TRUE,  0),
('blunt_arrow',     'Blunt arrow',     'WEAPON',   45, 110, TRUE,  TRUE,  0),
('throwing_stick',  'Throwing stick',  'WEAPON',   300,700, FALSE, TRUE,  0),
('throwing_club',   'Throwing club',   'WEAPON',   320,700, FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('flint_arrowhead', 'TECHNIQUE', 'a small keen point knapped from flint'),
('fletched_arrow',  'TECHNIQUE', 'a straight shaft fletched with feather'),
('broadhead_arrow', 'TECHNIQUE', 'a fletched shaft tipped with a broad flint point'),
('blunt_arrow',     'TECHNIQUE', 'a fletched shaft with a blunt head for small game'),
('throwing_stick',  'TECHNIQUE', 'a weighted stick balanced to be thrown'),
('throwing_club',   'TECHNIQUE', 'a short heavy club balanced to be thrown')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('fletched_arrow','HAND_RIGHT','CARRIED'),('fletched_arrow','HAND_LEFT','CARRIED'),
('broadhead_arrow','HAND_RIGHT','CARRIED'),('broadhead_arrow','HAND_LEFT','CARRIED'),
('blunt_arrow','HAND_RIGHT','CARRIED'),('blunt_arrow','HAND_LEFT','CARRIED'),
('throwing_stick','HAND_RIGHT','CARRIED'),('throwing_stick','HAND_LEFT','CARRIED'),
('throwing_club','HAND_RIGHT','CARRIED'),('throwing_club','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('knap_flint_arrowhead', 'Knap a flint arrowhead', 'flint_arrowhead', 1,2,NULL,FALSE,FALSE,20,'tools','PROCESS',
 'knap a flint arrowhead,flint arrowhead',
 'You pressure-flake a small, keen, barbed point from flint — the head a broadhead arrow will carry.','VERIFIED',now()),
('fletch_fletched_arrow','Fletch an arrow',        'fletched_arrow',  1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'fletch a fletched arrow,make a fletched arrow,fletched arrow',
 'You bind feather vanes along a straight shaft and cut the nock — an arrow that flies true, ready for a point.','VERIFIED',now()),
('fit_broadhead_arrow',  'Fit a broadhead arrow',  'broadhead_arrow', 1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'fit a broadhead arrow,make a broadhead arrow,broadhead arrow',
 'You seat a broad flint point in a fletched shaft and lash it fast — a cutting head that opens a deep wound in big game.','VERIFIED',now()),
('make_blunt_arrow',     'Make a blunt arrow',     'blunt_arrow',     1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'make a blunt arrow,fit a blunt arrow,blunt arrow',
 'You leave the head of a fletched shaft blunt and heavy — for small game and birds you want stunned, not torn.','VERIFIED',now()),
('carve_throwing_stick', 'Carve a throwing stick', 'throwing_stick',  1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a throwing stick,fashion a throwing stick,throwing stick',
 'You trim and weight a length of hard wood so it flies flat and spinning — a stick to knock down a bird or a hare at a distance.','VERIFIED',now()),
('carve_throwing_club',  'Carve a throwing club',  'throwing_club',   1,1,NULL,FALSE,FALSE,25,'tools','CRAFT',
 'carve a throwing club,fashion a throwing club,throwing club',
 'You shape a short, heavy-headed club balanced to be cast from the hand at close range.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('knap_flint_arrowhead', 'flint_stone',1),
('fletch_fletched_arrow','arrow_shaft',1),('fletch_fletched_arrow','feather',1),
('fit_broadhead_arrow',  'arrow_shaft',1),('fit_broadhead_arrow','flint_arrowhead',1),('fit_broadhead_arrow','feather',1),
('make_blunt_arrow',     'arrow_shaft',1),('make_blunt_arrow','feather',1),
('carve_throwing_stick', 'dry_branch',1),
('carve_throwing_club',  'dry_branch',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('knap_flint_arrowhead','flint arrowhead'),('knap_flint_arrowhead','arrowhead'),
('fletch_fletched_arrow','fletched arrow'),
('fit_broadhead_arrow','broadhead arrow'),('fit_broadhead_arrow','broadhead'),
('make_blunt_arrow','blunt arrow'),
('carve_throwing_stick','throwing stick'),
('carve_throwing_club','throwing club')
ON CONFLICT DO NOTHING;

-- Combat function (V205): the three arrows are ARROW ammunition (loosed from any bow); the thrown weapons are reach.
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
('fletched_arrow', 'ARROW',  'PLAIN', FALSE),
('broadhead_arrow','ARROW',  'PLAIN', FALSE),
('blunt_arrow',    'ARROW',  'PLAIN', FALSE),
('throwing_stick', 'JAVELIN','PLAIN', FALSE),
('throwing_club',  'JAVELIN','PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;
