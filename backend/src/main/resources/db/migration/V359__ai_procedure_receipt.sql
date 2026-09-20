-- #37 — the boundary between what was proposed and what the world allowed is a record, not an inference.
--
-- When the deterministic classifier misses, the interpreter proposes an ordered plan of EXISTING processes. Until
-- now what happened to that plan was visible only as prose: a player saw a refusal, and nobody could say afterwards
-- which candidate was proposed, which the world ran, which it refused and why. That is the half of the ticket's
-- complaint that is about the architecture rather than about any one action.
--
-- Each candidate step now gets a verdict with a reason code, written here as it is decided. Append-only: it is a
-- record of decisions, and a decision that has been made does not change afterwards.

CREATE TABLE ai_procedure_receipt (
    id              BIGSERIAL PRIMARY KEY,
    chronicle_id    UUID NOT NULL REFERENCES chronicle(id),
    action_text     TEXT NOT NULL,
    candidate_key   VARCHAR(80) NOT NULL,
    step_index      INTEGER NOT NULL CHECK (step_index >= 1),
    verdict         VARCHAR(8) NOT NULL CHECK (verdict IN ('ACCEPTED','REJECTED')),
    reason_code     VARCHAR(24) NOT NULL CHECK (reason_code IN ('RAN','REFUSED_BY_THE_WORLD','NO_SUCH_PROCESS','NOT_SURE_ENOUGH')),
    confidence      INTEGER NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    occurred_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ai_procedure_receipt_by_chronicle ON ai_procedure_receipt (chronicle_id, occurred_at);

CREATE OR REPLACE FUNCTION ai_procedure_receipt_is_a_record() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'a resolution receipt is a record of what was decided and cannot be % (#37)', lower(TG_OP);
END $$ LANGUAGE plpgsql;

CREATE TRIGGER ai_procedure_receipt_append_only
  BEFORE UPDATE OR DELETE ON ai_procedure_receipt
  FOR EACH ROW EXECUTE FUNCTION ai_procedure_receipt_is_a_record();

COMMENT ON TABLE ai_procedure_receipt IS
  '#37: what the interpreter proposed and what the deterministic resolver did with each candidate, with a reason code. Append-only.';
