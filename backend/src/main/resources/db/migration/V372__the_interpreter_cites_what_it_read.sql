-- #37 — the interpreter says what it was looking at, and is refused when it was looking at nothing.
--
-- #673 gave every candidate step a verdict and a reason code in `ai_procedure_receipt`: what was proposed, what
-- the world allowed, and why. #37's remaining complaint is the other half of the same contract — the reply cites
-- the PROCESS it means and never the THINGS it means, so a receipt records that `tan_hide` was proposed and
-- refused without recording that the model believed there was a hide.
--
-- WHY THAT IS NOT BOOKKEEPING. A model that composes a plan out of materials the Chronicle is not carrying has
-- not misjudged the catalogue; it has imagined the world. The deterministic gate catches it at the moment of
-- execution — the process asks for a hide, finds none, and refuses — but by then the reason code says
-- REFUSED_BY_THE_WORLD, which is the same thing it says when a Chronicle genuinely runs out halfway through a
-- real chain. The two are not the same event and the record could not tell them apart.
--
-- WHAT THIS ADDS.
--   * `cited_context` — the carried things the reply named, kept only when they were actually offered to it.
--     The same defensive parse the process keys already get: a model cannot smuggle in a thing that is not there.
--   * `CITED_WHAT_IS_NOT_THERE` — a reason code for a plan thrown out before a single step runs, because what it
--     rests on does not exist. Cheap, safe, and it costs the world nothing to refuse.
--
-- This mirrors the guard SimulationNarrator already carries for prose (claimsWhatIsNotThere): the narrator may
-- not describe a thing that is not on the ground, and now the interpreter may not plan from one.

ALTER TABLE ai_procedure_receipt ADD COLUMN cited_context VARCHAR(100)[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN ai_procedure_receipt.cited_context IS
  '#37: the carried item keys the interpreter named as the basis of this plan, filtered to the ones it was actually offered. Empty when it cited nothing, which is allowed; a citation of something absent is refused instead of recorded.';

ALTER TABLE ai_procedure_receipt DROP CONSTRAINT ai_procedure_receipt_reason_code_check;
ALTER TABLE ai_procedure_receipt ADD CONSTRAINT ai_procedure_receipt_reason_code_check
    CHECK (reason_code IN ('RAN', 'REFUSED_BY_THE_WORLD', 'NO_SUCH_PROCESS', 'NOT_SURE_ENOUGH', 'CITED_WHAT_IS_NOT_THERE'));

COMMENT ON COLUMN ai_procedure_receipt.reason_code IS
  '#37/#673: why the world answered as it did. RAN and REFUSED_BY_THE_WORLD are the deterministic side''s verdict on a step that was attempted; NO_SUCH_PROCESS and NOT_SURE_ENOUGH throw the plan out before it runs; CITED_WHAT_IS_NOT_THERE throws it out because the model was reasoning from things the Chronicle does not have.';
