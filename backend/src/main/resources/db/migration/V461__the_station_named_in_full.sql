-- #37/#99: the station named in full, and the work it is named for.
--
-- V460 gave `put the meat on the rack` to dry_meat and recorded what it could not fix:
--
--   put the meat on the drying rack   ADVANCE_ASSEMBLY   (began raising a rack)
--   put the meat on the smoke rack    ADVANCE_ASSEMBLY
--
-- The assemblies own the bare names `drying rack` and `smoke rack`, and the assembly matcher runs before the
-- material processes, so no process keyword could win those sentences. AssemblyService.advance now stands aside
-- when a process declares a keyword that the sentence contains, that is LONGER than the bare name matched and
-- contains it -- longest-keyword-wins, asked across both matchers at last. A build phrase never stands aside, so
-- `build a drying rack` still builds.
--
-- This file supplies the longer keywords. With a rack standing the meat goes on it; with none, the process
-- refuses for want of one -- which is the true answer, where the old one set the Chronicle to lashing poles.
-- The hide frame keeps V460's deliberate tie: both jobs are worked on it, so the full name asks which, too.
--
-- Anchored on comma boundaries, never LIKE '%...%' (the substring defect), so re-running adds nothing.
--
-- THE COLUMN IS WIDENED, AND THAT IS THE DECISION HERE. material_process.keywords has been varchar(200) since V52,
-- and V404, V415 and V436 each spent effort fitting a phrase under it. Measured before this file: the longest row
-- was 199 characters and seven were over 180, and this one overflowed it on the first try (dry_fish 153 + 63).
-- A ceiling that makes every phrasing fix a trade against an older phrasing is not a constraint worth keeping, so
-- it goes to 400 -- the width district_purpose.keywords was given in V295. No entity maps this table (ddl-auto
-- validate does not see it), and the one dependent view, process_mass_balance, does not read the column.
ALTER TABLE material_process ALTER COLUMN keywords TYPE VARCHAR(400);

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('dry_meat',      'put the meat on the drying rack,lay the meat on the drying rack'),
    ('dry_fish',      'put the fish on the drying rack,lay the fish on the drying rack'),
    ('dry_herbs',     'put the herbs on the drying rack,lay the herbs on the drying rack'),
    ('dry_mushrooms', 'put the mushrooms on the drying rack,lay the mushrooms on the drying rack'),
    ('smoke_meat',    'put the meat on the smoke rack,hang the meat on the smoke rack'),
    ('smoke_fish',    'put the fish on the smoke rack,hang the fish on the smoke rack'),
    ('smoke_fowl',    'put the fowl on the smoke rack,hang the fowl on the smoke rack'),
    ('dehair_hide',   'put the hide on the hide frame,stretch the hide on the hide frame'),
    ('flesh_hide',    'put the hide on the hide frame,stretch the hide on the hide frame')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');
