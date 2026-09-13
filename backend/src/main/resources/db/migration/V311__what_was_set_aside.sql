-- #38 — what was set aside, so a stopped procedure can be taken up again.
--
-- #495 made a written procedure mean what it says: each declared step is worked in order, and a step that fails
-- stops the plan with the work before it standing. What it left undone was the part the ticket still names as
-- missing — "restart/resume of a partially-executed plan". The undone steps existed only in the narration. A
-- Chronicle who wrote eight steps of hut-building and ran out of clay at step three had to fetch the clay and then
-- retype steps three to eight from memory, which is the same "typed a lot for nothing" failure #38 was opened for,
-- moved one turn later.
--
-- This table remembers the rest. When a plan stops — at a failed step, or at the eight-step limit of one stretch
-- of effort — the steps not yet done are kept here, in the player's order and in the player's words. "Carry on"
-- takes them up again. Nothing is re-worded, re-ordered or invented; the steps are replayed exactly as written.
--
-- WHY ROWS ARE ENDED RATHER THAN DELETED. A set-aside plan is part of what happened. It is RESUMED when taken up,
-- REPLACED when the player writes a new procedure instead, ENDED when the Chronicle who held it dies. At most one is
-- OPEN per Chronicle, enforced by the partial unique index, because "carry on" has to mean one thing.
--
-- source_action_id is deliberately not a foreign key: the step a plan stopped on can be a pre-pass answer to
-- gibberish, which is reported with an identity but never written to chronicle_action.

CREATE TABLE chronicle_plan_remainder (
    id                UUID PRIMARY KEY,
    chronicle_id      UUID        NOT NULL REFERENCES chronicle(id),
    steps             TEXT[]      NOT NULL,
    stopped_because   VARCHAR(20) NOT NULL,
    set_aside_at      TIMESTAMPTZ NOT NULL,
    source_action_id  UUID,
    state             VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    resume_key        UUID,
    resumed_at        TIMESTAMPTZ,
    CONSTRAINT plan_remainder_has_steps   CHECK (cardinality(steps) >= 1),
    CONSTRAINT plan_remainder_why         CHECK (stopped_because IN ('FAILED_STEP','STEP_LIMIT')),
    CONSTRAINT plan_remainder_state       CHECK (state IN ('OPEN','RESUMED','REPLACED','ENDED')),
    CONSTRAINT plan_remainder_resume_pair CHECK ((state = 'RESUMED') = (resumed_at IS NOT NULL))
);

CREATE UNIQUE INDEX plan_remainder_one_open ON chronicle_plan_remainder (chronicle_id) WHERE state = 'OPEN';
CREATE UNIQUE INDEX plan_remainder_resume_key ON chronicle_plan_remainder (resume_key) WHERE resume_key IS NOT NULL;

COMMENT ON TABLE chronicle_plan_remainder IS
  'The steps of a written procedure (#38) that were not done when it stopped, kept in the player''s own order and '
  'words so "carry on" can take them up. Read and written only by ChronicleActionService.resolvePlan.';
