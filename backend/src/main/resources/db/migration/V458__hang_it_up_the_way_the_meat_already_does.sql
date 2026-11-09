-- V458: the drying family knew how to be HUNG UP for two materials out of five (#37, #99).
--
-- Found by sweeping the drying vocabulary through a running stack, 14 phrases then 15, 0 no-reply. Two
-- defects sat on top of each other, and this file is the second of them; the first is a one-clause change in
-- ChronicleActionService that ships with it.
--
-- THE JAVA HALF. A bare `value.contains("to dry")` in the DRY_BODY rule claimed every sentence holding those
-- two words, so the Chronicle tried to dry THEMSELVES OFF whatever was hung up. The clearest pair:
--
--   dry the herbs          PROCESS_MATERIAL  "You tie the stems in small bunches and hang them heads-down…"
--   hang the herbs to dry  DRY_BODY          "You try to dry off, but with no fire and no cover here…"
--
-- The succeeding process says HANG in its own prose and the sentence that says hang reached the body act.
--
-- THE DATA HALF, WHICH IS THIS FILE. With DRY_BODY standing aside, `hang the meat to dry` resolved correctly
-- and `hang the hide to dry` and `hang the fish up to dry` reached NOTHING. The catalogue explains why: the
-- family is already declared for two materials and for no others —
--
--   dry_meat       dry the meat,dried meat,rack the meat,jerk the meat,dry,cut,HANG THE MEAT,HANG MEAT,HANG IT TO DRY
--   dry_herbs      dry the herbs,herb bundle,HANG THE HERBS,dry,season
--   dry_fish       dry the fish,dried fish,rack the fish,air dry the fish,dry          <- no hang form
--   dry_mushrooms  dry the mushrooms,dried mushroom,thread the mushrooms,dry           <- no hang form
--   make_rawhide   rawhide,stretch the hide,dry the hide hard,dry                      <- no hang form
--
-- So the phrase a person reaches for was answered for meat and herbs and not for fish, hide or mushrooms. The
-- subject gate is NOT the problem here and is left alone: process_subject already carries fish for dry_fish,
-- hide and dehaired for make_rawhide, and mushroom/oyster/porcini/chanterelle for dry_mushrooms. What was
-- missing is a keyword long enough to win, because the only keyword these three share with a hanging sentence
-- is the bare three-letter "dry", which five processes declare at once — and an equal-length tie among five is
-- settled by #593's rule, which with nothing in reach asks rather than answers.
--
-- EACH NEW PHRASE NAMES ITS OWN MATERIAL, so none of them can tie with another. "hang the hide" is 13
-- characters against "dry"'s 3 and belongs to one process only.
--
-- NO BARE "hang" ANYWHERE, and that is deliberate twice over. Six assemblies own hanging phrases — door
-- hanging, reed door, bark door, simple gate and the rest advertise "hang a door" and "hang the gate" — and a
-- plain "hang" keyword here would start shadowing an assembly's own words, which
-- noJavaIntentShadowsAnAssemblysOwnKeywords exists to forbid. It would also claim "hang it to dry", which
-- dry_meat already declares and should keep.

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('dry_fish',      'hang the fish,hang the fish to dry,hang fish'),
    ('make_rawhide',  'hang the hide,hang the hide to dry,hang the hide up'),
    ('dry_mushrooms', 'hang the mushrooms,hang the mushrooms to dry')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   -- Anchored on the comma boundaries the list is actually joined by. A plain LIKE '%hang%' guard would be the
   -- substring defect this project has paid for six times, and would match "hang the hide" inside
   -- "hang the hide up" as well.
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');
