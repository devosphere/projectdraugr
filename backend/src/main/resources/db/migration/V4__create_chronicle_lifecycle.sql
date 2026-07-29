CREATE TABLE chronicle (
    id UUID PRIMARY KEY REFERENCES world_object(id),
    world_id UUID NOT NULL REFERENCES world_genesis(world_id),
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    life_state VARCHAR(20) NOT NULL CHECK (life_state IN ('LIVING', 'DEAD')),
    arrived_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    died_at TIMESTAMPTZ NULL,
    death_cause VARCHAR(255) NULL,
    UNIQUE (world_id, sequence_number),
    CHECK ((life_state = 'LIVING' AND died_at IS NULL) OR (life_state = 'DEAD' AND died_at IS NOT NULL))
);

-- Local-first MVP has one local player profile, and therefore one living body.
CREATE UNIQUE INDEX one_living_chronicle ON chronicle ((1)) WHERE life_state = 'LIVING';

CREATE TABLE chronicle_body (
    chronicle_id UUID PRIMARY KEY REFERENCES chronicle(id),
    health VARCHAR(40) NOT NULL,
    condition_summary VARCHAR(100) NOT NULL,
    hunger VARCHAR(40) NOT NULL,
    thirst VARCHAR(40) NOT NULL,
    energy VARCHAR(40) NOT NULL,
    temperature VARCHAR(40) NOT NULL,
    wetness VARCHAR(40) NOT NULL,
    bladder VARCHAR(40) NOT NULL,
    bowel VARCHAR(40) NOT NULL,
    hygiene VARCHAR(40) NOT NULL
);

CREATE TABLE chronicle_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_chronicle_event_timeline ON chronicle_event (chronicle_id, occurred_at);

CREATE OR REPLACE FUNCTION prevent_chronicle_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'chronicle_event is an immutable personal archive';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER chronicle_event_is_immutable
BEFORE UPDATE OR DELETE ON chronicle_event
FOR EACH ROW EXECUTE FUNCTION prevent_chronicle_event_mutation();
