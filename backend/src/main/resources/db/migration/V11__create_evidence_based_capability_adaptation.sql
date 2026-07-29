CREATE TABLE chronicle_capability_adaptation (
    chronicle_id UUID PRIMARY KEY REFERENCES chronicle(id),
    load_conditioning REAL NOT NULL DEFAULT 0 CHECK (load_conditioning BETWEEN 0 AND 1),
    locomotion_familiarity REAL NOT NULL DEFAULT 0 CHECK (locomotion_familiarity BETWEEN 0 AND 1),
    fine_motor_familiarity REAL NOT NULL DEFAULT 0 CHECK (fine_motor_familiarity BETWEEN 0 AND 1),
    visual_aim_familiarity REAL NOT NULL DEFAULT 0 CHECK (visual_aim_familiarity BETWEEN 0 AND 1),
    attention_resilience REAL NOT NULL DEFAULT 0 CHECK (attention_resilience BETWEEN 0 AND 1),
    recovery_readiness REAL NOT NULL DEFAULT 0.5 CHECK (recovery_readiness BETWEEN 0 AND 1),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE chronicle_capability_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), chronicle_id UUID NOT NULL REFERENCES chronicle(id), action_id UUID NULL,
    occurred_at TIMESTAMPTZ NOT NULL, domain VARCHAR(40) NOT NULL, exposure_minutes INTEGER NOT NULL CHECK (exposure_minutes > 0),
    load_ratio REAL NOT NULL DEFAULT 0 CHECK (load_ratio BETWEEN 0 AND 1), recovery_context REAL NOT NULL DEFAULT 0 CHECK (recovery_context BETWEEN 0 AND 1), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(payload)='object')
);
CREATE INDEX idx_capability_evidence_chronicle_time ON chronicle_capability_evidence(chronicle_id,occurred_at);
CREATE OR REPLACE FUNCTION prevent_capability_evidence_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'capability evidence is immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER capability_evidence_is_immutable BEFORE UPDATE OR DELETE ON chronicle_capability_evidence FOR EACH ROW EXECUTE FUNCTION prevent_capability_evidence_mutation();
INSERT INTO chronicle_capability_adaptation (chronicle_id) SELECT id FROM chronicle ON CONFLICT DO NOTHING;
