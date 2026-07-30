-- How a chronicle finds its way back. Three kinds of knowledge make a place
-- locatable from afar: a physical marker left at a named, memorized spot; a route
-- written on a carried map; or passive routine from walking the same ground often.
-- A name blurted once, unmarked and never revisited, fades like real memory.

-- Physical markers left in the world: a blaze carved on a tree, a cairn of stones,
-- a driven stake. A marker makes a place recognizable; it carries no name of its own.
CREATE TABLE location_marker (
    object_id UUID PRIMARY KEY REFERENCES world_object(id),
    chunk_id UUID NOT NULL REFERENCES world_chunk(id),
    marker_kind VARCHAR(30) NOT NULL,
    description TEXT NULL,
    created_by_chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_location_marker_chunk ON location_marker(chunk_id);

-- The chronicle's route memory: how often and how recently it has stood in a chunk.
-- Frequent, recent presence becomes passive knowledge of the way there.
CREATE TABLE chronicle_chunk_visit (
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    chunk_id UUID NOT NULL REFERENCES world_chunk(id),
    visit_count INTEGER NOT NULL DEFAULT 1,
    last_visited_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (chronicle_id, chunk_id)
);

-- A name gains memory: whether the chronicle deliberately committed the place to
-- memory, and when it was last there, so unmarked names fade if never returned to.
ALTER TABLE chronicle_named_location
    ADD COLUMN memorized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_visited_at TIMESTAMPTZ NULL;
