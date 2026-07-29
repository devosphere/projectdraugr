CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE world_object (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_type VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    lifecycle_state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    current_location_id UUID NULL,
    current_owner_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    destroyed_at TIMESTAMPTZ NULL,
    CHECK (current_location_id IS NOT NULL OR current_owner_id IS NOT NULL OR lifecycle_state = 'DESTROYED'),
    CHECK (NOT (current_location_id IS NOT NULL AND current_owner_id IS NOT NULL))
);

ALTER TABLE world_object
    ADD CONSTRAINT fk_world_object_location FOREIGN KEY (current_location_id) REFERENCES world_object(id),
    ADD CONSTRAINT fk_world_object_owner FOREIGN KEY (current_owner_id) REFERENCES world_object(id);

CREATE TABLE simulation_clock (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    simulated_at TIMESTAMPTZ NOT NULL,
    tick BIGINT NOT NULL DEFAULT 0 CHECK (tick >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO simulation_clock (id, simulated_at) VALUES (1, now());

CREATE TABLE world_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NULL REFERENCES world_object(id),
    causation_id UUID NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_world_event_occurred_at ON world_event (occurred_at);
CREATE INDEX idx_world_event_aggregate ON world_event (aggregate_id, occurred_at);

CREATE OR REPLACE FUNCTION prevent_world_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'world_event is an immutable historical archive';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER world_event_is_immutable
BEFORE UPDATE OR DELETE ON world_event
FOR EACH ROW EXECUTE FUNCTION prevent_world_event_mutation();
