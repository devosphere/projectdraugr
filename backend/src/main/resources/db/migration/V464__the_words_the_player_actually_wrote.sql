-- V464: keep the submission the player actually wrote, not only the steps it was cut into (#892).
--
-- #873 put the player's words beside the world's answer, and the live screen shows exactly what was typed —
-- it typed it. The LEDGER, though, records one row per resolved STEP, each holding that step's words. So a
-- procedure written as one sentence came back after a relaunch as its pieces:
--
--   typed      gather plant fiber, then prepare a tinder nest, then spin the bow drill
--   reloaded   "gather plant fiber"  /  "… prepare a tinder nest"  /  "… spin the bow drill"
--
-- The ellipsis marks a continuation step, and that is honest about the steps — but the sentence the player
-- composed was nowhere, so the timeline before and after a relaunch disagreed about their own words. #873
-- recorded this as the known gap; this is the column that closes it.
--
-- NULLABLE ON PURPOSE. Every row written before this migration has no submission to recover — the original
-- was never stored — and inventing one by gluing the steps back together would produce a sentence the player
-- never wrote, with the connectives guessed. A null means "this row predates the record", and the readers
-- fall back to the step text, which is what those rows have always shown.
ALTER TABLE chronicle_action ADD COLUMN IF NOT EXISTS submission_text varchar(2500);

COMMENT ON COLUMN chronicle_action.submission_text IS
  'The whole text the player submitted, repeated on every step it was cut into, so a procedure reads back as '
  'the one sentence it was written as. Null for rows written before V464, and for those the step text stands.';

-- AND THE SET-ASIDE REMAINDER REMEMBERS IT TOO. A procedure that stops part-way keeps its undone steps so a
-- bare "carry on" can take them up (V311). Those steps were cut from the same sentence, so without this the
-- resumed half of a procedure would read back as pieces even though the done half read as the sentence --
-- the one submission split into two shapes by nothing but where it happened to stop.
ALTER TABLE chronicle_plan_remainder ADD COLUMN IF NOT EXISTS submission_text varchar(2500);

COMMENT ON COLUMN chronicle_plan_remainder.submission_text IS
  'The submission the undone steps were cut out of, carried so a "carry on" records the same words as the '
  'part that already ran. Null for remainders set aside before V464.';
