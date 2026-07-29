CREATE TABLE chronicle_condition_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    condition_kind VARCHAR(40) NOT NULL,
    severity SMALLINT NOT NULL CHECK (severity BETWEEN 0 AND 100),
    source_action_id UUID NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_chronicle_condition_event_timeline ON chronicle_condition_event(chronicle_id, occurred_at);
CREATE OR REPLACE FUNCTION prevent_condition_event_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'condition history is immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER chronicle_condition_event_is_immutable BEFORE UPDATE OR DELETE ON chronicle_condition_event FOR EACH ROW EXECUTE FUNCTION prevent_condition_event_mutation();
