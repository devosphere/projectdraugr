-- A chronicle makes a place its own by naming it: the Sleeping Area, the Drinking
-- Area, Wolf Kingdom. The world is a grid of chunks; a named location is the
-- chronicle's designation of one chunk's role and identity. One name per chunk per
-- chronicle — re-designating replaces it, as renaming a place would.
CREATE TABLE chronicle_named_location (
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    chunk_id UUID NOT NULL REFERENCES world_chunk(id),
    name TEXT NOT NULL,
    purpose_tag VARCHAR(40) NULL,
    designated_at TIMESTAMPTZ NOT NULL,
    source_action_id UUID NULL,
    PRIMARY KEY (chronicle_id, chunk_id)
);

CREATE INDEX idx_named_location_chunk ON chronicle_named_location(chunk_id);
