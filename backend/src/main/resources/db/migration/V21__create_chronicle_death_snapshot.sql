CREATE TABLE chronicle_death_snapshot (
    chronicle_id UUID PRIMARY KEY REFERENCES chronicle(id),
    died_at TIMESTAMPTZ NOT NULL,
    death_location_id UUID NOT NULL REFERENCES world_object(id),
    cause VARCHAR(255) NOT NULL,
    body_snapshot JSONB NOT NULL,
    CHECK (jsonb_typeof(body_snapshot) = 'object')
);
CREATE OR REPLACE FUNCTION prevent_chronicle_death_snapshot_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'death snapshots are immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER chronicle_death_snapshot_is_immutable BEFORE UPDATE OR DELETE ON chronicle_death_snapshot FOR EACH ROW EXECUTE FUNCTION prevent_chronicle_death_snapshot_mutation();
