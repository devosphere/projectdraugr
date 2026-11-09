-- V460: a station you have built, and no way to say you are putting something on it (#37, #99).
--
-- Matrix row 13. Measured on a FRESH database, because the previous one had been hand-patched past the point of
-- telling the truth (see the note at the end — it matters more than this fix does):
--
--   rack the meat                     PROCESS_MATERIAL    the canonical phrase works
--   dry the meat / smoke the meat     PROCESS_MATERIAL    so do the verbs
--   put the meat on the rack          NOTHING AT ALL
--   lay the meat on the rack          NOTHING AT ALL
--   put the fish on the rack          NOTHING AT ALL
--   put the hide on the frame         NOTHING AT ALL
--
-- Four stations in this game are STRUCTURES rather than carried benches -- DRYING_RACK, SMOKE_RACK, HIDE_FRAME
-- and CORDAGE_POST -- and fourteen processes ask for one. A Chronicle who has raised a drying rack and is
-- holding meat says the plainest thing there is about it, and the world says nothing back.
--
-- THE RACK IS THE DRYING RACK AND THE SMOKE IS THE SMOKING, which is the one judgement in this file. Both
-- dry_meat and smoke_meat want meat and a rack, so "put the meat on the rack" could have been made to ask
-- which. It is not: in English the rack is where a thing is laid to dry, and smoking is done IN the smoke. So
-- each phrasing goes to one process and the player is not interrogated about a sentence that is already clear.
-- `rack the meat` already belonged to dry_meat alone, by the same reading, so this follows the catalogue rather
-- than overruling it.
--
-- THE HIDE FRAME IS THE EXCEPTION, and deliberately asks. dehair_hide and flesh_hide BOTH work a hide on the
-- frame and are genuinely different jobs -- one takes the hair off, the other the flesh -- so "put the hide on
-- the frame" is given to both and #593's tie rule names them and asks. That is the honest answer to a sentence
-- that really is ambiguous.
--
-- NOT FIXED HERE, AND MEASURED: naming the station SPECIFICALLY reaches the station's own BUILD.
--
--   put the meat on the drying rack   ADVANCE_ASSEMBLY
--   put the meat on the smoke rack    ADVANCE_ASSEMBLY
--
-- The drying_rack and smoke_rack assemblies own `drying rack` and `smoke rack` as keywords, and the assembly
-- matcher runs BEFORE the material-process matcher in the routing chain -- so no process keyword, however
-- long, can win those two sentences. Telling a Chronicle who has already built a rack that they are building
-- one is a real defect, and fixing it means the assembly standing aside once it is already raised. That is a
-- Java change in a file another session is holding, so it is recorded on the ticket instead of half-done here.
--
-- All ten phrases were checked against every process and assembly keyword in the catalogue first, and all ten
-- were free.

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('dry_meat',      'put the meat on the rack,lay the meat on the rack'),
    ('dry_fish',      'put the fish on the rack,lay the fish on the rack'),
    ('dry_herbs',     'put the herbs on the rack,lay the herbs on the rack'),
    ('dry_mushrooms', 'put the mushrooms on the rack,lay the mushrooms on the rack'),
    ('smoke_meat',    'put the meat in the smoke,hang the meat in the smoke'),
    ('smoke_fish',    'put the fish in the smoke,hang the fish in the smoke'),
    ('smoke_fowl',    'put the fowl in the smoke,hang the fowl in the smoke'),
    -- Both of these, so the frame asks which job is meant.
    ('dehair_hide',   'put the hide on the frame,stretch the hide on the frame'),
    ('flesh_hide',    'put the hide on the frame,stretch the hide on the frame')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   -- Anchored on the comma boundaries the list is actually joined by. A LIKE '%rack%' guard would be the
   -- substring defect this project has paid for six times, and `rack the meat` is already in two of these rows.
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');

-- The subject gate has to pass the new phrasings too: a keyword alone is turned away without a
-- process_subject row on the SAME process. "rack", "frame" and "smoke" are the nouns these sentences carry
-- that the existing subject terms (meat, fish, herbs, mushroom, hide, dehaired) do not.
INSERT INTO process_subject (process_key, subject_term)
SELECT p, t FROM (VALUES
  ('dry_meat','rack'), ('dry_fish','rack'), ('dry_herbs','rack'), ('dry_mushrooms','rack'),
  ('smoke_meat','smoke'), ('smoke_fish','smoke'), ('smoke_fowl','smoke'),
  ('dehair_hide','frame'), ('flesh_hide','frame')
) AS s(p, t)
ON CONFLICT (process_key, subject_term) DO NOTHING;
