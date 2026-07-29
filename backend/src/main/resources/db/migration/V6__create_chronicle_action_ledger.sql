CREATE TABLE chronicle_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ NOT NULL,
    action_text VARCHAR(2500) NOT NULL CHECK (length(trim(action_text)) BETWEEN 1 AND 2500),
    intent_type VARCHAR(50) NOT NULL,
    outcome VARCHAR(30) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'PARTIAL')),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0)
);

CREATE TABLE chronicle_action_effect (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id UUID NOT NULL REFERENCES chronicle_action(id),
    effect_domain VARCHAR(50) NOT NULL,
    effect_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_chronicle_action_timeline ON chronicle_action (chronicle_id, resolved_at);
CREATE INDEX idx_chronicle_action_effect_action ON chronicle_action_effect (action_id);

CREATE OR REPLACE FUNCTION prevent_chronicle_action_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'chronicle_action and its effects are immutable history';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER chronicle_action_is_immutable
BEFORE UPDATE OR DELETE ON chronicle_action
FOR EACH ROW EXECUTE FUNCTION prevent_chronicle_action_mutation();

CREATE TRIGGER chronicle_action_effect_is_immutable
BEFORE UPDATE OR DELETE ON chronicle_action_effect
FOR EACH ROW EXECUTE FUNCTION prevent_chronicle_action_mutation();
