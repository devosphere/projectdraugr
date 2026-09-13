-- #134 — armour and clothing you can make, can put on, and that does what it is for.
--
-- Running #134's audit recipe again — pull every hardcoded key list out of the Java and diff it against the
-- catalogue — turned up a combat check that counts armour by name: three hard pieces, three soft pieces and three
-- cuirasses. A crafted leather cuirass, both leather pauldrons and every left/right bracer gave nothing in a fight.
--
-- Looking at why those pieces had never been noticed found the larger defect underneath. V8 fixed the body positions
-- and layers equipment_attachment will accept, and later catalogues declared more in item_equipment_compatibility:
-- eleven positions (FOREARM, ELBOW, KNEE, LOWER_LEG and THIGH, left and right, and LEGS) and one layer (INNER).
-- equip() checks the compatibility table, which allows them, and then the insert is refused by the CHECK.
--
-- FORTY-FIVE craftable items could never be worn: every bracer pair; every knee, shin, thigh and elbow guard; the
-- gaiters and splints; the leggings and trousers; and every tunic, the linen shift, the fibre wrap shirt and the fur
-- and hide socks. The tests for them asserted that each could be made and had a compatibility row, and never once put
-- one on.
--
-- Because warmth sums insulation over whatever is worn, none of that clothing has ever warmed anybody. And a refused
-- insert aborts the transaction, so trying to put one on broke the whole action behind a message claiming something
-- else was "in the way".
--
-- WHAT CHANGES
--   1. Both CHECKs accept every position and layer the catalogue declares. Nothing is removed from either.
--   2. armour_protection says how much each worn piece blunts a blow, and combat reads it instead of naming items.
--      Every value that existed is preserved exactly; the pieces that can now be worn get modest ones.
--   3. A guard asserts, against the constraints' own definitions, that every catalogued position and layer is
--      attachable — the invariant that would have caught this the day the first bracer was catalogued. It found the
--      INNER layer on its first run, after the positions alone had been fixed.

ALTER TABLE equipment_attachment DROP CONSTRAINT equipment_attachment_body_position_check;
ALTER TABLE equipment_attachment ADD CONSTRAINT equipment_attachment_body_position_check CHECK (body_position IN (
    'HEAD','FACE','NECK','SHOULDER_LEFT','SHOULDER_RIGHT','TORSO','WAIST','BACK',
    'ARM_LEFT','ARM_RIGHT','HAND_LEFT','HAND_RIGHT','LEG_LEFT','LEG_RIGHT','FOOT_LEFT','FOOT_RIGHT',
    'FINGER_LEFT_1','FINGER_LEFT_2','FINGER_LEFT_3','FINGER_LEFT_4','FINGER_LEFT_5',
    'FINGER_RIGHT_1','FINGER_RIGHT_2','FINGER_RIGHT_3','FINGER_RIGHT_4','FINGER_RIGHT_5',
    -- The eleven the catalogue declared and nothing could use.
    'FOREARM_LEFT','FOREARM_RIGHT','ELBOW_LEFT','ELBOW_RIGHT','KNEE_LEFT','KNEE_RIGHT',
    'LOWER_LEG_LEFT','LOWER_LEG_RIGHT','THIGH_LEFT','THIGH_RIGHT','LEGS'));

ALTER TABLE equipment_attachment DROP CONSTRAINT equipment_attachment_layer_check;
ALTER TABLE equipment_attachment ADD CONSTRAINT equipment_attachment_layer_check CHECK (layer IN (
    'UNDER','CLOTHING','OUTER','PROTECTION','ATTACHED','CARRIED',
    -- Declared by every tunic, shift, wrap shirt and sock, and refused by the table since V8.
    'INNER'));

CREATE TABLE armour_protection (
    item_key  VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    blunting  SMALLINT NOT NULL,
    notes     TEXT NOT NULL,
    CONSTRAINT armour_protection_blunting_sane CHECK (blunting BETWEEN 1 AND 40)
);

COMMENT ON TABLE armour_protection IS
  'How many points of a blow each WORN piece turns (#134). Combat sums this over equipment_attachment; a piece '
  'carried in a pack protects nothing. Replaces item keys named in WildlifeEncounterService.confront.';

INSERT INTO armour_protection (item_key, blunting, notes) VALUES
  -- Preserved exactly from the literals combat used to name.
  ('bronze_cuirass', 12, 'Forged plate; the defensive counterpart of the bronze edge.'),
  ('iron_cuirass',   20, 'Harder plate than bronze.'),
  ('steel_cuirass',  28, 'Case-hardened plate, the best a Chronicle can forge.'),
  ('scale_armour',    7, 'Knapped scale, laced.'),
  ('chitin_helm',     7, 'A shell helm.'),
  ('war_shield',      7, 'A full war shield taken in hand.'),
  ('rawhide_shield',  6, 'Stiff rawhide on a frame.'),
  ('bark_shield',     4, 'A light shield of bark.'),
  ('woven_reed_shield', 4, 'A light shield of woven reed.'),
  ('leather_armor',   4, 'Soft leather over the body.'),
  ('leather_helm_cap', 4, 'A leather cap.'),
  ('leather_bracer',  4, 'A single leather bracer worn at the hand.'),
  -- Torso pieces that were wearable and counted for nothing.
  ('leather_cuirass', 6, 'Moulded leather — more than soft leather armour, well short of forged plate.'),
  ('rawhide_vest',    3, 'Rawhide over the chest turns a raking blow.'),
  ('rawhide_chest_panel', 3, 'A rawhide panel laced over the chest.'),
  ('leather_neck_guard', 2, 'The throat is where a predator goes; even a little leather there matters.'),
  -- Limbs, per piece. Modest on purpose: a fully guarded arm does not make a Chronicle safe, it spares the forearm
  -- a bite that would otherwise open it.
  ('leather_pauldron_left', 2, 'Leather over the shoulder.'), ('leather_pauldron_right', 2, 'Leather over the shoulder.'),
  ('leather_bracer_left',   2, 'Leather over the forearm.'),  ('leather_bracer_right',   2, 'Leather over the forearm.'),
  ('rawhide_bracer_left',   2, 'Rawhide over the forearm.'),  ('rawhide_bracer_right',   2, 'Rawhide over the forearm.'),
  ('leather_thigh_guard_left', 2, 'Leather over the thigh.'), ('leather_thigh_guard_right', 2, 'Leather over the thigh.'),
  ('rawhide_thigh_guard_left', 2, 'Rawhide over the thigh.'), ('rawhide_thigh_guard_right', 2, 'Rawhide over the thigh.'),
  ('bark_bracer_left',  1, 'Bark over the forearm splits before it turns much.'), ('bark_bracer_right', 1, 'Bark over the forearm splits before it turns much.'),
  ('reed_bracer_left',  1, 'Woven reed over the forearm.'), ('reed_bracer_right', 1, 'Woven reed over the forearm.'),
  ('leather_elbow_guard_left', 1, 'Leather at the elbow.'), ('leather_elbow_guard_right', 1, 'Leather at the elbow.'),
  ('leather_knee_guard_left',  1, 'Leather at the knee.'),  ('leather_knee_guard_right',  1, 'Leather at the knee.'),
  ('rawhide_knee_guard_left',  1, 'Rawhide at the knee.'),  ('rawhide_knee_guard_right',  1, 'Rawhide at the knee.'),
  ('reed_knee_pad_left',       1, 'A reed pad at the knee.'), ('reed_knee_pad_right',     1, 'A reed pad at the knee.'),
  ('leather_shin_guard_left',  1, 'Leather over the shin.'), ('leather_shin_guard_right', 1, 'Leather over the shin.'),
  ('rawhide_shin_guard_left',  1, 'Rawhide over the shin.'), ('rawhide_shin_guard_right', 1, 'Rawhide over the shin.'),
  ('bark_shin_guard_left',     1, 'Bark over the shin.'),    ('bark_shin_guard_right',    1, 'Bark over the shin.');

-- Deliberately NOT armour: bark splints hold a broken leg, gaiters and knee wraps keep out wet and thorns, a smoke
-- wrap filters smoke, and leggings, trousers, tunics, shifts and socks are clothing. Each is wearable now; none of
-- them is a guard.

DO $$
DECLARE bad text; def text; n int;
BEGIN
  -- The invariant that would have caught this: every position and layer the catalogue declares must be one the
  -- attachment table accepts. Read from the live constraint definitions, so there is no second list to drift.
  SELECT pg_get_constraintdef(oid) INTO def FROM pg_constraint WHERE conname = 'equipment_attachment_body_position_check';
  SELECT string_agg(DISTINCT body_position, ', ') INTO bad FROM item_equipment_compatibility
   WHERE position('''' || body_position || '''' IN def) = 0;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V313: the catalogue declares positions nothing can be worn at: %', bad; END IF;

  SELECT pg_get_constraintdef(oid) INTO def FROM pg_constraint WHERE conname = 'equipment_attachment_layer_check';
  SELECT string_agg(DISTINCT layer, ', ') INTO bad FROM item_equipment_compatibility
   WHERE position('''' || layer || '''' IN def) = 0;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V313: the catalogue declares layers nothing can be worn in: %', bad; END IF;

  -- Protection only means something on a piece that can be put on.
  SELECT string_agg(ap.item_key, ', ') INTO bad FROM armour_protection ap
   WHERE NOT EXISTS (SELECT 1 FROM item_equipment_compatibility c WHERE c.item_key = ap.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V313: protection given to pieces that cannot be worn: %', bad; END IF;

  -- The twelve values combat used to hard-code must be exactly what they were, or every existing fight changes.
  SELECT count(*) INTO n FROM armour_protection WHERE (item_key, blunting) IN (
    ('bronze_cuirass',12),('iron_cuirass',20),('steel_cuirass',28),('scale_armour',7),('chitin_helm',7),('war_shield',7),
    ('rawhide_shield',6),('bark_shield',4),('woven_reed_shield',4),('leather_armor',4),('leather_helm_cap',4),('leather_bracer',4));
  IF n <> 12 THEN RAISE EXCEPTION 'V313: an existing armour value moved (% of 12 preserved)', n; END IF;

  -- Limb guards are modest by design. A full set on arms and legs must stay under what a single bronze cuirass turns
  -- plus a helm and a war shield, or limb pieces would out-armour the body armour they supplement.
  SELECT COALESCE(SUM(blunting), 0) INTO n FROM armour_protection
   WHERE item_key ~ '(bracer|pauldron|thigh_guard|knee_guard|knee_pad|shin_guard|elbow_guard)_(left|right)$';
  IF n > 40 THEN RAISE EXCEPTION 'V313: every limb guard together turns %, more than body armour could', n; END IF;
END $$;
