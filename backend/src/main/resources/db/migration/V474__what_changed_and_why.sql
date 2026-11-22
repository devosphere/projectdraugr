-- V474: WHAT CHANGED, AND WHY (GitHub #953).
--
-- chronicle_capability_adaptation has held eight hidden numbers since V11, and every write to them has been
-- blind:
--
--     UPDATE chronicle_capability_adaptation SET load_conditioning = LEAST(1, load_conditioning + ?) ...
--
-- The value before the change was never read, so it was never recorded, so nothing anywhere can say what a
-- Chronicle's conditioning WAS, what moved it, or by how much. chronicle_capability_evidence records that an
-- exposure happened -- its domain, its minutes, its load -- but not what the exposure did. The cause is kept
-- and the effect is thrown away, which is the wrong half.
--
-- AND NOTHING EVER DECLINES. Of the eight columns, seven are written only as LEAST(1, col + delta) and the
-- eighth, recovery_readiness, is the only one that can fall. So a Chronicle who hauled timber for a week and
-- then sat by the fire for a month is, to the simulation, exactly as conditioned as the day they stopped.
-- #953 asks for this directly -- "Review existing conditioning gains and losses so sustained recovery and
-- neglect have appropriate effects" -- and the honest answer is that there were no losses to review.
--
-- TWO TABLES. metric_subject declares what can be tracked and where it lives, so a new metric joins by being
-- inserted rather than by a Java switch learning its name; and chronicle_metric_change is the immutable
-- causal ledger -- before, after, delta, cause, the action or event responsible, and the simulated hour.
--
-- THE LEDGER IS NOT ONLY A RECORD, IT IS THE STATE THE DECAY READS. When a metric last saw real practice is
-- MAX(occurred_at) for its PRACTICE rows, so neglect can be measured per metric without eight more columns
-- to keep in step. The record and the mechanic are the same fact, which is the only way they cannot drift.

-- ---------------------------------------------------------------------------------------------------
-- WHAT CAN BE TRACKED.
--
-- felt_step is how much a metric must move, in total, before it is worth telling someone about. #953:
-- "Avoid repetitive notices on every tiny change." A practice event moves a familiarity by about 0.002, so
-- a step of 0.05 is roughly twenty-five sessions of real work -- a change a person would actually notice in
-- their own hands, rather than a running commentary on arithmetic.
--
-- slips_per_idle_week is what neglect costs. Zero means the metric does not fade: recovery_readiness is a
-- state of the body rather than a thing learned, and it already moves both ways on its own.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE metric_subject (
    metric_key          VARCHAR(50) PRIMARY KEY,
    display_name        VARCHAR(80) NOT NULL,
    source_table        VARCHAR(60) NOT NULL,
    source_column       VARCHAR(60) NOT NULL,
    hidden              BOOLEAN     NOT NULL DEFAULT TRUE,
    felt_step           REAL        NOT NULL,
    slips_per_idle_week REAL        NOT NULL DEFAULT 0,
    notes               TEXT        NOT NULL,
    CONSTRAINT metric_subject_column_is_one_place UNIQUE (source_table, source_column),
    CONSTRAINT metric_felt_step_is_a_real_step    CHECK (felt_step > 0 AND felt_step <= 1),
    CONSTRAINT metric_slip_is_gentle              CHECK (slips_per_idle_week >= 0 AND slips_per_idle_week <= 0.1),
    CONSTRAINT metric_subject_needs_a_reason      CHECK (length(btrim(notes)) >= 40)
);

INSERT INTO metric_subject (metric_key, display_name, source_table, source_column, felt_step, slips_per_idle_week, notes) VALUES
 ('LOAD_CONDITIONING', 'what you can carry without it telling', 'chronicle_capability_adaptation', 'load_conditioning', 0.05, 0.010,
  'Built by hauling, breaking ground and felling. The first thing to go when a body stops working, and the most obviously felt when it does.'),
 ('LOCOMOTION_FAMILIARITY', 'how surely you move over bad ground', 'chronicle_capability_adaptation', 'locomotion_familiarity', 0.05, 0.008,
  'Built by travelling and by getting away from things. Footwork fades more slowly than strength but it does fade.'),
 ('FINE_MOTOR_FAMILIARITY', 'what your hands can do without watching them', 'chronicle_capability_adaptation', 'fine_motor_familiarity', 0.05, 0.004,
  'Built by skilled hand-work. The slowest of them to go: hands remember a long time, which is why the slip here is the gentlest.'),
 ('VISUAL_AIM_FAMILIARITY', 'whether the thing goes where you meant it', 'chronicle_capability_adaptation', 'visual_aim_familiarity', 0.05, 0.010,
  'Built by shooting, throwing and trapping. Aim is notoriously quick to desert anyone who stops practising it.'),
 ('ATTENTION_RESILIENCE', 'how much you notice, and for how long', 'chronicle_capability_adaptation', 'attention_resilience', 0.05, 0.006,
  'Built by looking, listening, tracking and appraising. Fades when nothing is asked of it, as any watchfulness does.'),
 ('INSIGHT_FAMILIARITY', 'how readily you work out how a thing works', 'chronicle_capability_adaptation', 'insight_familiarity', 0.05, 0.003,
  'Built by analysing and reasoning things through. Understanding is kept more easily than conditioning, so it slips least of all.'),
 ('KNOWLEDGE_FAMILIARITY', 'what you know and can call on', 'chronicle_capability_adaptation', 'knowledge_familiarity', 0.05, 0.003,
  'Built by reading and investigating. Knowledge is not lost so much as it goes quiet, so it slips as slowly as insight does.'),
 ('RECOVERY_READINESS', 'how well your body takes a rest', 'chronicle_capability_adaptation', 'recovery_readiness', 0.08, 0,
  'A state of the body rather than a thing learned, and the one number here that already moved both ways. It does not fade from neglect because it is not practised.');

-- ---------------------------------------------------------------------------------------------------
-- THE CAUSAL LEDGER. Immutable, like chronicle_capability_evidence and chronicle_skill_practice_event:
-- a record of what happened to a body is not something anything should be able to go back and tidy.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE chronicle_metric_change (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id     UUID        NOT NULL REFERENCES chronicle(id),
    metric_key       VARCHAR(50) NOT NULL REFERENCES metric_subject(metric_key),
    occurred_at      TIMESTAMPTZ NOT NULL,
    before_value     REAL        NOT NULL,
    after_value      REAL        NOT NULL,
    delta            REAL        NOT NULL,
    cause            VARCHAR(30) NOT NULL,
    source_action_id UUID,
    source_event     VARCHAR(60),
    exposure_minutes INTEGER,
    recovery_context REAL,
    told             BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT metric_change_cause_is_known CHECK (cause IN (
        -- Why a hidden number moved. PRACTICE and NEGLECT are the two halves #953 asks to be balanced;
        -- the rest are here so the immunity and resistance work (#950, #951, #952) has somewhere to write.
        'PRACTICE', 'NEGLECT', 'RECOVERY', 'EXPOSURE', 'ILLNESS', 'INJURY', 'NOURISHMENT', 'CORRECTION')),
    CONSTRAINT metric_change_delta_agrees   CHECK (abs(delta - (after_value - before_value)) < 0.000001),
    CONSTRAINT metric_change_is_a_change    CHECK (delta <> 0),
    CONSTRAINT metric_change_has_a_source   CHECK (source_action_id IS NOT NULL OR source_event IS NOT NULL),
    CONSTRAINT metric_change_exposure_real  CHECK (exposure_minutes IS NULL OR exposure_minutes > 0)
);

-- The decay reads MAX(occurred_at) per metric for a chronicle, and the narration reads the untold rows, so
-- both of those are the index.
CREATE INDEX idx_metric_change_chronicle_metric_time
    ON chronicle_metric_change (chronicle_id, metric_key, occurred_at DESC);
CREATE INDEX idx_metric_change_untold
    ON chronicle_metric_change (chronicle_id, told) WHERE NOT told;

CREATE OR REPLACE FUNCTION prevent_metric_change_rewrite() RETURNS trigger AS $$
BEGIN
    -- `told` is the one field that may move, and only ever from false to true: it records that a notice has
    -- been given, which is not a change to what happened. Everything else about a recorded change is final.
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'metric change evidence is immutable'; END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.chronicle_id     IS DISTINCT FROM OLD.chronicle_id
       OR NEW.metric_key       IS DISTINCT FROM OLD.metric_key
       OR NEW.occurred_at      IS DISTINCT FROM OLD.occurred_at
       OR NEW.before_value     IS DISTINCT FROM OLD.before_value
       OR NEW.after_value      IS DISTINCT FROM OLD.after_value
       OR NEW.delta            IS DISTINCT FROM OLD.delta
       OR NEW.cause            IS DISTINCT FROM OLD.cause
       OR NEW.source_action_id IS DISTINCT FROM OLD.source_action_id
       OR NEW.source_event     IS DISTINCT FROM OLD.source_event
       OR NEW.exposure_minutes IS DISTINCT FROM OLD.exposure_minutes
       OR NEW.recovery_context IS DISTINCT FROM OLD.recovery_context
       OR (OLD.told AND NOT NEW.told)
    THEN RAISE EXCEPTION 'metric change evidence is immutable'; END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER metric_change_is_immutable
    BEFORE UPDATE OR DELETE ON chronicle_metric_change
    FOR EACH ROW EXECUTE FUNCTION prevent_metric_change_rewrite();
