-- V459: the three recipes that refused with every declared input and every tool in the pack (#37, #99).
--
-- #37's FIRST complaint is about crafting: "excessively hit a lot of crafting restrictions even if the
-- procedure is rationally possible given the tools and materials around the vicinity and carried load". It had
-- never been swept, because sweeping it needs the inputs ARMED rather than merely named.
--
-- THE AUDIT. For each of the 332 processes that declare an input and want no fire, water or station, put every
-- declared input in the pack (material_process_input, plus one member of each either/or
-- material_process_input_group), give the Chronicle every tool in tool_profile, and say the process's own first
-- keyword. A refusal then is not a missing material — it is the complaint.
--
--   332 run:  327 succeeded,  2 asked which,  3 refused,  0 no-reply
--
-- SO THE COMPLAINT LARGELY DOES NOT REPRODUCE, and that is the headline rather than this file: when the
-- materials really are in reach, the restriction logic lets the work happen. The two "asked which" are #593's
-- ambiguity rule working as designed (make_draft_harness and make_draft_yoke each share their phrase with a
-- sibling recipe). What the sweep did find is three recipes that could not be run at all, each broken a
-- different way, and two of the three are fixed here.
--
-- 1. shape_sling_stone — A DECLARED KEYWORD ITS OWN SUBJECT GATE REFUSED.
--
--      shape a sling stone   PROCESS_MATERIAL  "You knock a hard stone round and smooth…"
--      sling stone           PROCESS_MATERIAL  the same
--      shape sling stones    NOTHING AT ALL
--
--    The keyword `shape sling stones` is declared on the process. The subject term is `sling stone`, singular,
--    and the subject matcher is whole-word — so the plural the keyword itself advertises could not pass the
--    gate standing behind it. The two halves of the same recipe disagreed about one word.
--
-- 2. make_reed_screen — WHOLLY SHADOWED BY AN ASSEMBLY OF THE SAME NAME.
--
--    Asked the catalogue which processes have EVERY keyword owned by an assembly as well, and it holds exactly
--    one: this. `reed screen` and `make a reed screen` are both declared by the process and by the reed_screen
--    assembly, and the assembly wins — so the process was unreachable by any sentence.
--
--    They are not duplicates, which is why nothing is removed: the ASSEMBLY builds a screen from 2 straight
--    saplings in staged work, and the PROCESS weaves one from 2 reed bundles in a single step. Measured, the
--    assembly answers "That step wants 2 straight sapling, and you have none to hand" to a Chronicle holding
--    six reed bundles. Two honest ways to make a screen, and only one of them could be asked for.
--
--    So the process gains phrases that name ITS material. The shared `reed screen` and `make a reed screen` are
--    deliberately left with the assembly: it is the older, staged reading, and a tie between a process and an
--    assembly is settled in the assembly's favour everywhere else in this game
--    (noJavaIntentShadowsAnAssemblysOwnKeywords is the same principle for Java intents).
--
-- 3. reinforce_timber — shadowed by the REPAIR_ITEM intent, whose `mendsSomething` holds a bare "reinforce".
--    That one is a Java change and ships beside this file; `lash the planks` and `bind the planks` already
--    reached the process, so only the reinforce phrasings were lost.
--
-- All four new phrases were checked against every process and assembly keyword in the catalogue before being
-- written here, and all four were free.

INSERT INTO process_subject (process_key, subject_term)
VALUES ('shape_sling_stone', 'sling stones')
ON CONFLICT (process_key, subject_term) DO NOTHING;

UPDATE material_process mp
   SET keywords = mp.keywords || ',' || add.kw
  FROM (VALUES
    ('make_reed_screen', 'weave the reed bundles,reed bundle screen,make a screen from reeds,screen of reeds')
  ) AS add(process_key, kw)
 WHERE mp.process_key = add.process_key
   -- Anchored on the comma boundaries the keyword list is actually joined by, because a plain LIKE '%reed%'
   -- guard would be the substring defect this project has paid for six times — and this very list already
   -- contains "reed screen", which such a guard would match.
   AND mp.keywords !~ ('(^|,)' || split_part(add.kw, ',', 1) || '(,|$)');

-- The subject gate has to let the new phrases through as well: a family word needs a keyword AND a
-- process_subject row on the same process, and "reeds" is not "reed screen".
INSERT INTO process_subject (process_key, subject_term)
VALUES ('make_reed_screen', 'reeds'), ('make_reed_screen', 'reed bundle'), ('make_reed_screen', 'reed bundles')
ON CONFLICT (process_key, subject_term) DO NOTHING;
